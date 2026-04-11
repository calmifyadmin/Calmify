package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.model.ThreadProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class SearchService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(SearchService::class.java)

    @Serializable
    data class UserResult(
        val userId: String = "",
        val username: String = "",
        val displayName: String = "",
        val avatarUrl: String = "",
        val bio: String = "",
        val isVerified: Boolean = false,
        val followerCount: Long = 0,
    )

    suspend fun searchThreads(query: String, limit: Int): List<ThreadProto> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val normalizedQuery = query.lowercase().trim()

        // Firestore doesn't support full-text search natively.
        // Strategy: prefix match on text field + client-side filter.
        // For production: integrate Algolia or Vertex AI Vector Search.
        val docs = db.collection("threads")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200) // Fetch a batch and filter client-side
            .get().get().documents

        docs.filter { doc ->
            val text = doc.getString("text")?.lowercase() ?: ""
            text.contains(normalizedQuery)
        }.take(limit).map { doc ->
            ThreadProto(
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
            )
        }
    }

    suspend fun searchUsers(query: String, limit: Int): List<UserResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val normalizedQuery = query.lowercase().trim()

        // Prefix match on username and displayName
        val docs = db.collection("user_profiles")
            .limit(200)
            .get().get().documents

        docs.filter { doc ->
            val username = doc.getString("username")?.lowercase() ?: ""
            val displayName = doc.getString("displayName")?.lowercase() ?: ""
            username.contains(normalizedQuery) || displayName.contains(normalizedQuery)
        }.take(limit).map { doc ->
            UserResult(
                userId = doc.id,
                username = doc.getString("username") ?: "",
                displayName = doc.getString("displayName") ?: "",
                avatarUrl = doc.getString("avatarUrl") ?: "",
                bio = doc.getString("bio") ?: "",
                isVerified = doc.getBoolean("isVerified") ?: false,
                followerCount = doc.getLong("followerCount") ?: 0,
            )
        }
    }
}
