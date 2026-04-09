package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.PsychologicalProfileProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

class KtorProfileRepository(
    private val api: KtorApiClient,
) : ProfileRepository {

    override fun getProfilesForUser(
        userId: String,
        weeks: Int,
    ): Flow<RequestState<List<PsychologicalProfile>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ProfileListDto>("/api/v1/profile/psychological?weeks=$weeks")
        emit(result.map { it.data.map { p -> p.toDomain() } })
    }

    override fun getLatestProfile(userId: String): Flow<RequestState<PsychologicalProfile?>> = flow {
        emit(RequestState.Loading)
        val result = api.get<PsychologicalProfileProto>("/api/v1/profile/psychological/latest")
        emit(result.map { it.toDomain() })
    }

    override fun getProfileByWeek(
        userId: String,
        weekNumber: Int,
        year: Int,
    ): Flow<RequestState<PsychologicalProfile?>> = flow {
        emit(RequestState.Loading)
        val result = api.get<PsychologicalProfileProto>("/api/v1/profile/psychological/$weekNumber/$year")
        emit(result.map { it.toDomain() })
    }
}

@Serializable
data class ProfileListDto(val data: List<PsychologicalProfileProto> = emptyList())
