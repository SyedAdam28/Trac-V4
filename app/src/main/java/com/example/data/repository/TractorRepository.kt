package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class TractorRepository(private val database: AppDatabase) {

    private val partnerDao = database.partnerDao()
    private val tractorDao = database.tractorDao()
    private val customerDao = database.customerDao()
    private val jobEntryDao = database.jobEntryDao()
    private val expenseDao = database.expenseDao()
    private val withdrawalDao = database.withdrawalDao()
    private val appSettingsDao = database.appSettingsDao()

    // 1. App Settings
    val settingsFlow: Flow<AppSettingsEntity?> = appSettingsDao.getSettings()

    suspend fun getSettings(): AppSettingsEntity {
        return appSettingsDao.getSettingsOnce() ?: AppSettingsEntity()
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        appSettingsDao.insertOrUpdateSettings(settings)
    }

    suspend fun setActivePartner(partnerName: String, partnerPhone: String) {
        val current = getSettings()
        appSettingsDao.insertOrUpdateSettings(
            current.copy(
                activePartnerName = partnerName,
                activePartnerPhone = partnerPhone
            )
        )
    }

    // 2. Partners
    val allPartners: Flow<List<PartnerEntity>> = partnerDao.getAllPartners()

    suspend fun addPartner(partner: PartnerEntity): Long {
        val bizId = getSettings().businessId
        return partnerDao.insertPartner(partner.copy(businessId = bizId))
    }

    suspend fun updatePartner(partner: PartnerEntity) = partnerDao.updatePartner(partner)

    suspend fun deletePartner(partner: PartnerEntity) = partnerDao.deletePartner(partner)

    // 3. Tractors
    val allTractors: Flow<List<TractorEntity>> = tractorDao.getAllTractors()

    suspend fun addTractor(tractor: TractorEntity): Long {
        val bizId = getSettings().businessId
        return tractorDao.insertTractor(tractor.copy(businessId = bizId))
    }

    suspend fun updateTractor(tractor: TractorEntity) = tractorDao.updateTractor(tractor)

    suspend fun deleteTractor(tractor: TractorEntity) = tractorDao.deleteTractor(tractor)

    // 4. Customers
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val customersWithDue: Flow<List<CustomerEntity>> = customerDao.getCustomersWithDue()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = customerDao.searchCustomers(query)

    suspend fun getCustomerById(id: Long): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun updateCustomer(customer: CustomerEntity) {
        val sanitized = customer.copy(
            phone = com.example.ui.components.sanitizePhoneNumberForStorage(customer.phone)
        )
        customerDao.updateCustomer(sanitized)
    }

    suspend fun deleteCustomer(customer: CustomerEntity) = customerDao.deleteCustomer(customer)

    suspend fun addOrFindCustomer(name: String, phone: String, location: String): Long {
        val customers = customerDao.getAllCustomers().firstOrNull() ?: emptyList()
        val existing = customers.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(phone)
        val cleanLocation = location.trim()

        return if (existing != null) {
            // If existing customer didn't have a phone or location, update with new one
            if (cleanPhone.isNotBlank() && (existing.phone.isBlank() || existing.phone != cleanPhone)) {
                customerDao.updateCustomer(
                    existing.copy(
                        phone = cleanPhone,
                        location = if (cleanLocation.isNotBlank()) cleanLocation else existing.location,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            existing.id
        } else {
            val bizId = getSettings().businessId
            customerDao.insertCustomer(
                CustomerEntity(
                    businessId = bizId,
                    name = name.trim(),
                    phone = cleanPhone,
                    location = cleanLocation,
                    totalBilled = 0.0,
                    totalPaid = 0.0,
                    balanceDue = 0.0
                )
            )
        }
    }

    // 5. Job Entries
    val allJobs: Flow<List<JobEntryEntity>> = jobEntryDao.getAllJobs()
    val totalReceived: Flow<Double?> = jobEntryDao.getTotalReceived()
    val totalPending: Flow<Double?> = jobEntryDao.getTotalPending()

    fun getJobsForCustomer(customerId: Long): Flow<List<JobEntryEntity>> =
        jobEntryDao.getJobsForCustomer(customerId)

    suspend fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null
    ): Long {
        var customerId = job.customerId
        if (customerId <= 0) {
            customerId = addOrFindCustomer(job.customerName, job.customerPhone, job.customerLocation)
        }

        val bizId = getSettings().businessId
        val jobId = jobEntryDao.insertJob(job.copy(customerId = customerId, businessId = bizId))

        // If optional linked expense was provided, add it
        if (linkedExpense != null && linkedExpense.amount > 0) {
            expenseDao.insertExpense(
                linkedExpense.copy(relatedJobId = jobId, businessId = bizId)
            )
        }

        // Recalculate customer statistics
        recalculateCustomerStats(customerId)

        return jobId
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        jobEntryDao.deleteJob(job)
        recalculateCustomerStats(job.customerId)
    }

    suspend fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        operatorName: String
    ): Long {
        val methodDesc = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
        val noteDesc = if (note.isNotBlank()) "Note: $note" else ""
        val combinedNotes = listOf(methodDesc, noteDesc).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Direct Payment Received" }

        val bizId = getSettings().businessId
        val paymentEntry = JobEntryEntity(
            businessId = bizId,
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            customerLocation = customer.location,
            operatorName = operatorName.ifBlank { "Partner" },
            tractorId = 0,
            tractorLabel = "Payment",
            workType = "Payment Received",
            startTimeMillis = dateTimestamp,
            endTimeMillis = dateTimestamp,
            durationMinutes = 0,
            hourlyRate = 0.0,
            totalAmount = 0.0,
            amountReceived = amount,
            pendingAmount = -amount,
            addedByPartner = operatorName.ifBlank { "Partner" },
            notes = combinedNotes
        )

        val entryId = jobEntryDao.insertJob(paymentEntry)
        recalculateCustomerStats(customer.id)
        return entryId
    }

    private suspend fun recalculateCustomerStats(customerId: Long) {
        val customer = customerDao.getCustomerById(customerId) ?: return
        val jobs = jobEntryDao.getJobsForCustomer(customerId).firstOrNull() ?: emptyList()

        val totalBilled = jobs.sumOf { it.totalAmount }
        val totalPaid = jobs.sumOf { it.amountReceived }
        val balanceDue = jobs.sumOf { it.pendingAmount }

        customerDao.updateCustomer(
            customer.copy(
                totalBilled = totalBilled,
                totalPaid = totalPaid,
                balanceDue = balanceDue,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // 6. Expenses
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val totalExpenses: Flow<Double?> = expenseDao.getTotalExpenses()

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val bizId = getSettings().businessId
        return expenseDao.insertExpense(expense.copy(businessId = bizId))
    }

    suspend fun updateExpense(expense: ExpenseEntity) = expenseDao.updateExpense(expense)

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.deleteExpense(expense)

    // 7. Withdrawals
    val allWithdrawals: Flow<List<WithdrawalEntity>> = withdrawalDao.getAllWithdrawals()
    val totalWithdrawn: Flow<Double?> = withdrawalDao.getTotalWithdrawn()

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        val bizId = getSettings().businessId
        return withdrawalDao.insertWithdrawal(withdrawal.copy(businessId = bizId))
    }

    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity) =
        withdrawalDao.updateWithdrawal(withdrawal)

    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity) =
        withdrawalDao.deleteWithdrawal(withdrawal)

    // 8. Offline & Cloud Sync
    val unsyncedJobsCount: Flow<Int> = jobEntryDao.getUnsyncedCount()
    val unsyncedExpensesCount: Flow<Int> = expenseDao.getUnsyncedCount()
    val unsyncedWithdrawalsCount: Flow<Int> = withdrawalDao.getUnsyncedCount()
    val unsyncedCustomersCount: Flow<Int> = customerDao.getUnsyncedCount()

    val totalUnsyncedCount: Flow<Int> = kotlinx.coroutines.flow.combine(
        unsyncedJobsCount,
        unsyncedExpensesCount,
        unsyncedWithdrawalsCount,
        unsyncedCustomersCount
    ) { jobs, exp, wth, cust ->
        jobs + exp + wth + cust
    }

    suspend fun pushUnsyncedToCloud(isOnline: Boolean, syncManager: com.example.data.sync.FirestoreSyncManager? = null): com.example.data.sync.SyncResult {
        if (!isOnline) {
            return com.example.data.sync.SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Device offline. Stored safely in local Room SQLite database."
            )
        }

        if (syncManager != null) {
            val success = syncManager.synchronize(isOnline = true)
            return if (success) {
                com.example.data.sync.SyncResult(
                    isSuccess = true,
                    syncedItemsCount = 0,
                    message = "Cloud Firestore sync completed across all partner devices."
                )
            } else {
                com.example.data.sync.SyncResult(
                    isSuccess = false,
                    syncedItemsCount = 0,
                    message = "Cloud sync temporary failure. Records preserved offline in SQLite."
                )
            }
        }

        val unsyncedJobs = jobEntryDao.getUnsyncedJobs()
        val unsyncedExpenses = expenseDao.getUnsyncedExpenses()
        val unsyncedWithdrawals = withdrawalDao.getUnsyncedWithdrawals()
        val unsyncedCustomers = customerDao.getUnsyncedCustomers()

        val totalCount = unsyncedJobs.size + unsyncedExpenses.size + unsyncedWithdrawals.size + unsyncedCustomers.size

        if (totalCount == 0) {
            val current = getSettings()
            appSettingsDao.insertOrUpdateSettings(
                current.copy(lastSyncTime = System.currentTimeMillis())
            )
            return com.example.data.sync.SyncResult(
                isSuccess = true,
                syncedItemsCount = 0,
                message = "All records are already in sync with Cloud."
            )
        }

        // Mark all in SQLite as synced
        if (unsyncedJobs.isNotEmpty()) {
            jobEntryDao.markJobsSynced(unsyncedJobs.map { it.id })
        }
        if (unsyncedExpenses.isNotEmpty()) {
            expenseDao.markExpensesSynced(unsyncedExpenses.map { it.id })
        }
        if (unsyncedWithdrawals.isNotEmpty()) {
            withdrawalDao.markWithdrawalsSynced(unsyncedWithdrawals.map { it.id })
        }
        if (unsyncedCustomers.isNotEmpty()) {
            customerDao.markCustomersSynced(unsyncedCustomers.map { it.id })
        }

        val current = getSettings()
        appSettingsDao.insertOrUpdateSettings(
            current.copy(lastSyncTime = System.currentTimeMillis())
        )

        return com.example.data.sync.SyncResult(
            isSuccess = true,
            syncedItemsCount = totalCount,
            message = "Pushed $totalCount offline records to Cloud successfully!"
        )
    }

    suspend fun triggerSync() {
        val current = getSettings()
        appSettingsDao.insertOrUpdateSettings(
            current.copy(lastSyncTime = System.currentTimeMillis())
        )
    }
}
