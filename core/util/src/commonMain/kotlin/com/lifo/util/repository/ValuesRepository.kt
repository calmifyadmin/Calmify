package com.lifo.util.repository

import com.lifo.util.model.ValuesDiscovery
import kotlinx.coroutines.flow.Flow

interface ValuesRepository {
    fun getDiscovery(userId: String): Flow<ValuesDiscovery?>
    suspend fun saveDiscovery(discovery: ValuesDiscovery): Result<Unit>
    suspend fun deleteDiscovery(discoveryId: String): Result<Unit>
}
