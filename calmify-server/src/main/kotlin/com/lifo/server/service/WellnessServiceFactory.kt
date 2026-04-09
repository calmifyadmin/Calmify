package com.lifo.server.service

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.lifo.shared.model.*

object WellnessServiceFactory {

    fun gratitude(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "gratitudeEntries",
        mapper = { doc: DocumentSnapshot ->
            GratitudeEntryProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                dayKey = doc.getString("dayKey") ?: "", timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                timezone = doc.getString("timezone") ?: "", item1 = doc.getString("item1") ?: "",
                item2 = doc.getString("item2") ?: "", item3 = doc.getString("item3") ?: "",
                category1 = doc.getString("category1") ?: "ALTRO",
                category2 = doc.getString("category2") ?: "ALTRO",
                category3 = doc.getString("category3") ?: "ALTRO",
            )
        },
        toFirestoreMap = { item: GratitudeEntryProto, userId: String ->
            mapOf("ownerId" to userId, "dayKey" to item.dayKey, "timestampMillis" to item.timestampMillis,
                "timezone" to item.timezone, "item1" to item.item1, "item2" to item.item2, "item3" to item.item3,
                "category1" to item.category1, "category2" to item.category2, "category3" to item.category3)
        },
        getId = { it.id },
    )

    fun energy(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "energyCheckIns",
        mapper = { doc: DocumentSnapshot ->
            EnergyCheckInProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                dayKey = doc.getString("dayKey") ?: "", timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                timezone = doc.getString("timezone") ?: "",
                energyLevel = doc.getLong("energyLevel")?.toInt() ?: 5,
                sleepHours = doc.getDouble("sleepHours")?.toFloat() ?: 7f,
                waterGlasses = doc.getLong("waterGlasses")?.toInt() ?: 0,
                didMovement = doc.getBoolean("didMovement") ?: false,
                movementType = doc.getString("movementType") ?: "NESSUNO",
                regularMeals = doc.getBoolean("regularMeals") ?: true,
            )
        },
        toFirestoreMap = { item: EnergyCheckInProto, userId: String ->
            mapOf("ownerId" to userId, "dayKey" to item.dayKey, "timestampMillis" to item.timestampMillis,
                "timezone" to item.timezone, "energyLevel" to item.energyLevel,
                "sleepHours" to item.sleepHours.toDouble(), "waterGlasses" to item.waterGlasses,
                "didMovement" to item.didMovement, "movementType" to item.movementType,
                "regularMeals" to item.regularMeals)
        },
        getId = { it.id },
    )

    fun sleep(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "sleepLogs",
        mapper = { doc: DocumentSnapshot ->
            SleepLogProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                dayKey = doc.getString("dayKey") ?: "", timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                timezone = doc.getString("timezone") ?: "",
                bedtimeHour = doc.getLong("bedtimeHour")?.toInt() ?: 23,
                bedtimeMinute = doc.getLong("bedtimeMinute")?.toInt() ?: 0,
                waketimeHour = doc.getLong("waketimeHour")?.toInt() ?: 7,
                waketimeMinute = doc.getLong("waketimeMinute")?.toInt() ?: 0,
                quality = doc.getLong("quality")?.toInt() ?: 3,
                disturbances = (doc.get("disturbances") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                screenFreeLastHour = doc.getBoolean("screenFreeLastHour") ?: false,
                notes = doc.getString("notes") ?: "",
            )
        },
        toFirestoreMap = { item: SleepLogProto, userId: String ->
            mapOf("ownerId" to userId, "dayKey" to item.dayKey, "timestampMillis" to item.timestampMillis,
                "timezone" to item.timezone, "bedtimeHour" to item.bedtimeHour,
                "bedtimeMinute" to item.bedtimeMinute, "waketimeHour" to item.waketimeHour,
                "waketimeMinute" to item.waketimeMinute, "quality" to item.quality,
                "disturbances" to item.disturbances, "screenFreeLastHour" to item.screenFreeLastHour,
                "notes" to item.notes)
        },
        getId = { it.id },
    )

    fun meditation(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "meditationSessions",
        mapper = { doc: DocumentSnapshot ->
            MeditationSessionProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                type = doc.getString("type") ?: "TIMER",
                breathingPattern = doc.getString("breathingPattern") ?: "",
                durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 300,
                completedSeconds = doc.getLong("completedSeconds")?.toInt() ?: 0,
                postNote = doc.getString("postNote") ?: "",
            )
        },
        toFirestoreMap = { item: MeditationSessionProto, userId: String ->
            mapOf("ownerId" to userId, "timestampMillis" to item.timestampMillis,
                "type" to item.type, "breathingPattern" to item.breathingPattern,
                "durationSeconds" to item.durationSeconds, "completedSeconds" to item.completedSeconds,
                "postNote" to item.postNote)
        },
        getId = { it.id },
    )

    fun habits(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "habits",
        mapper = { doc: DocumentSnapshot ->
            HabitProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                name = doc.getString("name") ?: "", description = doc.getString("description") ?: "",
                category = doc.getString("category") ?: "CRESCITA",
                anchorHabit = doc.getString("anchorHabit") ?: "",
                minimumAction = doc.getString("minimumAction") ?: "",
                targetFrequency = doc.getString("targetFrequency") ?: "DAILY",
                reminderTime = doc.getString("reminderTime") ?: "",
                createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
                isActive = doc.getBoolean("isActive") ?: true,
            )
        },
        toFirestoreMap = { item: HabitProto, userId: String ->
            mapOf("ownerId" to userId, "name" to item.name, "description" to item.description,
                "category" to item.category, "anchorHabit" to item.anchorHabit,
                "minimumAction" to item.minimumAction, "targetFrequency" to item.targetFrequency,
                "reminderTime" to item.reminderTime, "createdAtMillis" to item.createdAtMillis,
                "isActive" to item.isActive)
        },
        getId = { it.id },
    )

    fun movement(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "movementLogs",
        mapper = { doc: DocumentSnapshot ->
            MovementLogProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                dayKey = doc.getString("dayKey") ?: "",
                movementType = doc.getString("movementType") ?: "CAMMINATA",
                durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 20,
                feelingAfter = doc.getString("feelingAfter") ?: "MEGLIO",
                note = doc.getString("note") ?: "",
            )
        },
        toFirestoreMap = { item: MovementLogProto, userId: String ->
            mapOf("ownerId" to userId, "timestampMillis" to item.timestampMillis,
                "dayKey" to item.dayKey, "movementType" to item.movementType,
                "durationMinutes" to item.durationMinutes, "feelingAfter" to item.feelingAfter,
                "note" to item.note)
        },
        getId = { it.id },
    )

    fun reframe(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "thoughtReframes",
        mapper = { doc: DocumentSnapshot ->
            ThoughtReframeProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                originalThought = doc.getString("originalThought") ?: "",
                evidenceFor = doc.getString("evidenceFor") ?: "",
                evidenceAgainst = doc.getString("evidenceAgainst") ?: "",
                friendPerspective = doc.getString("friendPerspective") ?: "",
                reframedThought = doc.getString("reframedThought") ?: "",
                category = doc.getString("category") ?: "ALTRO",
            )
        },
        toFirestoreMap = { item: ThoughtReframeProto, userId: String ->
            mapOf("ownerId" to userId, "timestampMillis" to item.timestampMillis,
                "originalThought" to item.originalThought, "evidenceFor" to item.evidenceFor,
                "evidenceAgainst" to item.evidenceAgainst, "friendPerspective" to item.friendPerspective,
                "reframedThought" to item.reframedThought, "category" to item.category)
        },
        getId = { it.id },
    )

    fun wellbeing(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "wellbeingSnapshots",
        mapper = { doc: DocumentSnapshot ->
            WellbeingSnapshotProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                dayKey = doc.getString("dayKey") ?: "", timezone = doc.getString("timezone") ?: "",
                lifeSatisfaction = doc.getLong("lifeSatisfaction")?.toInt() ?: 5,
                workSatisfaction = doc.getLong("workSatisfaction")?.toInt() ?: 5,
                relationshipsQuality = doc.getLong("relationshipsQuality")?.toInt() ?: 5,
                mindfulnessScore = doc.getLong("mindfulnessScore")?.toInt() ?: 5,
                purposeMeaning = doc.getLong("purposeMeaning")?.toInt() ?: 5,
                gratitude = doc.getLong("gratitude")?.toInt() ?: 5,
                autonomy = doc.getLong("autonomy")?.toInt() ?: 5,
                competence = doc.getLong("competence")?.toInt() ?: 5,
                relatedness = doc.getLong("relatedness")?.toInt() ?: 5,
                loneliness = doc.getLong("loneliness")?.toInt() ?: 5,
                notes = doc.getString("notes") ?: "",
                completionTime = doc.getLong("completionTime") ?: 0L,
                wasReminded = doc.getBoolean("wasReminded") ?: false,
            )
        },
        toFirestoreMap = { item: WellbeingSnapshotProto, userId: String ->
            mapOf("ownerId" to userId, "timestampMillis" to item.timestampMillis,
                "dayKey" to item.dayKey, "timezone" to item.timezone,
                "lifeSatisfaction" to item.lifeSatisfaction, "workSatisfaction" to item.workSatisfaction,
                "relationshipsQuality" to item.relationshipsQuality, "mindfulnessScore" to item.mindfulnessScore,
                "purposeMeaning" to item.purposeMeaning, "gratitude" to item.gratitude,
                "autonomy" to item.autonomy, "competence" to item.competence,
                "relatedness" to item.relatedness, "loneliness" to item.loneliness,
                "notes" to item.notes, "completionTime" to item.completionTime,
                "wasReminded" to item.wasReminded)
        },
        getId = { it.id },
    )

    fun awe(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "aweEntries",
        mapper = { doc: DocumentSnapshot ->
            AweEntryProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                description = doc.getString("description") ?: "",
                context = doc.getString("context") ?: "",
                photoUrl = doc.getString("photoUrl") ?: "",
                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                dayKey = doc.getString("dayKey") ?: "",
            )
        },
        toFirestoreMap = { item: AweEntryProto, userId: String ->
            mapOf("ownerId" to userId, "description" to item.description,
                "context" to item.context, "photoUrl" to item.photoUrl,
                "timestampMillis" to item.timestampMillis, "dayKey" to item.dayKey)
        },
        getId = { it.id },
    )

    fun connection(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "connectionEntries",
        mapper = { doc: DocumentSnapshot ->
            ConnectionEntryProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                dayKey = doc.getString("dayKey") ?: "", timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                type = doc.getString("type") ?: "GRATITUDE",
                personName = doc.getString("personName") ?: "",
                description = doc.getString("description") ?: "",
                expressed = doc.getBoolean("expressed") ?: false,
            )
        },
        toFirestoreMap = { item: ConnectionEntryProto, userId: String ->
            mapOf("ownerId" to userId, "dayKey" to item.dayKey, "timestampMillis" to item.timestampMillis,
                "type" to item.type, "personName" to item.personName,
                "description" to item.description, "expressed" to item.expressed)
        },
        getId = { it.id },
    )

    fun recurringThought(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "recurringThoughts",
        mapper = { doc: DocumentSnapshot ->
            RecurringThoughtProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                theme = doc.getString("theme") ?: "",
                type = doc.getString("type") ?: "NEUTRAL",
                occurrences = doc.getLong("occurrences")?.toInt() ?: 1,
                firstSeenMillis = doc.getLong("firstSeenMillis") ?: 0L,
                lastSeenMillis = doc.getLong("lastSeenMillis") ?: 0L,
                reframedAtMillis = doc.getLong("reframedAtMillis") ?: 0L,
                reframeId = doc.getString("reframeId") ?: "",
                occurrencesPostReframe = doc.getLong("occurrencesPostReframe")?.toInt() ?: 0,
                isResolved = doc.getBoolean("isResolved") ?: false,
            )
        },
        toFirestoreMap = { item: RecurringThoughtProto, userId: String ->
            mapOf("ownerId" to userId, "theme" to item.theme, "type" to item.type,
                "occurrences" to item.occurrences, "firstSeenMillis" to item.firstSeenMillis,
                "lastSeenMillis" to item.lastSeenMillis, "reframedAtMillis" to item.reframedAtMillis,
                "reframeId" to item.reframeId, "occurrencesPostReframe" to item.occurrencesPostReframe,
                "isResolved" to item.isResolved)
        },
        getId = { it.id },
    )

    fun block(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "blocks",
        mapper = { doc: DocumentSnapshot ->
            BlockProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                description = doc.getString("description") ?: "",
                type = doc.getString("type") ?: "UNKNOWN",
                resolution = doc.getString("resolution") ?: "",
                resolutionNote = doc.getString("resolutionNote") ?: "",
                isResolved = doc.getBoolean("isResolved") ?: false,
                resolvedAtMillis = doc.getLong("resolvedAtMillis") ?: 0L,
            )
        },
        toFirestoreMap = { item: BlockProto, userId: String ->
            mapOf("ownerId" to userId, "timestampMillis" to item.timestampMillis,
                "description" to item.description, "type" to item.type,
                "resolution" to item.resolution, "resolutionNote" to item.resolutionNote,
                "isResolved" to item.isResolved, "resolvedAtMillis" to item.resolvedAtMillis)
        },
        getId = { it.id },
    )

    fun values(db: Firestore?) = GenericWellnessService(
        db = db,
        collectionName = "valuesDiscovery",
        mapper = { doc: DocumentSnapshot ->
            ValuesDiscoveryProto(
                id = doc.id, ownerId = doc.getString("ownerId") ?: "",
                completedSteps = doc.getLong("completedSteps")?.toInt() ?: 0,
                aliveMoments = (doc.get("aliveMoments") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                indignationTopics = (doc.get("indignationTopics") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                finalReflection = doc.getString("finalReflection") ?: "",
                discoveredValues = (doc.get("discoveredValues") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                confirmedValues = (doc.get("confirmedValues") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
                lastReviewMillis = doc.getLong("lastReviewMillis") ?: 0L,
                nextReviewMillis = doc.getLong("nextReviewMillis") ?: 0L,
            )
        },
        toFirestoreMap = { item: ValuesDiscoveryProto, userId: String ->
            mapOf("ownerId" to userId, "completedSteps" to item.completedSteps,
                "aliveMoments" to item.aliveMoments, "indignationTopics" to item.indignationTopics,
                "finalReflection" to item.finalReflection, "discoveredValues" to item.discoveredValues,
                "confirmedValues" to item.confirmedValues, "createdAtMillis" to item.createdAtMillis,
                "lastReviewMillis" to item.lastReviewMillis, "nextReviewMillis" to item.nextReviewMillis)
        },
        getId = { it.id },
    )
}
