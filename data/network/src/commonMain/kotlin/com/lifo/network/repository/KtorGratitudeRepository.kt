package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.GratitudeEntryProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.GratitudeEntry
import com.lifo.util.model.RequestState
import com.lifo.util.repository.GratitudeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.*

class KtorGratitudeRepository(
    private val api: KtorApiClient,
) : GratitudeRepository {

    override suspend fun upsertEntry(entry: GratitudeEntry): RequestState<String> {
        val proto = entry.toProto()
        val result = if (entry.id.isEmpty()) {
            api.post<GratitudeEntryProto>("/api/v1/wellness/gratitude", proto)
        } else {
            api.put<GratitudeEntryProto>("/api/v1/wellness/gratitude/${entry.id}", proto)
        }
        return result.map { it.id }
    }

    override fun getTodayEntry(): Flow<RequestState<GratitudeEntry?>> = flow {
        emit(RequestState.Loading)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayKey = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"
        val result = api.get<WellnessListDto<GratitudeEntryProto>>("/api/v1/wellness/gratitude/day/$dayKey")
        emit(result.map { it.data.firstOrNull()?.toDomain() })
    }

    override fun getRecentEntries(limit: Int): Flow<RequestState<List<GratitudeEntry>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<GratitudeEntryProto>>("/api/v1/wellness/gratitude?limit=$limit")
        emit(result.map { it.data.map { e -> e.toDomain() } })
    }

    override fun getEntriesInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<GratitudeEntry>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<GratitudeEntryProto>>("/api/v1/wellness/gratitude?limit=100")
        emit(result.map { response ->
            response.data
                .filter { it.timestampMillis in startMillis..endMillis }
                .map { it.toDomain() }
        })
    }

    override suspend fun deleteEntry(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/wellness/gratitude/$id")
    }
}
