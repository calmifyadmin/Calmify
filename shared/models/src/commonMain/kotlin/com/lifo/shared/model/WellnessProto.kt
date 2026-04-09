package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// --- Gratitude ---

@Serializable
data class GratitudeEntryProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val dayKey: String = "",
    @ProtoNumber(4) val timestampMillis: Long = 0L,
    @ProtoNumber(5) val timezone: String = "",
    @ProtoNumber(6) val item1: String = "",
    @ProtoNumber(7) val item2: String = "",
    @ProtoNumber(8) val item3: String = "",
    @ProtoNumber(9) val category1: String = "ALTRO",
    @ProtoNumber(10) val category2: String = "ALTRO",
    @ProtoNumber(11) val category3: String = "ALTRO",
)

// --- Energy ---

@Serializable
data class EnergyCheckInProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val dayKey: String = "",
    @ProtoNumber(4) val timestampMillis: Long = 0L,
    @ProtoNumber(5) val timezone: String = "",
    @ProtoNumber(6) val energyLevel: Int = 5,
    @ProtoNumber(7) val sleepHours: Float = 7f,
    @ProtoNumber(8) val waterGlasses: Int = 0,
    @ProtoNumber(9) val didMovement: Boolean = false,
    @ProtoNumber(10) val movementType: String = "NESSUNO",
    @ProtoNumber(11) val regularMeals: Boolean = true,
)

// --- Sleep ---

@Serializable
data class SleepLogProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val dayKey: String = "",
    @ProtoNumber(4) val timestampMillis: Long = 0L,
    @ProtoNumber(5) val timezone: String = "",
    @ProtoNumber(6) val bedtimeHour: Int = 23,
    @ProtoNumber(7) val bedtimeMinute: Int = 0,
    @ProtoNumber(8) val waketimeHour: Int = 7,
    @ProtoNumber(9) val waketimeMinute: Int = 0,
    @ProtoNumber(10) val quality: Int = 3,
    @ProtoNumber(11) val disturbances: List<String> = emptyList(),
    @ProtoNumber(12) val screenFreeLastHour: Boolean = false,
    @ProtoNumber(13) val notes: String = "",
)

// --- Meditation ---

@Serializable
data class MeditationSessionProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val timestampMillis: Long = 0L,
    @ProtoNumber(4) val type: String = "TIMER",
    @ProtoNumber(5) val breathingPattern: String = "",
    @ProtoNumber(6) val durationSeconds: Int = 300,
    @ProtoNumber(7) val completedSeconds: Int = 0,
    @ProtoNumber(8) val postNote: String = "",
)

// --- Thought Reframe ---

@Serializable
data class ThoughtReframeProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val timestampMillis: Long = 0L,
    @ProtoNumber(4) val originalThought: String = "",
    @ProtoNumber(5) val evidenceFor: String = "",
    @ProtoNumber(6) val evidenceAgainst: String = "",
    @ProtoNumber(7) val friendPerspective: String = "",
    @ProtoNumber(8) val reframedThought: String = "",
    @ProtoNumber(9) val category: String = "ALTRO",
)

// --- Movement ---

@Serializable
data class MovementLogProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val timestampMillis: Long = 0L,
    @ProtoNumber(4) val dayKey: String = "",
    @ProtoNumber(5) val movementType: String = "CAMMINATA",
    @ProtoNumber(6) val durationMinutes: Int = 20,
    @ProtoNumber(7) val feelingAfter: String = "MEGLIO",
    @ProtoNumber(8) val note: String = "",
)

// --- Wellbeing Snapshot ---

@Serializable
data class WellbeingSnapshotProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val timestampMillis: Long = 0L,
    @ProtoNumber(4) val dayKey: String = "",
    @ProtoNumber(5) val timezone: String = "",
    @ProtoNumber(6) val lifeSatisfaction: Int = 5,
    @ProtoNumber(7) val workSatisfaction: Int = 5,
    @ProtoNumber(8) val relationshipsQuality: Int = 5,
    @ProtoNumber(9) val mindfulnessScore: Int = 5,
    @ProtoNumber(10) val purposeMeaning: Int = 5,
    @ProtoNumber(11) val gratitude: Int = 5,
    @ProtoNumber(12) val autonomy: Int = 5,
    @ProtoNumber(13) val competence: Int = 5,
    @ProtoNumber(14) val relatedness: Int = 5,
    @ProtoNumber(15) val loneliness: Int = 5,
    @ProtoNumber(16) val notes: String = "",
    @ProtoNumber(17) val completionTime: Long = 0L,
    @ProtoNumber(18) val wasReminded: Boolean = false,
)

// --- Block ---

@Serializable
data class BlockProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val timestampMillis: Long = 0L,
    @ProtoNumber(4) val description: String = "",
    @ProtoNumber(5) val type: String = "UNKNOWN",
    @ProtoNumber(6) val resolution: String = "",
    @ProtoNumber(7) val resolutionNote: String = "",
    @ProtoNumber(8) val isResolved: Boolean = false,
    @ProtoNumber(9) val resolvedAtMillis: Long = 0L,
)

// --- Awe Entry ---

@Serializable
data class AweEntryProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val description: String = "",
    @ProtoNumber(4) val context: String = "",
    @ProtoNumber(5) val photoUrl: String = "",
    @ProtoNumber(6) val timestampMillis: Long = 0L,
    @ProtoNumber(7) val dayKey: String = "",
)

// --- Connection ---

@Serializable
data class ConnectionEntryProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val dayKey: String = "",
    @ProtoNumber(4) val timestampMillis: Long = 0L,
    @ProtoNumber(5) val type: String = "GRATITUDE",
    @ProtoNumber(6) val personName: String = "",
    @ProtoNumber(7) val description: String = "",
    @ProtoNumber(8) val expressed: Boolean = false,
)

@Serializable
data class RelationshipReflectionProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val monthKey: String = "",
    @ProtoNumber(4) val timestampMillis: Long = 0L,
    @ProtoNumber(5) val nurturingRelationships: List<String> = emptyList(),
    @ProtoNumber(6) val drainingRelationships: List<String> = emptyList(),
    @ProtoNumber(7) val intention: String = "",
)

// --- Recurring Thought ---

@Serializable
data class RecurringThoughtProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val theme: String = "",
    @ProtoNumber(4) val type: String = "NEUTRAL",
    @ProtoNumber(5) val occurrences: Int = 1,
    @ProtoNumber(6) val firstSeenMillis: Long = 0L,
    @ProtoNumber(7) val lastSeenMillis: Long = 0L,
    @ProtoNumber(8) val reframedAtMillis: Long = 0L,
    @ProtoNumber(9) val reframeId: String = "",
    @ProtoNumber(10) val occurrencesPostReframe: Int = 0,
    @ProtoNumber(11) val isResolved: Boolean = false,
)

// --- Values Discovery ---

@Serializable
data class ValuesDiscoveryProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val completedSteps: Int = 0,
    @ProtoNumber(4) val aliveMoments: List<String> = emptyList(),
    @ProtoNumber(5) val indignationTopics: List<String> = emptyList(),
    @ProtoNumber(6) val finalReflection: String = "",
    @ProtoNumber(7) val discoveredValues: List<String> = emptyList(),
    @ProtoNumber(8) val confirmedValues: List<String> = emptyList(),
    @ProtoNumber(9) val createdAtMillis: Long = 0L,
    @ProtoNumber(10) val lastReviewMillis: Long = 0L,
    @ProtoNumber(11) val nextReviewMillis: Long = 0L,
)

// --- Ikigai ---

@Serializable
data class IkigaiExplorationProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val passionItems: List<String> = emptyList(),
    @ProtoNumber(4) val talentItems: List<String> = emptyList(),
    @ProtoNumber(5) val missionItems: List<String> = emptyList(),
    @ProtoNumber(6) val professionItems: List<String> = emptyList(),
    @ProtoNumber(7) val aiInsight: String = "",
    @ProtoNumber(8) val createdAtMillis: Long = 0L,
    @ProtoNumber(9) val updatedAtMillis: Long = 0L,
)

// --- Environment Design ---

@Serializable
data class EnvironmentChecklistProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val items: List<ChecklistItemProto> = emptyList(),
    @ProtoNumber(4) val morningRoutine: List<RoutineStepProto> = emptyList(),
    @ProtoNumber(5) val eveningRoutine: List<RoutineStepProto> = emptyList(),
    @ProtoNumber(6) val detoxTimerMinutes: Int = 60,
)

@Serializable
data class ChecklistItemProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val text: String = "",
    @ProtoNumber(3) val isCompleted: Boolean = false,
    @ProtoNumber(4) val category: String = "GENERALE",
)

@Serializable
data class RoutineStepProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val text: String = "",
    @ProtoNumber(3) val durationMinutes: Int = 5,
    @ProtoNumber(4) val isCompleted: Boolean = false,
)
