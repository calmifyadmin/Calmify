package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.IkigaiExploration
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.GardenRepository
import com.lifo.util.repository.IkigaiRepository
import kotlinx.coroutines.launch

class GardenViewModel(
    private val repository: GardenRepository,
    private val ikigaiRepository: IkigaiRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<GardenContract.Intent, GardenContract.State, GardenContract.Effect>(
    GardenContract.State()
) {

    init {
        onIntent(GardenContract.Intent.Load)
    }

    override fun handleIntent(intent: GardenContract.Intent) {
        when (intent) {
            is GardenContract.Intent.Load -> load()
            is GardenContract.Intent.ToggleExpand -> toggleExpand(intent.activityId)
            is GardenContract.Intent.ToggleFavorite -> toggleFavorite(intent.activityId)
            is GardenContract.Intent.MarkExplored -> markExplored(intent.activityId)
            is GardenContract.Intent.SelectIkigaiCircle -> updateState {
                copy(selectedIkigaiCircle = if (selectedIkigaiCircle == intent.circle) null else intent.circle)
            }
            is GardenContract.Intent.UpdateIkigaiInput -> updateState { copy(ikigaiInput = intent.text) }
            is GardenContract.Intent.AddIkigaiItem -> addIkigaiItem()
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getExploredActivities(userId).collect { explored ->
                updateState { copy(exploredIds = explored, isLoading = false) }
            }
        }
        scope.launch {
            repository.getFavorites(userId).collect { favorites ->
                updateState { copy(favoriteIds = favorites) }
            }
        }
        scope.launch {
            ikigaiRepository.getExploration(userId).collect { exploration ->
                updateState { copy(ikigaiExploration = exploration) }
            }
        }
    }

    private fun toggleExpand(activityId: String) {
        updateState {
            copy(expandedId = if (expandedId == activityId) null else activityId)
        }
    }

    private fun toggleFavorite(activityId: String) {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            try {
                repository.toggleFavorite(userId, activityId)
            } catch (_: Exception) { }
        }
    }

    private fun markExplored(activityId: String) {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            try {
                repository.markExplored(userId, activityId)
            } catch (_: Exception) { }
        }
    }

    private fun addIkigaiItem() {
        val input = state.value.ikigaiInput.trim()
        if (input.isBlank()) return
        val selectedCircle = state.value.selectedIkigaiCircle ?: return
        val userId = authProvider.currentUserId ?: return

        val current = state.value.ikigaiExploration ?: IkigaiExploration(ownerId = userId)
        val updated = when (selectedCircle) {
            GardenContract.IkigaiCircle.PASSION -> current.copy(passionItems = current.passionItems + input)
            GardenContract.IkigaiCircle.TALENT -> current.copy(talentItems = current.talentItems + input)
            GardenContract.IkigaiCircle.MISSION -> current.copy(missionItems = current.missionItems + input)
            GardenContract.IkigaiCircle.PROFESSION -> current.copy(professionItems = current.professionItems + input)
        }

        updateState { copy(ikigaiInput = "") }
        scope.launch { ikigaiRepository.saveExploration(updated) }
    }
}
