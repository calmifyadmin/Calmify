package com.lifo.mongo.repository

import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * ProfileRepository Interface
 *
 * Manages psychological profiles in Firestore
 * Week 7 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5 (Week 7)
 *
 * Firestore collection: psychological_profiles
 * Database: calmify-native
 */
interface ProfileRepository {
    /**
     * Get multiple profiles for a user (for trend visualization)
     * @param userId User ID
     * @param weeks Number of weeks to retrieve (default 4 for 4-week chart)
     * @return Flow of profiles ordered by year DESC, weekNumber DESC
     */
    fun getProfilesForUser(userId: String, weeks: Int = 4): Flow<RequestState<List<PsychologicalProfile>>>

    /**
     * Get the most recent profile for a user
     * @param userId User ID
     * @return Flow of the latest profile or null if none exist
     */
    fun getLatestProfile(userId: String): Flow<RequestState<PsychologicalProfile?>>

    /**
     * Get a specific profile by week and year
     * @param userId User ID
     * @param weekNumber ISO week number (1-53)
     * @param year Year
     * @return Flow of the profile or null if not found
     */
    fun getProfileByWeek(
        userId: String,
        weekNumber: Int,
        year: Int
    ): Flow<RequestState<PsychologicalProfile?>>
}
