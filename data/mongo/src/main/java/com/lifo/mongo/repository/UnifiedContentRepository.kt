package com.lifo.mongo.repository

import com.lifo.util.model.ContentFilter
import com.lifo.util.model.ContentType
import com.lifo.util.model.HomeContentItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for unified content (Diary + Chat) operations
 */
interface UnifiedContentRepository {
    /**
     * Get all content items (Diary + Chat) sorted by creation date
     */
    fun getUnifiedContent(ownerId: String): Flow<List<HomeContentItem>>
    
    /**
     * Search content by query string
     */
    fun searchContent(ownerId: String, query: String): Flow<List<HomeContentItem>>
    
    /**
     * Filter content by type
     */
    fun filterByType(ownerId: String, type: ContentType): Flow<List<HomeContentItem>>
    
    /**
     * Filter content by date range
     */
    fun filterByDateRange(
        ownerId: String, 
        startDate: Long, 
        endDate: Long
    ): Flow<List<HomeContentItem>>
    
    /**
     * Apply unified filter
     */
    fun applyFilter(
        ownerId: String,
        filter: ContentFilter,
        searchQuery: String = ""
    ): Flow<List<HomeContentItem>>
}