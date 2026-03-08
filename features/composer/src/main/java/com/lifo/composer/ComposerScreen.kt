package com.lifo.composer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lifo.socialui.thread.ThreadLine
import com.lifo.socialui.avatar.UserAvatar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(
    state: ComposerContract.State,
    onIntent: (ComposerContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val canSubmit = state.characterCount <= state.maxCharacters
            && !state.isSubmitting
            && !state.isUploading

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
    ) { uris ->
        uris.forEach { uri ->
            onIntent(ComposerContract.Intent.AddMedia(uri.toString()))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.replyToAuthorName != null) "Rispondi" else "Nuovo post") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.content.isNotBlank()) {
                            onIntent(ComposerContract.Intent.Discard)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // -- Reply context: show parent post with content and images --
                if (state.replyToAuthorName != null) {
                    val parentThread = state.parentThread
                    if (parentThread != null) {
                        // Full parent post preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .height(IntrinsicSize.Min),
                        ) {
                            // LEFT: Avatar + thread line
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(48.dp),
                            ) {
                                UserAvatar(
                                    avatarUrl = parentThread.authorAvatarUrl,
                                    displayName = parentThread.authorDisplayName ?: parentThread.authorUsername ?: parentThread.authorId,
                                    size = 36.dp,
                                )
                                Spacer(Modifier.height(4.dp))
                                ThreadLine(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // RIGHT: Author name + text + images
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = parentThread.authorDisplayName ?: parentThread.authorUsername ?: parentThread.authorId.take(12),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel reply",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onNavigateBack() },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                if (parentThread.text.isNotBlank()) {
                                    Text(
                                        text = parentThread.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                    )
                                }
                                // Show images if present
                                if (parentThread.mediaUrls.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        parentThread.mediaUrls.take(3).forEach { url ->
                                            AsyncImage(
                                                model = url,
                                                contentDescription = "Media",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                            )
                                        }
                                        if (parentThread.mediaUrls.size > 3) {
                                            Text(
                                                text = "+${parentThread.mediaUrls.size - 3}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.align(Alignment.CenterVertically),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback: simple chip while loading parent thread
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = {
                                    Text(
                                        text = "Replying to @${state.replyToAuthorName}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel reply",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onNavigateBack() },
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                // -- Core Threads layout: Avatar column + Content column --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    // LEFT COLUMN: Avatar + Thread Line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(48.dp),
                    ) {
                        UserAvatar(
                            avatarUrl = null,
                            displayName = "You",
                            size = 36.dp,
                            showBorder = true,
                        )
                        Spacer(Modifier.height(4.dp))
                        ThreadLine(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // RIGHT COLUMN: Content
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        // Username
                        Text(
                            text = "You",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(Modifier.height(4.dp))

                        // BasicTextField (no border, transparent, Threads-style)
                        BasicTextField(
                            value = state.content,
                            onValueChange = { onIntent(ComposerContract.Intent.UpdateContent(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 12,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (state.content.isEmpty()) {
                                        Text(
                                            text = if (state.replyToAuthorName != null) "Rispondi..." else "Cosa hai in mente?",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        Spacer(Modifier.height(10.dp))

                        // Media toolbar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            // Image icon
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(
                                    onClick = {
                                        mediaPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = "Add image",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            // GIF icon
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = { /* GIF picker stub */ }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Gif,
                                        contentDescription = "Add GIF",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            // Attach file icon
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = { /* Attach file stub */ }) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = "Attach file",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            // Format quote icon
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = { /* Format quote stub */ }) {
                                    Icon(
                                        imageVector = Icons.Outlined.FormatQuote,
                                        contentDescription = "Format quote",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            // More options icon
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = { /* More options stub */ }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        // Media Preview Row
                        if (state.mediaUris.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            MediaPreviewRow(
                                mediaUris = state.mediaUris,
                                uploadProgress = state.uploadProgress,
                                isUploading = state.isUploading,
                                maxMedia = state.maxMedia,
                                onRemove = { index -> onIntent(ComposerContract.Intent.RemoveMedia(index)) },
                                onAddMore = {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                            )
                        }
                    }
                }

                // -- Additional thread drafts --
                state.threadDrafts.forEachIndexed { index, draft ->
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .height(IntrinsicSize.Min),
                    ) {
                        // LEFT COLUMN: thread connector
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(48.dp),
                        ) {
                            UserAvatar(
                                avatarUrl = null,
                                displayName = "You",
                                size = 28.dp,
                                showBorder = false,
                            )
                            Spacer(Modifier.height(4.dp))
                            ThreadLine(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // RIGHT COLUMN: draft content
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "You",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                IconButton(
                                    onClick = { onIntent(ComposerContract.Intent.RemoveThreadPost(index)) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            BasicTextField(
                                value = draft.text,
                                onValueChange = { onIntent(ComposerContract.Intent.UpdateThreadPost(index, it)) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 8,
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (draft.text.isEmpty()) {
                                            Text(
                                                text = "Continue thread...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    }
                }

                // -- "Add to thread" button --
                if (state.parentThreadId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntent(ComposerContract.Intent.AddThreadPost) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.width(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            UserAvatar(
                                avatarUrl = null,
                                displayName = "+",
                                size = 20.dp,
                                showBorder = false,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add to thread",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // -- Category selector (Scoperta / Sfida / Domanda) --
                if (state.parentThreadId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Tipo:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ComposerContract.PostCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = state.category == cat,
                                onClick = { onIntent(ComposerContract.Intent.SetCategory(cat)) },
                                label = {
                                    Text(
                                        text = "${cat.emoji} ${cat.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }

                // -- Mood Tags (compact inline) --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mood:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ComposerContract.MOOD_TAGS.forEach { tag ->
                        FilterChip(
                            selected = state.moodTag == tag,
                            onClick = { onIntent(ComposerContract.Intent.SetMoodTag(tag)) },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.height(28.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // -- Bottom bar: mood selected + visibility + char count --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Selected mood tag (if any)
                    if (state.moodTag != null) {
                        FilterChip(
                            selected = true,
                            onClick = { onIntent(ComposerContract.Intent.SetMoodTag(null)) },
                            label = {
                                Text(
                                    text = state.moodTag,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.height(26.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    // Visibility selector (compact)
                    VisibilitySelector(
                        currentVisibility = state.visibility,
                        onVisibilitySelected = { onIntent(ComposerContract.Intent.SetVisibility(it)) },
                    )

                    Spacer(Modifier.weight(1f))

                    // Character count
                    val isNearLimit = state.characterCount > (state.maxCharacters * 0.9).toInt()
                    val isOverLimit = state.characterCount > state.maxCharacters
                    val counterColor = when {
                        isOverLimit -> MaterialTheme.colorScheme.error
                        isNearLimit -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "${state.characterCount}/${state.maxCharacters}",
                        style = MaterialTheme.typography.labelSmall,
                        color = counterColor,
                        fontWeight = if (isOverLimit) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // -- Footer: Reply options + Publish --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val replyLabel = when (state.replyPermission) {
                        ComposerContract.ReplyPermission.Everyone -> "Anyone can reply"
                        ComposerContract.ReplyPermission.Followers -> "Followers can reply"
                        ComposerContract.ReplyPermission.Mentioned -> "Mentioned can reply"
                    }
                    Text(
                        text = replyLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val next = when (state.replyPermission) {
                                ComposerContract.ReplyPermission.Everyone -> ComposerContract.ReplyPermission.Followers
                                ComposerContract.ReplyPermission.Followers -> ComposerContract.ReplyPermission.Mentioned
                                ComposerContract.ReplyPermission.Mentioned -> ComposerContract.ReplyPermission.Everyone
                            }
                            onIntent(ComposerContract.Intent.ChangeReplyPermission(next))
                        },
                    )

                    Button(
                        onClick = { onIntent(ComposerContract.Intent.Submit) },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        ),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = "Pubblica",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // -- Loading overlay --
            AnimatedVisibility(
                visible = state.isSubmitting || state.isUploading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        if (state.isUploading) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Uploading media...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// -- Media Preview Row --------------------------------------------------------

@Composable
private fun MediaPreviewRow(
    mediaUris: List<String>,
    uploadProgress: Map<Int, Float>,
    isUploading: Boolean,
    maxMedia: Int,
    onRemove: (Int) -> Unit,
    onAddMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        mediaUris.forEachIndexed { index, uri ->
            val progress = uploadProgress[index]

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Media ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Upload progress overlay
                if (isUploading && progress != null && progress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // Upload complete checkmark
                if (progress != null && progress >= 1f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Uploaded",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // Remove button overlay (disabled during upload)
                if (!isUploading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .clickable { onRemove(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Add more button (if under limit and not uploading)
        if (mediaUris.size < maxMedia && !isUploading) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onAddMore() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add media",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- Visibility Selector (compact) --------------------------------------------

@Composable
private fun VisibilitySelector(
    currentVisibility: ComposerContract.Visibility,
    onVisibilitySelected: (ComposerContract.Visibility) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = currentVisibility.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = currentVisibility.label(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ComposerContract.Visibility.entries.forEach { visibility ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = visibility.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(visibility.label())
                        }
                    },
                    onClick = {
                        onVisibilitySelected(visibility)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun ComposerContract.Visibility.icon(): ImageVector = when (this) {
    ComposerContract.Visibility.PUBLIC -> Icons.Default.Public
    ComposerContract.Visibility.FOLLOWERS_ONLY -> Icons.Default.People
    ComposerContract.Visibility.PRIVATE -> Icons.Default.Lock
}

private fun ComposerContract.Visibility.label(): String = when (this) {
    ComposerContract.Visibility.PUBLIC -> "Public"
    ComposerContract.Visibility.FOLLOWERS_ONLY -> "Followers"
    ComposerContract.Visibility.PRIVATE -> "Private"
}
