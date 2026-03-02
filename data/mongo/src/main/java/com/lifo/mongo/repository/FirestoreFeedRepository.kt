package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.FeedRepository.FeedPage
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class FirestoreFeedRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
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
                    println("[$TAG] ERROR: Error getting ForYou feed: ${error.message}")
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
                        println("[$TAG] ERROR: Error getting Following feed: ${error.message}")
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
            println("[$TAG] ERROR: Error setting up Following feed: ${e.message}")
            trySend(RequestState.Error(e))
            close()
        }
    }

    /**
     * Refresh feed — MVP no-op. Production would trigger Cloud Function to rebuild feed cache.
     */
    override suspend fun refreshFeed(userId: String): RequestState<Boolean> {
        return try {
            // MVP: No server-side feed cache to rebuild.
            // In production, this would call a Cloud Function to regenerate
            // the fan-out feed_cache for the user.
            println("[$TAG] Feed refresh requested for $userId (no-op in MVP)")
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    // -- Helpers --

    private fun snapshotToThread(doc: com.google.firebase.firestore.DocumentSnapshot): Thread {
        return Thread(
            threadId = doc.id,
            authorId = doc.getString("authorId") ?: "",
            parentThreadId = doc.getString("parentThreadId"),
            text = doc.getString("text") ?: "",
            likeCount = doc.getLong("likeCount") ?: 0,
            replyCount = doc.getLong("replyCount") ?: 0,
            visibility = doc.getString("visibility") ?: "public",
            moodTag = doc.getString("moodTag"),
            isFromJournal = doc.getBoolean("isFromJournal") ?: false,
            createdAt = doc.getLong("createdAt") ?: 0,
            updatedAt = doc.getLong("updatedAt")
        )
    }
}
