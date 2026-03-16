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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SocialProfileScreen -- Threads-style profile layout
 *
 * Layout:
 * - ProfileHeader: displayName, @userId, avatar, bio, stats, follow button
 * - TabRow: Threads | Replies | Reposts
 * - Thread list using ThreadPostCard shared component
 */
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error via snackbar
    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.profile?.displayName
                            ?: state.profile?.username
                            ?: state.profile?.userId?.take(12)
                            ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!state.isOwnProfile && state.profile != null) {
                        IconButton(onClick = { onIntent(SocialProfileContract.Intent.BlockUser) }) {
                            Icon(
                                imageVector = Icons.Outlined.Block,
                                contentDescription = "Block user"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        when {
            state.isLoading && state.profile == null -> {
                // Full-screen loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.profile == null -> {
                // Full-screen error
                ErrorContent(
                    message = state.error,
                    onRetry = {
                        if (state.userId.isNotEmpty()) {
                            onIntent(SocialProfileContract.Intent.LoadProfile(state.userId))
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                // Profile content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // -- Cover Photo --
                    item(key = "cover_photo") {
                        CoverPhotoSection(
                            coverPhotoUrl = state.profile?.coverPhotoUrl,
                        )
                    }

                    // -- Profile Header --
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
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Check out @${state.profile?.username?.takeIf { it.isNotBlank() } ?: state.profile?.userId} on Calmify!"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                            onFollowersClick = { onFollowersClick(state.userId) },
                            onFollowingClick = { onFollowingClick(state.userId) },
                        )
                    }

                    // -- Tab Row: Threads | Replies | Reposts --
                    item(key = "tab_row") {
                        ProfileTabRow(
                            selectedTab = state.selectedProfileTab,
                            onTabSelected = { tab ->
                                onIntent(SocialProfileContract.Intent.SelectProfileTab(tab))
                            }
                        )
                    }

                    // -- Divider under tabs --
                    item(key = "tab_divider") {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // -- Threads list using ThreadPostCard --
                    if (state.threads.isEmpty() && !state.isLoading) {
                        item(key = "empty") {
                            EmptyThreadsPlaceholder(
                                isOwnProfile = state.isOwnProfile,
                                selectedTab = state.selectedProfileTab,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 48.dp)
                            )
                        }
                    } else {
                        items(
                            items = state.threads,
                            key = { it.threadId }
                        ) { thread ->
                            ThreadPostCard(
                                thread = thread,
                                onThreadClick = { onThreadClick(thread.threadId) },
                                onUserClick = { onUserClick(thread.authorId) },
                                onLike = {
                                    if (thread.isLikedByCurrentUser) {
                                        onIntent(SocialProfileContract.Intent.UnlikeThread(thread.threadId))
                                    } else {
                                        onIntent(SocialProfileContract.Intent.LikeThread(thread.threadId))
                                    }
                                },
                                onReply = { onThreadClick(thread.threadId) },
                                onRepost = { /* TODO: repost */ },
                                onShare = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, "${thread.text}\n\n— shared via Calmify")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onOptions = { },
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -- Profile Header (Threads-style) -------------------------------------------

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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top row: Name + Username on left, Avatar on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: display name + username
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Display name with optional verified badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile?.displayName ?: "User",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile?.isVerified == true) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // @username (falls back to userId if no username set)
                Text(
                    text = "@${profile?.username?.takeIf { it.isNotBlank() } ?: profile?.userId ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(16.dp))

            // Right side: Avatar (large, with border)
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Interest tags
        if (!profile?.interests.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                profile?.interests?.forEach { interest ->
                    SuggestionChip(
                        onClick = { /* no-op for now */ },
                        label = {
                            Text(
                                text = interest,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }
            }
        }

        // Stats: "X followers . Y following" (inline, clickable style) + avatar previews
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val followerCount = profile?.followerCount ?: 0
            val followingCount = profile?.followingCount ?: 0

            // Follower avatar previews
            if (!profile?.followerPreviewAvatars.isNullOrEmpty()) {
                OverlappingAvatars(
                    avatarUrls = profile?.followerPreviewAvatars.orEmpty(),
                    avatarSize = 16.dp,
                )
                Spacer(Modifier.width(6.dp))
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(formatCount(followerCount))
                        append(" followers")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFollowersClick
                )
            )

            Text(
                text = " \u00B7 ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(formatCount(followingCount))
                        append(" following")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFollowingClick
                )
            )
        }

        // Analytics: profile views in last 30 days
        if ((profile?.profileViews30Days ?: 0) > 0) {
            Text(
                text = "${formatCount(profile?.profileViews30Days ?: 0)} views in last 30 days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Follow / Unfollow button OR Edit + Share buttons
        if (isOwnProfile) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onEditProfileClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Edit profile",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = onShareProfileClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Share profile",
                        fontWeight = FontWeight.SemiBold,
                    )
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
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = "Following",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFollowClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Text(
                            text = "Follow",
                            fontWeight = FontWeight.SemiBold,
                        )
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (!coverPhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverPhotoUrl,
                contentDescription = "Cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Gradient overlay at bottom for text readability
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
    modifier: Modifier = Modifier
) {
    val tabs = SocialProfileContract.ProfileTab.entries
    val selectedIndex = tabs.indexOf(selectedTab)

    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { tabPositions ->
            if (selectedIndex in tabPositions.indices) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        divider = { /* No divider, we add our own */ }
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            val textColor = animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "tabTextColor"
            )

            Tab(
                selected = selected,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.label(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor.value,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            )
        }
    }
}

private fun SocialProfileContract.ProfileTab.label(): String = when (this) {
    SocialProfileContract.ProfileTab.THREADS -> "Threads"
    SocialProfileContract.ProfileTab.REPLIES -> "Replies"
    SocialProfileContract.ProfileTab.REPOSTS -> "Reposts"
}

// -- Empty / Error states -----------------------------------------------------

@Composable
private fun EmptyThreadsPlaceholder(
    isOwnProfile: Boolean,
    selectedTab: SocialProfileContract.ProfileTab,
    modifier: Modifier = Modifier
) {
    val message = when (selectedTab) {
        SocialProfileContract.ProfileTab.THREADS -> {
            if (isOwnProfile) "You haven't posted any threads yet"
            else "No threads yet"
        }
        SocialProfileContract.ProfileTab.REPLIES -> {
            if (isOwnProfile) "You haven't replied to any threads yet"
            else "No replies yet"
        }
        SocialProfileContract.ProfileTab.REPOSTS -> {
            if (isOwnProfile) "You haven't reposted any threads yet"
            else "No reposts yet"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Forum,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// -- Utility ------------------------------------------------------------------

/**
 * Format a large count into a compact form (e.g. 1.2K, 3.4M).
 */
private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

/**
 * Format a unix-millis timestamp into a human-readable relative or short date.
 */
private fun formatTimestamp(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - epochMillis
    val diffMinutes = diffMs / 60_000
    val diffHours = diffMs / 3_600_000
    val diffDays = diffMs / 86_400_000

    return when {
        diffMinutes < 1 -> "now"
        diffMinutes < 60 -> "${diffMinutes}m"
        diffHours < 24 -> "${diffHours}h"
        diffDays < 7 -> "${diffDays}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    }
}
