package com.lifo.messaging

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android entry point for the Messaging feature that wires up the system gallery
 * image picker via [ActivityResultContracts.PickMultipleVisualMedia].
 *
 * Obtains the same [MessagingViewModel] instance as [MessagingRouteContent] (same
 * Activity ViewModelStore) so that selected URIs are dispatched as
 * [MessagingContract.Intent.AddAttachments] directly to the shared VM.
 */
@Composable
fun MessagingPickerRoute(
    conversationId: String? = null,
    onNavigateBack: () -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val viewModel: MessagingViewModel = koinViewModel()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onIntent(
                MessagingContract.Intent.AddAttachments(uris.map { it.toString() })
            )
        }
    }

    MessagingRouteContent(
        conversationId = conversationId,
        onNavigateBack = onNavigateBack,
        onUserClick = onUserClick,
        onPickImages = {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )
}
