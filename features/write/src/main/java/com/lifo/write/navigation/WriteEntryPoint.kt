package com.lifo.write.navigation

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.model.Mood
import com.lifo.write.WriteScreen
import com.lifo.write.WriteViewModel

/**
 * Public entry point composable for the Write feature.
 * Wraps the internal WriteScreen with ViewModel setup.
 * Used by DecomposeApp to render the write destination.
 *
 * @param diaryId Optional diary ID for editing an existing diary. Null for new diary.
 * @param navigateBack Callback to navigate back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WriteRouteContent(
    diaryId: String? = null,
    navigateBack: () -> Unit
) {
    // Use diaryId as key so a new ViewModel is created for each diary
    val viewModel: WriteViewModel = koinViewModel(key = "write_${diaryId ?: "new"}")
    val context = LocalContext.current
    val uiState = viewModel.uiState
    val galleryState = viewModel.galleryState
    val pagerState = rememberPagerState(pageCount = { Mood.values().size })
    val pageNumber by remember { derivedStateOf { pagerState.currentPage } }

    // Supply diaryId if it was not picked up from SavedStateHandle (Decompose path)
    LaunchedEffect(diaryId) {
        viewModel.setDiaryIdAndLoad(diaryId)
    }

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
                onSuccess = navigateBack,
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
