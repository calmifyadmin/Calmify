package com.lifo.util.repository

import com.lifo.util.model.AweEntry
import kotlinx.coroutines.flow.Flow

interface AweRepository {
    fun getEntries(userId: String): Flow<List<AweEntry>>
    suspend fun saveEntry(entry: AweEntry): Result<Unit>
    suspend fun deleteEntry(entryId: String): Result<Unit>
}
