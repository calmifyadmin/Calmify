package com.lifo.server.service

import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * SocialGraph — follow / block / profile.
 *
 * Firestore layout (unchanged from legacy client impl):
 *   social_graph/{userId}/following/{followeeId}   followedAt: Long
 *   social_graph/{userId}/followers/{followerId}   followedAt: Long
 *   social_graph/{userId}/blocked/{blockedId}      blockedAt: Long
 *   user_profiles/{userId}                         public profile
 *   notifications/{autoId}                         NEW_FOLLOWER events
 */
class SocialGraphService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(SocialGraphService::class.java)

    companion object {
        private const val SOCIAL_GRAPH = "social_graph"
        private const val USER_PROFILES = "user_profiles"
        private const val NOTIFICATIONS = "notifications"
        private const val FOLLOWING = "following"
        private const val FOLLOWERS = "followers"
        private const val BLOCKED = "blocked"

        private val ALLOWED_PROFILE_FIELDS = setOf(
            "username", "displayName", "avatarUrl", "coverPhotoUrl",
            "bio", "interests", "links",
        )
    }

    @Serializable
    data class SocialUserDto(
        val userId: String = "",
        val username: String? = null,
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val coverPhotoUrl: String? = null,
        val bio: String? = null,
        val isVerified: Boolean = false,
        val followerCount: Long = 0,
        val followingCount: Long = 0,
        val threadCount: Long = 0,
        val interests: List<String> = emptyList(),
        val links: List<String> = emptyList(),
        val followerPreviewAvatars: List<String> = emptyList(),
        val profileViews30Days: Long = 0,
    )

    @Serializable
    data class UsersResponse(val data: List<SocialUserDto> = emptyList())

    @Serializable
    data class CheckFollowingResponse(val following: Boolean = false)

    @Serializable
    data class CheckBlockedResponse(val blocked: Boolean = false)

    @Serializable
    data class UsernameAvailableResponse(val available: Boolean = false)

    @Serializable
    data class ResolveUsernameResponse(val userId: String = "")

    suspend fun follow(followerId: String, followeeId: String) = withContext(Dispatchers.IO) {
        if (followerId == followeeId) error("cannot follow yourself")
        val now = System.currentTimeMillis()
        val batch = db.batch()

        val followingRef = db.collection(SOCIAL_GRAPH).document(followerId)
            .collection(FOLLOWING).document(followeeId)
        batch.set(followingRef, mapOf("followedAt" to now))

        val followerRef = db.collection(SOCIAL_GRAPH).document(followeeId)
            .collection(FOLLOWERS).document(followerId)
        batch.set(followerRef, mapOf("followedAt" to now))

        val followerProfileRef = db.collection(USER_PROFILES).document(followerId)
        batch.set(followerProfileRef, mapOf("followingCount" to FieldValue.increment(1)), SetOptions.merge())

        val followeeProfileRef = db.collection(USER_PROFILES).document(followeeId)
        batch.set(followeeProfileRef, mapOf("followerCount" to FieldValue.increment(1)), SetOptions.merge())

        batch.commit().get()

        // Best-effort notification (failure non-fatal)
        try {
            db.collection(NOTIFICATIONS).add(
                mapOf(
                    "userId" to followeeId,
                    "type" to "NEW_FOLLOWER",
                    "actorId" to followerId,
                    "message" to "started following you",
                    "isRead" to false,
                    "createdAt" to now,
                )
            ).get()
        } catch (e: Exception) {
            logger.warn("Failed to create follow notification: ${e.message}")
        }

        logger.info("$followerId → follows → $followeeId")
    }

    suspend fun unfollow(followerId: String, followeeId: String) = withContext(Dispatchers.IO) {
        val batch = db.batch()
        batch.delete(
            db.collection(SOCIAL_GRAPH).document(followerId)
                .collection(FOLLOWING).document(followeeId)
        )
        batch.delete(
            db.collection(SOCIAL_GRAPH).document(followeeId)
                .collection(FOLLOWERS).document(followerId)
        )
        batch.set(
            db.collection(USER_PROFILES).document(followerId),
            mapOf("followingCount" to FieldValue.increment(-1)), SetOptions.merge()
        )
        batch.set(
            db.collection(USER_PROFILES).document(followeeId),
            mapOf("followerCount" to FieldValue.increment(-1)), SetOptions.merge()
        )
        batch.commit().get()
        logger.info("$followerId unfollowed $followeeId")
    }

    suspend fun block(blockerId: String, blockedId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val batch = db.batch()

        batch.set(
            db.collection(SOCIAL_GRAPH).document(blockerId)
                .collection(BLOCKED).document(blockedId),
            mapOf("blockedAt" to now)
        )
        // Remove follow relations in both directions
        batch.delete(
            db.collection(SOCIAL_GRAPH).document(blockerId)
                .collection(FOLLOWING).document(blockedId)
        )
        batch.delete(
            db.collection(SOCIAL_GRAPH).document(blockedId)
                .collection(FOLLOWERS).document(blockerId)
        )
        batch.delete(
            db.collection(SOCIAL_GRAPH).document(blockedId)
                .collection(FOLLOWING).document(blockerId)
        )
        batch.delete(
            db.collection(SOCIAL_GRAPH).document(blockerId)
                .collection(FOLLOWERS).document(blockedId)
        )
        batch.commit().get()
        logger.info("$blockerId blocked $blockedId")
    }

    suspend fun unblock(blockerId: String, blockedId: String) = withContext(Dispatchers.IO) {
        db.collection(SOCIAL_GRAPH).document(blockerId)
            .collection(BLOCKED).document(blockedId)
            .delete().get()
        logger.info("$blockerId unblocked $blockedId")
    }

    suspend fun isFollowing(followerId: String, followeeId: String): Boolean = withContext(Dispatchers.IO) {
        db.collection(SOCIAL_GRAPH).document(followerId)
            .collection(FOLLOWING).document(followeeId)
            .get().get().exists()
    }

    suspend fun isBlocked(blockerId: String, blockedId: String): Boolean = withContext(Dispatchers.IO) {
        db.collection(SOCIAL_GRAPH).document(blockerId)
            .collection(BLOCKED).document(blockedId)
            .get().get().exists()
    }

    suspend fun getFollowers(userId: String, limit: Int): List<SocialUserDto> = withContext(Dispatchers.IO) {
        val ids = db.collection(SOCIAL_GRAPH).document(userId)
            .collection(FOLLOWERS)
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().get().documents.map { it.id }
        resolveProfiles(ids)
    }

    suspend fun getFollowing(userId: String, limit: Int): List<SocialUserDto> = withContext(Dispatchers.IO) {
        val ids = db.collection(SOCIAL_GRAPH).document(userId)
            .collection(FOLLOWING)
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().get().documents.map { it.id }
        resolveProfiles(ids)
    }

    suspend fun getSuggestions(userId: String, limit: Int): List<SocialUserDto> = withContext(Dispatchers.IO) {
        val candidates = db.collection(USER_PROFILES)
            .orderBy("followerCount", Query.Direction.DESCENDING)
            .limit(limit + 10)
            .get().get()

        val followingIds = db.collection(SOCIAL_GRAPH).document(userId)
            .collection(FOLLOWING)
            .get().get().documents.map { it.id }.toSet()

        val blockedIds = db.collection(SOCIAL_GRAPH).document(userId)
            .collection(BLOCKED)
            .get().get().documents.map { it.id }.toSet()

        val exclude = followingIds + blockedIds + userId

        candidates.documents
            .filter { it.id !in exclude }
            .take(limit)
            .map { it.toSocialUser() }
    }

    suspend fun getProfile(userId: String): SocialUserDto = withContext(Dispatchers.IO) {
        val doc = db.collection(USER_PROFILES).document(userId).get().get()
        if (!doc.exists()) return@withContext SocialUserDto(userId = userId)
        doc.toSocialUser()
    }

    suspend fun updateProfile(userId: String, raw: Map<String, Any?>): SocialUserDto = withContext(Dispatchers.IO) {
        val sanitized = raw.filterKeys { it in ALLOWED_PROFILE_FIELDS }
            .mapNotNull { (k, v) -> if (v != null) k to v else null }
            .toMap()
        if (sanitized.isEmpty()) return@withContext getProfile(userId)
        val data = sanitized.toMutableMap<String, Any>()
        data["updatedAt"] = System.currentTimeMillis()
        db.collection(USER_PROFILES).document(userId).set(data, SetOptions.merge()).get()
        logger.info("Updated profile $userId fields=${sanitized.keys}")
        getProfile(userId)
    }

    suspend fun isUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
        val snap = db.collection(USER_PROFILES)
            .whereEqualTo("username", username)
            .limit(1)
            .get().get()
        snap.isEmpty
    }

    suspend fun resolveUsername(username: String): String = withContext(Dispatchers.IO) {
        val snap = db.collection(USER_PROFILES)
            .whereEqualTo("username", username)
            .limit(1)
            .get().get()
        snap.documents.firstOrNull()?.id.orEmpty()
    }

    // -- internals --

    private fun resolveProfiles(userIds: List<String>): List<SocialUserDto> {
        if (userIds.isEmpty()) return emptyList()
        // Firestore whereIn is limited to 30 values; chunk accordingly
        val chunks = userIds.chunked(30)
        val resolved = mutableListOf<SocialUserDto>()
        for (chunk in chunks) {
            val snap = db.collection(USER_PROFILES)
                .whereIn(FieldPath.documentId(), chunk)
                .get().get()
            resolved.addAll(snap.documents.map { it.toSocialUser() })
        }
        // Preserve original order
        val byId = resolved.associateBy { it.userId }
        return userIds.mapNotNull { byId[it] ?: SocialUserDto(userId = it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toSocialUser(): SocialUserDto = SocialUserDto(
        userId = id,
        username = getString("username"),
        displayName = getString("displayName"),
        avatarUrl = getString("avatarUrl"),
        coverPhotoUrl = getString("coverPhotoUrl"),
        bio = getString("bio"),
        isVerified = getBoolean("isVerified") ?: false,
        followerCount = getLong("followerCount") ?: 0,
        followingCount = getLong("followingCount") ?: 0,
        threadCount = getLong("threadCount") ?: 0,
        interests = (get("interests") as? List<String>) ?: emptyList(),
        links = (get("links") as? List<String>) ?: emptyList(),
        followerPreviewAvatars = (get("followerPreviewAvatars") as? List<String>) ?: emptyList(),
        profileViews30Days = getLong("profileViews30Days") ?: 0,
    )
}
