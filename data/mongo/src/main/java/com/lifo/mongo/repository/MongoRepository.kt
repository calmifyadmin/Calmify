package com.lifo.mongo.repository

import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Diary Repository Interface - Firestore 2025
 *
 * Renamed from MongoRepository but kept name for backward compatibility
 * Now uses String IDs instead of MongoDB ObjectId
 */

// Type alias for diary collections returned by the repository
typealias Diaries = RequestState<Map<LocalDate, List<Diary>>>

interface MongoRepository {
    fun getAllDiaries(): Flow<RequestState<Map<LocalDate, List<Diary>>>>
    fun getFilteredDiaries(zonedDateTime: ZonedDateTime): Flow<RequestState<Map<LocalDate, List<Diary>>>>
    fun getSelectedDiary(diaryId: String): Flow<RequestState<Diary>>
    suspend fun insertDiary(diary: Diary): RequestState<Diary>
    suspend fun updateDiary(diary: Diary): RequestState<Diary>
    suspend fun deleteDiary(id: String): RequestState<Boolean>
    suspend fun deleteAllDiaries(): RequestState<Boolean>

    // Delete ALL user data (diaries, insights, profiles, snapshots, chat sessions)
    suspend fun deleteAllUserData(): RequestState<Boolean>

    // Week 8: FCM Token Management
    suspend fun saveFCMToken(token: String): RequestState<Boolean>
}
