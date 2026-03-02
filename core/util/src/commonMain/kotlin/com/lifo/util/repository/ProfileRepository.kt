package com.lifo.util.repository

import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * ProfileRepository Interface
 *
 * Manages psychological profiles in Firestore
 */
interface ProfileRepository {
    fun getProfilesForUser(userId: String, weeks: Int = 4): Flow<RequestState<List<PsychologicalProfile>>>
    fun getLatestProfile(userId: String): Flow<RequestState<PsychologicalProfile?>>
    fun getProfileByWeek(
        userId: String,
        weekNumber: Int,
        year: Int
    ): Flow<RequestState<PsychologicalProfile?>>
}
