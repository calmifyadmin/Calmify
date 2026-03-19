package com.lifo.util.repository

import com.lifo.util.model.RequestState

/**
 * Repository for managing PRO waitlist email signups.
 * Used when `premium_enabled = false` to capture demand.
 */
interface WaitlistRepository {
    suspend fun saveWaitlistEmail(
        email: String,
        userId: String?,
        source: String,
    ): RequestState<Boolean>
}
