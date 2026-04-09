package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.DiaryListResponse
import com.lifo.shared.api.DiaryResponse
import com.lifo.shared.model.DiaryProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate

/**
 * REST-backed implementation of [MongoRepository].
 *
 * Replaces FirestoreDiaryRepository — all operations go through Ktor Server.
 * Flow-returning methods do a single fetch (no real-time updates).
 * Real-time will be added by Sync Engine (W2).
 */
class KtorDiaryRepository(
    private val api: KtorApiClient,
) : MongoRepository {

    override fun getAllDiaries(): Flow<RequestState<Map<LocalDate, List<Diary>>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<DiaryListResponse>("/api/v1/diaries?limit=100")
        emit(result.map { response ->
            response.data.map { it.toDomain() }.groupBy { diary ->
                LocalDate.parse(diary.dayKey.ifEmpty { "2000-01-01" })
            }
        })
    }

    override fun getFilteredDiaries(dayKey: String): Flow<RequestState<Map<LocalDate, List<Diary>>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<DiaryListResponse>("/api/v1/diaries/range?start=$dayKey&end=$dayKey")
        emit(result.map { response ->
            response.data.map { it.toDomain() }.groupBy { diary ->
                LocalDate.parse(diary.dayKey.ifEmpty { dayKey })
            }
        })
    }

    override fun getSelectedDiary(diaryId: String): Flow<RequestState<Diary>> = flow {
        emit(RequestState.Loading)
        val result = api.get<DiaryResponse>("/api/v1/diaries/$diaryId")
        emit(result.map { it.data?.toDomain() ?: throw Exception("Diary not found") })
    }

    override suspend fun insertDiary(diary: Diary): RequestState<Diary> {
        val result = api.post<DiaryResponse>("/api/v1/diaries", diary.toProto())
        return result.map { it.data?.toDomain() ?: throw Exception("Failed to create diary") }
    }

    override suspend fun updateDiary(diary: Diary): RequestState<Diary> {
        val result = api.put<DiaryResponse>("/api/v1/diaries/${diary._id}", diary.toProto())
        return result.map { it.data?.toDomain() ?: throw Exception("Failed to update diary") }
    }

    override suspend fun deleteDiary(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/diaries/$id")
    }

    override suspend fun deleteAllDiaries(): RequestState<Boolean> {
        // Server doesn't have a bulk delete endpoint yet — would need batch
        return RequestState.Error(Exception("Bulk delete not supported via REST yet"))
    }

    override suspend fun deleteAllUserData(): RequestState<Boolean> {
        // GDPR delete — needs dedicated server endpoint
        return RequestState.Error(Exception("GDPR delete not supported via REST yet"))
    }

    override suspend fun saveFCMToken(token: String): RequestState<Boolean> {
        return api.postNoBody("/api/v1/profile/fcm-token") {
            url { parameters.append("token", token) }
        }
    }
}
