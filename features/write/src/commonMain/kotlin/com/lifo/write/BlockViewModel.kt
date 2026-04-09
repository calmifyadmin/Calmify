package com.lifo.write

import com.lifo.util.analysis.BlockDetector
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.Block
import com.lifo.util.model.BlockType
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.BlockRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class BlockViewModel(
    private val repository: BlockRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<BlockContract.Intent, BlockContract.State, BlockContract.Effect>(
    BlockContract.State()
) {

    init {
        onIntent(BlockContract.Intent.LoadBlocks)
    }

    override fun handleIntent(intent: BlockContract.Intent) {
        when (intent) {
            is BlockContract.Intent.LoadBlocks -> loadBlocks()
            is BlockContract.Intent.SetDescription -> updateState { copy(description = intent.text) }

            is BlockContract.Intent.AnalyzeBlock -> analyzeBlock()
            is BlockContract.Intent.SelectBlockType -> updateState { copy(selectedType = intent.type) }
            is BlockContract.Intent.SelectResolution -> updateState { copy(selectedResolution = intent.resolution) }
            is BlockContract.Intent.SetResolutionNote -> updateState { copy(resolutionNote = intent.note) }
            is BlockContract.Intent.SaveBlock -> saveBlock()
            is BlockContract.Intent.ResolveBlock -> resolveBlock(intent.blockId)
            is BlockContract.Intent.DeleteBlock -> deleteBlock(intent.blockId)

            is BlockContract.Intent.NavigateToBrainDump -> sendEffect(BlockContract.Effect.GoToBrainDump)
            is BlockContract.Intent.NavigateToReframing -> sendEffect(BlockContract.Effect.GoToReframing)
            is BlockContract.Intent.NavigateToMeditation -> sendEffect(BlockContract.Effect.GoToMeditation)
        }
    }

    private fun analyzeBlock() {
        val text = state.value.description
        val detectedType = BlockDetector.detectBlockType(text) ?: BlockType.UNKNOWN
        updateState {
            copy(
                detectedType = detectedType,
                selectedType = detectedType,
                step = BlockContract.Step.DIAGNOSIS,
            )
        }
    }

    private fun saveBlock() {
        val userId = authProvider.currentUserId ?: return
        val s = state.value
        scope.launch {
            updateState { copy(isLoading = true) }
            @OptIn(ExperimentalUuidApi::class)
            val block = Block(
                id = Uuid.random().toString(),
                ownerId = userId,
                timestampMillis = Clock.System.now().toEpochMilliseconds(),
                description = s.description,
                type = s.selectedType,
            )
            repository.saveBlock(block)
                .onSuccess {
                    sendEffect(BlockContract.Effect.BlockSaved)
                    updateState { copy(step = BlockContract.Step.ACTION) }
                }
                .onFailure { sendEffect(BlockContract.Effect.Error(it.message ?: "Errore")) }
            updateState { copy(isLoading = false) }
        }
    }

    private fun resolveBlock(blockId: String) {
        val s = state.value
        scope.launch {
            repository.resolveBlock(
                blockId = blockId,
                resolution = s.selectedResolution?.name ?: "",
                note = s.resolutionNote,
            ).onSuccess {
                sendEffect(BlockContract.Effect.BlockResolved)
            }.onFailure {
                sendEffect(BlockContract.Effect.Error(it.message ?: "Errore"))
            }
        }
    }

    private fun deleteBlock(blockId: String) {
        scope.launch {
            try { repository.deleteBlock(blockId) } catch (_: Exception) { }
        }
    }

    private fun loadBlocks() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getActiveBlocks(userId).collect { blocks ->
                updateState { copy(activeBlocks = blocks) }
            }
        }
        scope.launch {
            repository.getResolvedBlocks(userId).collect { blocks ->
                updateState { copy(resolvedBlocks = blocks) }
            }
        }
    }
}
