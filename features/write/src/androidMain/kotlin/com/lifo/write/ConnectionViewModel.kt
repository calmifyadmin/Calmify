package com.lifo.write

import com.lifo.util.model.ConnectionEntry
import com.lifo.util.model.ConnectionType
import com.lifo.util.model.RelationshipReflection
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.auth.AuthProvider
import com.lifo.util.repository.ConnectionRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ConnectionViewModel(
    private val connectionRepository: ConnectionRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<ConnectionContract.Intent, ConnectionContract.State, ConnectionContract.Effect>(
    ConnectionContract.State()
) {

    override fun handleIntent(intent: ConnectionContract.Intent) {
        when (intent) {
            is ConnectionContract.Intent.Load -> loadEntries()
            is ConnectionContract.Intent.SelectTab -> updateState { copy(selectedTab = intent.index) }
            is ConnectionContract.Intent.UpdatePerson -> updateState { copy(personName = intent.name) }
            is ConnectionContract.Intent.UpdateDescription -> updateState { copy(description = intent.text) }
            is ConnectionContract.Intent.SelectType -> updateState { copy(selectedType = intent.type) }
            is ConnectionContract.Intent.ToggleExpressed -> updateState { copy(expressed = intent.value) }
            is ConnectionContract.Intent.SaveEntry -> saveEntry()
            is ConnectionContract.Intent.DeleteEntry -> deleteEntry(intent.id)
            is ConnectionContract.Intent.ShowReflection -> showReflection()
            is ConnectionContract.Intent.DismissReflection -> updateState { copy(showReflection = false) }
            is ConnectionContract.Intent.AddNurturing -> updateState { copy(nurturingInput = nurturingInput + intent.name) }
            is ConnectionContract.Intent.RemoveNurturing -> updateState { copy(nurturingInput = nurturingInput.toMutableList().also { it.removeAt(intent.index) }) }
            is ConnectionContract.Intent.AddDraining -> updateState { copy(drainingInput = drainingInput + intent.name) }
            is ConnectionContract.Intent.RemoveDraining -> updateState { copy(drainingInput = drainingInput.toMutableList().also { it.removeAt(intent.index) }) }
            is ConnectionContract.Intent.UpdateIntention -> updateState { copy(intentionInput = intent.text) }
            is ConnectionContract.Intent.SaveReflection -> saveReflection()
        }
    }

    private fun loadEntries() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            connectionRepository.getEntries(userId).collectLatest { entries ->
                val now = Clock.System.now()
                val tz = TimeZone.currentSystemDefault()
                val today = now.toLocalDateTime(tz).date
                val weekStart = today.toEpochDays() - today.dayOfWeek.ordinal
                val weekEntries = entries.filter { entry ->
                    val entryDate = try {
                        kotlinx.datetime.LocalDate.parse(entry.dayKey)
                    } catch (_: Exception) { return@filter false }
                    entryDate.toEpochDays() >= weekStart
                }
                updateState {
                    copy(
                        entries = entries,
                        weeklyGratitudeCount = weekEntries.count { it.type == ConnectionType.GRATITUDE },
                        weeklyServiceCount = weekEntries.count { it.type == ConnectionType.SERVICE },
                        weeklyQualityTimeCount = weekEntries.count { it.type == ConnectionType.QUALITY_TIME },
                    )
                }
            }
        }
    }

    private fun saveEntry() {
        val userId = authProvider.currentUserId ?: return
        val state = state.value
        if (state.personName.isBlank() || state.description.isBlank()) return
        val entry = ConnectionEntry.create(userId, state.selectedType).copy(
            personName = state.personName,
            description = state.description,
            expressed = state.expressed,
        )
        scope.launch {
            try {
                connectionRepository.saveEntry(entry)
                updateState { copy(personName = "", description = "", expressed = false) }
                sendEffect(ConnectionContract.Effect.EntrySaved)
            } catch (_: Exception) { }
        }
    }

    private fun deleteEntry(id: String) {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            try { connectionRepository.deleteEntry(id, userId) } catch (_: Exception) { }
        }
    }

    private fun showReflection() {
        val userId = authProvider.currentUserId ?: return
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val localDate = now.toLocalDateTime(tz).date
        val monthKey = "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}"
        updateState { copy(showReflection = true) }
        scope.launch {
            connectionRepository.getReflection(userId, monthKey).collectLatest { reflection ->
                updateState {
                    copy(
                        reflection = reflection,
                        nurturingInput = reflection?.nurturingRelationships ?: emptyList(),
                        drainingInput = reflection?.drainingRelationships ?: emptyList(),
                        intentionInput = reflection?.intention ?: "",
                    )
                }
            }
        }
    }

    private fun saveReflection() {
        val userId = authProvider.currentUserId ?: return
        val state = state.value
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val localDate = now.toLocalDateTime(tz).date
        val monthKey = "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}"
        val reflection = RelationshipReflection(
            id = "${userId}_$monthKey",
            ownerId = userId,
            monthKey = monthKey,
            timestampMillis = now.toEpochMilliseconds(),
            nurturingRelationships = state.nurturingInput,
            drainingRelationships = state.drainingInput,
            intention = state.intentionInput,
        )
        scope.launch {
            try {
                connectionRepository.saveReflection(reflection)
                updateState { copy(showReflection = false) }
                sendEffect(ConnectionContract.Effect.ReflectionSaved)
            } catch (_: Exception) { }
        }
    }
}
