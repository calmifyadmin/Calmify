package com.lifo.util.repository

import com.lifo.util.model.RecurringThought
import kotlinx.coroutines.flow.Flow

interface RecurringThoughtRepository {
    fun getThoughts(userId: String): Flow<List<RecurringThought>>
    suspend fun saveThought(thought: RecurringThought): Result<Unit>
    suspend fun incrementOccurrence(thoughtId: String): Result<Unit>
    suspend fun markReframed(thoughtId: String, reframeId: String): Result<Unit>
    suspend fun incrementPostReframe(thoughtId: String): Result<Unit>
    suspend fun resolveThought(thoughtId: String): Result<Unit>
    suspend fun deleteThought(thoughtId: String): Result<Unit>
}
