package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.EnergyCheckInProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.EnergyCheckIn
import com.lifo.util.model.RequestState
import com.lifo.util.repository.EnergyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.*

class KtorEnergyRepository(
    private val api: KtorApiClient,
) : EnergyRepository {

    override suspend fun upsertCheckIn(checkIn: EnergyCheckIn): RequestState<String> {
        val proto = checkIn.toProto()
        val result = if (checkIn.id.isEmpty()) {
            api.post<EnergyCheckInProto>("/api/v1/wellness/energy", proto)
        } else {
            api.put<EnergyCheckInProto>("/api/v1/wellness/energy/${checkIn.id}", proto)
        }
        return result.map { it.id }
    }

    override fun getTodayCheckIn(): Flow<RequestState<EnergyCheckIn?>> = flow {
        emit(RequestState.Loading)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayKey = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"
        val result = api.get<WellnessListDto<EnergyCheckInProto>>("/api/v1/wellness/energy/day/$dayKey")
        emit(result.map { it.data.firstOrNull()?.toDomain() })
    }

    override fun getRecentCheckIns(limit: Int): Flow<RequestState<List<EnergyCheckIn>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<EnergyCheckInProto>>("/api/v1/wellness/energy?limit=$limit")
        emit(result.map { it.data.map { e -> e.toDomain() } })
    }

    override fun getCheckInsInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<EnergyCheckIn>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<EnergyCheckInProto>>("/api/v1/wellness/energy?limit=100")
        emit(result.map { response ->
            response.data
                .filter { it.timestampMillis in startMillis..endMillis }
                .map { it.toDomain() }
        })
    }

    override suspend fun deleteCheckIn(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/wellness/energy/$id")
    }
}
