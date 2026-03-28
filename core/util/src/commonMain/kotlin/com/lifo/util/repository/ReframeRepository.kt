package com.lifo.util.repository

import com.lifo.util.model.RequestState
import com.lifo.util.model.ThoughtReframe
import kotlinx.coroutines.flow.Flow

interface ReframeRepository {
    suspend fun saveReframe(reframe: ThoughtReframe): RequestState<String>
    fun getRecentReframes(limit: Int = 30): Flow<RequestState<List<ThoughtReframe>>>
    suspend fun deleteReframe(id: String): RequestState<Boolean>
}
