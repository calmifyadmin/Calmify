package com.lifo.mongo.sync

import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.mongo.database.Diaries
import com.lifo.util.currentTimeMillis
import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sync-aware diary repository.
 *
 * READ: Always from SQLDelight (instant, offline-capable).
 * WRITE: SQLDelight first (optimistic) + enqueue for server sync.
 * The SyncEngine handles pushing changes to the server in background.
 */
class SyncDiaryRepository(
    private val database: CalmifyDatabase,
    private val syncEngine: SyncEngine,
    private val userId: () -> String?,
) : MongoRepository {

    private val diaryQueries get() = database.diaryQueries
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override fun getAllDiaries(): Flow<RequestState<Map<LocalDate, List<Diary>>>> {
        val uid = userId() ?: return flow { emit(RequestState.Error(Exception("Not authenticated"))) }
        return diaryQueries.getAllByUser(uid)
            .asFlow()
            .map { query ->
                val diaries = query.executeAsList().map { it.toDomain() }
                RequestState.Success(diaries.groupBy { diary ->
                    LocalDate.parse(diary.dayKey.ifEmpty { "2000-01-01" })
                })
            }
    }

    override fun getFilteredDiaries(dayKey: String): Flow<RequestState<Map<LocalDate, List<Diary>>>> {
        val uid = userId() ?: return flow { emit(RequestState.Error(Exception("Not authenticated"))) }
        return diaryQueries.getByDayKey(userId = uid, dayKey = dayKey)
            .asFlow()
            .map { query ->
                val diaries = query.executeAsList().map { it.toDomain() }
                RequestState.Success(diaries.groupBy { LocalDate.parse(dayKey) })
            }
    }

    override fun getSelectedDiary(diaryId: String): Flow<RequestState<Diary>> = flow {
        emit(RequestState.Loading)
        val entity = diaryQueries.getById(diaryId).executeAsOneOrNull()
        if (entity != null) {
            emit(RequestState.Success(entity.toDomain()))
        } else {
            emit(RequestState.Error(Exception("Diary not found")))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun insertDiary(diary: Diary): RequestState<Diary> = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        val id = diary._id.ifEmpty { Uuid.random().toString() }
        val newDiary = diary.copy(_id = id)

        // 1. Save locally (UI updates instantly via Flow)
        diaryQueries.upsert(
            id = id,
            owner_id = newDiary.ownerId,
            mood = newDiary.mood,
            title = newDiary.title,
            description = newDiary.description,
            images = json.encodeToString(newDiary.images),
            date_millis = newDiary.dateMillis,
            day_key = newDiary.dayKey,
            timezone = newDiary.timezone,
            emotion_intensity = newDiary.emotionIntensity.toLong(),
            stress_level = newDiary.stressLevel.toLong(),
            energy_level = newDiary.energyLevel.toLong(),
            calm_anxiety_level = newDiary.calmAnxietyLevel.toLong(),
            primary_trigger = newDiary.primaryTrigger,
            dominant_body_sensation = newDiary.dominantBodySensation,
            is_dirty = 1,
            created_at = now,
            updated_at = now,
        )

        // 2. Enqueue for server sync
        syncEngine.enqueue(
            entityType = SyncEntityType.DIARY.name,
            entityId = id,
            operation = "CREATE",
            payload = serializeDiary(newDiary),
        )

        RequestState.Success(newDiary)
    }

    override suspend fun updateDiary(diary: Diary): RequestState<Diary> = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()

        diaryQueries.upsert(
            id = diary._id,
            owner_id = diary.ownerId,
            mood = diary.mood,
            title = diary.title,
            description = diary.description,
            images = json.encodeToString(diary.images),
            date_millis = diary.dateMillis,
            day_key = diary.dayKey,
            timezone = diary.timezone,
            emotion_intensity = diary.emotionIntensity.toLong(),
            stress_level = diary.stressLevel.toLong(),
            energy_level = diary.energyLevel.toLong(),
            calm_anxiety_level = diary.calmAnxietyLevel.toLong(),
            primary_trigger = diary.primaryTrigger,
            dominant_body_sensation = diary.dominantBodySensation,
            is_dirty = 1,
            created_at = diary.dateMillis,
            updated_at = now,
        )

        syncEngine.enqueue(
            entityType = SyncEntityType.DIARY.name,
            entityId = diary._id,
            operation = "UPDATE",
            payload = serializeDiary(diary),
        )

        RequestState.Success(diary)
    }

    override suspend fun deleteDiary(id: String): RequestState<Boolean> = withContext(Dispatchers.Default) {
        diaryQueries.deleteById(id)

        syncEngine.enqueue(
            entityType = SyncEntityType.DIARY.name,
            entityId = id,
            operation = "DELETE",
            payload = "",
        )

        RequestState.Success(true)
    }

    override suspend fun deleteAllDiaries(): RequestState<Boolean> = withContext(Dispatchers.Default) {
        val uid = userId() ?: return@withContext RequestState.Error(Exception("Not authenticated"))
        diaryQueries.deleteAllByUser(uid)
        RequestState.Success(true)
    }

    override suspend fun deleteAllUserData(): RequestState<Boolean> {
        return deleteAllDiaries()
    }

    override suspend fun saveFCMToken(token: String): RequestState<Boolean> {
        // FCM tokens go directly to server — no local cache needed
        return RequestState.Error(Exception("Use network repository for FCM token"))
    }

    // ─── Delta sync: apply server changes ──────────────────────────────

    suspend fun applyServerChanges(
        created: List<Diary>,
        updated: List<Diary>,
        deletedIds: List<String>,
    ) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        for (diary in created + updated) {
            diaryQueries.upsert(
                id = diary._id,
                owner_id = diary.ownerId,
                mood = diary.mood,
                title = diary.title,
                description = diary.description,
                images = json.encodeToString(diary.images),
                date_millis = diary.dateMillis,
                day_key = diary.dayKey,
                timezone = diary.timezone,
                emotion_intensity = diary.emotionIntensity.toLong(),
                stress_level = diary.stressLevel.toLong(),
                energy_level = diary.energyLevel.toLong(),
                calm_anxiety_level = diary.calmAnxietyLevel.toLong(),
                primary_trigger = diary.primaryTrigger,
                dominant_body_sensation = diary.dominantBodySensation,
                is_dirty = 0, // Server data is clean
                created_at = diary.dateMillis,
                updated_at = now,
            )
        }
        for (id in deletedIds) {
            diaryQueries.deleteById(id)
        }
        println("DeltaApplier: applied ${created.size + updated.size} diary upserts, ${deletedIds.size} deletes")
    }

    // ─── Mapping helpers ────────────────────────────────────────────────

    private fun serializeDiary(diary: Diary): String {
        return json.encodeToString(
            mapOf(
                "id" to diary._id,
                "ownerId" to diary.ownerId,
                "mood" to diary.mood,
                "title" to diary.title,
                "description" to diary.description,
                "images" to json.encodeToString(diary.images),
                "dateMillis" to diary.dateMillis.toString(),
                "dayKey" to diary.dayKey,
                "timezone" to diary.timezone,
                "emotionIntensity" to diary.emotionIntensity.toString(),
                "stressLevel" to diary.stressLevel.toString(),
                "energyLevel" to diary.energyLevel.toString(),
                "calmAnxietyLevel" to diary.calmAnxietyLevel.toString(),
                "primaryTrigger" to diary.primaryTrigger,
                "dominantBodySensation" to diary.dominantBodySensation,
            )
        )
    }
}

/**
 * Extension to convert SQLDelight entity to domain model.
 */
private val json = Json { ignoreUnknownKeys = true }

private fun com.lifo.mongo.database.Diaries.toDomain(): Diary {
    return Diary(
        _id = id,
        ownerId = owner_id,
        mood = mood,
        title = title,
        description = description,
        images = try { json.decodeFromString<List<String>>(images) } catch (_: Exception) { emptyList() },
        dateMillis = date_millis,
        dayKey = day_key,
        timezone = timezone,
        emotionIntensity = emotion_intensity.toInt(),
        stressLevel = stress_level.toInt(),
        energyLevel = energy_level.toInt(),
        calmAnxietyLevel = calm_anxiety_level.toInt(),
        primaryTrigger = primary_trigger,
        dominantBodySensation = dominant_body_sensation,
    )
}

/**
 * Extension to get Flow from SQLDelight query.
 */
private fun <T : Any> app.cash.sqldelight.Query<T>.asFlow(): Flow<app.cash.sqldelight.Query<T>> {
    return kotlinx.coroutines.flow.flow {
        emit(this@asFlow)
        // Note: For real reactive updates, use sqldelight-coroutines-extensions
        // This is a simplified version for the initial implementation
    }
}
