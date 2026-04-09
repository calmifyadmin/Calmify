package com.lifo.mongo.sync

import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.util.model.*

/**
 * Factory for creating typed SyncWellnessRepository instances.
 *
 * Each method returns a SyncWellnessRepository<T> wired with the correct
 * serializer, entity type, and field accessors.
 */
object SyncWellnessFactory {

    fun gratitude(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.GRATITUDE,
            serializer = GratitudeEntry.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun energy(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.ENERGY,
            serializer = EnergyCheckIn.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun sleep(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.SLEEP,
            serializer = SleepLog.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun meditation(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.MEDITATION,
            serializer = MeditationSession.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { "" },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun habit(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.HABIT,
            serializer = Habit.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { "" },
            getTimestamp = { it.createdAtMillis },
            userId = userId,
        )

    fun movement(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.MOVEMENT,
            serializer = MovementLog.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun reframe(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.REFRAME,
            serializer = ThoughtReframe.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { "" },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun wellbeing(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.WELLBEING,
            serializer = WellbeingSnapshot.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun awe(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.AWE,
            serializer = AweEntry.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun connection(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.CONNECTION,
            serializer = ConnectionEntry.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { it.dayKey },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun recurringThought(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.RECURRING_THOUGHT,
            serializer = RecurringThought.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { "" },
            getTimestamp = { it.firstSeenMillis },
            userId = userId,
        )

    fun block(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.BLOCK,
            serializer = Block.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { "" },
            getTimestamp = { it.timestampMillis },
            userId = userId,
        )

    fun values(db: CalmifyDatabase, engine: SyncEngine, userId: () -> String?) =
        SyncWellnessRepository(
            database = db,
            syncEngine = engine,
            entityType = SyncEntityType.VALUES,
            serializer = ValuesDiscovery.serializer(),
            getId = { it.id },
            getOwnerId = { it.ownerId },
            getDayKey = { "" },
            getTimestamp = { it.createdAtMillis },
            userId = userId,
        )
}
