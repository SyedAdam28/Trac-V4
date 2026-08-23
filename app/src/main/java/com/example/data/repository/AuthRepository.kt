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

    suspend fun syncUserToFirestore(name: String) = withContext(Dispatchers.IO) {
        val firebaseUser = auth.currentUser ?: return@withContext null
        val uid = firebaseUser.uid
        val phone = firebaseUser.phoneNumber ?: ""

        val userEntity = UserEntity(
            uid = uid,
            phoneNumber = phone,
            name = name,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )

        // Save to Room
        database.userDao().insertUser(userEntity)

        // Save to Firestore
        firestore.collection("users").document(uid).set(userEntity).await()

        userEntity
    }

    suspend fun createBusiness(ownerUid: String, businessName: String, ownerName: String, phone: String): BusinessEntity = withContext(Dispatchers.IO) {
        val businessId = "BIZ-${UUID.randomUUID()}"
        
        val business = BusinessEntity(
            businessId = businessId,
            businessName = businessName,
            ownerUserId = ownerUid,
            businessPhone = phone,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )

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
            subscriptionId = "SUB-${UUID.randomUUID()}",
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

        // Save to Firestore
        val batch = firestore.batch()
        
        val bizRef = firestore.collection("businesses").document(businessId)
        batch.set(bizRef, business)
        
        val memRef = bizRef.collection("memberships").document(membership.membershipId)
        batch.set(memRef, membership)
        
        val subRef = bizRef.collection("subscription").document("current")
        batch.set(subRef, subscription)
        
        batch.commit().await()

        business
    }

    suspend fun checkExistingMemberships(uid: String): List<MembershipEntity> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collectionGroup("memberships")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()
                
            val memberships = snapshot.documents.mapNotNull { doc ->
                doc.toObject(MembershipEntity::class.java)
            }
            
            // Sync to room
            memberships.forEach { mem ->
                database.membershipDao().insertMembership(mem)
            }
            
            memberships
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun loadBusinessData(businessId: String) = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("businesses").document(businessId).get().await()
            doc.toObject(BusinessEntity::class.java)?.let {
                database.businessDao().insertBusiness(it)
            }
            
            val subDoc = firestore.collection("businesses").document(businessId).collection("subscription").document("current").get().await()
            subDoc.toObject(SubscriptionEntity::class.java)?.let {
                database.subscriptionDao().insertSubscription(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
