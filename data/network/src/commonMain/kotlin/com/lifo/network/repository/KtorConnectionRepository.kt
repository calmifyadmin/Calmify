package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.ConnectionEntryProto
import com.lifo.shared.model.RelationshipReflectionProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.ConnectionEntry
import com.lifo.util.model.RelationshipReflection
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorConnectionRepository(
    private val api: KtorApiClient,
) : ConnectionRepository {

    override fun getEntries(ownerId: String): Flow<List<ConnectionEntry>> = flow {
        val result = api.get<WellnessListDto<ConnectionEntryProto>>("/api/v1/wellness/connection?limit=100")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }
            else -> emptyList()
        })
    }

    override suspend fun saveEntry(entry: ConnectionEntry) {
        val proto = entry.toProto()
        if (entry.id.isEmpty()) {
            api.post<ConnectionEntryProto>("/api/v1/wellness/connection", proto)
        } else {
            api.put<ConnectionEntryProto>("/api/v1/wellness/connection/${entry.id}", proto)
        }
    }

    override suspend fun deleteEntry(id: String, ownerId: String) {
        api.deleteNoBody("/api/v1/wellness/connection/$id")
    }

    override fun getReflection(ownerId: String, monthKey: String): Flow<RelationshipReflection?> = flow {
        // Reflections are stored as a separate resource — use connection sub-path
        // For now, no server endpoint exists for reflections specifically
        emit(null)
    }

    override suspend fun saveReflection(reflection: RelationshipReflection) {
        // No server endpoint for reflections yet — silent no-op
    }
}
