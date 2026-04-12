package com.lifo.mongo.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.SocialGraphRepository.SocialUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * FirestoreSocialGraphRepository Implementation
 *
 * Firestore-backed implementation of SocialGraphRepository.
 * Structure:
 *   social_graph/{userId}/following/{followeeId}  — who this user follows
 *   social_graph/{userId}/followers/{followerId}   — who follows this user
 *   social_graph/{userId}/blocked/{blockedId}      — users blocked by this user
 *   user_profiles/{userId}                         — public user profile (SocialUser data)
 *
 * MVP implementation — production would use Spanner Graph for traversal queries.
 */
class FirestoreSocialGraphRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SocialGraphRepository {

    companion object {
        private const val TAG = "FirestoreSocialGraphRepo"
        private const val COLLECTION_SOCIAL_GRAPH = "social_graph"
        private const val COLLECTION_USER_PROFILES = "user_profiles"
        private const val SUBCOLLECTION_FOLLOWING = "following"
        private const val SUBCOLLECTION_FOLLOWERS = "followers"
        private const val SUBCOLLECTION_BLOCKED = "blocked"
        private const val COLLECTION_NOTIFICATIONS = "notifications"
    }

    private val socialGraphCollection by lazy { firestore.collection(COLLECTION_SOCIAL_GRAPH) }
    private val userProfilesCollection by lazy { firestore.collection(COLLECTION_USER_PROFILES) }
    private val notificationsCollection by lazy { firestore.collection(COLLECTION_NOTIFICATIONS) }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override suspend fun follow(followerId: String, followeeId: String): RequestState<Boolean> {
        return try {
            if (followerId == followeeId) {
                return RequestState.Error(Exception("Cannot follow yourself"))
            }

            val batch = firestore.batch()

            // Add to follower's "following" subcollection
            val followingRef = socialGraphCollection.document(followerId)
                .collection(SUBCOLLECTION_FOLLOWING)
                .document(followeeId)
            batch.set(followingRef, mapOf("followedAt" to System.currentTimeMillis()))

            // Add to followee's "followers" subcollection
            val followerRef = socialGraphCollection.document(followeeId)
                .collection(SUBCOLLECTION_FOLLOWERS)
                .document(followerId)
            batch.set(followerRef, mapOf("followedAt" to System.currentTimeMillis()))

            // Update counts on user profiles
            val followerProfileRef = userProfilesCollection.document(followerId)
            batch.set(followerProfileRef, mapOf("followingCount" to FieldValue.increment(1)),
                com.google.firebase.firestore.SetOptions.merge())

            val followeeProfileRef = userProfilesCollection.document(followeeId)
            batch.set(followeeProfileRef, mapOf("followerCount" to FieldValue.increment(1)),
                com.google.firebase.firestore.SetOptions.merge())

            batch.commit().await()

            Log.d(TAG, "$followerId now follows $followeeId")

            // Send follow notification
            try {
                notificationsCollection.add(mapOf(
                    "userId" to followeeId,
                    "type" to "NEW_FOLLOWER",
                    "actorId" to followerId,
                    "message" to "started following you",
                    "isRead" to false,
                    "createdAt" to System.currentTimeMillis()
                )).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create follow notification: ${e.message}")
            }

            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error following: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun unfollow(followerId: String, followeeId: String): RequestState<Boolean> {
        return try {
            val batch = firestore.batch()

            // Remove from follower's "following"
            val followingRef = socialGraphCollection.document(followerId)
                .collection(SUBCOLLECTION_FOLLOWING)
                .document(followeeId)
            batch.delete(followingRef)

            // Remove from followee's "followers"
            val followerRef = socialGraphCollection.document(followeeId)
                .collection(SUBCOLLECTION_FOLLOWERS)
                .document(followerId)
            batch.delete(followerRef)

            // Update counts
            val followerProfileRef = userProfilesCollection.document(followerId)
            batch.set(followerProfileRef, mapOf("followingCount" to FieldValue.increment(-1)),
                com.google.firebase.firestore.SetOptions.merge())

            val followeeProfileRef = userProfilesCollection.document(followeeId)
            batch.set(followeeProfileRef, mapOf("followerCount" to FieldValue.increment(-1)),
                com.google.firebase.firestore.SetOptions.merge())

            batch.commit().await()

            Log.d(TAG, "$followerId unfollowed $followeeId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unfollowing: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun block(blockerId: String, blockedId: String): RequestState<Boolean> {
        return try {
            // Also unfollow in both directions when blocking
            val batch = firestore.batch()

            // Add block
            val blockRef = socialGraphCollection.document(blockerId)
                .collection(SUBCOLLECTION_BLOCKED)
                .document(blockedId)
            batch.set(blockRef, mapOf("blockedAt" to System.currentTimeMillis()))

            // Remove any existing follow relationship (both directions)
            batch.delete(
                socialGraphCollection.document(blockerId)
                    .collection(SUBCOLLECTION_FOLLOWING).document(blockedId)
            )
            batch.delete(
                socialGraphCollection.document(blockedId)
                    .collection(SUBCOLLECTION_FOLLOWERS).document(blockerId)
            )
            batch.delete(
                socialGraphCollection.document(blockedId)
                    .collection(SUBCOLLECTION_FOLLOWING).document(blockerId)
            )
            batch.delete(
                socialGraphCollection.document(blockerId)
                    .collection(SUBCOLLECTION_FOLLOWERS).document(blockedId)
            )

            batch.commit().await()

            Log.d(TAG, "$blockerId blocked $blockedId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun unblock(blockerId: String, blockedId: String): RequestState<Boolean> {
        return try {
            socialGraphCollection.document(blockerId)
                .collection(SUBCOLLECTION_BLOCKED)
                .document(blockedId)
                .delete()
                .await()

            Log.d(TAG, "$blockerId unblocked $blockedId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking: ${e.message}")
            RequestState.Error(e)
        }
    }

    override fun isFollowing(followerId: String, followeeId: String): Flow<Boolean> = callbackFlow {
        val listenerRegistration = socialGraphCollection.document(followerId)
            .collection(SUBCOLLECTION_FOLLOWING)
            .document(followeeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                trySend(snapshot?.exists() == true)
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun isBlocked(blockerId: String, blockedId: String): Flow<Boolean> = callbackFlow {
        val listenerRegistration = socialGraphCollection.document(blockerId)
            .collection(SUBCOLLECTION_BLOCKED)
            .document(blockedId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                trySend(snapshot?.exists() == true)
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getFollowers(userId: String, limit: Int): Flow<RequestState<List<SocialUser>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = socialGraphCollection.document(userId)
            .collection(SUBCOLLECTION_FOLLOWERS)
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting followers: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // We have follower IDs — resolve to SocialUser profiles
                    val followerIds = snapshot.documents.map { it.id }
                    if (followerIds.isEmpty()) {
                        trySend(RequestState.Success(emptyList()))
                    } else {
                        // Fetch user profiles for these IDs
                        resolveUserProfiles(followerIds) { users ->
                            trySend(RequestState.Success(users))
                        }
                    }
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getFollowing(userId: String, limit: Int): Flow<RequestState<List<SocialUser>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = socialGraphCollection.document(userId)
            .collection(SUBCOLLECTION_FOLLOWING)
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting following: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val followingIds = snapshot.documents.map { it.id }
                    if (followingIds.isEmpty()) {
                        trySend(RequestState.Success(emptyList()))
                    } else {
                        resolveUserProfiles(followingIds) { users ->
                            trySend(RequestState.Success(users))
                        }
                    }
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Suggestions — MVP returns recent user profiles (not followed, not blocked).
     * Production would use Spanner Graph friend-of-friend traversal.
     */
    override fun getSuggestions(userId: String, limit: Int): Flow<RequestState<List<SocialUser>>> = flow {
        emit(RequestState.Loading)

        try {
            // MVP: Fetch recent user profiles, exclude self
            val snapshot = userProfilesCollection
                .orderBy("followerCount", Query.Direction.DESCENDING)
                .limit((limit + 10).toLong()) // Fetch extra to account for filtering
                .get()
                .await()

            // Get blocked and following lists to filter them out
            val followingSnapshot = socialGraphCollection.document(userId)
                .collection(SUBCOLLECTION_FOLLOWING)
                .get()
                .await()
            val followingIds = followingSnapshot.documents.map { it.id }.toSet()

            val blockedSnapshot = socialGraphCollection.document(userId)
                .collection(SUBCOLLECTION_BLOCKED)
                .get()
                .await()
            val blockedIds = blockedSnapshot.documents.map { it.id }.toSet()

            val excludeIds = followingIds + blockedIds + userId

            val suggestions = snapshot.documents
                .filter { it.id !in excludeIds }
                .take(limit)
                .mapNotNull { doc -> docToSocialUser(doc) }

            emit(RequestState.Success(suggestions))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions: ${e.message}")
            emit(RequestState.Error(e))
        }
    }

    // -- Helpers --

    private fun resolveUserProfiles(
        userIds: List<String>,
        callback: (List<SocialUser>) -> Unit
    ) {
        if (userIds.isEmpty()) {
            callback(emptyList())
            return
        }

        // Firestore whereIn limited to 30
        val chunkedIds = userIds.take(30)
        userProfilesCollection
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunkedIds)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc -> docToSocialUser(doc) }
                callback(users)
            }
            .addOnFailureListener {
                // Return empty list with user IDs as fallback
                val fallback = userIds.map { SocialUser(userId = it) }
                callback(fallback)
            }
    }

    override fun getProfile(userId: String): Flow<RequestState<SocialUser>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = userProfilesCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(RequestState.Success(docToSocialUser(snapshot)))
                } else {
                    // Profile doesn't exist yet — return empty with userId
                    trySend(RequestState.Success(SocialUser(userId = userId)))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun updateProfile(
        userId: String,
        updates: Map<String, Any?>
    ): RequestState<Boolean> {
        return try {
            userProfilesCollection.document(userId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d(TAG, "Profile updated for $userId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun isUsernameAvailable(username: String): RequestState<Boolean> {
        return try {
            val snapshot = userProfilesCollection
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()
            RequestState.Success(snapshot.isEmpty)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking username availability: ${e.message}")
            RequestState.Error(e)
        }
    }

    override suspend fun resolveUsername(username: String): RequestState<String?> {
        return try {
            val snapshot = userProfilesCollection
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()
            val userId = snapshot.documents.firstOrNull()?.id
            RequestState.Success(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving username: ${e.message}")
            RequestState.Error(e)
        }
    }

    private fun docToSocialUser(doc: com.google.firebase.firestore.DocumentSnapshot): SocialUser {
        return SocialUser(
            userId = doc.id,
            username = doc.getString("username"),
            displayName = doc.getString("displayName"),
            avatarUrl = doc.getString("avatarUrl"),
            coverPhotoUrl = doc.getString("coverPhotoUrl"),
            bio = doc.getString("bio"),
            isVerified = doc.getBoolean("isVerified") ?: false,
            followerCount = doc.getLong("followerCount") ?: 0,
            followingCount = doc.getLong("followingCount") ?: 0,
            threadCount = doc.getLong("threadCount") ?: 0,
            interests = (doc.get("interests") as? List<String>) ?: emptyList(),
            links = (doc.get("links") as? List<String>) ?: emptyList(),
            followerPreviewAvatars = (doc.get("followerPreviewAvatars") as? List<String>) ?: emptyList(),
            profileViews30Days = doc.getLong("profileViews30Days") ?: 0
        )
    }
}
