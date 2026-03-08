package com.lifo.write.navigation

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.model.Mood
import com.lifo.write.DiaryDetailScreen
import com.lifo.write.WriteScreen
import com.lifo.write.WriteViewModel

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
    navigateBack: () -> Unit,
    onShareToComposer: ((prefilledContent: String) -> Unit)? = null,
    onNavigateToInsight: ((diaryId: String) -> Unit)? = null,
) {
    // Use diaryId as key so a new ViewModel is created for each diary
    val viewModel: WriteViewModel = koinViewModel(key = "write_${diaryId ?: "new"}")
    val context = LocalContext.current
    val uiState = viewModel.uiState
    val galleryState = viewModel.galleryState
    val pagerState = rememberPagerState(pageCount = { Mood.values().size })
    val pageNumber by remember { derivedStateOf { pagerState.currentPage } }

    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var savedMoodName by remember { mutableStateOf("") }
    var savedDescription by remember { mutableStateOf("") }

    // Supply diaryId if it was not picked up from SavedStateHandle (Decompose path)
    LaunchedEffect(diaryId) {
        viewModel.setDiaryIdAndLoad(diaryId)
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
                            savedMoodName = Mood.values()[pageNumber].name
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
        val moodEmoji = moodToEmoji(savedMoodName)
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
            title = { Text("Salvato!") },
            text = { Text("Vuoi condividere come ti senti con la community?") },
            confirmButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    val prefilledContent = "$moodEmoji $excerpt"
                    onShareToComposer?.invoke(prefilledContent)
                }) {
                    Text("Condividi")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    navigateBack()
                }) {
                    Text("No, grazie")
                }
            }
        )
    }
}

private fun moodToEmoji(moodName: String): String = when (moodName.lowercase()) {
    "happy" -> "\uD83D\uDE0A"
    "calm" -> "\uD83E\uDDD8"
    "romantic" -> "\u2764\uFE0F"
    "angry" -> "\uD83D\uDE21"
    "depressed" -> "\uD83D\uDE1E"
    "disappointed" -> "\uD83D\uDE14"
    "lonely" -> "\uD83E\uDEE5"
    "tense" -> "\uD83D\uDE30"
    "surprised" -> "\uD83D\uDE32"
    "mysterious" -> "\uD83E\uDD14"
    "humorous" -> "\uD83D\uDE02"
    "suspicious" -> "\uD83E\uDD28"
    "bored" -> "\uD83D\uDE12"
    "shameful" -> "\uD83D\uDE16"
    "awful" -> "\uD83D\uDE29"
    else -> "\uD83D\uDCDD"
}
