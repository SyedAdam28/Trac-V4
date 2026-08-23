package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partners WHERE isDeleted = 0 ORDER BY id ASC")
    fun getAllPartners(): Flow<List<PartnerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Delete
    suspend fun deletePartner(partner: PartnerEntity)

    @Query("SELECT COUNT(*) FROM partners WHERE isDeleted = 0")
    suspend fun getCount(): Int

    @Query("SELECT * FROM partners WHERE uuid = :uuid LIMIT 1")
    suspend fun getPartnerByUuid(uuid: String): PartnerEntity?

    @Query("SELECT * FROM partners WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    suspend fun getUnsyncedPartners(): List<PartnerEntity>

    @Query("UPDATE partners SET isSynced = 1, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markPartnersSynced(ids: List<Long>)

    @Query("DELETE FROM partners")
    suspend fun deleteAllPartners()
}

@Dao
interface TractorDao {
    @Query("SELECT * FROM tractors WHERE isDeleted = 0 ORDER BY id ASC")
    fun getAllTractors(): Flow<List<TractorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTractor(tractor: TractorEntity): Long

    @Update
    suspend fun updateTractor(tractor: TractorEntity)

    @Delete
    suspend fun deleteTractor(tractor: TractorEntity)

    @Query("SELECT COUNT(*) FROM tractors WHERE isDeleted = 0")
    suspend fun getCount(): Int

    @Query("SELECT * FROM tractors WHERE uuid = :uuid LIMIT 1")
    suspend fun getTractorByUuid(uuid: String): TractorEntity?

    @Query("SELECT * FROM tractors WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    suspend fun getUnsyncedTractors(): List<TractorEntity>

    @Query("UPDATE tractors SET isSynced = 1, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markTractorsSynced(ids: List<Long>)

    @Query("DELETE FROM tractors")
    suspend fun deleteAllTractors()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDue(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%')")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    suspend fun getUnsyncedCustomers(): List<CustomerEntity>

    @Query("UPDATE customers SET isSynced = 1, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markCustomersSynced(ids: List<Long>)

    @Query("UPDATE customers SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("SELECT * FROM customers WHERE uuid = :uuid LIMIT 1")
    suspend fun getCustomerByUuid(uuid: String): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()
}

@Dao
interface JobEntryDao {
    @Query("SELECT * FROM job_entries WHERE isDeleted = 0 ORDER BY startTimeMillis DESC")
    fun getAllJobs(): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE isDeleted = 0 AND customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE isDeleted = 0 AND (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobs(query: String): Flow<List<JobEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntryEntity): Long

    @Update
    suspend fun updateJob(job: JobEntryEntity)

    @Delete
    suspend fun deleteJob(job: JobEntryEntity)

    @Query("SELECT SUM(amountReceived) FROM job_entries WHERE isDeleted = 0")
    fun getTotalReceived(): Flow<Double?>

    @Query("SELECT SUM(pendingAmount) FROM job_entries WHERE isDeleted = 0")
    fun getTotalPending(): Flow<Double?>

    @Query("SELECT * FROM job_entries WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    suspend fun getUnsyncedJobs(): List<JobEntryEntity>

    @Query("UPDATE job_entries SET isSynced = 1, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markJobsSynced(ids: List<Long>)

    @Query("UPDATE job_entries SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("SELECT * FROM job_entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getJobByUuid(uuid: String): JobEntryEntity?

    @Query("SELECT COUNT(*) FROM job_entries WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM job_entries")
    suspend fun deleteAllJobs()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT SUM(amount) FROM expenses WHERE isDeleted = 0")
    fun getTotalExpenses(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<Long>)

    @Query("UPDATE expenses SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("SELECT * FROM expenses WHERE uuid = :uuid LIMIT 1")
    suspend fun getExpenseByUuid(uuid: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity): Long

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    @Delete
    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity)

    @Query("SELECT SUM(amount) FROM withdrawals WHERE isDeleted = 0")
    fun getTotalWithdrawn(): Flow<Double?>

    @Query("SELECT * FROM withdrawals WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    suspend fun getUnsyncedWithdrawals(): List<WithdrawalEntity>

    @Query("UPDATE withdrawals SET isSynced = 1, syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markWithdrawalsSynced(ids: List<Long>)

    @Query("UPDATE withdrawals SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("SELECT * FROM withdrawals WHERE uuid = :uuid LIMIT 1")
    suspend fun getWithdrawalByUuid(uuid: String): WithdrawalEntity?

    @Query("SELECT COUNT(*) FROM withdrawals WHERE isDeleted = 0 AND (isSynced = 0 OR syncStatus != 'SYNCED')")
    fun getUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM withdrawals")
    suspend fun deleteAllWithdrawals()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettingsEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    fun getUserFlow(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUser(uid: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE businessId = :businessId LIMIT 1")
    fun getBusinessFlow(businessId: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE businessId = :businessId LIMIT 1")
    suspend fun getBusiness(businessId: String): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: BusinessEntity)

    @Update
    suspend fun updateBusiness(business: BusinessEntity)
}

@Dao
interface MembershipDao {
    @Query("SELECT * FROM memberships WHERE userId = :userId")
    fun getMembershipsForUserFlow(userId: String): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE userId = :userId")
    suspend fun getMembershipsForUser(userId: String): List<MembershipEntity>

    @Query("SELECT * FROM memberships WHERE businessId = :businessId AND userId = :userId LIMIT 1")
    suspend fun getMembership(businessId: String, userId: String): MembershipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembership(membership: MembershipEntity)

    @Update
    suspend fun updateMembership(membership: MembershipEntity)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE businessId = :businessId LIMIT 1")
    fun getSubscriptionForBusinessFlow(businessId: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE businessId = :businessId LIMIT 1")
    suspend fun getSubscriptionForBusiness(businessId: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)
}
