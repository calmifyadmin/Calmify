package com.lifo.mongo.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.util.model.*
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.UnifiedContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

class UnifiedContentRepositoryImpl(
    private val database: CalmifyDatabase,
    private val diaryRepository: MongoRepository  // Inject Firestore repository
) : UnifiedContentRepository {

    override fun getUnifiedContent(ownerId: String): Flow<List<HomeContentItem>> {
        println("[UnifiedContent] " + "Loading unified content for owner: $ownerId")

        return combine(
            // Load chat sessions (uses ownerId for SQLDelight database filtering)
            database.chatSessionQueries.getAllSessions(ownerId).asFlow().mapToList(Dispatchers.IO),
            // Load diaries (Firestore uses current authenticated user)
            diaryRepository.getAllDiaries()
                .onStart {
                    println("[UnifiedContent] " + "Starting Firestore diary load")
                }
                .catch { e ->
                    println("[UnifiedContent] WARN: " + "Firestore diary load failed: ${e.message}")
                    // Emit empty success as fallback - don't block the entire feed
                    emit(RequestState.Success(emptyMap<LocalDate, List<Diary>>()))
                }
        ) { chatSessions, diariesResult ->
            println("[UnifiedContent] " + "Combining content: diaries=${diariesResult::class.simpleName}, chats=${chatSessions.size}")
            
            // Process chat items (always available from Room)
            val chatItems: List<HomeContentItem.ChatItem> = chatSessions.map { session -> 
                session.toHomeContentItem() 
            }
            
            // Process diary items (from Firestore)
            val diaryItems: List<HomeContentItem.DiaryItem> = when (diariesResult) {
                is RequestState.Success -> {
                    val diaryMap = diariesResult.data
                    println("[UnifiedContent] " + "Processing ${diaryMap.size} diary date groups")
                    val diaries = diaryMap.values.flatten()
                    println("[UnifiedContent] " + "Found ${diaries.size} total diary entries")
                    diaries.map { diary -> diary.toHomeContentItem() }
                }
                is RequestState.Error -> {
                    println("[UnifiedContent] WARN: " + "Diary result error: ${diariesResult.error.message}")
                    emptyList()
                }
                else -> {
                    println("[UnifiedContent] " + "Diary result idle/loading")
                    emptyList()
                }
            }
            
            println("[UnifiedContent] " + "Final counts: ${diaryItems.size} diary items, ${chatItems.size} chat items")
            
            // Combine and sort by creation date (most recent first)
            val allItems: List<HomeContentItem> = chatItems + diaryItems
            allItems.sortedByDescending { item -> item.createdAt }
        }
    }

    override fun searchContent(ownerId: String, query: String): Flow<List<HomeContentItem>> {
        return getUnifiedContent(ownerId).map { items ->
            if (query.isBlank()) {
                items
            } else {
                items.filter { item ->
                    when (item) {
                        is HomeContentItem.DiaryItem -> {
                            item.title.contains(query, ignoreCase = true) ||
                            item.content.contains(query, ignoreCase = true)
                        }
                        is HomeContentItem.ChatItem -> {
                            item.title.contains(query, ignoreCase = true) ||
                            item.summary?.contains(query, ignoreCase = true) == true ||
                            item.lastMessage?.contains(query, ignoreCase = true) == true
                        }
                    }
                }
            }
        }
    }

    override fun filterByType(ownerId: String, type: ContentType): Flow<List<HomeContentItem>> {
        return getUnifiedContent(ownerId).map { items ->
            when (type) {
                ContentType.ALL -> items
                ContentType.DIARY -> items.filterIsInstance<HomeContentItem.DiaryItem>()
                ContentType.CHAT -> items.filterIsInstance<HomeContentItem.ChatItem>()
            }
        }
    }

    override fun filterByDateRange(
        ownerId: String, 
        startDate: Long, 
        endDate: Long
    ): Flow<List<HomeContentItem>> {
        return getUnifiedContent(ownerId).map { items ->
            items.filter { item ->
                item.createdAt >= startDate && item.createdAt <= endDate
            }
        }
    }

    override fun applyFilter(
        ownerId: String,
        filter: ContentFilter,
        searchQuery: String
    ): Flow<List<HomeContentItem>> {
        val baseFlow = when (filter) {
            ContentFilter.ALL -> getUnifiedContent(ownerId)
            ContentFilter.DIARY -> filterByType(ownerId, ContentType.DIARY)
            ContentFilter.CHAT -> filterByType(ownerId, ContentType.CHAT)
            ContentFilter.TODAY -> {
                val startOfDay = java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = java.time.LocalDate.now().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                filterByDateRange(ownerId, startOfDay, endOfDay)
            }
            ContentFilter.THIS_WEEK -> {
                val now = java.time.LocalDate.now()
                val startOfWeek = now.with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfWeek = now.with(DayOfWeek.SUNDAY).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                filterByDateRange(ownerId, startOfWeek, endOfWeek)
            }
            ContentFilter.THIS_MONTH -> {
                val now = java.time.LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                filterByDateRange(ownerId, startOfMonth, endOfMonth)
            }
        }
        
        return if (searchQuery.isBlank()) {
            baseFlow
        } else {
            baseFlow.map { items ->
                items.filter { item ->
                    when (item) {
                        is HomeContentItem.DiaryItem -> {
                            item.title.contains(searchQuery, ignoreCase = true) ||
                            item.content.contains(searchQuery, ignoreCase = true)
                        }
                        is HomeContentItem.ChatItem -> {
                            item.title.contains(searchQuery, ignoreCase = true) ||
                            item.summary?.contains(searchQuery, ignoreCase = true) == true ||
                            item.lastMessage?.contains(searchQuery, ignoreCase = true) == true
                        }
                    }
                }
            }
        }
    }
}