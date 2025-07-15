package com.lifo.mongo.database.dao

import androidx.room.*
import com.lifo.mongo.database.entity.ChatSessionEntity
import com.lifo.util.Constants.CHAT_SESSION_TABLE
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM $CHAT_SESSION_TABLE WHERE ownerId = :ownerId ORDER BY lastMessageAt DESC")
    fun getAllSessions(ownerId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM $CHAT_SESSION_TABLE WHERE id = :sessionId AND ownerId = :ownerId")
    suspend fun getSession(sessionId: String, ownerId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM $CHAT_SESSION_TABLE WHERE id = :sessionId AND ownerId = :ownerId")
    suspend fun deleteSession(sessionId: String, ownerId: String)

    @Query("UPDATE $CHAT_SESSION_TABLE SET messageCount = messageCount + 1, lastMessageAt = :timestamp WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: String, timestamp: Long)
}