package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCategory
import com.lifo.util.model.HabitCompletion
import com.lifo.util.model.HabitFrequency
import com.lifo.util.model.RequestState
import com.lifo.util.repository.HabitRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FirestoreHabitRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : HabitRepository {

    private val habitsCollection = firestore.collection("habits")
    private val completionsCollection = firestore.collection("habit_completions")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    // ── Habits CRUD ──

    override suspend fun upsertHabit(habit: Habit): RequestState<String> {
        return try {
            val withOwner = if (habit.ownerId.isEmpty()) habit.copy(ownerId = currentUserId) else habit
            habitsCollection.document(withOwner.id).set(withOwner.toFirestoreMap()).await()
            RequestState.Success(withOwner.id)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getActiveHabits(): Flow<RequestState<List<Habit>>> = callbackFlow {
        trySend(RequestState.Loading)

        val registration = habitsCollection
            .whereEqualTo("ownerId", currentUserId)
            .whereEqualTo("isActive", true)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                val habits = snapshot?.documents?.mapNotNull { it.toHabit() } ?: emptyList()
                trySend(RequestState.Success(habits))
            }

        awaitClose { registration.remove() }
    }

    override suspend fun deleteHabit(id: String): RequestState<Boolean> {
        return try {
            habitsCollection.document(id).update("isActive", false).await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    // ── Completions ──

    override suspend fun toggleCompletion(habitId: String, dayKey: String): RequestState<Boolean> {
        return try {
            val docId = "${currentUserId}_${habitId}_$dayKey"
            val docRef = completionsCollection.document(docId)
            val existing = docRef.get().await()

            if (existing.exists()) {
                docRef.delete().await()
                RequestState.Success(false) // uncompleted
            } else {
                val completion = HabitCompletion(
                    id = docId,
                    habitId = habitId,
                    ownerId = currentUserId,
                    completedAtMillis = Clock.System.now().toEpochMilliseconds(),
                    dayKey = dayKey,
                )
                docRef.set(completion.toFirestoreMap()).await()
                RequestState.Success(true) // completed
            }
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getCompletionsForDay(dayKey: String): Flow<RequestState<List<HabitCompletion>>> = callbackFlow {
        trySend(RequestState.Loading)

        val registration = completionsCollection
            .whereEqualTo("ownerId", currentUserId)
            .whereEqualTo("dayKey", dayKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                val completions = snapshot?.documents?.mapNotNull { it.toHabitCompletion() } ?: emptyList()
                trySend(RequestState.Success(completions))
            }

        awaitClose { registration.remove() }
    }

    override fun getCompletionsForHabit(habitId: String, limit: Int): Flow<RequestState<List<HabitCompletion>>> = callbackFlow {
        trySend(RequestState.Loading)

        val registration = completionsCollection
            .whereEqualTo("ownerId", currentUserId)
            .whereEqualTo("habitId", habitId)
            .orderBy("completedAtMillis", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                val completions = snapshot?.documents?.mapNotNull { it.toHabitCompletion() } ?: emptyList()
                trySend(RequestState.Success(completions))
            }

        awaitClose { registration.remove() }
    }
}

// ── Firestore Mapping: Habit ──

private fun Habit.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "name" to name,
    "description" to description,
    "category" to category.name,
    "anchorHabit" to anchorHabit,
    "minimumAction" to minimumAction,
    "targetFrequency" to targetFrequency.name,
    "reminderTime" to reminderTime,
    "createdAtMillis" to createdAtMillis,
    "isActive" to isActive,
)

private fun DocumentSnapshot.toHabit(): Habit? {
    if (!exists()) return null
    return try {
        Habit(
            id = id,
            ownerId = getString("ownerId") ?: "",
            name = getString("name") ?: "",
            description = getString("description") ?: "",
            category = getString("category")?.let { safeEnum<HabitCategory>(it) } ?: HabitCategory.CRESCITA,
            anchorHabit = getString("anchorHabit"),
            minimumAction = getString("minimumAction") ?: "",
            targetFrequency = getString("targetFrequency")?.let { safeEnum<HabitFrequency>(it) } ?: HabitFrequency.DAILY,
            reminderTime = getString("reminderTime"),
            createdAtMillis = getLong("createdAtMillis") ?: 0L,
            isActive = getBoolean("isActive") ?: true,
        )
    } catch (e: Exception) {
        null
    }
}

// ── Firestore Mapping: HabitCompletion ──

private fun HabitCompletion.toFirestoreMap(): Map<String, Any?> = mapOf(
    "habitId" to habitId,
    "ownerId" to ownerId,
    "completedAtMillis" to completedAtMillis,
    "dayKey" to dayKey,
    "note" to note,
)

private fun DocumentSnapshot.toHabitCompletion(): HabitCompletion? {
    if (!exists()) return null
    return try {
        HabitCompletion(
            id = id,
            habitId = getString("habitId") ?: "",
            ownerId = getString("ownerId") ?: "",
            completedAtMillis = getLong("completedAtMillis") ?: 0L,
            dayKey = getString("dayKey") ?: "",
            note = getString("note"),
        )
    } catch (e: Exception) {
        null
    }
}

private inline fun <reified T : Enum<T>> safeEnum(name: String): T? =
    try { enumValueOf<T>(name) } catch (_: Exception) { null }
