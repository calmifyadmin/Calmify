package com.lifo.mongo.repository

import com.lifo.mongo.database.Chat_sessions
import com.lifo.util.model.Diary
import com.lifo.util.model.HomeContentItem
import com.lifo.util.model.Mood
/**
 * Extension functions to convert database entities to HomeContentItem
 */

/**
 * Convert Diary (Firestore data class) to HomeContentItem.DiaryItem
 */
fun Diary.toHomeContentItem(): HomeContentItem.DiaryItem {
    return HomeContentItem.DiaryItem(
        id = _id,
        title = title.ifBlank { "Diary Entry" },
        createdAt = dateMillis,
        content = description,
        mood = try { Mood.valueOf(mood) } catch (e: Exception) { Mood.Neutral },
        images = images
    )
}

/**
 * Convert SQLDelight Chat_sessions to HomeContentItem.ChatItem
 */
fun Chat_sessions.toHomeContentItem(): HomeContentItem.ChatItem {
    return HomeContentItem.ChatItem(
        id = id,
        title = title.ifBlank { "Chat Session" },
        createdAt = createdAt,
        summary = summary,
        messageCount = messageCount.toInt(), // SQLDelight uses Long
        lastMessage = lastMessage,
        lastMessageAt = lastMessageAt,
        mood = mood,
        aiModel = aiModel,
        isLiveMode = isLiveMode == 1L // SQLDelight uses Long for boolean
    )
}