package com.lifo.socialprofile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.util.model.RequestState
import com.lifo.ui.i18n.Strings
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.SocialGraphRepository.SocialUser
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    userId: String,
    showFollowers: Boolean,
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val socialGraphRepository: SocialGraphRepository = koinInject()

    var selectedTab by remember { mutableStateOf(if (showFollowers) 0 else 1) }
    val users = remember { mutableStateOf<List<SocialUser>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }

    // Load based on selected tab
    val flow = remember(selectedTab, userId) {
        if (selectedTab == 0) socialGraphRepository.getFollowers(userId)
        else socialGraphRepository.getFollowing(userId)
    }

    LaunchedEffect(flow) {
        isLoading.value = true
        flow.collect { result ->
            when (result) {
                is RequestState.Success -> {
                    users.value = result.data
                    isLoading.value = false
                }
                is RequestState.Error -> {
                    isLoading.value = false
                }
                is RequestState.Loading -> {
                    isLoading.value = true
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) stringResource(Strings.Screen.FollowList.tabFollowers)
                               else stringResource(Strings.Screen.FollowList.tabFollowing),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Strings.A11y.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Follower") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Seguiti") }
                )
            }

            when {
                isLoading.value -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                users.value.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == 0) "Nessun follower" else "Non segui nessuno",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(users.value, key = { it.userId }) { user ->
                            UserListItem(
                                user = user,
                                onClick = { onUserClick(user.userId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: SocialUser,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar placeholder
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (user.displayName?.firstOrNull() ?: user.username?.firstOrNull() ?: user.userId.first()).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName ?: user.username ?: user.userId.take(12),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            user.bio?.let { bio ->
                if (bio.isNotBlank()) {
                    Text(
                        text = if (bio.length > 60) bio.take(60) + "..." else bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
