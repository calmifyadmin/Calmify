package com.lifo.server.service

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.shared.model.ThreadProto
import com.lifo.server.model.PaginationParams
import org.slf4j.LoggerFactory

class SocialService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(SocialService::class.java)
    private val threadsCollection = "threads"
    private val likesCollection = "threadLikes"
    private val repostsCollection = "threadReposts"
    private val followsCollection = "follows"

    data class PagedThreads(val items: List<ThreadProto>, val meta: PaginationMeta)

    // --- Threads ---

    suspend fun getFeed(userId: String, params: PaginationParams): PagedThreads {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        // For-you feed: public threads ordered by recency (ranking will be server-side later)
        var query = firestore.collection(threadsCollection)
            .whereEqualTo("visibility", "public")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(threadsCollection).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val threadIds = docs.take(params.limit).map { it.id }

        // Batch check likes and reposts for current user
        val likedSet = batchCheckLikes(firestore, userId, threadIds)
        val repostedSet = batchCheckReposts(firestore, userId, threadIds)

        val items = docs.take(params.limit).map { doc ->
            docToThread(doc, doc.id in likedSet, doc.id in repostedSet)
        }

        return PagedThreads(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().threadId else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getFollowingFeed(userId: String, params: PaginationParams): PagedThreads {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        // Get who user follows
        val followingDocs = firestore.collection(followsCollection)
            .whereEqualTo("followerId", userId)
            .get().get().documents
        val followingIds = followingDocs.map { it.getString("followedId") ?: "" }.filter { it.isNotEmpty() }

        if (followingIds.isEmpty()) return PagedThreads(emptyList(), PaginationMeta())

        // Firestore whereIn max 30 items
        val chunks = followingIds.chunked(30)
        val allDocs = chunks.flatMap { chunk ->
            firestore.collection(threadsCollection)
                .whereIn("authorId", chunk)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(params.limit + 1)
                .get().get().documents
        }.sortedByDescending { it.getLong("createdAt") ?: 0L }
            .take(params.limit + 1)

        val hasMore = allDocs.size > params.limit
        val items = allDocs.take(params.limit).map { doc ->
            docToThread(doc, isLikedByCurrentUser = false, isRepostedByCurrentUser = false)
        }

        return PagedThreads(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().threadId else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getThreadById(userId: String, threadId: String): ThreadProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(threadsCollection).document(threadId).get().get()
        if (!doc.exists()) return null

        val isLiked = firestore.collection(likesCollection)
            .document("${userId}_$threadId").get().get().exists()
        val isReposted = firestore.collection(repostsCollection)
            .document("${userId}_$threadId").get().get().exists()

        return docToThread(doc, isLiked, isReposted)
    }

    suspend fun getReplies(threadId: String, params: PaginationParams): PagedThreads {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        var query = firestore.collection(threadsCollection)
            .whereEqualTo("parentThreadId", threadId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(threadsCollection).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            docToThread(doc, isLikedByCurrentUser = false, isRepostedByCurrentUser = false)
        }

        return PagedThreads(items, PaginationMeta(
            cursor = if (hasMore && items.isNotEmpty()) items.last().threadId else "",
            hasMore = hasMore,
        ))
    }

    suspend fun createThread(userId: String, thread: ThreadProto): ThreadProto {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val now = System.currentTimeMillis()

        val data = hashMapOf<String, Any>(
            "authorId" to userId,
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
            firestore.collection(threadsCollection).document(thread.parentThreadId)
                .update("replyCount", FieldValue.increment(1)).get()
        }

        val docRef = firestore.collection(threadsCollection).document()
        docRef.set(data).get()
        logger.info("Created thread ${docRef.id} by user $userId")

        return thread.copy(threadId = docRef.id, authorId = userId, createdAt = now, updatedAt = now)
    }

    suspend fun deleteThread(userId: String, threadId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(threadsCollection).document(threadId).get().get()
        if (!doc.exists() || doc.getString("authorId") != userId) return false

        firestore.collection(threadsCollection).document(threadId).delete().get()
        logger.info("Deleted thread $threadId by user $userId")
        return true
    }

    // --- Engagement ---

    suspend fun likeThread(userId: String, threadId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val likeId = "${userId}_$threadId"
        val likeRef = firestore.collection(likesCollection).document(likeId)

        if (likeRef.get().get().exists()) return false // Already liked

        likeRef.set(mapOf("userId" to userId, "threadId" to threadId, "createdAt" to System.currentTimeMillis())).get()
        firestore.collection(threadsCollection).document(threadId)
            .update("likeCount", FieldValue.increment(1)).get()
        return true
    }

    suspend fun unlikeThread(userId: String, threadId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val likeRef = firestore.collection(likesCollection).document("${userId}_$threadId")

        if (!likeRef.get().get().exists()) return false

        likeRef.delete().get()
        firestore.collection(threadsCollection).document(threadId)
            .update("likeCount", FieldValue.increment(-1)).get()
        return true
    }

    suspend fun repostThread(userId: String, threadId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val repostId = "${userId}_$threadId"
        val repostRef = firestore.collection(repostsCollection).document(repostId)

        if (repostRef.get().get().exists()) return false

        repostRef.set(mapOf("userId" to userId, "threadId" to threadId, "createdAt" to System.currentTimeMillis())).get()
        firestore.collection(threadsCollection).document(threadId)
            .update("repostCount", FieldValue.increment(1)).get()
        return true
    }

    // --- Social Graph ---

    suspend fun follow(followerId: String, followedId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        if (followerId == followedId) return false

        val followId = "${followerId}_$followedId"
        val followRef = firestore.collection(followsCollection).document(followId)
        if (followRef.get().get().exists()) return false

        followRef.set(mapOf(
            "followerId" to followerId,
            "followedId" to followedId,
            "createdAt" to System.currentTimeMillis(),
        )).get()
        return true
    }

    suspend fun unfollow(followerId: String, followedId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val followRef = firestore.collection(followsCollection).document("${followerId}_$followedId")
        if (!followRef.get().get().exists()) return false
        followRef.delete().get()
        return true
    }

    // --- Helpers ---

    private fun batchCheckLikes(firestore: Firestore, userId: String, threadIds: List<String>): Set<String> {
        if (threadIds.isEmpty()) return emptySet()
        return threadIds.filter { threadId ->
            firestore.collection(likesCollection).document("${userId}_$threadId").get().get().exists()
        }.toSet()
    }

    private fun batchCheckReposts(firestore: Firestore, userId: String, threadIds: List<String>): Set<String> {
        if (threadIds.isEmpty()) return emptySet()
        return threadIds.filter { threadId ->
            firestore.collection(repostsCollection).document("${userId}_$threadId").get().get().exists()
        }.toSet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToThread(
        doc: com.google.cloud.firestore.DocumentSnapshot,
        isLikedByCurrentUser: Boolean,
        isRepostedByCurrentUser: Boolean,
    ): ThreadProto = ThreadProto(
        threadId = doc.id,
        authorId = doc.getString("authorId") ?: "",
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
