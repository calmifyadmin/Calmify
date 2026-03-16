package com.lifo.mongo.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.repository.ThreadHydrator
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * FirestoreThreadHydrator — Enriches Thread objects with author info + like status.
 *
 * Performs two batch operations:
 * 1. Fetches author profiles from user_profiles (whereIn, max 30)
 * 2. Checks like status for each thread in parallel (async per thread)
 *
 * Registered in Koin as single<ThreadHydrator>.
 */
class FirestoreThreadHydrator(
    private val firestore: FirebaseFirestore
) : ThreadHydrator {

    companion object {
        private const val COLLECTION_USER_PROFILES = "user_profiles"
        private const val COLLECTION_THREADS = "threads"
        private const val SUBCOLLECTION_LIKES = "likes"
        private const val SUBCOLLECTION_REPOSTS = "reposts"
    }

    private val userProfilesCollection by lazy { firestore.collection(COLLECTION_USER_PROFILES) }
    private val threadsCollection by lazy { firestore.collection(COLLECTION_THREADS) }

    override suspend fun hydrate(threads: List<Thread>, currentUserId: String): List<Thread> {
        if (threads.isEmpty()) return threads

        return coroutineScope {
            // Step 1: Batch-fetch author profiles
            val authorIds = threads.map { it.authorId }.distinct()
            val authorProfiles = fetchAuthorProfiles(authorIds)

            // Step 2: Batch-check like + repost status in parallel
            val likeStatuses = threads.map { thread ->
                async { thread.threadId to checkLikeStatus(thread.threadId, currentUserId) }
            }.awaitAll().toMap()

            val repostStatuses = threads.map { thread ->
                async { thread.threadId to checkRepostStatus(thread.threadId, currentUserId) }
            }.awaitAll().toMap()

            // Step 3: Fetch reply preview avatars for threads with replies
            val replyAvatars = threads.filter { it.replyCount > 0 }.map { thread ->
                async { thread.threadId to fetchReplyPreviewAvatars(thread.threadId) }
            }.awaitAll().toMap()

            // Step 4: Merge enriched data into threads
            threads.map { thread ->
                val profile = authorProfiles[thread.authorId]
                thread.copy(
                    authorDisplayName = profile?.displayName ?: thread.authorDisplayName,
                    authorUsername = profile?.username ?: thread.authorUsername,
                    authorAvatarUrl = profile?.avatarUrl ?: thread.authorAvatarUrl,
                    authorIsVerified = profile?.isVerified ?: thread.authorIsVerified,
                    isLikedByCurrentUser = likeStatuses[thread.threadId] ?: false,
                    isRepostedByCurrentUser = repostStatuses[thread.threadId] ?: false,
                    replyPreviewAvatars = replyAvatars[thread.threadId] ?: thread.replyPreviewAvatars
                )
            }
        }
    }

    override suspend fun hydrateSingle(thread: Thread, currentUserId: String): Thread {
        return hydrate(listOf(thread), currentUserId).first()
    }

    private suspend fun fetchAuthorProfiles(authorIds: List<String>): Map<String, AuthorProfile> {
        if (authorIds.isEmpty()) return emptyMap()

        return try {
            // Firestore whereIn limited to 30
            val chunked = authorIds.take(30)
            val snapshot = userProfilesCollection
                .whereIn(FieldPath.documentId(), chunked)
                .get()
                .await()

            snapshot.documents.associate { doc ->
                doc.id to AuthorProfile(
                    displayName = doc.getString("displayName"),
                    username = doc.getString("username"),
                    avatarUrl = doc.getString("avatarUrl"),
                    isVerified = doc.getBoolean("isVerified") ?: false
                )
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun checkLikeStatus(threadId: String, userId: String): Boolean {
        return try {
            val likeDoc = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_LIKES)
                .document(userId)
                .get()
                .await()
            likeDoc.exists()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun checkRepostStatus(threadId: String, userId: String): Boolean {
        return try {
            val repostDoc = threadsCollection.document(threadId)
                .collection(SUBCOLLECTION_REPOSTS)
                .document(userId)
                .get()
                .await()
            repostDoc.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch up to 3 reply author avatar URLs for a thread.
     */
    private suspend fun fetchReplyPreviewAvatars(threadId: String): List<String> {
        return try {
            // Get up to 3 most recent replies
            val repliesSnapshot = threadsCollection
                .whereEqualTo("parentThreadId", threadId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .await()

            val replyAuthorIds = repliesSnapshot.documents
                .mapNotNull { it.getString("authorId") }
                .distinct()

            if (replyAuthorIds.isEmpty()) return emptyList()

            // Fetch their profiles
            val profilesSnapshot = userProfilesCollection
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), replyAuthorIds)
                .get()
                .await()

            profilesSnapshot.documents.mapNotNull { doc ->
                doc.getString("avatarUrl")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class AuthorProfile(
        val displayName: String?,
        val username: String?,
        val avatarUrl: String?,
        val isVerified: Boolean
    )
}
