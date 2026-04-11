package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.WellbeingSnapshotProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.RequestState
import com.lifo.util.model.WellbeingSnapshot
import com.lifo.util.repository.WellbeingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorWellbeingRepository(
    private val api: KtorApiClient,
) : WellbeingRepository {

    override suspend fun insertSnapshot(snapshot: WellbeingSnapshot): RequestState<String> {
        val proto = snapshot.toProto()
        val result = if (snapshot.id.isEmpty()) {
            api.post<WellbeingSnapshotProto>("/api/v1/wellness/wellbeing", proto)
        } else {
            api.put<WellbeingSnapshotProto>("/api/v1/wellness/wellbeing/${snapshot.id}", proto)
        }
        return result.map { it.id }
    }

    override fun getAllSnapshots(): Flow<RequestState<List<WellbeingSnapshot>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<WellbeingSnapshotProto>>("/api/v1/wellness/wellbeing?limit=100")
        emit(result.map { it.data.map { s -> s.toDomain() } })
    }

    override suspend fun getLatestSnapshot(): RequestState<WellbeingSnapshot?> {
        val result = api.get<WellnessListDto<WellbeingSnapshotProto>>("/api/v1/wellness/wellbeing?limit=1")
        return result.map { it.data.firstOrNull()?.toDomain() }
    }

    override fun getSnapshotsInRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): Flow<RequestState<List<WellbeingSnapshot>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<WellbeingSnapshotProto>>("/api/v1/wellness/wellbeing?limit=100")
        emit(result.map { response ->
            response.data
                .filter { it.timestampMillis in startTimestamp..endTimestamp }
                .map { it.toDomain() }
        })
    }

    override suspend fun deleteSnapshot(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/wellness/wellbeing/$id")
    }
}
