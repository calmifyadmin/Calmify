package com.lifo.home.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

/**
 * Material3 DockedSearchBar component for the unified feed
 * Provides a native Material3 search experience with expansion support
 * Positioned to overlay content without pushing it down
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isActive: Boolean = false
) {
    val placeholderText = placeholder ?: stringResource(Strings.Screen.Home.searchPlaceholder)
    val keyboardController = LocalSoftwareKeyboardController.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Search history (can be persisted later)
    var searchHistory by remember { mutableStateOf(listOf<String>()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(1f) // Ensures search bar overlays content
    ) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {
                        keyboardController?.hide()
                        if (query.isNotBlank() && !searchHistory.contains(query)) {
                            searchHistory = (listOf(query) + searchHistory).take(5)
                        }
                        expanded = false
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        // Show back arrow when expanded, search icon when collapsed
                        IconButton(
                            onClick = {
                                if (expanded) {
                                    expanded = false
                                    onClearSearch()
                                    keyboardController?.hide()
                                }
                            }
                        ) {
                            AnimatedContent(
                                targetState = expanded,
                                transitionSpec = {
                                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                                },
                                label = "LeadingIconAnimation"
                            ) { isExpanded ->
                                Icon(
                                    imageVector = if (isExpanded) {
                                        Icons.AutoMirrored.Filled.ArrowBack
                                    } else {
                                        Icons.Default.Search
                                    },
                                    contentDescription = if (isExpanded) stringResource(Strings.A11y.close) else stringResource(Strings.A11y.search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = query.isNotBlank(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(
                                onClick = {
                                    onClearSearch()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(Strings.Screen.Home.a11yClearSearch),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = SearchBarDefaults.dockedShape,
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                dividerColor = MaterialTheme.colorScheme.outlineVariant
            ),
            tonalElevation = SearchBarDefaults.TonalElevation,
            shadowElevation = SearchBarDefaults.ShadowElevation
        ) {
            // Search suggestions and history
            if (searchHistory.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(Strings.Screen.Home.searchRecent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(searchHistory) { historyItem ->
                        ListItem(
                            headlineContent = { Text(historyItem) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        searchHistory = searchHistory.filter { it != historyItem }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(Strings.Screen.Home.a11yRemoveHistory),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onQueryChange(historyItem)
                                expanded = false
                            }
                        )
                    }
                }
            } else {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Strings.Screen.Home.searchTypeHint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Alternative compact search bar for when space is limited
 */
@Composable
fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    val placeholderText = placeholder ?: stringResource(Strings.Screen.Home.searchPlaceholder)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(Strings.A11y.search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(Strings.Screen.Home.a11yClearSearch),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Search result counter component
 */
@Composable
fun SearchResultCounter(
    resultCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    if (resultCount < totalCount) {
        Text(
            text = "$resultCount of $totalCount items",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}