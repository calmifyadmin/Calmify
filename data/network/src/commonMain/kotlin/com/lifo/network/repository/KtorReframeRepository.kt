package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.ThoughtReframeProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.RequestState
import com.lifo.util.model.ThoughtReframe
import com.lifo.util.repository.ReframeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorReframeRepository(
    private val api: KtorApiClient,
) : ReframeRepository {

    override suspend fun saveReframe(reframe: ThoughtReframe): RequestState<String> {
        val proto = reframe.toProto()
        val result = if (reframe.id.isEmpty()) {
            api.post<ThoughtReframeProto>("/api/v1/wellness/reframe", proto)
        } else {
            api.put<ThoughtReframeProto>("/api/v1/wellness/reframe/${reframe.id}", proto)
        }
        return result.map { it.id }
    }

    override fun getRecentReframes(limit: Int): Flow<RequestState<List<ThoughtReframe>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<ThoughtReframeProto>>("/api/v1/wellness/reframe?limit=$limit")
        emit(result.map { it.data.map { r -> r.toDomain() } })
    }

    override suspend fun deleteReframe(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/wellness/reframe/$id")
    }
}
