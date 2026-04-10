package com.lifo.server.service

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.shared.model.ThreadProto
import com.lifo.server.model.PaginationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class SocialService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(SocialService::class.java)

    companion object {
        const val THREADS_COLLECTION = "threads"
        const val SOCIAL_GRAPH_COLLECTION = "social_graph"
        private const val AUTHOR_FIELD = "authorId"
        private const val BATCH_LIMIT = 500
    }

    data class PagedThreads(val items: List<ThreadProto>, val meta: PaginationMeta)

    // ─── Threads ─────────────────────────────────────────────────────

    suspend fun getFeed(userId: String, params: PaginationParams): PagedThreads = withContext(Dispatchers.IO) {
        // For-you feed: public threads ordered by recency
        var query = db.collection(THREADS_COLLECTION)
            .whereEqualTo("visibility", "public")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = db.collection(THREADS_COLLECTION).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val threadIds = docs.take(params.limit).map { it.id }

        // Batch check likes and reposts for current user via subcollections
        val likedSet = batchCheckLikes(userId, threadIds)
        val repostedSet = batchCheckReposts(userId, threadIds)

        val items = docs.take(params.limit).map { doc ->
            docToThread(doc, doc.id in likedSet, doc.id in repostedSet)
        }

        PagedThreads(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().threadId else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getFollowingFeed(userId: String, params: PaginationParams): PagedThreads = withContext(Dispatchers.IO) {
        // Get who user follows from subcollection social_graph/{userId}/following
        val followingDocs = db.collection(SOCIAL_GRAPH_COLLECTION)
            .document(userId)
            .collection("following")
            .get().get().documents
        val followingIds = followingDocs.map { it.id }.filter { it.isNotEmpty() }

        if (followingIds.isEmpty()) return@withContext PagedThreads(emptyList(), PaginationMeta())

        // Firestore whereIn max 30 items
        val chunks = followingIds.chunked(30)
        val allDocs = chunks.flatMap { chunk ->
            db.collection(THREADS_COLLECTION)
                .whereIn(AUTHOR_FIELD, chunk)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(params.limit + 1)
                .get().get().documents
        }.sortedByDescending { it.getLong("createdAt") ?: 0L }
            .take(params.limit + 1)

        val hasMore = allDocs.size > params.limit
        val items = allDocs.take(params.limit).map { doc ->
            docToThread(doc, isLikedByCurrentUser = false, isRepostedByCurrentUser = false)
        }

        PagedThreads(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().threadId else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getThreadById(userId: String, threadId: String): ThreadProto? = withContext(Dispatchers.IO) {
        val doc = db.collection(THREADS_COLLECTION).document(threadId).get().get()
        if (!doc.exists()) return@withContext null

        // Check likes/reposts via subcollections
        val isLiked = db.collection(THREADS_COLLECTION).document(threadId)
            .collection("likes").document(userId).get().get().exists()
        val isReposted = db.collection(THREADS_COLLECTION).document(threadId)
            .collection("reposts").document(userId).get().get().exists()

        docToThread(doc, isLiked, isReposted)
    }

    suspend fun getReplies(threadId: String, params: PaginationParams): PagedThreads = withContext(Dispatchers.IO) {
        var query = db.collection(THREADS_COLLECTION)
            .whereEqualTo("parentThreadId", threadId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = db.collection(THREADS_COLLECTION).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            docToThread(doc, isLikedByCurrentUser = false, isRepostedByCurrentUser = false)
        }

        PagedThreads(items, PaginationMeta(
            cursor = if (hasMore && items.isNotEmpty()) items.last().threadId else "",
            hasMore = hasMore,
        ))
    }

    suspend fun createThread(userId: String, thread: ThreadProto): ThreadProto = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val data = hashMapOf<String, Any>(
            AUTHOR_FIELD to userId,
            "text" to thread.text,
            "visibility" to thread.visibility.ifEmpty { "public" },
            "moodTag" to thread.moodTag,
            "isFromJournal" to thread.isFromJournal,
            "createdAt" to now,
            "updatedAt" to now,
            "likeCount" to 0L,
            "replyCount" to 0L,
            "repostCount" to 0L,
            "viewCount" to 0L,
            "shareCount" to 0L,
            "mediaUrls" to thread.mediaUrls,
            "postCategory" to thread.postCategory,
        )

        if (thread.parentThreadId.isNotEmpty()) {
            data["parentThreadId"] = thread.parentThreadId
            // Increment parent reply count
            db.collection(THREADS_COLLECTION).document(thread.parentThreadId)
                .update("replyCount", FieldValue.increment(1)).get()
        }

        val docRef = db.collection(THREADS_COLLECTION).document()
        docRef.set(data).get()
        logger.info("Created thread ${docRef.id} by user $userId")

        thread.copy(threadId = docRef.id, authorId = userId, createdAt = now, updatedAt = now)
    }

    suspend fun deleteThread(userId: String, threadId: String): Boolean = withContext(Dispatchers.IO) {
        val doc = db.collection(THREADS_COLLECTION).document(threadId).get().get()
        if (!doc.exists() || doc.getString(AUTHOR_FIELD) != userId) return@withContext false

        // Delete likes subcollection
        deleteSubcollection(THREADS_COLLECTION, threadId, "likes")
        // Delete reposts subcollection
        deleteSubcollection(THREADS_COLLECTION, threadId, "reposts")

        db.collection(THREADS_COLLECTION).document(threadId).delete().get()
        logger.info("Deleted thread $threadId by user $userId")
        true
    }

    // ─── Engagement (subcollection-based) ────────────────────────────

    suspend fun likeThread(userId: String, threadId: String): Boolean = withContext(Dispatchers.IO) {
        val likeRef = db.collection(THREADS_COLLECTION).document(threadId)
            .collection("likes").document(userId)

        if (likeRef.get().get().exists()) return@withContext false // Already liked

        likeRef.set(mapOf("userId" to userId, "createdAt" to System.currentTimeMillis())).get()
        db.collection(THREADS_COLLECTION).document(threadId)
            .update("likeCount", FieldValue.increment(1)).get()
        true
    }

    suspend fun unlikeThread(userId: String, threadId: String): Boolean = withContext(Dispatchers.IO) {
        val likeRef = db.collection(THREADS_COLLECTION).document(threadId)
            .collection("likes").document(userId)

        if (!likeRef.get().get().exists()) return@withContext false

        likeRef.delete().get()
        db.collection(THREADS_COLLECTION).document(threadId)
            .update("likeCount", FieldValue.increment(-1)).get()
        true
    }

    suspend fun repostThread(userId: String, threadId: String): Boolean = withContext(Dispatchers.IO) {
        val repostRef = db.collection(THREADS_COLLECTION).document(threadId)
            .collection("reposts").document(userId)

        if (repostRef.get().get().exists()) return@withContext false

        repostRef.set(mapOf("userId" to userId, "createdAt" to System.currentTimeMillis())).get()
        db.collection(THREADS_COLLECTION).document(threadId)
            .update("repostCount", FieldValue.increment(1)).get()
        true
    }

    // ─── Social Graph (subcollection-based) ──────────────────────────

    suspend fun follow(followerId: String, followedId: String): Boolean = withContext(Dispatchers.IO) {
        if (followerId == followedId) return@withContext false

        val followingRef = db.collection(SOCIAL_GRAPH_COLLECTION)
            .document(followerId).collection("following").document(followedId)
        if (followingRef.get().get().exists()) return@withContext false

        val now = System.currentTimeMillis()

        // Write to follower's "following" subcollection
        followingRef.set(mapOf(
            "followedId" to followedId,
            "createdAt" to now,
        )).get()

        // Write to followed's "followers" subcollection
        db.collection(SOCIAL_GRAPH_COLLECTION)
            .document(followedId).collection("followers").document(followerId)
            .set(mapOf(
                "followerId" to followerId,
                "createdAt" to now,
            )).get()

        true
    }

    suspend fun unfollow(followerId: String, followedId: String): Boolean = withContext(Dispatchers.IO) {
        val followingRef = db.collection(SOCIAL_GRAPH_COLLECTION)
            .document(followerId).collection("following").document(followedId)
        if (!followingRef.get().get().exists()) return@withContext false

        followingRef.delete().get()

        // Remove from followed's "followers" subcollection
        db.collection(SOCIAL_GRAPH_COLLECTION)
            .document(followedId).collection("followers").document(followerId)
            .delete().get()

        true
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Batch check likes via subcollections: threads/{threadId}/likes/{userId}
     */
    private fun batchCheckLikes(userId: String, threadIds: List<String>): Set<String> {
        if (threadIds.isEmpty()) return emptySet()
        return threadIds.filter { threadId ->
            db.collection(THREADS_COLLECTION).document(threadId)
                .collection("likes").document(userId).get().get().exists()
        }.toSet()
    }

    /**
     * Batch check reposts via subcollections: threads/{threadId}/reposts/{userId}
     */
    private fun batchCheckReposts(userId: String, threadIds: List<String>): Set<String> {
        if (threadIds.isEmpty()) return emptySet()
        return threadIds.filter { threadId ->
            db.collection(THREADS_COLLECTION).document(threadId)
                .collection("reposts").document(userId).get().get().exists()
        }.toSet()
    }

    /**
     * Delete all documents in a subcollection, chunked by BATCH_LIMIT.
     */
    private fun deleteSubcollection(parentCollection: String, parentDocId: String, subcollectionName: String) {
        val docs = db.collection(parentCollection).document(parentDocId)
            .collection(subcollectionName).get().get().documents
        for (chunk in docs.chunked(BATCH_LIMIT)) {
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().get()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToThread(
        doc: com.google.cloud.firestore.DocumentSnapshot,
        isLikedByCurrentUser: Boolean,
        isRepostedByCurrentUser: Boolean,
    ): ThreadProto = ThreadProto(
        threadId = doc.id,
        authorId = doc.getString(AUTHOR_FIELD) ?: "",
        parentThreadId = doc.getString("parentThreadId") ?: "",
        text = doc.getString("text") ?: "",
        likeCount = doc.getLong("likeCount") ?: 0,
        replyCount = doc.getLong("replyCount") ?: 0,
        repostCount = doc.getLong("repostCount") ?: 0,
        visibility = doc.getString("visibility") ?: "public",
        moodTag = doc.getString("moodTag") ?: "",
        isFromJournal = doc.getBoolean("isFromJournal") ?: false,
        createdAt = doc.getLong("createdAt") ?: 0,
        updatedAt = doc.getLong("updatedAt") ?: 0,
        authorDisplayName = doc.getString("authorDisplayName") ?: "",
        authorUsername = doc.getString("authorUsername") ?: "",
        authorAvatarUrl = doc.getString("authorAvatarUrl") ?: "",
        authorIsVerified = doc.getBoolean("authorIsVerified") ?: false,
        mediaUrls = (doc.get("mediaUrls") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        isLikedByCurrentUser = isLikedByCurrentUser,
        isRepostedByCurrentUser = isRepostedByCurrentUser,
        replyPreviewAvatars = (doc.get("replyPreviewAvatars") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        viewCount = doc.getLong("viewCount") ?: 0,
        shareCount = doc.getLong("shareCount") ?: 0,
        postCategory = doc.getString("postCategory") ?: "",
    )
}
