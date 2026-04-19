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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*
import com.lifo.ui.i18n.Strings

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

    val colorScheme = MaterialTheme.colorScheme

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
                title = {
                    Text(
                        text = if (state.replyToAuthorName != null) stringResource(Strings.Screen.Composer.titleReply)
                               else stringResource(Strings.Screen.Composer.titleNew),
                        fontWeight = FontWeight.Bold,
                    )
                },
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
                            contentDescription = stringResource(Res.string.back_cd),
                        )
                    }
                },
                actions = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
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
                // -- Reply context: show parent post --
                if (state.replyToAuthorName != null) {
                    val parentThread = state.parentThread
                    if (parentThread != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .height(IntrinsicSize.Min),
                            ) {
                                // LEFT: Avatar + thread line
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(44.dp),
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
                                Spacer(Modifier.width(10.dp))
                                // RIGHT: Author + text + images
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = parentThread.authorDisplayName ?: parentThread.authorUsername ?: parentThread.authorId.take(12),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            onClick = { onNavigateBack() },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = stringResource(Res.string.composer_cancel_reply_cd),
                                                modifier = Modifier.size(16.dp),
                                                tint = colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (parentThread.text.isNotBlank()) {
                                        Text(
                                            text = parentThread.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                        )
                                    }
                                    if (parentThread.mediaUrls.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            parentThread.mediaUrls.take(3).forEach { url ->
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(12.dp)),
                                                )
                                            }
                                            if (parentThread.mediaUrls.size > 3) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.size(56.dp),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "+${parentThread.mediaUrls.size - 3}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback chip while loading parent
                        Surface(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.composer_reply_to, state.replyToAuthorName ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSecondaryContainer,
                                )
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(Res.string.cancel),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onNavigateBack() },
                                    tint = colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                // -- Core Threads layout: Avatar column + Content column --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    // LEFT COLUMN: Avatar + Thread Line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(44.dp),
                    ) {
                        UserAvatar(
                            avatarUrl = null,
                            displayName = stringResource(Res.string.you),
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

                    Spacer(Modifier.width(10.dp))

                    // RIGHT COLUMN: Content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.you),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )

                        Spacer(Modifier.height(6.dp))

                        // BasicTextField (transparent, Threads-style)
                        BasicTextField(
                            value = state.content,
                            onValueChange = { onIntent(ComposerContract.Intent.UpdateContent(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            maxLines = 12,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (state.content.isEmpty()) {
                                        Text(
                                            text = if (state.replyToAuthorName != null) stringResource(Strings.Screen.Composer.titleReply) + "…"
                                                   else stringResource(Strings.Screen.Composer.placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        Spacer(Modifier.height(12.dp))

                        // Media toolbar — subtle icon row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val toolbarTint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            // Image
                            IconButton(
                                onClick = {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = stringResource(Res.string.composer_add_image_cd), modifier = Modifier.size(20.dp), tint = toolbarTint)
                            }
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Gif, contentDescription = stringResource(Strings.Screen.Composer.a11yGif), modifier = Modifier.size(20.dp), tint = toolbarTint)
                            }
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(Strings.Screen.Composer.a11yAttach), modifier = Modifier.size(20.dp), tint = toolbarTint)
                            }
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.FormatQuote, contentDescription = stringResource(Strings.Screen.Composer.a11yQuote), modifier = Modifier.size(20.dp), tint = toolbarTint)
                            }
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(Strings.Screen.Composer.a11yMore), modifier = Modifier.size(20.dp), tint = toolbarTint)
                            }
                        }

                        // Media Preview Row
                        if (state.mediaUris.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        color = colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .height(IntrinsicSize.Min),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(44.dp),
                            ) {
                                UserAvatar(
                                    avatarUrl = null,
                                    displayName = stringResource(Res.string.you),
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
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.you),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    IconButton(
                                        onClick = { onIntent(ComposerContract.Intent.RemoveThreadPost(index)) },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(Res.string.composer_remove_cd),
                                            modifier = Modifier.size(14.dp),
                                            tint = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                BasicTextField(
                                    value = draft.text,
                                    onValueChange = { onIntent(ComposerContract.Intent.UpdateThreadPost(index, it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(colorScheme.primary),
                                    maxLines = 8,
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (draft.text.isEmpty()) {
                                                Text(
                                                    text = stringResource(Res.string.composer_thread_continuation),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // -- "Add to thread" button --
                if (state.parentThreadId == null) {
                    Surface(
                        onClick = { onIntent(ComposerContract.Intent.AddThreadPost) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primaryContainer,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(Res.string.composer_add_to_thread),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // -- Options card: Category + Mood + Visibility + Char count --
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Category selector
                        if (state.parentThreadId == null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.composer_type_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                ComposerContract.PostCategory.entries.forEach { cat ->
                                    FilterChip(
                                        selected = state.category == cat,
                                        onClick = { onIntent(ComposerContract.Intent.SetCategory(cat)) },
                                        label = {
                                            Text(
                                                text = cat.label,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        leadingIcon = if (state.category == cat) {
                                            {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.tertiaryContainer,
                                            selectedLabelColor = colorScheme.onTertiaryContainer,
                                            selectedLeadingIconColor = colorScheme.onTertiaryContainer,
                                            containerColor = colorScheme.surfaceContainerHigh,
                                        ),
                                    )
                                }
                            }
                        }

                        // Mood Tags
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.composer_mood_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant,
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
                                    leadingIcon = if (state.moodTag == tag) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    } else null,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primaryContainer,
                                        selectedLabelColor = colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = colorScheme.onPrimaryContainer,
                                        containerColor = colorScheme.surfaceContainerHigh,
                                    ),
                                )
                            }
                        }

                        // Bottom: visibility + char count
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Selected mood badge
                            if (state.moodTag != null) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = state.moodTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            VisibilitySelector(
                                currentVisibility = state.visibility,
                                onVisibilitySelected = { onIntent(ComposerContract.Intent.SetVisibility(it)) },
                            )

                            Spacer(Modifier.weight(1f))

                            // Character count
                            val isNearLimit = state.characterCount > (state.maxCharacters * 0.9).toInt()
                            val isOverLimit = state.characterCount > state.maxCharacters
                            val counterColor = when {
                                isOverLimit -> colorScheme.error
                                isNearLimit -> colorScheme.error.copy(alpha = 0.7f)
                                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                            Text(
                                text = "${state.characterCount}/${state.maxCharacters}",
                                style = MaterialTheme.typography.labelSmall,
                                color = counterColor,
                                fontWeight = if (isOverLimit) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // -- Footer: Reply permission + Publish --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val replyLabel = when (state.replyPermission) {
                        ComposerContract.ReplyPermission.Everyone -> stringResource(Strings.Screen.Composer.replyPermAll)
                        ComposerContract.ReplyPermission.Followers -> stringResource(Strings.Screen.Composer.replyPermFollowers)
                        ComposerContract.ReplyPermission.Mentioned -> stringResource(Strings.Screen.Composer.replyPermMentioned)
                    }
                    Surface(
                        onClick = {
                            val next = when (state.replyPermission) {
                                ComposerContract.ReplyPermission.Everyone -> ComposerContract.ReplyPermission.Followers
                                ComposerContract.ReplyPermission.Followers -> ComposerContract.ReplyPermission.Mentioned
                                ComposerContract.ReplyPermission.Mentioned -> ComposerContract.ReplyPermission.Everyone
                            }
                            onIntent(ComposerContract.Intent.ChangeReplyPermission(next))
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = replyLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }

                    Button(
                        onClick = { onIntent(ComposerContract.Intent.Submit) },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            disabledContainerColor = colorScheme.surfaceContainerHigh,
                            disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        ),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.composer_publish),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
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
                        .background(colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            if (state.isUploading) {
                                Text(
                                    text = stringResource(Res.string.composer_loading_media),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                )
                            }
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
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(Res.string.composer_media_cd, index + 1),
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
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(Res.string.composer_uploaded_cd),
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // Remove button
                if (!isUploading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .clickable { onRemove(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.composer_remove_cd),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Add more button
        if (mediaUris.size < maxMedia && !isUploading) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                onClick = { onAddMore() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(Res.string.composer_add_media_cd),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = currentVisibility.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(currentVisibility.labelKey()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                            Text(stringResource(visibility.labelKey()))
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

private fun ComposerContract.Visibility.labelKey(): org.jetbrains.compose.resources.StringResource = when (this) {
    ComposerContract.Visibility.PUBLIC -> Strings.Screen.Composer.visibilityPublic
    ComposerContract.Visibility.FOLLOWERS_ONLY -> Strings.Screen.Composer.visibilityFollowers
    ComposerContract.Visibility.PRIVATE -> Strings.Screen.Composer.visibilityPrivate
}
