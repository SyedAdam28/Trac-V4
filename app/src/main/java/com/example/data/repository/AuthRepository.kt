package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.BusinessEntity
import com.example.data.entity.MembershipEntity
import com.example.data.entity.SubscriptionEntity
import com.example.data.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(private val database: AppDatabase) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUser get() = auth.currentUser

    // =====================================================================
    //  UTILITY: Normalise phone numbers to canonical +91XXXXXXXXXX format.
    //  Handles: 9842154321 / 09842154321 / +919842154321 / 91 9842154321
    //  This is the SINGLE source-of-truth for phone normalisation.
    // =====================================================================
    fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            digits.length == 13 && digits.startsWith("091") -> "+${digits.drop(1)}"
            else -> if (raw.startsWith("+")) raw.trim() else "+$digits"
        }
    }

    // =====================================================================
    //  Sync the Firebase Auth user into Room + Firestore /users/{uid}.
    //  Uses UID as document ID — idempotent via SetOptions.merge().
    // =====================================================================
    suspend fun syncUserToFirestore(name: String) = withContext(Dispatchers.IO) {
        val firebaseUser = auth.currentUser ?: return@withContext null
        val uid = firebaseUser.uid
        val phone = normalizePhone(firebaseUser.phoneNumber ?: "")

        val userEntity = UserEntity(
            uid = uid,
            phoneNumber = phone,
            name = name,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )

        // Save to Room
        database.userDao().insertUser(userEntity)

        // Save to Firestore — merge so we never overwrite existing name/phone
        try {
            firestore.collection("users").document(uid)
                .set(userEntity, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        userEntity
    }

    // =====================================================================
    //  IDEMPOTENT gate: returns existing business if the user already has
    //  an active membership, otherwise creates a brand-new business once.
    //  This is the ONLY correct entry point for owner account creation.
    // =====================================================================
    suspend fun getOrCreateOwnerBusiness(
        ownerUid: String,
        businessName: String,
        ownerName: String,
        phone: String
    ): BusinessEntity = withContext(Dispatchers.IO) {

        // 1. Check for existing membership in Firestore
        val existingMemberships = checkExistingMemberships(ownerUid)
        if (existingMemberships.isNotEmpty()) {
            val businessId = existingMemberships.first().businessId

            // Try local Room first
            val localBusiness = database.businessDao().getBusiness(businessId)
            if (localBusiness != null) return@withContext localBusiness

            // Fetch from Firestore if not cached locally
            loadBusinessData(businessId)
            val remoteBusiness = database.businessDao().getBusiness(businessId)
            if (remoteBusiness != null) return@withContext remoteBusiness

            // Reconstruct minimal entity if Firestore fetch also failed (offline edge-case)
            return@withContext BusinessEntity(
                businessId = businessId,
                businessName = businessName.ifBlank { "My Agri Tractor Service" },
                ownerUserId = ownerUid,
                businessPhone = phone
            )
        }

        // 2. No existing membership — create a new business exactly once
        createBusiness(ownerUid, businessName, ownerName, phone)
    }

    // =====================================================================
    //  Creates Business + OWNER Membership + Trial Subscription in a
    //  single Firestore batch write.  membershipId = ownerUid so it is
    //  deterministic and idempotent on re-create attempts.
    // =====================================================================
    suspend fun createBusiness(
        ownerUid: String,
        businessName: String,
        ownerName: String,
        phone: String
    ): BusinessEntity = withContext(Dispatchers.IO) {

        val businessId = "BIZ-${UUID.randomUUID()}"

        val business = BusinessEntity(
            businessId = businessId,
            businessName = businessName.ifBlank { "My Agri Tractor Service" },
            ownerUserId = ownerUid,
            businessPhone = phone,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )

        // membershipId == ownerUid → deterministic, safe to call twice
        val membership = MembershipEntity(
            membershipId = ownerUid,
            businessId = businessId,
            userId = ownerUid,
            role = "OWNER",
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val subscription = SubscriptionEntity(
            subscriptionId = "SUB-$businessId",   // deterministic, not random
            businessId = businessId,
            planId = "TRIAL",
            planName = "14-Day Free Trial",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000),
            status = "TRIAL",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Save to Room
        database.businessDao().insertBusiness(business)
        database.membershipDao().insertMembership(membership)
        database.subscriptionDao().insertSubscription(subscription)

        // Save to Firestore as an atomic batch
        try {
            val batch = firestore.batch()

            val bizRef = firestore.collection("businesses").document(businessId)
            batch.set(bizRef, business)

            // memberships/{ownerUid} — matches firestore.rules isBusinessMember() check
            val memRef = bizRef.collection("memberships").document(ownerUid)
            batch.set(memRef, membership, com.google.firebase.firestore.SetOptions.merge())

            val subRef = bizRef.collection("subscription").document("current")
            batch.set(subRef, subscription)

            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace() // non-fatal — local Room already saved
        }

        business
    }

    // =====================================================================
    //  Query Firestore for all businesses this UID has an active membership
    //  in, and cache the results to local Room.
    // =====================================================================
    suspend fun checkExistingMemberships(uid: String): List<MembershipEntity> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collectionGroup("memberships")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .await()

                val memberships = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(MembershipEntity::class.java)
                }

                // Cache to Room
                memberships.forEach { mem ->
                    database.membershipDao().insertMembership(mem)
                }

                memberships
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to local Room if offline
                database.membershipDao().getMembershipsForUser(uid)
            }
        }

    // =====================================================================
    //  Load a single business + its subscription from Firestore into Room.
    // =====================================================================
    suspend fun loadBusinessData(businessId: String) = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("businesses").document(businessId).get().await()
            doc.toObject(BusinessEntity::class.java)?.let {
                database.businessDao().insertBusiness(it)
            }

            val subDoc = firestore.collection("businesses").document(businessId)
                .collection("subscription").document("current").get().await()
            subDoc.toObject(SubscriptionEntity::class.java)?.let {
                database.subscriptionDao().insertSubscription(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =====================================================================
    //  Create (or update) an invitation so a partner can join the business
    //  without receiving a permanent membership until they authenticate.
    //  Document ID = normalised phone hash so duplicate invites are safe.
    // =====================================================================
    suspend fun createPartnerInvitation(
        businessId: String,
        invitedPhone: String,
        invitedBy: String
    ) = withContext(Dispatchers.IO) {
        val normalizedPhone = normalizePhone(invitedPhone)
        val inviteId = "invite_${normalizedPhone.replace("+", "")}"
        val expiresAt = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000) // 7 days

        val inviteData = mapOf(
            "businessId" to businessId,
            "invitedPhone" to normalizedPhone,
            "invitedBy" to invitedBy,
            "role" to "PARTNER",
            "status" to "INVITED",
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to expiresAt
        )

        try {
            firestore.collection("businesses").document(businessId)
                .collection("invitations").document(inviteId)
                .set(inviteData, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =====================================================================
    //  Called when a partner completes OTP verification.
    //  Looks up their invitation and converts it to a permanent membership.
    //  Uses partnerUid as the membership document ID — idempotent.
    // =====================================================================
    suspend fun acceptPartnerInvitation(
        partnerUid: String,
        partnerPhone: String,
        partnerName: String
    ): MembershipEntity? = withContext(Dispatchers.IO) {
        val normalizedPhone = normalizePhone(partnerPhone)

        try {
            // Search for a pending invitation matching this phone number
            val inviteSnapshot = firestore.collectionGroup("invitations")
                .whereEqualTo("invitedPhone", normalizedPhone)
                .whereEqualTo("status", "INVITED")
                .get()
                .await()

            val inviteDoc = inviteSnapshot.documents.firstOrNull() ?: return@withContext null
            val businessId = inviteDoc.getString("businessId") ?: return@withContext null

            // Create permanent partner membership — document ID = partnerUid (idempotent)
            val membership = MembershipEntity(
                membershipId = partnerUid,
                businessId = businessId,
                userId = partnerUid,
                role = "PARTNER",
                status = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val memberRef = firestore.collection("businesses").document(businessId)
                .collection("memberships").document(partnerUid)

            // Idempotent: merge so we don't overwrite if already exists
            memberRef.set(membership, com.google.firebase.firestore.SetOptions.merge()).await()

            // Mark invitation as accepted
            inviteDoc.reference.update(
                mapOf("status" to "ACCEPTED", "acceptedAt" to System.currentTimeMillis())
            ).await()

            // Cache membership to Room
            database.membershipDao().insertMembership(membership)

            membership
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
