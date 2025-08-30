package com.lifo.mongo.repository

import com.lifo.mongo.database.entity.ChatSessionEntity
import com.lifo.util.model.Diary
import com.lifo.util.model.HomeContentItem
import com.lifo.util.model.Mood
import com.lifo.util.toInstant

/**
 * Extension functions to convert database entities to HomeContentItem
 */

/**
 * Convert Diary (Realm object) to HomeContentItem.DiaryItem
 */
fun Diary.toHomeContentItem(): HomeContentItem.DiaryItem {
    return HomeContentItem.DiaryItem(
        id = _id.toHexString(),
        title = title.ifBlank { "Diary Entry" },
        createdAt = date.toInstant().toEpochMilli(),
        content = description,
        mood = try { Mood.valueOf(mood) } catch (e: Exception) { Mood.Neutral },
        images = images.toList()
    )
}

/**
 * Convert ChatSessionEntity to HomeContentItem.ChatItem
 */
fun ChatSessionEntity.toHomeContentItem(): HomeContentItem.ChatItem {
    return HomeContentItem.ChatItem(
        id = id,
        title = title.ifBlank { "Chat Session" },
        createdAt = createdAt,
        summary = summary,
        messageCount = messageCount,
        lastMessage = lastMessage,
        lastMessageAt = lastMessageAt,
        mood = mood,
        aiModel = aiModel,
        isLiveMode = isLiveMode
    )
}