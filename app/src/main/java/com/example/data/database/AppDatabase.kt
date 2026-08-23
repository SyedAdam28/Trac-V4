package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AppSettingsDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.JobEntryDao
import com.example.data.dao.PartnerDao
import com.example.data.dao.TractorDao
import com.example.data.dao.WithdrawalDao
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.entity.UserEntity
import com.example.data.entity.BusinessEntity
import com.example.data.entity.MembershipEntity
import com.example.data.entity.SubscriptionEntity
import com.example.data.dao.UserDao
import com.example.data.dao.BusinessDao
import com.example.data.dao.MembershipDao
import com.example.data.dao.SubscriptionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PartnerEntity::class,
        TractorEntity::class,
        CustomerEntity::class,
        JobEntryEntity::class,
        ExpenseEntity::class,
        WithdrawalEntity::class,
        AppSettingsEntity::class,
        UserEntity::class,
        BusinessEntity::class,
        MembershipEntity::class,
        SubscriptionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun partnerDao(): PartnerDao
    abstract fun tractorDao(): TractorDao
    abstract fun customerDao(): CustomerDao
    abstract fun jobEntryDao(): JobEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun userDao(): UserDao
    abstract fun businessDao(): BusinessDao
    abstract fun membershipDao(): MembershipDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Partners
                db.execSQL("ALTER TABLE partners ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE partners ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE partners ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE partners ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE partners ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE partners ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE partners ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE partners ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // 2. Tractors
                db.execSQL("ALTER TABLE tractors ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tractors ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tractors ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE tractors ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // 3. Customers
                db.execSQL("ALTER TABLE customers ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE customers ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")

                // 4. Job Entries
                db.execSQL("ALTER TABLE job_entries ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN customerUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN tractorUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // 5. Expenses
                db.execSQL("ALTER TABLE expenses ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN tractorUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN relatedJobUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // 6. Withdrawals
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN businessId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN partnerUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // 7. App Settings
                db.execSQL("ALTER TABLE app_settings ADD COLUMN businessId TEXT NOT NULL DEFAULT 'AIDHUNT-TRAC-SHARED-01'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Force re-login through Firebase Auth on all existing devices
                // so that FirebaseAuth.currentUser is set before Firestore sync runs
                db.execSQL("UPDATE app_settings SET isLoggedIn = 0 WHERE id = 1")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create users table
                db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`uid` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                
                // Create businesses table
                db.execSQL("CREATE TABLE IF NOT EXISTS `businesses` (`businessId` TEXT NOT NULL, `businessName` TEXT NOT NULL, `ownerUserId` TEXT NOT NULL, `businessPhone` TEXT NOT NULL, `address` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`businessId`))")

                // Create memberships table
                db.execSQL("CREATE TABLE IF NOT EXISTS `memberships` (`membershipId` TEXT NOT NULL, `businessId` TEXT NOT NULL, `userId` TEXT NOT NULL, `role` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`membershipId`))")

                // Create subscriptions table
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions` (`subscriptionId` TEXT NOT NULL, `businessId` TEXT NOT NULL, `planId` TEXT NOT NULL, `planName` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`subscriptionId`))")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE partners ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tractors ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aidhunt_trac_v5.db"
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration(false)
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val appContext: Context) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial partners, tractors, default settings
                CoroutineScope(Dispatchers.IO).launch {
                    val database = getInstance(appContext)
                    seedDatabase(database)
                }
            }
        }

        private suspend fun seedDatabase(db: AppDatabase) {
            val partnerDao = db.partnerDao()
            val tractorDao = db.tractorDao()
            val customerDao = db.customerDao()
            val jobEntryDao = db.jobEntryDao()
            val expenseDao = db.expenseDao()
            val withdrawalDao = db.withdrawalDao()
            val settingsDao = db.appSettingsDao()

            // 1. Settings
            settingsDao.insertOrUpdateSettings(
                AppSettingsEntity(
                    id = 1,
                    businessName = "AIDHUNT Agri & Tractor Services",
                    ownerName = "Muthu (Owner)",
                    businessPhone = "+91 98421 54321",
                    businessAddress = "Kaveri Road, Tiruchirappalli, Tamil Nadu",
                    defaultHourlyRate = 1100.0,
                    language = "EN",
                    sharedAccountId = "AIDHUNT-TRAC-SHARED-01",
                    isLoggedIn = false,
                    activePartnerName = "Muthu (Owner)",
                    activePartnerPhone = "+91 98421 54321",
                    lastSyncTime = System.currentTimeMillis()
                )
            )

            // 2. 3 Shared Business Partners
            val muthuId = partnerDao.insertPartner(
                PartnerEntity(name = "Muthu", phone = "+91 98421 54321", role = "Owner", avatarColorHex = "#1E4D2B", isCurrentActive = true)
            )
            val sureshId = partnerDao.insertPartner(
                PartnerEntity(name = "Suresh", phone = "+91 97890 12345", role = "Partner", avatarColorHex = "#2E6B3F", isCurrentActive = false)
            )
            val rameshId = partnerDao.insertPartner(
                PartnerEntity(name = "Ramesh", phone = "+91 94432 67890", role = "Partner", avatarColorHex = "#3F7D52", isCurrentActive = false)
            )

            // 3. Tractors
            val t1 = tractorDao.insertTractor(
                TractorEntity(label = "Mahindra 575 DI (Red)", chassisNo = "MH-575-TN45-9871", modelYear = "2023", operatorName = "Karthik")
            )
            val t2 = tractorDao.insertTractor(
                TractorEntity(label = "John Deere 5310 4WD (Green)", chassisNo = "JD-5310-TN48-4421", modelYear = "2024", operatorName = "Velu")
            )
            val t3 = tractorDao.insertTractor(
                TractorEntity(label = "Swaraj 744 FE", chassisNo = "SW-744-TN45-3120", modelYear = "2022", operatorName = "Saravanan")
            )

            // 4. Initial Customers
            val now = System.currentTimeMillis()
            val c1Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Ramasamy Gounder",
                    phone = "+91 94431 87654",
                    location = "Manapparai West",
                    totalBilled = 14300.0,
                    totalPaid = 9000.0,
                    balanceDue = 5300.0,
                    createdAt = now - 86400000L * 4
                )
            )
            val c2Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Kaliannan Farmer",
                    phone = "+91 98940 22334",
                    location = "Viralimalai Fields",
                    totalBilled = 8800.0,
                    totalPaid = 8800.0,
                    balanceDue = 0.0,
                    createdAt = now - 86400000L * 3
                )
            )
            val c3Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Shanmugam Chettiar",
                    phone = "+91 97500 11223",
                    location = "Kulithalai Canal",
                    totalBilled = 19800.0,
                    totalPaid = 12000.0,
                    balanceDue = 7800.0,
                    createdAt = now - 86400000L * 2
                )
            )
            val c4Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Palanisamy Thottam",
                    phone = "+91 96290 88990",
                    location = "Musiri East",
                    totalBilled = 6600.0,
                    totalPaid = 3000.0,
                    balanceDue = 3600.0,
                    createdAt = now - 86400000L * 1
                )
            )

            // 5. Initial Job Entries
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c1Id,
                    customerName = "Ramasamy Gounder",
                    customerPhone = "+91 94431 87654",
                    customerLocation = "Manapparai West",
                    operatorName = "Karthik",
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    workType = "Ploughing (3-Blade MB)",
                    startTimeMillis = now - 86400000L * 3 - 3600000L * 5,
                    endTimeMillis = now - 86400000L * 3 - 3600000L * 1,
                    durationMinutes = 240, // 4 hours
                    hourlyRate = 1100.0,
                    totalAmount = 4400.0,
                    amountReceived = 4400.0,
                    pendingAmount = 0.0,
                    addedByPartner = "Muthu",
                    notes = "Completed sugarcane field ploughing first pass",
                    createdAt = now - 86400000L * 3
                )
            )
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c1Id,
                    customerName = "Ramasamy Gounder",
                    customerPhone = "+91 94431 87654",
                    customerLocation = "Manapparai West",
                    operatorName = "Velu",
                    tractorId = t2,
                    tractorLabel = "John Deere 5310 4WD (Green)",
                    workType = "Rotavator (42 Blades)",
                    startTimeMillis = now - 86400000L * 2 - 3600000L * 9,
                    endTimeMillis = now - 86400000L * 2,
                    durationMinutes = 540, // 9 hours
                    hourlyRate = 1100.0,
                    totalAmount = 9900.0,
                    amountReceived = 4600.0,
                    pendingAmount = 5300.0,
                    addedByPartner = "Suresh",
                    notes = "Second pass rotavator, promised balance next Monday",
                    createdAt = now - 86400000L * 2
                )
            )
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c2Id,
                    customerName = "Kaliannan Farmer",
                    customerPhone = "+91 98940 22334",
                    customerLocation = "Viralimalai Fields",
                    operatorName = "Saravanan",
                    tractorId = t3,
                    tractorLabel = "Swaraj 744 FE",
                    workType = "Cultivator (9-Tyne)",
                    startTimeMillis = now - 86400000L * 1 - 3600000L * 8,
                    endTimeMillis = now - 86400000L * 1,
                    durationMinutes = 480, // 8 hours
                    hourlyRate = 1100.0,
                    totalAmount = 8800.0,
                    amountReceived = 8800.0,
                    pendingAmount = 0.0,
                    addedByPartner = "Ramesh",
                    notes = "Cash received on spot after work",
                    createdAt = now - 86400000L * 1
                )
            )
            jobEntryDao.insertJob(
                JobEntryEntity(
                    customerId = c3Id,
                    customerName = "Shanmugam Chettiar",
                    customerPhone = "+91 97500 11223",
                    customerLocation = "Kulithalai Canal",
                    operatorName = "Karthik",
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    workType = "Harvester Attachment / Bundle",
                    startTimeMillis = now - 3600000L * 18,
                    endTimeMillis = now - 3600000L * 2,
                    durationMinutes = 1080, // 18 hours (2 days)
                    hourlyRate = 1100.0,
                    totalAmount = 19800.0,
                    amountReceived = 12000.0,
                    pendingAmount = 7800.0,
                    addedByPartner = "Muthu",
                    notes = "Paddy harvest work. Sent statement on WhatsApp.",
                    createdAt = now - 3600000L * 2
                )
            )

            // 6. Initial Expenses
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Diesel",
                    amount = 4500.0,
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    operatorName = "Karthik",
                    description = "50 Litres HPCL Bunk Diesel",
                    addedByPartner = "Muthu",
                    dateTimestamp = now - 86400000L * 3,
                    createdAt = now - 86400000L * 3
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Repair",
                    amount = 1200.0,
                    tractorId = t2,
                    tractorLabel = "John Deere 5310 4WD (Green)",
                    operatorName = "Velu",
                    description = "Hydraulic pipe clamp welding & grease",
                    addedByPartner = "Suresh",
                    dateTimestamp = now - 86400000L * 2,
                    createdAt = now - 86400000L * 2
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Puncture",
                    amount = 350.0,
                    tractorId = t3,
                    tractorLabel = "Swaraj 744 FE",
                    operatorName = "Saravanan",
                    description = "Rear tube puncture patch at Manapparai workshop",
                    addedByPartner = "Ramesh",
                    dateTimestamp = now - 86400000L * 1,
                    createdAt = now - 86400000L * 1
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    expenseType = "Driver Bata",
                    amount = 1500.0,
                    tractorId = t1,
                    tractorLabel = "Mahindra 575 DI (Red)",
                    operatorName = "Karthik",
                    description = "Night work allowance for Kulithalai harvest job",
                    addedByPartner = "Muthu",
                    dateTimestamp = now - 3600000L * 4,
                    createdAt = now - 3600000L * 4
                )
            )

            // 7. Initial Withdrawals
            withdrawalDao.insertWithdrawal(
                WithdrawalEntity(
                    partnerId = muthuId,
                    partnerName = "Muthu",
                    amount = 5000.0,
                    category = "Personal Use",
                    note = "Weekly profit withdrawal",
                    timestamp = now - 86400000L * 2,
                    createdAt = now - 86400000L * 2
                )
            )
            withdrawalDao.insertWithdrawal(
                WithdrawalEntity(
                    partnerId = sureshId,
                    partnerName = "Suresh",
                    amount = 4000.0,
                    category = "Fuel Advance",
                    note = "Advance taken for upcoming diesel bulk barrel",
                    timestamp = now - 86400000L * 1,
                    createdAt = now - 86400000L * 1
                )
            )
        }
    }
}
