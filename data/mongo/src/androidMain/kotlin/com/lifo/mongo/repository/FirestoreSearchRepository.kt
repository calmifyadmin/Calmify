package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SearchRepository
import com.lifo.util.repository.SocialGraphRepository.SocialUser
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirestoreSearchRepository Implementation
 *
 * Firestore-backed implementation of SearchRepository.
 * MVP: Uses basic Firestore queries with prefix matching.
 * Production would use Vertex AI Vector Search for semantic search.
 *
 * Limitations:
 *   - Firestore has no full-text search — uses prefix matching on text fields
 *   - semanticSearch falls back to keyword search in MVP
 *   - For production, integrate with Algolia/Typesense or Vertex AI Vector Search
 */
@Singleton
class FirestoreSearchRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SearchRepository {

    companion object {
        private const val TAG = "FirestoreSearchRepo"
        private const val COLLECTION_THREADS = "threads"
        private const val COLLECTION_USER_PROFILES = "user_profiles"
    }

    private val threadsCollection by lazy { firestore.collection(COLLECTION_THREADS) }
    private val userProfilesCollection by lazy { firestore.collection(COLLECTION_USER_PROFILES) }

    /**
     * Search threads by text content.
     * MVP: Fetches recent public threads and filters client-side by keyword.
     * Firestore does not support full-text search natively.
     */
    override fun searchThreads(query: String, limit: Int): Flow<RequestState<List<Thread>>> = flow {
        emit(RequestState.Loading)

        try {
            val normalizedQuery = query.trim().lowercase()

            if (normalizedQuery.isBlank()) {
                emit(RequestState.Success(emptyList()))
                return@flow
            }

            // MVP strategy: Fetch a batch of recent public threads, filter client-side
            // This is acceptable for small scale; production needs server-side search
            val snapshot = threadsCollection
                .whereEqualTo("visibility", "public")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200) // Fetch a reasonable batch for client-side filtering
                .get()
                .await()

            val matchingThreads = snapshot.documents
                .mapNotNull { doc ->
                    try {
                        snapshotToThread(doc)
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { thread ->
                    thread.text.lowercase().contains(normalizedQuery) ||
                        thread.moodTag?.lowercase()?.contains(normalizedQuery) == true
                }
                .take(limit)

            emit(RequestState.Success(matchingThreads))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching threads: ${e.message}")
            emit(RequestState.Error(e))
        }
    }

    /**
     * Search users by display name.
     * MVP: Uses Firestore prefix matching on displayName field.
     */
    override fun searchUsers(query: String, limit: Int): Flow<RequestState<List<SocialUser>>> = flow {
        emit(RequestState.Loading)

        try {
            val normalizedQuery = query.trim()

            if (normalizedQuery.isBlank()) {
                emit(RequestState.Success(emptyList()))
                return@flow
            }

            // Firestore prefix search: >= query and < query + high unicode char
            val endQuery = normalizedQuery + "\uf8ff"

            val snapshot = userProfilesCollection
                .whereGreaterThanOrEqualTo("displayName", normalizedQuery)
                .whereLessThanOrEqualTo("displayName", endQuery)
                .limit(limit.toLong())
                .get()
                .await()

            val users = snapshot.documents.mapNotNull { doc ->
                try {
                    docToSocialUser(doc)
                } catch (e: Exception) {
                    null
                }
            }

            emit(RequestState.Success(users))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users: ${e.message}")
            emit(RequestState.Error(e))
        }
    }

    /**
     * Semantic search — MVP falls back to keyword search.
     * Production would use Vertex AI embeddings + Vector Search.
     */
    override fun semanticSearch(query: String, limit: Int): Flow<RequestState<List<Thread>>> {
        // MVP: Delegate to keyword search. Same results.
        // Production: Generate embedding via Vertex AI, query Vector Search index
        Log.d(TAG, "semanticSearch called — falling back to keyword search (MVP)")
        return searchThreads(query, limit)
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

    private fun docToSocialUser(doc: com.google.firebase.firestore.DocumentSnapshot): SocialUser {
        return SocialUser(
            userId = doc.id,
            displayName = doc.getString("displayName"),
            avatarUrl = doc.getString("avatarUrl"),
            bio = doc.getString("bio"),
            isVerified = doc.getBoolean("isVerified") ?: false,
            followerCount = doc.getLong("followerCount") ?: 0,
            followingCount = doc.getLong("followingCount") ?: 0,
            threadCount = doc.getLong("threadCount") ?: 0
        )
    }
}
