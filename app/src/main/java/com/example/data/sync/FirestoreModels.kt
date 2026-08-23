package com.example.data.sync

import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.SyncStatus
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity

/**
 * Clean Firestore DTO Models for the business collections:
 * businesses/{businessId}/[customers, jobs, expenses, withdrawals, tractors, partners]
 */

data class FirestoreCustomer(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val phone: String = "",
    val location: String = "",
    val totalBilled: Double = 0.0,
    val totalPaid: Double = 0.0,
    val balanceDue: Double = 0.0,
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FirestoreJobEntry(
    val id: String = "",
    val businessId: String = "",
    val customerUuid: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerLocation: String = "",
    val operatorName: String = "",
    val tractorUuid: String = "",
    val tractorLabel: String = "",
    val workType: String = "",
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val durationMinutes: Long = 0L,
    val hourlyRate: Double = 0.0,
    val totalAmount: Double = 0.0,
    val amountReceived: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val addedByPartner: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FirestoreExpense(
    val id: String = "",
    val businessId: String = "",
    val expenseType: String = "",
    val amount: Double = 0.0,
    val tractorUuid: String = "",
    val tractorLabel: String = "",
    val operatorName: String = "",
    val description: String = "",
    val addedByPartner: String = "",
    val dateTimestamp: Long = 0L,
    val relatedJobUuid: String = "",
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FirestoreWithdrawal(
    val id: String = "",
    val businessId: String = "",
    val partnerUuid: String = "",
    val partnerName: String = "",
    val amount: Double = 0.0,
    val category: String = "Personal Use",
    val note: String = "",
    val timestamp: Long = 0L,
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FirestoreTractor(
    val id: String = "",
    val businessId: String = "",
    val label: String = "",
    val chassisNo: String = "",
    val modelYear: String = "",
    val operatorName: String = "",
    val isActive: Boolean = true,
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class FirestorePartner(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "Partner",
    val avatarColorHex: String = "#10B981",
    val photoUri: String = "",
    val createdBy: String = "",
    val deviceId: String = "",
    val version: Long = 1L,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Mappers between Room Entities and Firestore DTOs
 */
object FirestoreMappers {

    // Customer Mappers
    fun CustomerEntity.toFirestore(businessId: String, deviceId: String): FirestoreCustomer {
        return FirestoreCustomer(
            id = this.uuid.ifBlank { this.id.toString() },
            businessId = this.businessId.ifBlank { businessId },
            name = this.name,
            phone = this.phone,
            location = this.location,
            totalBilled = this.totalBilled,
            totalPaid = this.totalPaid,
            balanceDue = this.balanceDue,
            createdBy = this.createdBy,
            deviceId = this.deviceId.ifBlank { deviceId },
            version = this.version,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = if (this.updatedAt > 0) this.updatedAt else this.createdAt
        )
    }

    fun FirestoreCustomer.toRoomEntity(existingLocalId: Long = 0): CustomerEntity {
        return CustomerEntity(
            id = existingLocalId,
            uuid = this.id,
            businessId = this.businessId,
            name = this.name,
            phone = this.phone,
            location = this.location,
            totalBilled = this.totalBilled,
            totalPaid = this.totalPaid,
            balanceDue = this.balanceDue,
            createdBy = this.createdBy,
            deviceId = this.deviceId,
            version = this.version,
            isSynced = true,
            syncStatus = SyncStatus.SYNCED.name,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    // Job Entry Mappers
    fun JobEntryEntity.toFirestore(businessId: String, deviceId: String): FirestoreJobEntry {
        return FirestoreJobEntry(
            id = this.uuid.ifBlank { this.id.toString() },
            businessId = this.businessId.ifBlank { businessId },
            customerUuid = this.customerUuid,
            customerName = this.customerName,
            customerPhone = this.customerPhone,
            customerLocation = this.customerLocation,
            operatorName = this.operatorName,
            tractorUuid = this.tractorUuid,
            tractorLabel = this.tractorLabel,
            workType = this.workType,
            startTimeMillis = this.startTimeMillis,
            endTimeMillis = this.endTimeMillis,
            durationMinutes = this.durationMinutes,
            hourlyRate = this.hourlyRate,
            totalAmount = this.totalAmount,
            amountReceived = this.amountReceived,
            pendingAmount = this.pendingAmount,
            addedByPartner = this.addedByPartner,
            notes = this.notes,
            createdBy = this.createdBy.ifBlank { this.addedByPartner },
            deviceId = this.deviceId.ifBlank { deviceId },
            version = this.version,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = if (this.updatedAt > 0) this.updatedAt else this.createdAt
        )
    }

    fun FirestoreJobEntry.toRoomEntity(
        existingLocalId: Long = 0,
        localCustomerId: Long = 0,
        localTractorId: Long = 0
    ): JobEntryEntity {
        return JobEntryEntity(
            id = existingLocalId,
            uuid = this.id,
            businessId = this.businessId,
            customerId = localCustomerId,
            customerUuid = this.customerUuid,
            customerName = this.customerName,
            customerPhone = this.customerPhone,
            customerLocation = this.customerLocation,
            operatorName = this.operatorName,
            tractorId = localTractorId,
            tractorUuid = this.tractorUuid,
            tractorLabel = this.tractorLabel,
            workType = this.workType,
            startTimeMillis = this.startTimeMillis,
            endTimeMillis = this.endTimeMillis,
            durationMinutes = this.durationMinutes,
            hourlyRate = this.hourlyRate,
            totalAmount = this.totalAmount,
            amountReceived = this.amountReceived,
            pendingAmount = this.pendingAmount,
            addedByPartner = this.addedByPartner,
            notes = this.notes,
            createdBy = this.createdBy,
            deviceId = this.deviceId,
            version = this.version,
            isSynced = true,
            syncStatus = SyncStatus.SYNCED.name,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    // Expense Mappers
    fun ExpenseEntity.toFirestore(businessId: String, deviceId: String): FirestoreExpense {
        return FirestoreExpense(
            id = this.uuid.ifBlank { this.id.toString() },
            businessId = this.businessId.ifBlank { businessId },
            expenseType = this.expenseType,
            amount = this.amount,
            tractorUuid = this.tractorUuid,
            tractorLabel = this.tractorLabel,
            operatorName = this.operatorName,
            description = this.description,
            addedByPartner = this.addedByPartner,
            dateTimestamp = this.dateTimestamp,
            relatedJobUuid = this.relatedJobUuid,
            createdBy = this.createdBy.ifBlank { this.addedByPartner },
            deviceId = this.deviceId.ifBlank { deviceId },
            version = this.version,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = if (this.updatedAt > 0) this.updatedAt else this.createdAt
        )
    }

    fun FirestoreExpense.toRoomEntity(
        existingLocalId: Long = 0,
        localTractorId: Long = 0,
        localRelatedJobId: Long? = null
    ): ExpenseEntity {
        return ExpenseEntity(
            id = existingLocalId,
            uuid = this.id,
            businessId = this.businessId,
            expenseType = this.expenseType,
            amount = this.amount,
            tractorId = localTractorId,
            tractorUuid = this.tractorUuid,
            tractorLabel = this.tractorLabel,
            operatorName = this.operatorName,
            description = this.description,
            addedByPartner = this.addedByPartner,
            dateTimestamp = this.dateTimestamp,
            relatedJobId = localRelatedJobId,
            relatedJobUuid = this.relatedJobUuid,
            createdBy = this.createdBy,
            deviceId = this.deviceId,
            version = this.version,
            isSynced = true,
            syncStatus = SyncStatus.SYNCED.name,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    // Withdrawal Mappers
    fun WithdrawalEntity.toFirestore(businessId: String, deviceId: String): FirestoreWithdrawal {
        return FirestoreWithdrawal(
            id = this.uuid.ifBlank { this.id.toString() },
            businessId = this.businessId.ifBlank { businessId },
            partnerUuid = this.partnerUuid,
            partnerName = this.partnerName,
            amount = this.amount,
            category = this.category,
            note = this.note,
            timestamp = this.timestamp,
            createdBy = this.createdBy.ifBlank { this.partnerName },
            deviceId = this.deviceId.ifBlank { deviceId },
            version = this.version,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = if (this.updatedAt > 0) this.updatedAt else this.createdAt
        )
    }

    fun FirestoreWithdrawal.toRoomEntity(
        existingLocalId: Long = 0,
        localPartnerId: Long = 0
    ): WithdrawalEntity {
        return WithdrawalEntity(
            id = existingLocalId,
            uuid = this.id,
            businessId = this.businessId,
            partnerId = localPartnerId,
            partnerUuid = this.partnerUuid,
            partnerName = this.partnerName,
            amount = this.amount,
            category = this.category,
            note = this.note,
            timestamp = this.timestamp,
            createdBy = this.createdBy,
            deviceId = this.deviceId,
            version = this.version,
            isSynced = true,
            syncStatus = SyncStatus.SYNCED.name,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    // Tractor Mappers
    fun TractorEntity.toFirestore(businessId: String, deviceId: String): FirestoreTractor {
        return FirestoreTractor(
            id = this.uuid.ifBlank { this.id.toString() },
            businessId = this.businessId.ifBlank { businessId },
            label = this.label,
            chassisNo = this.chassisNo,
            modelYear = this.modelYear,
            operatorName = this.operatorName,
            isActive = this.isActive,
            createdBy = this.createdBy,
            deviceId = this.deviceId.ifBlank { deviceId },
            version = this.version,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = if (this.updatedAt > 0) this.updatedAt else this.createdAt
        )
    }

    fun FirestoreTractor.toRoomEntity(existingLocalId: Long = 0): TractorEntity {
        return TractorEntity(
            id = existingLocalId,
            uuid = this.id,
            businessId = this.businessId,
            label = this.label,
            chassisNo = this.chassisNo,
            modelYear = this.modelYear,
            operatorName = this.operatorName,
            isActive = this.isActive,
            createdBy = this.createdBy,
            deviceId = this.deviceId,
            version = this.version,
            isSynced = true,
            syncStatus = SyncStatus.SYNCED.name,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    // Partner Mappers
    fun PartnerEntity.toFirestore(businessId: String, deviceId: String): FirestorePartner {
        return FirestorePartner(
            id = this.uuid.ifBlank { this.id.toString() },
            businessId = this.businessId.ifBlank { businessId },
            name = this.name,
            phone = this.phone,
            role = this.role,
            avatarColorHex = this.avatarColorHex,
            photoUri = this.photoUri,
            createdBy = this.createdBy,
            deviceId = this.deviceId.ifBlank { deviceId },
            version = this.version,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = if (this.updatedAt > 0) this.updatedAt else this.createdAt
        )
    }

    fun FirestorePartner.toRoomEntity(
        existingLocalId: Long = 0,
        isCurrentActive: Boolean = false
    ): PartnerEntity {
        return PartnerEntity(
            id = existingLocalId,
            uuid = this.id,
            businessId = this.businessId,
            name = this.name,
            phone = this.phone,
            role = this.role,
            avatarColorHex = this.avatarColorHex,
            photoUri = this.photoUri,
            isCurrentActive = isCurrentActive,
            createdBy = this.createdBy,
            deviceId = this.deviceId,
            version = this.version,
            isSynced = true,
            syncStatus = SyncStatus.SYNCED.name,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
