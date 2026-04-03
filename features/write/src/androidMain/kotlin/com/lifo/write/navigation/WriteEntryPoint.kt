package com.lifo.write.navigation

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.delay
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.write.BrainDumpScreen
import com.lifo.write.DiaryDetailScreen
import com.lifo.write.EnergyCheckInScreen
import com.lifo.write.EnergyContract
import com.lifo.write.EnergyViewModel
import com.lifo.write.ReframeContract
import com.lifo.write.ReframeScreen
import com.lifo.write.ReframeViewModel
import com.lifo.write.SleepContract
import com.lifo.write.SleepLogScreen
import com.lifo.write.SleepLogViewModel
import com.lifo.write.GratitudeContract
import com.lifo.write.GratitudeScreen
import com.lifo.write.GratitudeViewModel
import com.lifo.write.BlockContract
import com.lifo.write.BlockScreen
import com.lifo.write.BlockViewModel
import com.lifo.write.MovementContract
import com.lifo.write.MovementScreen
import com.lifo.write.MovementViewModel
import com.lifo.write.WriteScreen
import com.lifo.write.WriteViewModel
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

/**
 * Public entry point composable for the Write feature.
 * Wraps the internal WriteScreen with ViewModel setup.
 * Used by DecomposeApp to render the write destination.
 *
 * @param diaryId Optional diary ID for editing an existing diary. Null for new diary.
 * @param navigateBack Callback to navigate back.
 * @param onShareToComposer Optional callback to share diary content to the social composer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WriteRouteContent(
    diaryId: String? = null,
    isBrainDump: Boolean = false,
    isGratitude: Boolean = false,
    isEnergyCheckIn: Boolean = false,
    isSleepLog: Boolean = false,
    isReframe: Boolean = false,
    isBlock: Boolean = false,
    isMovement: Boolean = false,
    navigateBack: () -> Unit,
    onShareToComposer: ((prefilledContent: String) -> Unit)? = null,
    onNavigateToInsight: ((diaryId: String) -> Unit)? = null,
    onNavigateToBrainDump: (() -> Unit)? = null,
    onNavigateToReframing: (() -> Unit)? = null,
    onNavigateToMeditation: (() -> Unit)? = null,
) {
    // Gratitude mode: completely separate ViewModel and screen
    if (isGratitude) {
        val gratitudeVm: GratitudeViewModel = koinViewModel(key = "gratitude")
        val gratitudeState by gratitudeVm.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            gratitudeVm.effects.collect { effect ->
                when (effect) {
                    is GratitudeContract.Effect.SavedSuccessfully -> {
                        Toast.makeText(context, "Salvato!", Toast.LENGTH_SHORT).show()
                        delay(1500) // let the celebration particles play
                        navigateBack()
                    }
                    is GratitudeContract.Effect.Error -> {
                        Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        GratitudeScreen(
            state = gratitudeState,
            onSetItem = { index, text -> gratitudeVm.onIntent(GratitudeContract.Intent.SetItem(index, text)) },
            onSetCategory = { index, cat -> gratitudeVm.onIntent(GratitudeContract.Intent.SetCategory(index, cat)) },
            onSaveClicked = { gratitudeVm.onIntent(GratitudeContract.Intent.Save) },
            onBackPressed = navigateBack,
        )
        return
    }

    // Energy Check-In mode: separate ViewModel and screen
    if (isEnergyCheckIn) {
        val energyVm: EnergyViewModel = koinViewModel(key = "energy")
        val energyState by energyVm.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            energyVm.effects.collect { effect ->
                when (effect) {
                    is EnergyContract.Effect.SavedSuccessfully -> {
                        Toast.makeText(context, "Salvato!", Toast.LENGTH_SHORT).show()
                        navigateBack()
                    }
                    is EnergyContract.Effect.Error -> {
                        Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        EnergyCheckInScreen(
            state = energyState,
            onIntent = { energyVm.onIntent(it) },
            onBackPressed = navigateBack,
        )
        return
    }

    // Sleep Log mode
    if (isSleepLog) {
        val sleepVm: SleepLogViewModel = koinViewModel(key = "sleep")
        val sleepState by sleepVm.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            sleepVm.effects.collect { effect ->
                when (effect) {
                    is SleepContract.Effect.SavedSuccessfully -> {
                        Toast.makeText(context, "Salvato!", Toast.LENGTH_SHORT).show()
                        navigateBack()
                    }
                    is SleepContract.Effect.Error -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        SleepLogScreen(state = sleepState, onIntent = { sleepVm.onIntent(it) }, onBackPressed = navigateBack)
        return
    }

    // Reframe mode (CBT Lite)
    if (isReframe) {
        val reframeVm: ReframeViewModel = koinViewModel(key = "reframe_${System.currentTimeMillis()}")
        val reframeState by reframeVm.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            reframeVm.effects.collect { effect ->
                when (effect) {
                    is ReframeContract.Effect.SavedSuccessfully -> {
                        Toast.makeText(context, "Pensiero riformulato!", Toast.LENGTH_SHORT).show()
                        navigateBack()
                    }
                    is ReframeContract.Effect.Error -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        ReframeScreen(state = reframeState, onIntent = { reframeVm.onIntent(it) }, onBackPressed = navigateBack)
        return
    }

    // Block Identification mode
    if (isBlock) {
        val blockVm: BlockViewModel = koinViewModel(key = "block_${System.currentTimeMillis()}")
        val blockState by blockVm.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            blockVm.effects.collect { effect ->
                when (effect) {
                    is BlockContract.Effect.BlockSaved -> {
                        Toast.makeText(context, "Blocco registrato", Toast.LENGTH_SHORT).show()
                    }
                    is BlockContract.Effect.BlockResolved -> {
                        Toast.makeText(context, "Blocco superato!", Toast.LENGTH_SHORT).show()
                    }
                    is BlockContract.Effect.Error -> {
                        Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                    }
                    is BlockContract.Effect.GoToBrainDump -> {
                        onNavigateToBrainDump?.invoke() ?: navigateBack()
                    }
                    is BlockContract.Effect.GoToReframing -> {
                        onNavigateToReframing?.invoke() ?: navigateBack()
                    }
                    is BlockContract.Effect.GoToMeditation -> {
                        onNavigateToMeditation?.invoke() ?: navigateBack()
                    }
                }
            }
        }

        BlockScreen(state = blockState, onIntent = { blockVm.onIntent(it) }, onBackPressed = navigateBack)
        return
    }

    // Movement log mode
    if (isMovement) {
        val movementVm: MovementViewModel = koinViewModel(key = "movement_${System.currentTimeMillis()}")
        val movementState by movementVm.state.collectAsState()
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            movementVm.effects.collect { effect ->
                when (effect) {
                    is MovementContract.Effect.SavedSuccessfully -> {
                        Toast.makeText(context, "Movimento registrato!", Toast.LENGTH_SHORT).show()
                        navigateBack()
                    }
                    is MovementContract.Effect.Error -> {
                        Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        MovementScreen(state = movementState, onIntent = { movementVm.onIntent(it) }, onBackPressed = navigateBack)
        return
    }

    // Use diaryId as key so a new ViewModel is created for each diary
    val vmKey = if (isBrainDump) "write_braindump_${System.currentTimeMillis()}" else "write_${diaryId ?: "new"}"
    val viewModel: WriteViewModel = koinViewModel(key = vmKey)
    val context = LocalContext.current
    val uiState = viewModel.uiState
    val galleryState = viewModel.galleryState
    val pagerState = rememberPagerState(pageCount = { Mood.values().size })
    val pageNumber by remember { derivedStateOf { pagerState.currentPage } }

    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var savedDescription by remember { mutableStateOf("") }

    // Supply diaryId or brain dump mode
    LaunchedEffect(diaryId, isBrainDump) {
        if (isBrainDump) {
            viewModel.setBrainDumpMode(true)
        } else {
            viewModel.setDiaryIdAndLoad(diaryId)
        }
    }

    // Brain Dump mode: minimal UI — just text, no mood/metrics/title
    if (uiState.isBrainDumpMode) {
        BrainDumpScreen(
            description = uiState.description,
            onDescriptionChanged = { viewModel.setDescription(it) },
            onBackPressed = navigateBack,
            onSaveClicked = {
                val diary = Diary().apply {
                    title = "Scarico mentale"
                    description = uiState.description
                    mood = Mood.Neutral.name
                }
                viewModel.upsertDiary(
                    diary = diary,
                    onSuccess = { navigateBack() },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
        return
    }

    // View mode: read-only detail screen for existing diary
    if (uiState.isViewMode && uiState.selectedDiary != null) {
        DiaryDetailScreen(
            diary = uiState.selectedDiary!!,
            onBackPressed = navigateBack,
            onInsightClicked = { id -> onNavigateToInsight?.invoke(id) },
            onDeleteConfirmed = {
                viewModel.deleteDiary(
                    onSuccess = {
                        Toast.makeText(context, "Eliminato", Toast.LENGTH_SHORT).show()
                        navigateBack()
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            },
        )
    } else {
        // Edit / Create mode: wizard flow
        WriteScreen(
            uiState = uiState,
            pagerState = pagerState,
            galleryState = galleryState,
            moodName = { Mood.values()[pageNumber].name },
            onTitleChanged = { viewModel.setTitle(title = it) },
            onDescriptionChanged = { viewModel.setDescription(description = it) },
            onDateTimeUpdated = { viewModel.updateDateTime(zonedDateTime = it) },
            onBackPressed = navigateBack,
            onDeleteConfirmed = {
                viewModel.deleteDiary(
                    onSuccess = {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        navigateBack()
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onSaveClicked = {
                viewModel.upsertDiary(
                    diary = it.apply { mood = Mood.values()[pageNumber].name },
                    onSuccess = {
                        if (onShareToComposer != null && diaryId == null) {
                            savedDescription = uiState.description
                            showShareDialog = true
                        } else {
                            navigateBack()
                        }
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onImageSelect = { imageString ->
                val imageUri = android.net.Uri.parse(imageString)
                val type = context.contentResolver.getType(imageUri)?.split("/")?.last() ?: "jpg"
                viewModel.addImage(image = imageUri, imageType = type)
            },
            onImageDeleteClicked = { galleryState.removeImage(it) },
            onImageClicked = { index ->
                viewModel.onImageSelected(index)
            },
            viewModel = viewModel
        )
    }

    // Share to community dialog
    if (showShareDialog) {
        val excerpt = if (savedDescription.length > 100) {
            savedDescription.take(100) + "..."
        } else {
            savedDescription
        }

        AlertDialog(
            onDismissRequest = {
                showShareDialog = false
                navigateBack()
            },
            title = { Text(stringResource(Res.string.write_entry_saved_title)) },
            text = { Text(stringResource(Res.string.write_share_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    val prefilledContent = excerpt
                    onShareToComposer?.invoke(prefilledContent)
                }) {
                    Text(stringResource(Res.string.share))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    navigateBack()
                }) {
                    Text(stringResource(Res.string.thanks_no))
                }
            }
        )
    }
}

