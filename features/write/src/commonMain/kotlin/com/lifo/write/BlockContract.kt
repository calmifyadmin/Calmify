package com.lifo.write

import com.lifo.util.model.Block
import com.lifo.util.model.BlockResolution
import com.lifo.util.model.BlockType
import com.lifo.util.mvi.MviContract

object BlockContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadBlocks : Intent
        data class SetDescription(val text: String) : Intent
        data object AnalyzeBlock : Intent
        data class SelectBlockType(val type: BlockType) : Intent
        data class SelectResolution(val resolution: BlockResolution) : Intent
        data class SetResolutionNote(val note: String) : Intent
        data object SaveBlock : Intent
        data class ResolveBlock(val blockId: String) : Intent
        data class DeleteBlock(val blockId: String) : Intent

        // Navigate to suggested tool
        data object NavigateToBrainDump : Intent
        data object NavigateToReframing : Intent
        data object NavigateToMeditation : Intent
    }

    data class State(
        val step: Step = Step.DESCRIBE,
        val description: String = "",
        val detectedType: BlockType? = null,
        val selectedType: BlockType = BlockType.UNKNOWN,
        val selectedResolution: BlockResolution? = null,
        val resolutionNote: String = "",
        val activeBlocks: List<Block> = emptyList(),
        val resolvedBlocks: List<Block> = emptyList(),
        val isLoading: Boolean = false,
    ) : MviContract.State

    enum class Step { DESCRIBE, DIAGNOSIS, ACTION, HISTORY }

    sealed interface Effect : MviContract.Effect {
        data object BlockSaved : Effect
        data object BlockResolved : Effect
        data class Error(val message: String) : Effect
        data object GoToBrainDump : Effect
        data object GoToReframing : Effect
        data object GoToMeditation : Effect
    }
}
