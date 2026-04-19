package com.lifo.socialprofile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import com.lifo.socialui.avatar.OverlappingAvatars
import com.lifo.socialui.post.ThreadPostCard
import com.lifo.socialui.avatar.UserAvatar
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SocialProfileScreen(
    state: SocialProfileContract.State,
    onIntent: (SocialProfileContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    onThreadClick: (String) -> Unit,
    onUserClick: (String) -> Unit = {},
    onFollowersClick: (String) -> Unit = {},
    onFollowingClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        containerColor = colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.profile?.displayName
                            ?: state.profile?.username
                            ?: state.profile?.userId?.take(12) ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Strings.A11y.back))
                    }
                },
                actions = {
                    if (!state.isOwnProfile && state.profile != null) {
                        IconButton(onClick = { onIntent(SocialProfileContract.Intent.BlockUser) }) {
                            Icon(Icons.Outlined.Block, contentDescription = stringResource(Strings.Screen.SocialProfile.a11yBlockUser))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        when {
            state.isLoading && state.profile == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            state.error != null && state.profile == null -> {
                ErrorContent(
                    message = state.error,
                    onRetry = {
                        if (state.userId.isNotEmpty()) {
                            onIntent(SocialProfileContract.Intent.LoadProfile(state.userId))
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    // Cover Photo
                    item(key = "cover_photo") {
                        CoverPhotoSection(coverPhotoUrl = state.profile?.coverPhotoUrl)
                    }

                    // Profile Header — M3 Expressive
                    item(key = "profile_header") {
                        ProfileHeader(
                            profile = state.profile,
                            isOwnProfile = state.isOwnProfile,
                            isFollowing = state.isFollowing,
                            onFollowClick = { onIntent(SocialProfileContract.Intent.FollowUser) },
                            onUnfollowClick = { onIntent(SocialProfileContract.Intent.UnfollowUser) },
                            onEditProfileClick = { onIntent(SocialProfileContract.Intent.EditProfile) },
                            onShareProfileClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "Check out @${state.profile?.username?.takeIf { it.isNotBlank() } ?: state.profile?.userId} on Calmify!")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                            onFollowersClick = { onFollowersClick(state.userId) },
                            onFollowingClick = { onFollowingClick(state.userId) },
                        )
                    }

                    // Tab Row
                    item(key = "tab_row") {
                        ProfileTabRow(
                            selectedTab = state.selectedProfileTab,
                            onTabSelected = { tab -> onIntent(SocialProfileContract.Intent.SelectProfileTab(tab)) },
                        )
                    }

                    // Threads
                    if (state.threads.isEmpty() && !state.isLoading) {
                        item(key = "empty") {
                            EmptyThreadsPlaceholder(
                                isOwnProfile = state.isOwnProfile,
                                selectedTab = state.selectedProfileTab,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                            )
                        }
                    } else {
                        items(items = state.threads, key = { it.threadId }) { thread ->
                            ThreadPostCard(
                                thread = thread,
                                onThreadClick = { onThreadClick(thread.threadId) },
                                onUserClick = { onUserClick(thread.authorId) },
                                onLike = {
                                    if (thread.isLikedByCurrentUser)
                                        onIntent(SocialProfileContract.Intent.UnlikeThread(thread.threadId))
                                    else
                                        onIntent(SocialProfileContract.Intent.LikeThread(thread.threadId))
                                },
                                onReply = { onThreadClick(thread.threadId) },
                                onRepost = { },
                                onShare = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, "${thread.text}\n\n-- shared via Calmify")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onOptions = { },
                            )
                        }
                    }
                }
            }
        }
    }
}

// -- Profile Header (M3 Expressive) -------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(
    profile: SocialGraphRepository.SocialUser?,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onUnfollowClick: () -> Unit,
    onEditProfileClick: () -> Unit = {},
    onShareProfileClick: () -> Unit = {},
    onFollowersClick: () -> Unit = {},
    onFollowingClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top row: Name + Username on left, Avatar on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = profile?.displayName ?: stringResource(Strings.Screen.SocialProfile.userFallback),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profile?.isVerified == true) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = stringResource(Strings.SharedA11y.verifiedBadge),
                                modifier = Modifier.size(20.dp),
                                tint = colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = "@${profile?.username?.takeIf { it.isNotBlank() } ?: profile?.userId ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(16.dp))

                UserAvatar(
                    avatarUrl = profile?.avatarUrl,
                    displayName = profile?.displayName,
                    size = 80.dp,
                    showBorder = true,
                )
            }

            // Bio
            if (!profile?.bio.isNullOrBlank()) {
                Text(
                    text = profile?.bio.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Interest tags — pill chips
            if (!profile?.interests.isNullOrEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    profile?.interests?.forEach { interest ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = interest,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Stats row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val followerCount = profile?.followerCount ?: 0
                val followingCount = profile?.followingCount ?: 0

                if (!profile?.followerPreviewAvatars.isNullOrEmpty()) {
                    OverlappingAvatars(
                        avatarUrls = profile?.followerPreviewAvatars.orEmpty(),
                        avatarSize = 16.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)) {
                            append(formatCount(followerCount))
                        }
                        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                            append(" ")
                            append(stringResource(Strings.Screen.SocialProfile.followersSuffix))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onFollowersClick,
                    ),
                )

                Text(
                    text = " \u00B7 ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)) {
                            append(formatCount(followingCount))
                        }
                        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                            append(" ")
                            append(stringResource(Strings.Screen.SocialProfile.followingSuffix))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onFollowingClick,
                    ),
                )
            }

            // Profile views
            if ((profile?.profileViews30Days ?: 0) > 0) {
                Text(
                    text = "${formatCount(profile?.profileViews30Days ?: 0)} ${stringResource(Strings.Screen.SocialProfile.visits30d)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            // Follow / Edit buttons
            if (isOwnProfile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onEditProfileClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(Strings.Screen.SocialProfile.editButton), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onShareProfileClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(Strings.Screen.SocialProfile.shareButton), fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                val haptic = LocalHapticFeedback.current
                AnimatedContent(
                    targetState = isFollowing,
                    transitionSpec = {
                        (scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            )
                        ) + fadeIn()) togetherWith (scaleOut() + fadeOut())
                    },
                    label = "followButton",
                ) { following ->
                    if (following) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUnfollowClick()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(stringResource(Strings.Screen.SocialProfile.followingButton), fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onFollowClick()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                            ),
                        ) {
                            Text(stringResource(Strings.Screen.SocialProfile.followButton), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// -- Cover Photo Section ------------------------------------------------------

@Composable
private fun CoverPhotoSection(
    coverPhotoUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (!coverPhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverPhotoUrl,
                contentDescription = stringResource(Strings.Screen.SocialProfile.a11yCoverPhoto),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        ),
                    ),
                ),
        )
    }
}

// -- Profile Tab Row ----------------------------------------------------------

@Composable
private fun ProfileTabRow(
    selectedTab: SocialProfileContract.ProfileTab,
    onTabSelected: (SocialProfileContract.ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = SocialProfileContract.ProfileTab.entries
    val selectedIndex = tabs.indexOf(selectedTab)
    val colorScheme = MaterialTheme.colorScheme

    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        indicator = { tabPositions ->
            if (selectedIndex in tabPositions.indices) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 2.dp,
                    color = colorScheme.primary,
                )
            }
        },
        divider = { },
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            val textColor = animateColorAsState(
                targetValue = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "tabTextColor",
            )

            Tab(
                selected = selected,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.label(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor.value,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }
    }
}

private fun SocialProfileContract.ProfileTab.label(): String = when (this) {
    SocialProfileContract.ProfileTab.THREADS -> "Thread"
    SocialProfileContract.ProfileTab.REPLIES -> "Risposte"
    SocialProfileContract.ProfileTab.REPOSTS -> "Repost"
}

// -- Empty / Error states -----------------------------------------------------

@Composable
private fun EmptyThreadsPlaceholder(
    isOwnProfile: Boolean,
    selectedTab: SocialProfileContract.ProfileTab,
    modifier: Modifier = Modifier,
) {
    val message = when (selectedTab) {
        SocialProfileContract.ProfileTab.THREADS -> {
            if (isOwnProfile) "Non hai ancora pubblicato thread" else "Nessun thread"
        }
        SocialProfileContract.ProfileTab.REPLIES -> {
            if (isOwnProfile) "Non hai ancora risposto a thread" else "Nessuna risposta"
        }
        SocialProfileContract.ProfileTab.REPOSTS -> {
            if (isOwnProfile) "Non hai ancora repostato thread" else "Nessun repost"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("Riprova", fontWeight = FontWeight.SemiBold)
        }
    }
}

// -- Utility ------------------------------------------------------------------

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

private fun formatTimestamp(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - epochMillis
    val diffMinutes = diffMs / 60_000
    val diffHours = diffMs / 3_600_000
    val diffDays = diffMs / 86_400_000

    return when {
        diffMinutes < 1 -> "ora"
        diffMinutes < 60 -> "${diffMinutes}m"
        diffHours < 24 -> "${diffHours}h"
        diffDays < 7 -> "${diffDays}g"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))
    }
}
