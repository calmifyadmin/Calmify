package com.lifo.util.repository

import com.lifo.util.model.ContentFilter
import com.lifo.util.model.ContentType
import com.lifo.util.model.HomeContentItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for unified content (Diary + Chat) operations
 */
interface UnifiedContentRepository {
    fun getUnifiedContent(ownerId: String): Flow<List<HomeContentItem>>
    fun searchContent(ownerId: String, query: String): Flow<List<HomeContentItem>>
    fun filterByType(ownerId: String, type: ContentType): Flow<List<HomeContentItem>>
    fun filterByDateRange(
        ownerId: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<HomeContentItem>>
    fun applyFilter(
        ownerId: String,
        filter: ContentFilter,
        searchQuery: String = ""
    ): Flow<List<HomeContentItem>>
}
