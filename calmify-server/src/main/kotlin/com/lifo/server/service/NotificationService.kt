package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.server.model.PaginationParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.slf4j.LoggerFactory

@Serializable
data class NotificationProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val type: String = "",
    @ProtoNumber(4) val title: String = "",
    @ProtoNumber(5) val body: String = "",
    @ProtoNumber(6) val data: String = "",
    @ProtoNumber(7) val isRead: Boolean = false,
    @ProtoNumber(8) val createdAtMillis: Long = 0L,
)

class NotificationService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    private val collection = "notifications"

    data class PagedNotifications(val items: List<NotificationProto>, val meta: PaginationMeta)

    suspend fun getNotifications(userId: String, params: PaginationParams): PagedNotifications {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        var query = firestore.collection(collection)
            .whereEqualTo("userId", userId)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(collection).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            NotificationProto(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                type = doc.getString("type") ?: "",
                title = doc.getString("title") ?: "",
                body = doc.getString("body") ?: "",
                data = doc.getString("data") ?: "",
                isRead = doc.getBoolean("isRead") ?: false,
                createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
            )
        }

        return PagedNotifications(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().id else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun markAsRead(userId: String, notificationId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(collection).document(notificationId).get().get()
        if (!doc.exists() || doc.getString("userId") != userId) return false
        firestore.collection(collection).document(notificationId).update("isRead", true).get()
        return true
    }

    suspend fun markAllAsRead(userId: String) {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val unread = firestore.collection(collection)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get().get().documents

        val batch = firestore.batch()
        unread.forEach { batch.update(it.reference, "isRead", true) }
        batch.commit().get()
        logger.info("Marked ${unread.size} notifications as read for user $userId")
    }

    suspend fun getUnreadCount(userId: String): Int {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        return firestore.collection(collection)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get().get().documents.size
    }
}
