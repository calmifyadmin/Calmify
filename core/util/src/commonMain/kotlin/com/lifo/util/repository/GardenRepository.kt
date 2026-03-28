package com.lifo.util.repository

import kotlinx.coroutines.flow.Flow

interface GardenRepository {
    fun getExploredActivities(userId: String): Flow<Set<String>>
    fun getFavorites(userId: String): Flow<Set<String>>
    suspend fun markExplored(userId: String, activityId: String)
    suspend fun toggleFavorite(userId: String, activityId: String)
}
