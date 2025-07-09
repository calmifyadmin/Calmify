package com.lifo.chat.data.local

import androidx.room.*
import com.lifo.chat.data.local.entities.ChatMessageEntity
import com.lifo.chat.data.local.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Session operations
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE chat_sessions SET messageCount = messageCount + 1, lastMessageAt = :timestamp WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: String, timestamp: Long)

    // Message operations
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): ChatMessageEntity?

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("UPDATE chat_messages SET status = :status, error = :error WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String, error: String? = null)

    // Cleanup operations
    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: String) {
        deleteMessagesForSession(sessionId)
        deleteSession(sessionId)
    }

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int
}