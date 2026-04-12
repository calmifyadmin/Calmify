package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ThreadRepository
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * FirestoreThreadRepository Implementation
 *
 * Firestore-backed implementation of ThreadRepository.
 * Collection: threads (top-level)
 * Sub-collection: threads/{threadId}/likes (for like tracking)
 *
 * MVP implementation — social threads (posts/replies).
 */
class FirestoreThreadRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ThreadRepository {

    companion object {
        private const val TAG = "FirestoreThreadRepo"
        private const val COLLECTION_THREADS = "threads"
        private const val SUBCOLLECTION_LIKES = "likes"
        private const val SUBCOLLECTION_REPOSTS = "reposts"
        private const val COLLECTION_NOTIFICATIONS = "notifications"
    }

    private val threadsCollection by lazy { firestore.collection(COLLECTION_THREADS) }
    private val notificationsCollection by lazy { firestore.collection(COLLECTION_NOTIFICATIONS) }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override fun getThreadById(threadId: String): Flow<RequestState<Thread?>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = threadsCollection
            .document(threadId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting thread $threadId: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val thread = snapshotToThread(snapshot)
                        trySend(RequestState.Success(thread))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing thread: ${e.message}")
                        trySend(RequestState.Error(e))
                    }
                } else {
                    trySend(RequestState.Success(null))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getThreadsByAuthor(authorId: String, limit: Int): Flow<RequestState<List<Thread>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = threadsCollection
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting threads by author: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val threads = snapshot.documents.mapNotNull { doc ->
                        try {
                            snapshotToThread(doc)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed thread doc ${doc.id}")
                            null
                        }
                    }
                    trySend(RequestState.Success(threads))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getReplies(parentThreadId: String): Flow<RequestState<List<Thread>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = threadsCollection
            .whereEqualTo("parentThreadId", parentThreadId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting replies: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val threads = snapshot.documents.mapNotNull { doc ->
                        try {
                            snapshotToThread(doc)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(RequestState.Success(threads))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun createThread(thread: Thread): RequestState<String> {
        return try {
            val userId = currentUserId
            val docRef = threadsCollection.document()
            val threadWithId = thread.copy(
                threadId = docRef.id,
                authorId = userId,
                createdAt = System.currentTimeMillis()
            )

            val data = threadToMap(threadWithId)
            docRef.set(data).await()

            // If this is a reply, increment parent's replyCount and notify parent author
            val parentId = thread.parentThreadId
            if (parentId != null) {
                threadsCollection.document(parentId)
                    .update("replyCount", FieldValue.increment(1))
                    .await()

                // Notify parent thread author
                val parentDoc = threadsCollection.document(parentId).get().await()
                val parentAuthorId = parentDoc.getString("authorId")
                if (parentAuthorId != null) {
                    sendNotification(
                        targetUserId = parentAuthorId,
                        type = "REPLY",
                        actorId = userId,
                        threadId = parentId,
                        message = "replied to your post"
                    )
                }
            }

            Log.d(TAG, "Thread created: ${docRef.id}")
            RequestState.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating thread: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun deleteThread(threadId: String): RequestState<Boolean> {
        return try {
            val doc = threadsCollection.document(threadId).get().await()
            val authorId = doc.getString("authorId")

            if (authorId != currentUserId) {
                return RequestState.Error(Exception("Access denied: not the author"))
            }

            // If it's a reply, decrement parent's replyCount
            val parentId = doc.getString("parentThreadId")
            if (parentId != null) {
                threadsCollection.document(parentId)
                    .update("replyCount", FieldValue.increment(-1))
                    .await()
            }

            threadsCollection.document(threadId).delete().await()

            Log.d(TAG, "Thread deleted: $threadId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting thread: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun likeThread(userId: String, threadId: String): RequestState<Boolean> {
        return try {
            val likeRef = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_LIKES)
                .document(userId)

            // Check if already liked
            val existing = likeRef.get().await()
            if (existing.exists()) {
                return RequestState.Success(true) // Already liked
            }

            // Add like document and increment counter atomically
            val batch = firestore.batch()
            batch.set(likeRef, mapOf("userId" to userId, "createdAt" to System.currentTimeMillis()))
            batch.update(threadsCollection.document(threadId), "likeCount", FieldValue.increment(1))
            batch.commit().await()

            Log.d(TAG, "Thread $threadId liked by $userId")

            // Send like notification to thread author
            val threadDoc = threadsCollection.document(threadId).get().await()
            val authorId = threadDoc.getString("authorId")
            if (authorId != null) {
                sendNotification(
                    targetUserId = authorId,
                    type = "LIKE",
                    actorId = userId,
                    threadId = threadId,
                    message = "liked your post"
                )
            }

            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error liking thread: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun unlikeThread(userId: String, threadId: String): RequestState<Boolean> {
        return try {
            val likeRef = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_LIKES)
                .document(userId)

            val existing = likeRef.get().await()
            if (!existing.exists()) {
                return RequestState.Success(true) // Already not liked
            }

            // Remove like document and decrement counter atomically
            val batch = firestore.batch()
            batch.delete(likeRef)
            batch.update(threadsCollection.document(threadId), "likeCount", FieldValue.increment(-1))
            batch.commit().await()

            Log.d(TAG, "Thread $threadId unliked by $userId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unliking thread: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun isLikedByUser(userId: String, threadId: String): Boolean {
        return try {
            val likeDoc = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_LIKES)
                .document(userId)
                .get()
                .await()
            likeDoc.exists()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking like status: ${e.message}")
            false
        }
    }

    override suspend fun repostThread(userId: String, threadId: String): RequestState<Boolean> {
        return try {
            val repostRef = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_REPOSTS)
                .document(userId)

            val existing = repostRef.get().await()
            if (existing.exists()) {
                return RequestState.Success(true)
            }

            val batch = firestore.batch()
            batch.set(repostRef, mapOf("userId" to userId, "createdAt" to System.currentTimeMillis()))
            batch.update(threadsCollection.document(threadId), "repostCount", FieldValue.increment(1))
            batch.commit().await()

            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override suspend fun unrepostThread(userId: String, threadId: String): RequestState<Boolean> {
        return try {
            val repostRef = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_REPOSTS)
                .document(userId)

            val existing = repostRef.get().await()
            if (!existing.exists()) {
                return RequestState.Success(true)
            }

            val batch = firestore.batch()
            batch.delete(repostRef)
            batch.update(threadsCollection.document(threadId), "repostCount", FieldValue.increment(-1))
            batch.commit().await()

            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    // -- Notification Helpers --

    private suspend fun sendNotification(
        targetUserId: String,
        type: String,
        actorId: String,
        threadId: String?,
        message: String,
    ) {
        // Don't notify yourself
        if (actorId == targetUserId) return
        try {
            val data = mapOf(
                "userId" to targetUserId,
                "type" to type,
                "actorId" to actorId,
                "threadId" to threadId,
                "message" to message,
                "isRead" to false,
                "createdAt" to System.currentTimeMillis()
            )
            notificationsCollection.add(data).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create notification: ${e.message}")
        }
    }

    // -- Helpers --

    @Suppress("UNCHECKED_CAST")
    private fun snapshotToThread(doc: com.google.firebase.firestore.DocumentSnapshot): Thread {
        return Thread(
            threadId = doc.id,
            authorId = doc.getString("authorId") ?: "",
            parentThreadId = doc.getString("parentThreadId"),
            text = doc.getString("text") ?: "",
            likeCount = doc.getLong("likeCount") ?: 0,
            replyCount = doc.getLong("replyCount") ?: 0,
            repostCount = doc.getLong("repostCount") ?: 0,
            visibility = doc.getString("visibility") ?: "public",
            moodTag = doc.getString("moodTag"),
            isFromJournal = doc.getBoolean("isFromJournal") ?: false,
            mediaUrls = (doc.get("mediaUrls") as? List<String>) ?: emptyList(),
            createdAt = doc.getLong("createdAt") ?: 0,
            updatedAt = doc.getLong("updatedAt"),
            viewCount = doc.getLong("viewCount") ?: 0,
            shareCount = doc.getLong("shareCount") ?: 0,
            postCategory = doc.getString("postCategory")
        )
    }

    private fun threadToMap(thread: Thread): Map<String, Any?> {
        return mapOf(
            "authorId" to thread.authorId,
            "parentThreadId" to thread.parentThreadId,
            "text" to thread.text,
            "likeCount" to thread.likeCount,
            "replyCount" to thread.replyCount,
            "visibility" to thread.visibility,
            "moodTag" to thread.moodTag,
            "isFromJournal" to thread.isFromJournal,
            "mediaUrls" to thread.mediaUrls,
            "createdAt" to thread.createdAt,
            "updatedAt" to thread.updatedAt,
            "postCategory" to thread.postCategory
        )
    }
}
