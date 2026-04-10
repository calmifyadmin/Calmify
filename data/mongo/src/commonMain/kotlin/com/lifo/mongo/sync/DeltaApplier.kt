package com.lifo.mongo.sync

import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.shared.api.GenericDeltaResponse
import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import com.lifo.shared.model.DiaryProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.model.*
import com.lifo.util.sync.DeltaHandler
import kotlinx.serialization.json.Json

/**
 * Routes server delta responses to the correct local sync repository.
 *
 * When the SyncEngine pulls changes via KtorSyncExecutor, it receives
 * a GenericDeltaResponse with created/updated/deleted items as JSON-encoded strings.
 * This class deserializes them and applies to the right SQLDelight table.
 */
class DeltaApplier(
    private val database: CalmifyDatabase,
    private val syncDiaryRepository: SyncDiaryRepository,
    private val syncChatRepository: SyncChatRepository,
    private val wellnessRepos: Map<SyncEntityType, SyncWellnessRepository<*>>,
) : DeltaHandler {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun apply(entityType: String, delta: GenericDeltaResponse) {
        val type = try {
            SyncEntityType.valueOf(entityType)
        } catch (_: Exception) {
            println("DeltaApplier: unknown entity type '$entityType', skipping")
            return
        }

        val created = delta.created
        val updated = delta.updated
        val deletedIds = delta.deletedIds

        println("DeltaApplier: applying $entityType — ${created.size} created, ${updated.size} updated, ${deletedIds.size} deleted")

        when (type) {
            SyncEntityType.DIARY -> applyDiary(created, updated, deletedIds)
            SyncEntityType.CHAT_SESSION -> applyChatSessions(created, updated, deletedIds)
            SyncEntityType.CHAT_MESSAGE -> applyChatMessages(created, updated, deletedIds)
            else -> applyWellness(type, created, updated, deletedIds)
        }
    }

    private suspend fun applyDiary(
        created: List<String>,
        updated: List<String>,
        deletedIds: List<String>,
    ) {
        val createdItems = created.map { json.decodeFromString(DiaryProto.serializer(), it).toDomain() }
        val updatedItems = updated.map { json.decodeFromString(DiaryProto.serializer(), it).toDomain() }
        syncDiaryRepository.applyServerChanges(createdItems, updatedItems, deletedIds)
    }

    private suspend fun applyChatSessions(
        created: List<String>,
        updated: List<String>,
        deletedIds: List<String>,
    ) {
        val createdItems = created.map { json.decodeFromString(ChatSessionProto.serializer(), it).toDomain() }
        val updatedItems = updated.map { json.decodeFromString(ChatSessionProto.serializer(), it).toDomain() }
        syncChatRepository.applySessionChanges(createdItems, updatedItems, deletedIds)
    }

    private suspend fun applyChatMessages(
        created: List<String>,
        updated: List<String>,
        deletedIds: List<String>,
    ) {
        val createdItems = created.map { json.decodeFromString(ChatMessageProto.serializer(), it).toDomain() }
        val updatedItems = updated.map { json.decodeFromString(ChatMessageProto.serializer(), it).toDomain() }
        syncChatRepository.applyMessageChanges(createdItems, updatedItems, deletedIds)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun applyWellness(
        type: SyncEntityType,
        created: List<String>,
        updated: List<String>,
        deletedIds: List<String>,
    ) {
        val repo = wellnessRepos[type] as? SyncWellnessRepository<Any>
        if (repo == null) {
            println("DeltaApplier: no wellness repo for $type")
            return
        }

        val serializer = wellnessSerializer(type) ?: return
        val createdItems = created.map { json.decodeFromString(serializer, it) }
        val updatedItems = updated.map { json.decodeFromString(serializer, it) }
        repo.applyServerChanges(createdItems, updatedItems, deletedIds)
    }

    @Suppress("UNCHECKED_CAST")
    private fun wellnessSerializer(type: SyncEntityType): kotlinx.serialization.KSerializer<Any>? {
        return when (type) {
            SyncEntityType.GRATITUDE -> GratitudeEntry.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.ENERGY -> EnergyCheckIn.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.SLEEP -> SleepLog.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.MEDITATION -> MeditationSession.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.HABIT -> Habit.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.MOVEMENT -> MovementLog.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.REFRAME -> ThoughtReframe.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.WELLBEING -> WellbeingSnapshot.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.AWE -> AweEntry.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.CONNECTION -> ConnectionEntry.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.RECURRING_THOUGHT -> RecurringThought.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.BLOCK -> Block.serializer() as kotlinx.serialization.KSerializer<Any>
            SyncEntityType.VALUES -> ValuesDiscovery.serializer() as kotlinx.serialization.KSerializer<Any>
            else -> {
                println("DeltaApplier: not a wellness type: $type")
                null
            }
        }
    }
}
