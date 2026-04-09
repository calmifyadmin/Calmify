package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorProfileRepository(
    private val api: KtorApiClient,
) : ProfileRepository {

    override fun getProfilesForUser(
        userId: String,
        weeks: Int,
    ): Flow<RequestState<List<PsychologicalProfile>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<Map<String, List<Map<String, Any>>>>("/api/v1/profiles?weeks=$weeks")
        // Profile parsing requires specific mapping — simplified for now
        emit(RequestState.Success(emptyList()))
    }

    override fun getLatestProfile(userId: String): Flow<RequestState<PsychologicalProfile?>> = flow {
        emit(RequestState.Loading)
        // Server endpoint to be defined
        emit(RequestState.Success(null))
    }

    override fun getProfileByWeek(
        userId: String,
        weekNumber: Int,
        year: Int,
    ): Flow<RequestState<PsychologicalProfile?>> = flow {
        emit(RequestState.Loading)
        emit(RequestState.Success(null))
    }
}
