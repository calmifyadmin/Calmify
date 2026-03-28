package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.IkigaiExploration
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.IkigaiRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class IkigaiViewModel(
    private val repository: IkigaiRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<IkigaiContract.Intent, IkigaiContract.State, IkigaiContract.Effect>(
    IkigaiContract.State()
) {

    init {
        onIntent(IkigaiContract.Intent.Load)
    }

    override fun handleIntent(intent: IkigaiContract.Intent) {
        when (intent) {
            is IkigaiContract.Intent.Load -> load()
            is IkigaiContract.Intent.SelectCircle -> updateState { copy(selectedCircle = intent.circle, currentInput = "") }
            is IkigaiContract.Intent.UpdateInput -> updateState { copy(currentInput = intent.text) }
            is IkigaiContract.Intent.AddItem -> addItem()
            is IkigaiContract.Intent.RemoveItem -> removeItem(intent.circle, intent.index)
            is IkigaiContract.Intent.Save -> save()
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getExploration(userId).collect { exploration ->
                updateState { copy(exploration = exploration, isLoading = false) }
            }
        }
    }

    private fun addItem() {
        val input = state.value.currentInput.trim()
        if (input.isBlank()) return
        val userId = authProvider.currentUserId ?: return
        val current = state.value.exploration ?: IkigaiExploration(
            ownerId = userId,
            createdAtMillis = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = when (state.value.selectedCircle) {
            IkigaiContract.Circle.PASSION -> current.copy(passionItems = current.passionItems + input)
            IkigaiContract.Circle.TALENT -> current.copy(talentItems = current.talentItems + input)
            IkigaiContract.Circle.MISSION -> current.copy(missionItems = current.missionItems + input)
            IkigaiContract.Circle.PROFESSION -> current.copy(professionItems = current.professionItems + input)
        }
        updateState { copy(exploration = updated, currentInput = "") }
        scope.launch { try { repository.saveExploration(updated) } catch (_: Exception) { } }
    }

    private fun removeItem(circle: IkigaiContract.Circle, index: Int) {
        val current = state.value.exploration ?: return
        val updated = when (circle) {
            IkigaiContract.Circle.PASSION -> current.copy(passionItems = current.passionItems.filterIndexed { i, _ -> i != index })
            IkigaiContract.Circle.TALENT -> current.copy(talentItems = current.talentItems.filterIndexed { i, _ -> i != index })
            IkigaiContract.Circle.MISSION -> current.copy(missionItems = current.missionItems.filterIndexed { i, _ -> i != index })
            IkigaiContract.Circle.PROFESSION -> current.copy(professionItems = current.professionItems.filterIndexed { i, _ -> i != index })
        }
        updateState { copy(exploration = updated) }
        scope.launch { try { repository.saveExploration(updated) } catch (_: Exception) { } }
    }

    private fun save() {
        val current = state.value.exploration ?: return
        scope.launch {
            updateState { copy(isSaving = true) }
            repository.saveExploration(current)
            updateState { copy(isSaving = false) }
            sendEffect(IkigaiContract.Effect.Saved)
        }
    }
}
