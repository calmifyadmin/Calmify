package com.lifo.util.repository

import com.lifo.util.model.MeditationSession
import kotlinx.coroutines.flow.Flow

interface MeditationRepository {
    suspend fun saveSession(session: MeditationSession): Result<Unit>
    fun getRecentSessions(userId: String, limit: Int = 20): Flow<List<MeditationSession>>
    suspend fun getTotalMinutes(userId: String): Int
    suspend fun getSessionCount(userId: String): Int
    suspend fun deleteSession(sessionId: String): Result<Unit>
}
