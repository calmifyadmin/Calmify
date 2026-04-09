package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * GDPR compliance service.
 *
 * - Data export: collects ALL user data from every Firestore collection
 * - Account deletion: cascading delete across all collections + Firebase Auth
 *
 * SECURITY: Only the authenticated user can export/delete their own data.
 * All operations are audit-logged.
 */
class GdprService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(GdprService::class.java)

    // All Firestore collections that contain user data
    private val userCollections = listOf(
        "diary",
        "chatSessions",
        "chatMessages",
        "insights",
        "profileSettings",
        "threads",
        "notifications",
        "presence",
        // 13 wellness types
        "gratitude", "energy", "sleep", "meditation", "habits",
        "movement", "reframe", "wellbeing", "awe", "connection",
        "recurringThought", "block", "values",
        // Social graph
        "followers", "following",
        // AI usage
        "tokenUsage",
    )

    /**
     * Export all user data as a JSON structure.
     * Returns a map of collection name -> list of documents.
     */
    suspend fun exportUserData(userId: String): Map<String, List<Map<String, Any?>>> {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val export = mutableMapOf<String, List<Map<String, Any?>>>()

        for (collection in userCollections) {
            try {
                val docs = firestore.collection(collection)
                    .whereEqualTo("ownerId", userId)
                    .get().get()
                    .documents

                if (docs.isNotEmpty()) {
                    export[collection] = docs.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["_documentId"] = doc.id
                        data
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to export collection '{}' for user: {}", collection, e.message)
                // Continue with other collections — partial export is better than none
            }
        }

        logger.info("GDPR export completed for user {} — {} collections, {} total documents",
            userId, export.size, export.values.sumOf { it.size })

        return export
    }

    /**
     * Delete ALL user data across all collections + Firebase Auth account.
     * This is irreversible.
     *
     * Returns the number of documents deleted.
     */
    suspend fun deleteUserAccount(userId: String): Int {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        var totalDeleted = 0

        // Phase 1: Delete all user documents from every collection
        for (collection in userCollections) {
            try {
                val docs = firestore.collection(collection)
                    .whereEqualTo("ownerId", userId)
                    .get().get()
                    .documents

                val batch = firestore.batch()
                for (doc in docs) {
                    batch.delete(doc.reference)
                    totalDeleted++
                }
                if (docs.isNotEmpty()) {
                    batch.commit().get()
                    logger.info("Deleted {} documents from '{}' for user {}", docs.size, collection, userId)
                }
            } catch (e: Exception) {
                logger.error("Failed to delete collection '{}' for user {}: {}", collection, userId, e.message)
                // Continue — best effort deletion
            }
        }

        // Phase 2: Delete social graph entries where user is the TARGET (not just ownerId)
        try {
            val followerDocs = firestore.collection("followers")
                .whereEqualTo("targetUserId", userId)
                .get().get().documents
            val batch = firestore.batch()
            for (doc in followerDocs) {
                batch.delete(doc.reference)
                totalDeleted++
            }
            if (followerDocs.isNotEmpty()) batch.commit().get()
        } catch (e: Exception) {
            logger.warn("Failed to clean social graph for user {}: {}", userId, e.message)
        }

        // Phase 3: Record deletion in deletion_log (for sync engine delta queries)
        try {
            firestore.collection("deletion_log").document().set(
                hashMapOf<String, Any>(
                    "userId" to userId,
                    "type" to "ACCOUNT_DELETION",
                    "deletedAt" to System.currentTimeMillis(),
                    "documentCount" to totalDeleted,
                )
            ).get()
        } catch (e: Exception) {
            logger.warn("Failed to record deletion log for user {}: {}", userId, e.message)
        }

        // Phase 4: Delete Firebase Auth account
        try {
            FirebaseAuth.getInstance().deleteUser(userId)
            logger.info("Firebase Auth account deleted for user {}", userId)
        } catch (e: Exception) {
            logger.error("Failed to delete Firebase Auth for user {}: {}", userId, e.message)
            // Auth deletion failure is serious but we've already nuked the data
        }

        logger.info("GDPR account deletion completed for user {} — {} documents deleted", userId, totalDeleted)
        return totalDeleted
    }
}
