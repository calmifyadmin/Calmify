package com.lifo.util.mapper

import com.lifo.shared.model.*
import com.lifo.util.model.*

// --- Habit ---

fun Habit.toProto(): HabitProto = HabitProto(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    category = category.name,
    anchorHabit = anchorHabit ?: "",
    minimumAction = minimumAction,
    targetFrequency = targetFrequency.name,
    reminderTime = reminderTime ?: "",
    createdAtMillis = createdAtMillis,
    isActive = isActive,
)

fun HabitProto.toDomain(): Habit = Habit(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    category = HabitCategory.entries.find { it.name == category } ?: HabitCategory.CRESCITA,
    anchorHabit = anchorHabit.takeIf { it.isNotEmpty() },
    minimumAction = minimumAction,
    targetFrequency = HabitFrequency.entries.find { it.name == targetFrequency } ?: HabitFrequency.DAILY,
    reminderTime = reminderTime.takeIf { it.isNotEmpty() },
    createdAtMillis = createdAtMillis,
    isActive = isActive,
)

fun HabitCompletion.toProto(): HabitCompletionProto = HabitCompletionProto(
    id = id,
    habitId = habitId,
    ownerId = ownerId,
    completedAtMillis = completedAtMillis,
    dayKey = dayKey,
    note = note ?: "",
)

fun HabitCompletionProto.toDomain(): HabitCompletion = HabitCompletion(
    id = id,
    habitId = habitId,
    ownerId = ownerId,
    completedAtMillis = completedAtMillis,
    dayKey = dayKey,
    note = note.takeIf { it.isNotEmpty() },
)

// --- GratitudeEntry ---

fun GratitudeEntry.toProto(): GratitudeEntryProto = GratitudeEntryProto(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    timezone = timezone,
    item1 = item1,
    item2 = item2,
    item3 = item3,
    category1 = category1.name,
    category2 = category2.name,
    category3 = category3.name,
)

fun GratitudeEntryProto.toDomain(): GratitudeEntry = GratitudeEntry(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    timezone = timezone,
    item1 = item1,
    item2 = item2,
    item3 = item3,
    category1 = GratitudeCategory.entries.find { it.name == category1 } ?: GratitudeCategory.ALTRO,
    category2 = GratitudeCategory.entries.find { it.name == category2 } ?: GratitudeCategory.ALTRO,
    category3 = GratitudeCategory.entries.find { it.name == category3 } ?: GratitudeCategory.ALTRO,
)

// --- EnergyCheckIn ---

fun EnergyCheckIn.toProto(): EnergyCheckInProto = EnergyCheckInProto(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    timezone = timezone,
    energyLevel = energyLevel,
    sleepHours = sleepHours,
    waterGlasses = waterGlasses,
    didMovement = didMovement,
    movementType = movementType.name,
    regularMeals = regularMeals,
)

fun EnergyCheckInProto.toDomain(): EnergyCheckIn = EnergyCheckIn(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    timezone = timezone,
    energyLevel = energyLevel,
    sleepHours = sleepHours,
    waterGlasses = waterGlasses,
    didMovement = didMovement,
    movementType = MovementType.entries.find { it.name == movementType } ?: MovementType.NESSUNO,
    regularMeals = regularMeals,
)

// --- SleepLog ---

fun SleepLog.toProto(): SleepLogProto = SleepLogProto(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    timezone = timezone,
    bedtimeHour = bedtimeHour,
    bedtimeMinute = bedtimeMinute,
    waketimeHour = waketimeHour,
    waketimeMinute = waketimeMinute,
    quality = quality,
    disturbances = disturbances.map { it.name },
    screenFreeLastHour = screenFreeLastHour,
    notes = notes,
)

fun SleepLogProto.toDomain(): SleepLog = SleepLog(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    timezone = timezone,
    bedtimeHour = bedtimeHour,
    bedtimeMinute = bedtimeMinute,
    waketimeHour = waketimeHour,
    waketimeMinute = waketimeMinute,
    quality = quality,
    disturbances = disturbances.mapNotNull { name ->
        SleepDisturbance.entries.find { it.name == name }
    },
    screenFreeLastHour = screenFreeLastHour,
    notes = notes,
)

// --- MeditationSession ---

fun MeditationSession.toProto(): MeditationSessionProto = MeditationSessionProto(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    type = type.name,
    breathingPattern = breathingPattern?.name ?: "",
    durationSeconds = durationSeconds,
    completedSeconds = completedSeconds,
    postNote = postNote,
)

fun MeditationSessionProto.toDomain(): MeditationSession = MeditationSession(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    type = MeditationType.entries.find { it.name == type } ?: MeditationType.TIMER,
    breathingPattern = breathingPattern.takeIf { it.isNotEmpty() }?.let { name ->
        BreathingPattern.entries.find { it.name == name }
    },
    durationSeconds = durationSeconds,
    completedSeconds = completedSeconds,
    postNote = postNote,
)

// --- ThoughtReframe ---

fun ThoughtReframe.toProto(): ThoughtReframeProto = ThoughtReframeProto(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    originalThought = originalThought,
    evidenceFor = evidenceFor,
    evidenceAgainst = evidenceAgainst,
    friendPerspective = friendPerspective,
    reframedThought = reframedThought,
    category = category.name,
)

fun ThoughtReframeProto.toDomain(): ThoughtReframe = ThoughtReframe(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    originalThought = originalThought,
    evidenceFor = evidenceFor,
    evidenceAgainst = evidenceAgainst,
    friendPerspective = friendPerspective,
    reframedThought = reframedThought,
    category = ThoughtCategory.entries.find { it.name == category } ?: ThoughtCategory.ALTRO,
)

// --- MovementLog ---

fun MovementLog.toProto(): MovementLogProto = MovementLogProto(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    dayKey = dayKey,
    movementType = movementType.name,
    durationMinutes = durationMinutes,
    feelingAfter = feelingAfter.name,
    note = note,
)

fun MovementLogProto.toDomain(): MovementLog = MovementLog(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    dayKey = dayKey,
    movementType = MovementType.entries.find { it.name == movementType } ?: MovementType.CAMMINATA,
    durationMinutes = durationMinutes,
    feelingAfter = PostMovementFeeling.entries.find { it.name == feelingAfter } ?: PostMovementFeeling.MEGLIO,
    note = note,
)

// --- WellbeingSnapshot ---

fun WellbeingSnapshot.toProto(): WellbeingSnapshotProto = WellbeingSnapshotProto(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    dayKey = dayKey,
    timezone = timezone,
    lifeSatisfaction = lifeSatisfaction,
    workSatisfaction = workSatisfaction,
    relationshipsQuality = relationshipsQuality,
    mindfulnessScore = mindfulnessScore,
    purposeMeaning = purposeMeaning,
    gratitude = gratitude,
    autonomy = autonomy,
    competence = competence,
    relatedness = relatedness,
    loneliness = loneliness,
    notes = notes,
    completionTime = completionTime,
    wasReminded = wasReminded,
)

fun WellbeingSnapshotProto.toDomain(): WellbeingSnapshot = WellbeingSnapshot(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    dayKey = dayKey,
    timezone = timezone,
    lifeSatisfaction = lifeSatisfaction,
    workSatisfaction = workSatisfaction,
    relationshipsQuality = relationshipsQuality,
    mindfulnessScore = mindfulnessScore,
    purposeMeaning = purposeMeaning,
    gratitude = gratitude,
    autonomy = autonomy,
    competence = competence,
    relatedness = relatedness,
    loneliness = loneliness,
    notes = notes,
    completionTime = completionTime,
    wasReminded = wasReminded,
)

// --- Block ---

fun Block.toProto(): BlockProto = BlockProto(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    description = description,
    type = type.name,
    resolution = resolution?.name ?: "",
    resolutionNote = resolutionNote,
    isResolved = isResolved,
    resolvedAtMillis = resolvedAtMillis ?: 0L,
)

fun BlockProto.toDomain(): Block = Block(
    id = id,
    ownerId = ownerId,
    timestampMillis = timestampMillis,
    description = description,
    type = BlockType.entries.find { it.name == type } ?: BlockType.UNKNOWN,
    resolution = resolution.takeIf { it.isNotEmpty() }?.let { name ->
        BlockResolution.entries.find { it.name == name }
    },
    resolutionNote = resolutionNote,
    isResolved = isResolved,
    resolvedAtMillis = resolvedAtMillis.takeIf { it != 0L },
)

// --- AweEntry ---

fun AweEntry.toProto(): AweEntryProto = AweEntryProto(
    id = id,
    ownerId = ownerId,
    description = description,
    context = context,
    photoUrl = photoUrl ?: "",
    timestampMillis = timestampMillis,
    dayKey = dayKey,
)

fun AweEntryProto.toDomain(): AweEntry = AweEntry(
    id = id,
    ownerId = ownerId,
    description = description,
    context = context,
    photoUrl = photoUrl.takeIf { it.isNotEmpty() },
    timestampMillis = timestampMillis,
    dayKey = dayKey,
)

// --- ConnectionEntry ---

fun ConnectionEntry.toProto(): ConnectionEntryProto = ConnectionEntryProto(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    type = type.name,
    personName = personName,
    description = description,
    expressed = expressed,
)

fun ConnectionEntryProto.toDomain(): ConnectionEntry = ConnectionEntry(
    id = id,
    ownerId = ownerId,
    dayKey = dayKey,
    timestampMillis = timestampMillis,
    type = ConnectionType.entries.find { it.name == type } ?: ConnectionType.GRATITUDE,
    personName = personName,
    description = description,
    expressed = expressed,
)

// --- RecurringThought ---

fun RecurringThought.toProto(): RecurringThoughtProto = RecurringThoughtProto(
    id = id,
    ownerId = ownerId,
    theme = theme,
    type = type.name,
    occurrences = occurrences,
    firstSeenMillis = firstSeenMillis,
    lastSeenMillis = lastSeenMillis,
    reframedAtMillis = reframedAtMillis ?: 0L,
    reframeId = reframeId ?: "",
    occurrencesPostReframe = occurrencesPostReframe,
    isResolved = isResolved,
)

fun RecurringThoughtProto.toDomain(): RecurringThought = RecurringThought(
    id = id,
    ownerId = ownerId,
    theme = theme,
    type = ThoughtType.entries.find { it.name == type } ?: ThoughtType.NEUTRAL,
    occurrences = occurrences,
    firstSeenMillis = firstSeenMillis,
    lastSeenMillis = lastSeenMillis,
    reframedAtMillis = reframedAtMillis.takeIf { it != 0L },
    reframeId = reframeId.takeIf { it.isNotEmpty() },
    occurrencesPostReframe = occurrencesPostReframe,
    isResolved = isResolved,
)

// --- ValuesDiscovery ---

fun ValuesDiscovery.toProto(): ValuesDiscoveryProto = ValuesDiscoveryProto(
    id = id,
    ownerId = ownerId,
    completedSteps = completedSteps,
    aliveMoments = aliveMoments,
    indignationTopics = indignationTopics,
    finalReflection = finalReflection,
    discoveredValues = discoveredValues,
    confirmedValues = confirmedValues,
    createdAtMillis = createdAtMillis,
    lastReviewMillis = lastReviewMillis ?: 0L,
    nextReviewMillis = nextReviewMillis ?: 0L,
)

fun ValuesDiscoveryProto.toDomain(): ValuesDiscovery = ValuesDiscovery(
    id = id,
    ownerId = ownerId,
    completedSteps = completedSteps,
    aliveMoments = aliveMoments,
    indignationTopics = indignationTopics,
    finalReflection = finalReflection,
    discoveredValues = discoveredValues,
    confirmedValues = confirmedValues,
    createdAtMillis = createdAtMillis,
    lastReviewMillis = lastReviewMillis.takeIf { it != 0L },
    nextReviewMillis = nextReviewMillis.takeIf { it != 0L },
)

// --- IkigaiExploration ---

fun IkigaiExploration.toProto(): IkigaiExplorationProto = IkigaiExplorationProto(
    id = id,
    ownerId = ownerId,
    passionItems = passionItems,
    talentItems = talentItems,
    missionItems = missionItems,
    professionItems = professionItems,
    aiInsight = aiInsight,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun IkigaiExplorationProto.toDomain(): IkigaiExploration = IkigaiExploration(
    id = id,
    ownerId = ownerId,
    passionItems = passionItems,
    talentItems = talentItems,
    missionItems = missionItems,
    professionItems = professionItems,
    aiInsight = aiInsight,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
