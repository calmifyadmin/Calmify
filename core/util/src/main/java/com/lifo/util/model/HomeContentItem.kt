package com.lifo.util.model

/**
 * Sealed class representing unified content items for the home feed
 * Combines Diary entries and Chat sessions into a single data structure
 */
sealed class HomeContentItem {
    abstract val id: String
    abstract val title: String
    abstract val createdAt: Long
    abstract val contentType: ContentType
    
    /**
     * Represents a diary entry in the unified feed
     */
    data class DiaryItem(
        override val id: String,
        override val title: String,
        override val createdAt: Long,
        override val contentType: ContentType = ContentType.DIARY,
        val content: String,
        val mood: Mood,
        val images: List<String> = emptyList()
    ) : HomeContentItem()
    
    /**
     * Represents a chat session in the unified feed
     */
    data class ChatItem(
        override val id: String,
        override val title: String,
        override val createdAt: Long,
        override val contentType: ContentType = ContentType.CHAT,
        val summary: String?,
        val messageCount: Int,
        val lastMessage: String?,
        val lastMessageAt: Long,
        val mood: String?,
        val aiModel: String,
        val isLiveMode: Boolean = false
    ) : HomeContentItem()
}

/**
 * Enum representing the type of content in the unified feed
 */
enum class ContentType {
    DIARY,
    CHAT,
    ALL
}

/**
 * Enum representing filter options for the unified feed
 */
enum class ContentFilter {
    ALL,
    DIARY,
    CHAT,
    TODAY,
    THIS_WEEK,
    THIS_MONTH
}