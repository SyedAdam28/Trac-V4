package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.dao.AppSettingsDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.JobEntryDao
import com.example.data.dao.PartnerDao
import com.example.data.dao.TractorDao
import com.example.data.dao.WithdrawalDao
import com.example.data.database.AppDatabase
import com.example.data.entity.SyncStatus
import com.example.data.sync.FirestoreMappers.toFirestore
import com.example.data.sync.FirestoreMappers.toRoomEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    object Offline : SyncState()
}

class FirestoreSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "FirestoreSyncManager"
    private val PREFS_NAME = "aidhunt_sync_prefs"
    private val KEY_DEVICE_ID = "device_id"

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val customerDao: CustomerDao = database.customerDao()
    private val jobDao: JobEntryDao = database.jobEntryDao()
    private val expenseDao: ExpenseDao = database.expenseDao()
    private val withdrawalDao: WithdrawalDao = database.withdrawalDao()
    private val tractorDao: TractorDao = database.tractorDao()
    private val partnerDao: PartnerDao = database.partnerDao()
    private val settingsDao: AppSettingsDao = database.appSettingsDao()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not initialized: ${e.message}")
            null
        }
    }

    /**
     * Get or create a persistent, unique installation Device ID
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * Get active business ID
     */
    suspend fun getBusinessId(): String = withContext(Dispatchers.IO) {
        val settings = settingsDao.getSettingsOnce()
        settings?.businessId ?: ""
    }

    /**
     * Full Bi-directional Synchronisation:
     * 1. Room -> Firestore (Upload pending local changes)
     * 2. Firestore -> Room (Download cloud changes from other partners)
     * 3. Recalculate customer statistics
     */
    suspend fun synchronize(isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isOnline) {
            _syncState.value = SyncState.Offline
            return@withContext false
        }

        val db = firestore
        if (db == null) {
            Log.w(TAG, "Firestore is unavailable. Skipping cloud sync safely.")
            _syncState.value = SyncState.Offline
            return@withContext false
        }

        val businessId = getBusinessId()
        // GUARD: never sync against the shared demo account or a blank businessId.
        // Uploading to the demo account would expose user data to all demo users.
        val blockedIds = setOf("", "AIDHUNT-TRAC-SHARED-01")
        if (businessId in blockedIds) {
            Log.w(TAG, "Sync blocked: businessId='$businessId' is invalid or shared demo account.")
            _syncState.value = SyncState.Error("Please log in to sync your business data.")
            return@withContext false
        }
        // GUARD: only sync when Firebase Auth user is authenticated
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            Log.w(TAG, "Sync blocked: no authenticated Firebase user.")
            _syncState.value = SyncState.Error("Not logged in.")
            return@withContext false
        }
        val deviceId = getDeviceId()

        _syncState.value = SyncState.Syncing

        try {
            // STEP 0: Self-register this user as a member in Firestore (idempotent)
            registerCurrentUserAsMember(db, businessId)

            // STEP A: Upload Pending Local Records to Firestore
            uploadPendingCustomers(db, businessId, deviceId)
            uploadPendingTractors(db, businessId, deviceId)
            uploadPendingPartners(db, businessId, deviceId)
            uploadPendingJobs(db, businessId, deviceId)
            uploadPendingExpenses(db, businessId, deviceId)
            uploadPendingWithdrawals(db, businessId, deviceId)

            // STEP B: Download Cloud Records from Firestore -> Room
            downloadRemoteCustomers(db, businessId)
            downloadRemoteTractors(db, businessId)
            downloadRemotePartners(db, businessId)
            downloadRemoteJobs(db, businessId)
            downloadRemoteExpenses(db, businessId)
            downloadRemoteWithdrawals(db, businessId)

            // STEP C: Recalculate customer stats from local transaction ledger
            recalculateAllCustomerLedgers()

            // Update last sync time
            val now = System.currentTimeMillis()
            val settings = settingsDao.getSettingsOnce()
            if (settings != null) {
                settingsDao.insertOrUpdateSettings(settings.copy(lastSyncTime = now, deviceId = deviceId))
            }

            _syncState.value = SyncState.Success("Cloud sync complete", now)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed safely: ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync temporary failure")
            false
        }
    }

    /**
     * Self-registers the current Firebase user as a member of the given business.
     * - Only writes if no membership document exists yet (idempotent).
     * - Reads the actual role from the local Room membership table before writing.
     *   This prevents a Partner device from overwriting their role with OWNER.
     */
    private suspend fun registerCurrentUserAsMember(db: FirebaseFirestore, businessId: String) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val memberRef = db.collection("businesses").document(businessId)
                .collection("memberships").document(uid)

            // If the document already exists in Firestore, do nothing
            val existing = memberRef.get().await()
            if (existing.exists()) return

            // Determine the correct role from the local Room membership cache
            val localMembership = database.membershipDao().getMembership(businessId, uid)
            val role = localMembership?.role ?: "PARTNER"  // default to PARTNER if unknown

            memberRef.set(
                mapOf(
                    "membershipId" to uid,
                    "businessId" to businessId,
                    "userId" to uid,
                    "role" to role,
                    "status" to "ACTIVE",
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Log.d(TAG, "Membership self-registered: businessId=$businessId uid=$uid role=$role")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register membership (non-fatal): ${e.message}")
        }
    }

    // ================= UPLOAD LOGIC (Room -> Firestore) =================

    /**
     * Ensures every entity has a non-blank uuid before it is used as a
     * Firestore document ID.  If the uuid was seeded blank (pre-migration
     * records from before v4), we generate one now and persist it so the
     * same record always maps to the same Firestore document.
     */
    private suspend fun ensureUuid(uuid: String, persistFn: suspend (String) -> Unit): String {
        if (uuid.isNotBlank()) return uuid
        val newUuid = UUID.randomUUID().toString()
        try { persistFn(newUuid) } catch (_: Exception) {}
        return newUuid
    }

    private suspend fun uploadPendingCustomers(db: FirebaseFirestore, businessId: String, deviceId: String) {
        val pending = customerDao.getUnsyncedCustomers()
        for (c in pending) {
            try {
                customerDao.updateSyncStatus(c.id, SyncStatus.SYNCING.name)
                val safeUuid = ensureUuid(c.uuid) { newId ->
                    customerDao.updateCustomer(c.copy(uuid = newId))
                }
                val dto = c.copy(uuid = safeUuid).toFirestore(businessId, deviceId)
                db.collection("businesses").document(businessId)
                    .collection("customers").document(dto.id)
                    .set(dto, SetOptions.merge()).await()
                customerDao.updateSyncStatus(c.id, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload customer ${c.id}: ${e.message}")
                customerDao.updateSyncStatus(c.id, SyncStatus.FAILED.name)
            }
        }
    }

    private suspend fun uploadPendingTractors(db: FirebaseFirestore, businessId: String, deviceId: String) {
        val pending = tractorDao.getUnsyncedTractors()
        for (t in pending) {
            try {
                val safeUuid = ensureUuid(t.uuid) { newId ->
                    tractorDao.updateTractor(t.copy(uuid = newId))
                }
                val dto = t.copy(uuid = safeUuid).toFirestore(businessId, deviceId)
                db.collection("businesses").document(businessId)
                    .collection("tractors").document(dto.id)
                    .set(dto, SetOptions.merge()).await()
                tractorDao.markTractorsSynced(listOf(t.id))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload tractor ${t.id}: ${e.message}")
            }
        }
    }

    private suspend fun uploadPendingPartners(db: FirebaseFirestore, businessId: String, deviceId: String) {
        val pending = partnerDao.getUnsyncedPartners()
        for (p in pending) {
            try {
                val safeUuid = ensureUuid(p.uuid) { newId ->
                    partnerDao.updatePartner(p.copy(uuid = newId))
                }
                val dto = p.copy(uuid = safeUuid).toFirestore(businessId, deviceId)
                db.collection("businesses").document(businessId)
                    .collection("partners").document(dto.id)
                    .set(dto, SetOptions.merge()).await()
                partnerDao.markPartnersSynced(listOf(p.id))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload partner ${p.id}: ${e.message}")
            }
        }
    }

    private suspend fun uploadPendingJobs(db: FirebaseFirestore, businessId: String, deviceId: String) {
        val pending = jobDao.getUnsyncedJobs()
        for (j in pending) {
            try {
                jobDao.updateSyncStatus(j.id, SyncStatus.SYNCING.name)
                val safeUuid = ensureUuid(j.uuid) { newId ->
                    jobDao.updateJob(j.copy(uuid = newId))
                }
                val dto = j.copy(uuid = safeUuid).toFirestore(businessId, deviceId)
                db.collection("businesses").document(businessId)
                    .collection("jobs").document(dto.id)
                    .set(dto, SetOptions.merge()).await()
                jobDao.updateSyncStatus(j.id, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload job ${j.id}: ${e.message}")
                jobDao.updateSyncStatus(j.id, SyncStatus.FAILED.name)
            }
        }
    }

    private suspend fun uploadPendingExpenses(db: FirebaseFirestore, businessId: String, deviceId: String) {
        val pending = expenseDao.getUnsyncedExpenses()
        for (ex in pending) {
            try {
                expenseDao.updateSyncStatus(ex.id, SyncStatus.SYNCING.name)
                val safeUuid = ensureUuid(ex.uuid) { newId ->
                    expenseDao.updateExpense(ex.copy(uuid = newId))
                }
                val dto = ex.copy(uuid = safeUuid).toFirestore(businessId, deviceId)
                db.collection("businesses").document(businessId)
                    .collection("expenses").document(dto.id)
                    .set(dto, SetOptions.merge()).await()
                expenseDao.updateSyncStatus(ex.id, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload expense ${ex.id}: ${e.message}")
                expenseDao.updateSyncStatus(ex.id, SyncStatus.FAILED.name)
            }
        }
    }

    private suspend fun uploadPendingWithdrawals(db: FirebaseFirestore, businessId: String, deviceId: String) {
        val pending = withdrawalDao.getUnsyncedWithdrawals()
        for (w in pending) {
            try {
                withdrawalDao.updateSyncStatus(w.id, SyncStatus.SYNCING.name)
                val safeUuid = ensureUuid(w.uuid) { newId ->
                    withdrawalDao.updateWithdrawal(w.copy(uuid = newId))
                }
                val dto = w.copy(uuid = safeUuid).toFirestore(businessId, deviceId)
                db.collection("businesses").document(businessId)
                    .collection("withdrawals").document(dto.id)
                    .set(dto, SetOptions.merge()).await()
                withdrawalDao.updateSyncStatus(w.id, SyncStatus.SYNCED.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload withdrawal ${w.id}: ${e.message}")
                withdrawalDao.updateSyncStatus(w.id, SyncStatus.FAILED.name)
            }
        }
    }

    // ================= DOWNLOAD LOGIC (Firestore -> Room) =================

    private suspend fun downloadRemoteCustomers(db: FirebaseFirestore, businessId: String) {
        val snapshot = db.collection("businesses").document(businessId)
            .collection("customers").get().await()

        for (doc in snapshot.documents) {
            val remote = doc.toObject(FirestoreCustomer::class.java) ?: continue
            val existing = customerDao.getCustomerByUuid(remote.id)

            if (existing == null) {
                // New customer from another partner device -> Insert into Room
                customerDao.insertCustomer(remote.toRoomEntity())
            } else {
                // Conflict resolution: only update if remote is newer and local is not PENDING
                if (existing.syncStatus != SyncStatus.PENDING.name && remote.updatedAt >= existing.updatedAt) {
                    customerDao.updateCustomer(remote.toRoomEntity(existingLocalId = existing.id))
                }
            }
        }
    }

    private suspend fun downloadRemoteTractors(db: FirebaseFirestore, businessId: String) {
        val snapshot = db.collection("businesses").document(businessId)
            .collection("tractors").get().await()

        for (doc in snapshot.documents) {
            val remote = doc.toObject(FirestoreTractor::class.java) ?: continue
            val existing = tractorDao.getTractorByUuid(remote.id)

            if (existing == null) {
                tractorDao.insertTractor(remote.toRoomEntity())
            } else {
                if (remote.updatedAt >= existing.updatedAt) {
                    tractorDao.updateTractor(remote.toRoomEntity(existingLocalId = existing.id))
                }
            }
        }
    }

    private suspend fun downloadRemotePartners(db: FirebaseFirestore, businessId: String) {
        val snapshot = db.collection("businesses").document(businessId)
            .collection("partners").get().await()

        for (doc in snapshot.documents) {
            val remote = doc.toObject(FirestorePartner::class.java) ?: continue
            val existing = partnerDao.getPartnerByUuid(remote.id)

            if (existing == null) {
                partnerDao.insertPartner(remote.toRoomEntity())
            } else {
                if (remote.updatedAt >= existing.updatedAt) {
                    partnerDao.updatePartner(remote.toRoomEntity(existingLocalId = existing.id, isCurrentActive = existing.isCurrentActive))
                }
            }
        }
    }

    private suspend fun downloadRemoteJobs(db: FirebaseFirestore, businessId: String) {
        val snapshot = db.collection("businesses").document(businessId)
            .collection("jobs").get().await()

        for (doc in snapshot.documents) {
            val remote = doc.toObject(FirestoreJobEntry::class.java) ?: continue
            val existing = jobDao.getJobByUuid(remote.id)

            // Resolve local customer id if available
            val localCustomer = customerDao.getCustomerByUuid(remote.customerUuid)
            val localCustomerId = localCustomer?.id ?: 0L

            if (existing == null) {
                jobDao.insertJob(remote.toRoomEntity(localCustomerId = localCustomerId))
            } else {
                // Conflict handling: Keep local if PENDING; otherwise take newer updatedAt
                if (existing.syncStatus != SyncStatus.PENDING.name && remote.updatedAt >= existing.updatedAt) {
                    jobDao.updateJob(remote.toRoomEntity(existingLocalId = existing.id, localCustomerId = localCustomerId))
                }
            }
        }
    }

    private suspend fun downloadRemoteExpenses(db: FirebaseFirestore, businessId: String) {
        val snapshot = db.collection("businesses").document(businessId)
            .collection("expenses").get().await()

        for (doc in snapshot.documents) {
            val remote = doc.toObject(FirestoreExpense::class.java) ?: continue
            val existing = expenseDao.getExpenseByUuid(remote.id)

            if (existing == null) {
                expenseDao.insertExpense(remote.toRoomEntity())
            } else {
                if (existing.syncStatus != SyncStatus.PENDING.name && remote.updatedAt >= existing.updatedAt) {
                    expenseDao.updateExpense(remote.toRoomEntity(existingLocalId = existing.id))
                }
            }
        }
    }

    private suspend fun downloadRemoteWithdrawals(db: FirebaseFirestore, businessId: String) {
        val snapshot = db.collection("businesses").document(businessId)
            .collection("withdrawals").get().await()

        for (doc in snapshot.documents) {
            val remote = doc.toObject(FirestoreWithdrawal::class.java) ?: continue
            val existing = withdrawalDao.getWithdrawalByUuid(remote.id)

            if (existing == null) {
                withdrawalDao.insertWithdrawal(remote.toRoomEntity())
            } else {
                if (existing.syncStatus != SyncStatus.PENDING.name && remote.updatedAt >= existing.updatedAt) {
                    withdrawalDao.updateWithdrawal(remote.toRoomEntity(existingLocalId = existing.id))
                }
            }
        }
    }

    /**
     * Recalculates total billed, total paid, and balance due for all customers from local transactions
     */
    private suspend fun recalculateAllCustomerLedgers() {
        val customers = customerDao.getUnsyncedCustomers() // or all customers
        // Get all customers from DB
        val allCustomers = database.openHelper.readableDatabase.query("SELECT id FROM customers")
        val customerIds = mutableListOf<Long>()
        while (allCustomers.moveToNext()) {
            customerIds.add(allCustomers.getLong(0))
        }
        allCustomers.close()

        for (cId in customerIds) {
            val customer = customerDao.getCustomerById(cId) ?: continue
            // Query jobs for this customer
            val jobsCursor = database.openHelper.readableDatabase.query(
                "SELECT totalAmount, amountReceived, pendingAmount, workType FROM job_entries WHERE customerId = $cId"
            )
            var billed = 0.0
            var paid = 0.0
            while (jobsCursor.moveToNext()) {
                val totalAmount = jobsCursor.getDouble(0)
                val amountReceived = jobsCursor.getDouble(1)
                val workType = jobsCursor.getString(3)
                if (workType == "Payment Received") {
                    paid += amountReceived
                } else {
                    billed += totalAmount
                    paid += amountReceived
                }
            }
            jobsCursor.close()
            val due = (billed - paid).coerceAtLeast(0.0)
            customerDao.updateCustomer(
                customer.copy(
                    totalBilled = billed,
                    totalPaid = paid,
                    balanceDue = due,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
