package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.mongo.database.CachedThreadQueries
import com.lifo.util.model.RequestState
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.FeedRepository.FeedPage
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * FirestoreFeedRepository Implementation
 *
 * Firestore-backed implementation of FeedRepository.
 * Uses two strategies:
 *   - ForYou feed: reads from threads collection, sorted by likeCount (MVP proxy for ranking)
 *   - Following feed: reads from threads collection filtered by followed users (chronological)
 *
 * Feed cache collection: users/{userId}/feed_cache (for future server-side fan-out)
 *
 * MVP implementation — production would use Vertex AI ranking and Cloud Functions fan-out.
 */
class FirestoreFeedRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cachedThreadQueries: CachedThreadQueries,
) : FeedRepository {

    companion object {
        private const val TAG = "FirestoreFeedRepo"
        private const val COLLECTION_THREADS = "threads"
        private const val COLLECTION_SOCIAL_GRAPH = "social_graph"
    }

    private val threadsCollection by lazy { firestore.collection(COLLECTION_THREADS) }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    /**
     * ForYou feed — MVP uses popularity-based sorting (likeCount desc).
     * Production would use Vertex AI semantic ranking.
     * Supports cursor-based pagination via createdAt timestamp.
     */
    override fun getForYouFeed(
        userId: String,
        pageSize: Int,
        cursor: String?
    ): Flow<RequestState<FeedPage>> = callbackFlow {
        // Emit cached data immediately for instant UI (first page only)
        if (cursor == null) {
            try {
                val cached = cachedThreadQueries.getCachedThreads(pageSize.toLong())
                    .executeAsList()
                if (cached.isNotEmpty()) {
                    val cachedThreads = cached.map { row ->
                        Thread(
                            threadId = row.threadId,
                            authorId = row.authorId,
                            authorDisplayName = row.authorDisplayName,
                            authorAvatarUrl = row.authorAvatarUrl,
                            text = row.text,
                            moodTag = row.moodTag,
                            postCategory = row.postCategory,
                            likeCount = row.likeCount,
                            replyCount = row.replyCount,
                            repostCount = row.repostCount,
                            createdAt = row.createdAt,
                        )
                    }
                    trySend(RequestState.Success(FeedPage(items = cachedThreads, hasMore = true)))
                }
            } catch (_: Exception) { /* cache miss is fine */ }
        }

        trySend(RequestState.Loading)

        var query = threadsCollection
            .whereEqualTo("visibility", "public")
            .whereEqualTo("parentThreadId", null) // Top-level threads only
            .orderBy("likeCount", Query.Direction.DESCENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit((pageSize + 1).toLong()) // Fetch one extra to check hasMore

        // Apply cursor if provided (createdAt timestamp)
        if (cursor != null) {
            val cursorTimestamp = cursor.toLongOrNull()
            if (cursorTimestamp != null) {
                query = threadsCollection
                    .whereEqualTo("visibility", "public")
                    .whereEqualTo("parentThreadId", null)
                    .orderBy("likeCount", Query.Direction.DESCENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(cursorTimestamp)
                    .limit((pageSize + 1).toLong())
            }
        }

        val listenerRegistration = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting ForYou feed: ${error.message}")
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

                    val hasMore = threads.size > pageSize
                    val pageItems = if (hasMore) threads.take(pageSize) else threads
                    val nextCursor = if (hasMore) pageItems.lastOrNull()?.createdAt?.toString() else null

                    // Cache first page for offline/instant display
                    if (cursor == null) {
                        try {
                            val now = System.currentTimeMillis()
                            cachedThreadQueries.clearAll()
                            pageItems.forEach { t ->
                                cachedThreadQueries.insertThread(
                                    threadId = t.threadId,
                                    authorId = t.authorId,
                                    authorDisplayName = t.authorDisplayName,
                                    authorAvatarUrl = t.authorAvatarUrl,
                                    text = t.text,
                                    moodTag = t.moodTag,
                                    postCategory = t.postCategory,
                                    likeCount = t.likeCount,
                                    replyCount = t.replyCount,
                                    repostCount = t.repostCount,
                                    createdAt = t.createdAt,
                                    cachedAt = now,
                                )
                            }
                        } catch (_: Exception) { /* cache write failure is non-fatal */ }
                    }

                    trySend(RequestState.Success(FeedPage(
                        items = pageItems,
                        nextCursor = nextCursor,
                        hasMore = hasMore
                    )))
                } else {
                    trySend(RequestState.Success(FeedPage()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Following feed — chronological feed from users the current user follows.
     * Fetches following list first, then queries threads by those authors.
     */
    override fun getFollowingFeed(
        userId: String,
        pageSize: Int,
        cursor: String?
    ): Flow<RequestState<FeedPage>> = callbackFlow {
        trySend(RequestState.Loading)

        try {
            // Step 1: Get list of followed user IDs
            val followingSnapshot = firestore.collection(COLLECTION_SOCIAL_GRAPH)
                .document(userId)
                .collection("following")
                .get()
                .await()

            val followedIds = followingSnapshot.documents.map { it.id }

            if (followedIds.isEmpty()) {
                trySend(RequestState.Success(FeedPage()))
                close()
                return@callbackFlow
            }

            // Firestore whereIn is limited to 30 items — chunk if needed
            val chunkedIds = followedIds.take(30) // MVP limitation

            var query = threadsCollection
                .whereIn("authorId", chunkedIds)
                .whereEqualTo("parentThreadId", null)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit((pageSize + 1).toLong())

            if (cursor != null) {
                val cursorTimestamp = cursor.toLongOrNull()
                if (cursorTimestamp != null) {
                    query = threadsCollection
                        .whereIn("authorId", chunkedIds)
                        .whereEqualTo("parentThreadId", null)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .startAfter(cursorTimestamp)
                        .limit((pageSize + 1).toLong())
                }
            }

            val listenerRegistration = query
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error getting Following feed: ${error.message}")
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

                        val hasMore = threads.size > pageSize
                        val pageItems = if (hasMore) threads.take(pageSize) else threads
                        val nextCursor = if (hasMore) pageItems.lastOrNull()?.createdAt?.toString() else null

                        trySend(RequestState.Success(FeedPage(
                            items = pageItems,
                            nextCursor = nextCursor,
                            hasMore = hasMore
                        )))
                    } else {
                        trySend(RequestState.Success(FeedPage()))
                    }
                }

            awaitClose { listenerRegistration.remove() }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Following feed: ${e.message}")
            trySend(RequestState.Error(e))
            close()
        }
    }

    /**
     * Refresh feed — MVP no-op. Production would trigger Cloud Function to rebuild feed cache.
     */
    override suspend fun refreshFeed(userId: String): RequestState<Boolean> {
        return try {
            // Clear local cache so next getForYouFeed will skip stale cached data
            cachedThreadQueries.clearAll()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
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
            updatedAt = doc.getLong("updatedAt")
        )
    }
}
