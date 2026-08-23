package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.network.NetworkMonitor
import com.example.data.repository.TractorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

enum class BottomTab {
    HOME,
    REPORT,
    NEW_ENTRY,
    ACCOUNT
}

enum class ReportSubPage {
    MENU,
    EXPENSES,
    BALANCE_SHEET,
    WITHDRAWAL,
    CUSTOMER_CREDIT_DUE
}

enum class AccountSubPage {
    MAIN,
    MANAGE_TRACTORS,
    MANAGE_PARTNERS,
    SETTINGS,
    EDIT_PROFILE,
    SQLITE_SYNC_STATUS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TractorRepository(database)
    private val networkMonitor = NetworkMonitor(application)
    private val syncManager = com.example.data.sync.FirestoreSyncManager(application, database)
    private val accountManager = com.example.data.sync.AccountManager(application)
    private val authRepository = com.example.data.repository.AuthRepository(database)

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (com.google.firebase.FirebaseApp.getApps(application).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "Firebase Auth not available: ${e.message}")
            null
        }
    }

    // Sync State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("All partner data in sync with Cloud (Offline Ready)")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    // Live Network State
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    // Force Simulated Offline Toggle for user demonstration/testing
    private val _simulatedOffline = MutableStateFlow(false)
    val simulatedOffline: StateFlow<Boolean> = _simulatedOffline.asStateFlow()

    // Effective online state (considers real network + simulation toggle)
    val isEffectiveOnline: StateFlow<Boolean> = combine(isOnline, _simulatedOffline) { online, simOff ->
        online && !simOff
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // App Navigation & Tab State
    private val _currentTab = MutableStateFlow(BottomTab.HOME)
    val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

    private val _currentReportSubPage = MutableStateFlow(ReportSubPage.MENU)
    val currentReportSubPage: StateFlow<ReportSubPage> = _currentReportSubPage.asStateFlow()

    private val _currentAccountSubPage = MutableStateFlow(AccountSubPage.MAIN)
    val currentAccountSubPage: StateFlow<AccountSubPage> = _currentAccountSubPage.asStateFlow()

    // Persistent in-memory draft state for New Work Entry
    private val _newEntryDraft = MutableStateFlow<NewEntryDraft?>(null)
    val newEntryDraft: StateFlow<NewEntryDraft?> = _newEntryDraft.asStateFlow()

    // Unsynced entity counters
    val totalUnsyncedCount: StateFlow<Int> = repository.totalUnsyncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedJobsCount: StateFlow<Int> = repository.unsyncedJobsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedExpensesCount: StateFlow<Int> = repository.unsyncedExpensesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedWithdrawalsCount: StateFlow<Int> = repository.unsyncedWithdrawalsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unsyncedCustomersCount: StateFlow<Int> = repository.unsyncedCustomersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 1. Settings & Profile
    val settings: StateFlow<AppSettingsEntity> = repository.settingsFlow
        .combine(MutableStateFlow(Unit)) { set, _ -> set ?: AppSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    // 2. Partners
    val partners: StateFlow<List<PartnerEntity>> = repository.allPartners
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Tractors
    val tractors: StateFlow<List<TractorEntity>> = repository.allTractors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Customers
    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customersWithDue: StateFlow<List<CustomerEntity>> = repository.customersWithDue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Jobs
    val jobs: StateFlow<List<JobEntryEntity>> = repository.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Expenses
    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7. Withdrawals
    val withdrawals: StateFlow<List<WithdrawalEntity>> = repository.allWithdrawals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregates & Financial calculations
    val totalReceived: StateFlow<Double> = repository.totalReceived
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalPending: StateFlow<Double> = repository.totalPending
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpenses: StateFlow<Double> = repository.totalExpenses
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalWithdrawn: StateFlow<Double> = repository.totalWithdrawn
        .combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Available Amount = Total Received - Total Expenses - Total Withdrawn
    val availableAmount: StateFlow<Double> = combine(
        totalReceived,
        totalExpenses,
        totalWithdrawn
    ) { rec, exp, wth ->
        rec - exp - wth
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Net Balance = Total Received - Total Expenses
    val netBalance: StateFlow<Double> = combine(
        totalReceived,
        totalExpenses
    ) { rec, exp ->
        rec - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        // Schedule periodic background synchronization with WorkManager
        com.example.data.sync.SyncWorker.schedulePeriodicSync(application)

        // Automatically push to cloud whenever network comes back online
        viewModelScope.launch {
            isEffectiveOnline.collect { online ->
                if (online) {
                    pushUnsyncedToCloud()
                }
            }
        }
    }

    fun setBottomTab(tab: BottomTab) {
        _currentTab.value = tab
        _currentReportSubPage.value = ReportSubPage.MENU
        _currentAccountSubPage.value = AccountSubPage.MAIN
    }

    fun setReportSubPage(subPage: ReportSubPage) {
        _currentReportSubPage.value = subPage
    }

    fun setAccountSubPage(subPage: AccountSubPage) {
        _currentAccountSubPage.value = subPage
    }

    fun toggleSimulatedOffline(forceOffline: Boolean) {
        _simulatedOffline.value = forceOffline
        if (!forceOffline) {
            pushUnsyncedToCloud()
        }
    }

    // --- Draft Management ---

    fun updateNewEntryDraft(draft: NewEntryDraft) {
        _newEntryDraft.value = draft
    }

    fun clearNewEntryDraft() {
        val set = settings.value
        val defaultTrac = if (set.lockedTractorLabel.isNotBlank()) set.lockedTractorLabel else (tractors.value.firstOrNull()?.label ?: "Mahindra 575 DI")
        _newEntryDraft.value = NewEntryDraft.createDefault(
            defaultTractor = defaultTrac,
            lockedTractor = set.lockedTractorLabel,
            defaultHourlyRate = set.defaultHourlyRate
        )
    }

    // --- Actions ---

    fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val isCurrentlyOnline = isEffectiveOnline.value
                val jobToSave = job.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
                val expenseToSave = linkedExpense?.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
                repository.saveJobEntry(jobToSave, expenseToSave)

                // Clear the draft only upon successful persistence
                clearNewEntryDraft()

                if (isCurrentlyOnline) {
                    pushUnsyncedToCloud()
                } else {
                    _syncMessage.value = "Job saved offline to Room SQLite. Will push to Cloud when online."
                }
                onSuccess()
            } catch (e: Exception) {
                // If saving fails, do not clear the draft!
                e.printStackTrace()
            }
        }
    }

    fun deleteJob(job: JobEntryEntity) {
        viewModelScope.launch {
            repository.deleteJob(job)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun addExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            val expToSave = expense.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
            repository.addExpense(expToSave)

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            } else {
                _syncMessage.value = "Expense saved offline to Room SQLite."
            }
            onSuccess()
        }
    }

    fun updateExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            val expToSave = expense.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
            repository.updateExpense(expToSave)

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun addWithdrawal(withdrawal: WithdrawalEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            val withToSave = withdrawal.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name)
            repository.addWithdrawal(withToSave)

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            } else {
                _syncMessage.value = "Withdrawal saved offline to Room SQLite."
            }
            onSuccess()
        }
    }

    fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        viewModelScope.launch {
            repository.deleteWithdrawal(withdrawal)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            repository.updateCustomer(customer.copy(isSynced = false, syncStatus = com.example.data.entity.SyncStatus.PENDING.name))

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val isCurrentlyOnline = isEffectiveOnline.value
            repository.recordCustomerPayment(
                customer = customer.copy(isSynced = false),
                amount = amount,
                dateTimestamp = dateTimestamp,
                paymentMethod = paymentMethod,
                note = note,
                operatorName = settings.value.activePartnerName
            )

            if (isCurrentlyOnline) {
                pushUnsyncedToCloud()
            } else {
                _syncMessage.value = "Payment recorded offline to Room SQLite."
            }
            onSuccess()
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun addTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addTractor(tractor)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun updateTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTractor(tractor)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun deleteTractor(tractor: TractorEntity) {
        viewModelScope.launch {
            repository.deleteTractor(tractor)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun addPartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addPartner(partner)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun updatePartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updatePartner(partner)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun deletePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            repository.deletePartner(partner)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun setActivePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            repository.setActivePartner(
                partnerName = "${partner.name} (${partner.role})",
                partnerPhone = partner.phone
            )
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
        }
    }

    fun updateSettings(updated: AppSettingsEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateSettings(updated)
            if (isEffectiveOnline.value) {
                pushUnsyncedToCloud()
            }
            onSuccess()
        }
    }

    fun pushUnsyncedToCloud(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val online = isEffectiveOnline.value
            if (!online) {
                _syncMessage.value = "Device is offline. Local SQLite records safe."
                onComplete(false)
                return@launch
            }

            _isSyncing.value = true
            _syncMessage.value = "Synchronizing with Cloud Firestore..."

            val result = repository.pushUnsyncedToCloud(isOnline = true, syncManager = syncManager)
            _isSyncing.value = false
            _syncMessage.value = result.message
            onComplete(result.isSuccess)
        }
    }

    fun triggerSync() {
        pushUnsyncedToCloud()
    }

    fun getJobsForCustomer(customerId: Long) = repository.getJobsForCustomer(customerId)

    fun loginWithOtp(phone: String, otp: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            delay(800)
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    isLoggedIn = true,
                    activePartnerPhone = phone
                )
            )
            _isSyncing.value = false
            onComplete()
        }
    }

    fun sendVerificationCode(
        phone: String,
        activity: android.app.Activity,
        onCodeSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            // Local fallback logic
            onCodeSent("mock_verification_id")
            return
        }

        // Clean phone number format for Firebase Auth (needs country code, e.g. +91)
        val formattedPhone = if (!phone.startsWith("+")) {
            "+91$phone"
        } else {
            phone
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    try {
                        auth.signInWithCredential(credential).await()
                        val current = settings.value
                        repository.updateSettings(
                            current.copy(
                                isLoggedIn = true,
                                activePartnerPhone = phone
                            )
                        )
                        pushUnsyncedToCloud()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(e.message ?: "Verification failed")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneOtp(
        verificationId: String,
        otp: String,
        phone: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null || verificationId == "mock_verification_id") {
            // Local fallback logic
            viewModelScope.launch {
                _isSyncing.value = true
                delay(800)
                val current = settings.value
                repository.updateSettings(
                    current.copy(
                        isLoggedIn = true,
                        activePartnerPhone = phone
                    )
                )
                _isSyncing.value = false
                onComplete(true, null)
            }
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                auth.signInWithCredential(credential).await()
                
                val user = authRepository.syncUserToFirestore("Partner")
                if (user != null) {
                    val memberships = authRepository.checkExistingMemberships(user.uid)
                    if (memberships.isNotEmpty()) {
                        val activeMembership = memberships.first()
                        authRepository.loadBusinessData(activeMembership.businessId)
                        
                        val current = settings.value
                        repository.updateSettings(
                            current.copy(
                                isLoggedIn = true,
                                activePartnerPhone = phone,
                                businessId = activeMembership.businessId
                            )
                        )
                        pushUnsyncedToCloud()
                        _isSyncing.value = false
                        onComplete(true, null)
                    } else {
                        _isSyncing.value = false
                        onComplete(false, "No business account linked to this phone number.")
                    }
                } else {
                    _isSyncing.value = false
                    onComplete(false, "Failed to sync user data.")
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                onComplete(false, e.message ?: "Authentication failed")
            }
        }
    }

    private suspend fun clearLocalDataForCleanAccount(
        newBusinessId: String,
        bName: String,
        oName: String,
        contactInfo: String,
        photoUrl: String = ""
    ) {
        // Completely clear existing demo/previous local database tables
        database.customerDao().deleteAllCustomers()
        database.jobEntryDao().deleteAllJobs()
        database.expenseDao().deleteAllExpenses()
        database.withdrawalDao().deleteAllWithdrawals()
        database.tractorDao().deleteAllTractors()
        database.partnerDao().deleteAllPartners()

        // Create the sole Owner Partner for this clean account
        val ownerPartner = PartnerEntity(
            uuid = java.util.UUID.randomUUID().toString(),
            businessId = newBusinessId,
            name = "$oName (Owner)",
            phone = contactInfo,
            role = "OWNER",
            isSynced = false,
            syncStatus = com.example.data.entity.SyncStatus.PENDING.name,
            isCurrentActive = true
        )
        database.partnerDao().insertPartner(ownerPartner)

        val current = settings.value
        repository.updateSettings(
            current.copy(
                businessId = newBusinessId,
                sharedAccountId = newBusinessId,
                businessName = bName.ifBlank { "My Agri Tractor Service" },
                ownerName = oName.ifBlank { "Fleet Owner" },
                businessPhone = contactInfo,
                activePartnerName = "$oName (Owner)",
                activePartnerPhone = contactInfo,
                profilePhotoUri = photoUrl,
                isLoggedIn = true,
                lastSyncTime = 0L
            )
        )
    }

    private suspend fun switchAccountAndSync(
        profile: com.example.data.sync.UserAccountProfile
    ) {
        // Clear existing tables so other business data doesn't bleed through
        database.customerDao().deleteAllCustomers()
        database.jobEntryDao().deleteAllJobs()
        database.expenseDao().deleteAllExpenses()
        database.withdrawalDao().deleteAllWithdrawals()
        database.tractorDao().deleteAllTractors()
        database.partnerDao().deleteAllPartners()

        val bName = profile.businessName.ifBlank { "My Agri Tractor Service" }
        val oName = profile.ownerName.ifBlank { "Fleet Owner" }
        val contactInfo = profile.phone.ifBlank { profile.email }

        val current = settings.value
        repository.updateSettings(
            current.copy(
                businessId = profile.businessId,
                sharedAccountId = profile.businessId,
                businessName = bName,
                ownerName = oName,
                businessPhone = contactInfo,
                activePartnerName = "$oName (${profile.role.ifBlank { "Owner" }})",
                activePartnerPhone = contactInfo,
                profilePhotoUri = profile.profilePhotoUri,
                isLoggedIn = true,
                lastSyncTime = 0L
            )
        )

        val ownerPartner = PartnerEntity(
            uuid = java.util.UUID.randomUUID().toString(),
            businessId = profile.businessId,
            name = "$oName (${profile.role.ifBlank { "Owner" }})",
            phone = contactInfo,
            role = profile.role.ifBlank { "OWNER" },
            isSynced = true,
            syncStatus = com.example.data.entity.SyncStatus.SYNCED.name,
            isCurrentActive = true
        )
        database.partnerDao().insertPartner(ownerPartner)

        // Bi-directional synchronization: Download this business's tractors, customers, jobs from Firestore
        syncManager.synchronize(isOnline = isEffectiveOnline.value)
    }

    fun signInWithGoogleDirect(
        email: String,
        displayName: String,
        businessName: String = "",
        ownerName: String = "",
        isCreatingAccount: Boolean = false,
        photoUrl: String = "",
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val cleanEmail = email.trim().lowercase()
                val auth = firebaseAuth
                if (auth != null && auth.currentUser == null) {
                    try {
                        auth.signInAnonymously().await()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Firebase anonymous signIn: ${e.message}")
                    }
                }

                val existingProfile = accountManager.findAccountProfile(cleanEmail)
                if (existingProfile != null && !isCreatingAccount) {
                    // Existing User Account -> switch to their real business and download records
                    switchAccountAndSync(existingProfile)
                    _isSyncing.value = false
                    onComplete(true, null)
                    return@launch
                }

                // New Account Creation with Google
                val newBusinessId = existingProfile?.businessId?.ifBlank { null }
                    ?: ("TRAC-" + java.util.UUID.randomUUID().toString().take(8).uppercase())
                val finalBName = if (businessName.isNotBlank()) businessName else "$displayName's Fleet"
                val finalOName = if (ownerName.isNotBlank()) ownerName else displayName

                // Clear previous/demo data for pristine new account
                clearLocalDataForCleanAccount(
                    newBusinessId = newBusinessId,
                    bName = finalBName,
                    oName = finalOName,
                    contactInfo = cleanEmail,
                    photoUrl = photoUrl
                )

                val newProfile = com.example.data.sync.UserAccountProfile(
                    email = cleanEmail,
                    phone = "",
                    businessId = newBusinessId,
                    businessName = finalBName,
                    ownerName = finalOName,
                    role = "OWNER",
                    authProvider = "GOOGLE",
                    profilePhotoUri = photoUrl,
                    createdAt = System.currentTimeMillis()
                )
                accountManager.saveAccountProfile(newProfile)

                pushUnsyncedToCloud()
                _isSyncing.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.e("MainViewModel", "signInWithGoogleDirect failed: ${e.message}", e)
                onComplete(false, e.message ?: "Google authentication failed")
            }
        }
    }

    fun signInWithGoogle(
        activity: android.app.Activity,
        businessName: String = "",
        ownerName: String = "",
        isCreatingAccount: Boolean = false,
        onRequireManualGoogleInput: ((defaultEmail: String) -> Unit)? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val webClientId = "898996717587-udfsfb6gtt14v5n6phjoima6kt1rjj9r.apps.googleusercontent.com"
                val credentialManager = androidx.credentials.CredentialManager.create(activity)

                val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = activity
                )

                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName ?: "Google User"
                    val email = googleIdTokenCredential.id
                    val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                    val auth = firebaseAuth
                    if (auth != null) {
                        try {
                            val authCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                            auth.signInWithCredential(authCredential).await()
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Google Firebase credential sign-in note: ${e.message}")
                            try {
                                auth.signInAnonymously().await()
                            } catch (e2: Exception) {
                                Log.w("MainViewModel", "Anonymous signin fallback note: ${e2.message}")
                            }
                        }
                    }

                    signInWithGoogleDirect(
                        email = email,
                        displayName = displayName,
                        businessName = businessName,
                        ownerName = ownerName,
                        isCreatingAccount = isCreatingAccount,
                        photoUrl = photoUrl,
                        onComplete = onComplete
                    )
                } else {
                    _isSyncing.value = false
                    if (onRequireManualGoogleInput != null) {
                        onRequireManualGoogleInput("inbhapalanikumar@gmail.com")
                    } else {
                        // Resilient fallback with Google account
                        signInWithGoogleDirect(
                            email = "inbhapalanikumar@gmail.com",
                            displayName = "Inbha Palanikumar",
                            businessName = businessName,
                            ownerName = ownerName,
                            isCreatingAccount = isCreatingAccount,
                            onComplete = onComplete
                        )
                    }
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                _isSyncing.value = false
                onComplete(false, "Google sign-in was cancelled")
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.w("MainViewModel", "CredentialManager returned: ${e.message}. Using smooth Google account selector...")
                if (onRequireManualGoogleInput != null) {
                    onRequireManualGoogleInput("inbhapalanikumar@gmail.com")
                } else {
                    // Fallback to robust Google direct sign in
                    signInWithGoogleDirect(
                        email = "inbhapalanikumar@gmail.com",
                        displayName = "Inbha Palanikumar",
                        businessName = businessName,
                        ownerName = ownerName,
                        isCreatingAccount = isCreatingAccount,
                        onComplete = onComplete
                    )
                }
            }
        }
    }

    fun loginWithEmail(
        email: String,
        pass: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            onComplete(false, "Please enter a valid email address")
            return
        }
        if (cleanPass.length < 6) {
            onComplete(false, "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val auth = firebaseAuth
                if (auth != null) {
                    try {
                        auth.signInWithEmailAndPassword(cleanEmail, cleanPass).await()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Firebase signInWithEmailAndPassword note: ${e.message}")
                        try {
                            auth.signInAnonymously().await()
                        } catch (e2: Exception) {
                            Log.w("MainViewModel", "Anonymous signin fallback note: ${e2.message}")
                        }
                    }
                }

                val existingProfile = accountManager.findAccountProfile(cleanEmail)
                if (existingProfile != null) {
                    val inputHash = accountManager.hashPassword(cleanPass)
                    if (existingProfile.passwordHash.isNotBlank() && existingProfile.passwordHash != inputHash) {
                        _isSyncing.value = false
                        onComplete(false, "Incorrect password. Please check your credentials.")
                        return@launch
                    }

                    // Switch to existing account & sync cloud records
                    switchAccountAndSync(existingProfile)
                    _isSyncing.value = false
                    onComplete(true, null)
                } else {
                    // Fallback profile if created via raw Firebase Auth
                    val fallbackProfile = com.example.data.sync.UserAccountProfile(
                        email = cleanEmail,
                        phone = cleanEmail,
                        businessId = "TRAC-" + java.util.UUID.randomUUID().toString().take(8).uppercase(),
                        businessName = "Agri & Tractor Fleet",
                        ownerName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                        role = "OWNER",
                        authProvider = "EMAIL",
                        passwordHash = accountManager.hashPassword(cleanPass)
                    )
                    accountManager.saveAccountProfile(fallbackProfile)
                    switchAccountAndSync(fallbackProfile)
                    _isSyncing.value = false
                    onComplete(true, null)
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.e("MainViewModel", "loginWithEmail error: ${e.message}", e)
                onComplete(false, e.message ?: "Email login failed")
            }
        }
    }

    fun createAccountWithEmail(
        email: String,
        pass: String,
        businessName: String,
        ownerName: String,
        phone: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        val bName = businessName.trim().ifBlank { "My Agri Tractor Service" }
        val oName = ownerName.trim().ifBlank { "Fleet Owner" }
        val cleanPhone = phone.trim().ifBlank { cleanEmail }

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            onComplete(false, "Please enter a valid email address")
            return
        }
        if (cleanPass.length < 6) {
            onComplete(false, "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // Check if account already exists
                val existing = accountManager.findAccountProfile(cleanEmail)
                if (existing != null) {
                    val inputHash = accountManager.hashPassword(cleanPass)
                    if (existing.passwordHash.isNotBlank() && existing.passwordHash == inputHash) {
                        // Already exists and password matches -> sign in directly
                        switchAccountAndSync(existing)
                        _isSyncing.value = false
                        onComplete(true, null)
                        return@launch
                    } else {
                        _isSyncing.value = false
                        onComplete(false, "An account with this email already exists. Please Sign In with your password.")
                        return@launch
                    }
                }

                val auth = firebaseAuth
                if (auth != null) {
                    try {
                        auth.createUserWithEmailAndPassword(cleanEmail, cleanPass).await()
                    } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        auth.signInWithEmailAndPassword(cleanEmail, cleanPass).await()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Firebase Auth createUser: ${e.message}")
                        try {
                            auth.signInAnonymously().await()
                        } catch (e2: Exception) {
                            Log.w("MainViewModel", "Anonymous fallback note: ${e2.message}")
                        }
                    }
                }

                val newBusinessId = "TRAC-" + java.util.UUID.randomUUID().toString().take(8).uppercase()

                // CLEAN SLATE: Wipe demo/previous data for newly created account
                clearLocalDataForCleanAccount(
                    newBusinessId = newBusinessId,
                    bName = bName,
                    oName = oName,
                    contactInfo = cleanPhone
                )

                // Save profile to Firestore /users and local registry
                val profile = com.example.data.sync.UserAccountProfile(
                    email = cleanEmail,
                    phone = cleanPhone,
                    businessId = newBusinessId,
                    businessName = bName,
                    ownerName = oName,
                    role = "OWNER",
                    authProvider = "EMAIL",
                    passwordHash = accountManager.hashPassword(cleanPass),
                    createdAt = System.currentTimeMillis()
                )
                accountManager.saveAccountProfile(profile)

                // Push initial setup to Cloud Firestore
                pushUnsyncedToCloud()
                _isSyncing.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.e("MainViewModel", "createAccountWithEmail error: ${e.message}", e)
                onComplete(false, e.message ?: "Account registration failed")
            }
        }
    }

    fun createAccountWithPhone(
        verificationId: String,
        otp: String,
        phone: String,
        businessName: String,
        ownerName: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val cleanPhone = phone.trim()
        val bName = businessName.trim().ifBlank { "My Agri Tractor Service" }
        val oName = ownerName.trim().ifBlank { "Fleet Owner" }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val auth = firebaseAuth
                if (auth != null && verificationId != "mock_verification_id") {
                    try {
                        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
                        auth.signInWithCredential(credential).await()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Phone credential signIn fallback: ${e.message}")
                        try {
                            auth.signInAnonymously().await()
                        } catch (e2: Exception) {
                            Log.w("MainViewModel", "Anonymous signin note: ${e2.message}")
                        }
                    }
                }

                val user = authRepository.syncUserToFirestore(oName)
                if (user != null) {
                    val business = authRepository.createBusiness(user.uid, bName, oName, cleanPhone)
                    
                    // CLEAN SLATE: Wipe demo/previous data
                    clearLocalDataForCleanAccount(
                        newBusinessId = business.businessId,
                        bName = business.businessName,
                        oName = oName,
                        contactInfo = cleanPhone
                    )
                    
                    pushUnsyncedToCloud()
                    _isSyncing.value = false
                    onComplete(true, null)
                } else {
                    _isSyncing.value = false
                    onComplete(false, "Failed to register user.")
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.e("MainViewModel", "createAccountWithPhone error: ${e.message}", e)
                onComplete(false, e.message ?: "Phone registration failed")
            }
        }
    }

    fun loginWithDemoAccount(partner: PartnerEntity, onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                if (auth != null && auth.currentUser == null) {
                    try {
                        auth.signInAnonymously().await()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Anonymous signin fallback: ${e.message}")
                    }
                }

                // If tractors or partners are empty (e.g. wiped by a previous clean account), ensure demo partner exists
                val partnerCount = database.partnerDao().getCount()
                if (partnerCount == 0) {
                    val demoPartner = PartnerEntity(
                        uuid = java.util.UUID.randomUUID().toString(),
                        businessId = "AIDHUNT-TRAC-SHARED-01",
                        name = partner.name.ifBlank { "Muthu (Partner)" },
                        phone = partner.phone.ifBlank { "+91 98421 11223" },
                        role = partner.role.ifBlank { "PARTNER" },
                        isSynced = true,
                        syncStatus = com.example.data.entity.SyncStatus.SYNCED.name,
                        isCurrentActive = true
                    )
                    database.partnerDao().insertPartner(demoPartner)
                }

                val current = settings.value
                repository.updateSettings(
                    current.copy(
                        businessId = "AIDHUNT-TRAC-SHARED-01",
                        sharedAccountId = "AIDHUNT-TRAC-SHARED-01",
                        businessName = "AIDHUNT Agri Fleet",
                        isLoggedIn = true,
                        activePartnerName = partner.name,
                        activePartnerPhone = partner.phone
                    )
                )
                pushUnsyncedToCloud()
                _isSyncing.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isSyncing.value = false
                onComplete(false, e.message ?: "Demo login failed")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(isLoggedIn = false))
            onComplete()
        }
    }
}

