package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.repository.NotificationRepository
import com.lifo.util.repository.NotificationRepository.Notification
import com.lifo.util.repository.NotificationRepository.NotificationType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * FirestoreNotificationRepository Implementation
 *
 * Firestore-backed implementation of NotificationRepository.
 * Collection: notifications (top-level, filtered by userId)
 *
 * Notifications are created server-side (Cloud Functions) when social events occur
 * (new follower, like, reply, mention). This repository reads/manages them.
 */
class FirestoreNotificationRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : NotificationRepository {

    companion object {
        private const val TAG = "FirestoreNotifRepo"
        private const val COLLECTION_NOTIFICATIONS = "notifications"
    }

    private val notificationsCollection by lazy { firestore.collection(COLLECTION_NOTIFICATIONS) }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    /**
     * Get notifications for a user, real-time via Firestore listener.
     */
    override fun getNotifications(userId: String, limit: Int): Flow<RequestState<List<Notification>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = notificationsCollection
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting notifications: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToNotification(doc)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed notification ${doc.id}")
                            null
                        }
                    }
                    trySend(RequestState.Success(notifications))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Real-time unread count via Firestore listener.
     */
    override fun getUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val listenerRegistration = notificationsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error getting unread count: ${error.message}")
                    trySend(0)
                    return@addSnapshotListener
                }

                trySend(snapshot?.size() ?: 0)
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Mark a single notification as read.
     */
    override suspend fun markAsRead(notificationId: String): RequestState<Boolean> {
        return try {
            notificationsCollection.document(notificationId)
                .update("isRead", true)
                .await()

            Log.d(TAG, "Notification $notificationId marked as read")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read: ${e.message}")
            RequestState.Error(e)
        }
    }

    /**
     * Mark all notifications as read for a user (batch update).
     */
    override suspend fun markAllAsRead(userId: String): RequestState<Boolean> {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return RequestState.Success(true)
            }

            // Batch update (max 500 per batch)
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()

            Log.d(TAG, "Marked ${snapshot.size()} notifications as read for $userId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all as read: ${e.message}")
            RequestState.Error(e)
        }
    }

    /**
     * Create a notification document in Firestore.
     */
    override suspend fun createNotification(notification: Notification): RequestState<String> {
        return try {
            // Don't notify yourself
            if (notification.actorId == notification.userId) {
                return RequestState.Success("")
            }

            val data = mapOf(
                "userId" to notification.userId,
                "type" to notification.type.name,
                "actorId" to notification.actorId,
                "actorName" to notification.actorName,
                "actorAvatarUrl" to notification.actorAvatarUrl,
                "threadId" to notification.threadId,
                "message" to notification.message,
                "isRead" to false,
                "createdAt" to System.currentTimeMillis()
            )
            val docRef = notificationsCollection.add(data).await()
            RequestState.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification: ${e.message}")
            RequestState.Error(e)
        }
    }

    // -- Helpers --

    private fun docToNotification(doc: com.google.firebase.firestore.DocumentSnapshot): Notification {
        val typeString = doc.getString("type") ?: "OTHER"
        val type = try {
            NotificationType.valueOf(typeString)
        } catch (e: IllegalArgumentException) {
            NotificationType.OTHER
        }

        return Notification(
            id = doc.id,
            userId = doc.getString("userId") ?: "",
            type = type,
            actorId = doc.getString("actorId") ?: "",
            actorName = doc.getString("actorName"),
            actorAvatarUrl = doc.getString("actorAvatarUrl"),
            threadId = doc.getString("threadId"),
            message = doc.getString("message") ?: "",
            isRead = doc.getBoolean("isRead") ?: false,
            createdAt = doc.getLong("createdAt") ?: 0
        )
    }
}
