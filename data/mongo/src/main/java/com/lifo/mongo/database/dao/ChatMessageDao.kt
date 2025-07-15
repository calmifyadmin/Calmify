package com.lifo.mongo.database.dao

import androidx.room.*
import com.lifo.mongo.database.entity.ChatMessageEntity
import com.lifo.util.Constants.CHAT_MESSAGE_TABLE
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM $CHAT_MESSAGE_TABLE WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM $CHAT_MESSAGE_TABLE WHERE id = :messageId")
    suspend fun getMessage(messageId: String): ChatMessageEntity?

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM $CHAT_MESSAGE_TABLE WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM $CHAT_MESSAGE_TABLE WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("UPDATE $CHAT_MESSAGE_TABLE SET status = :status, error = :error WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String, error: String? = null)

    @Query("SELECT COUNT(*) FROM $CHAT_MESSAGE_TABLE WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int
}