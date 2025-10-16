package com.lifo.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController
) {
    val user = FirebaseAuth.getInstance().currentUser
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Image
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier
                            .size(120.dp)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = user?.photoUrl?.toString(),
                                error = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                                placeholder = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic)
                            ),
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Name
                    Text(
                        text = user?.displayName ?: "User",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )

                    // User Email
                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile Options
            ProfileSection {
                ProfileItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Manage notification settings",
                    onClick = { /* TODO */ }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileItem(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy",
                    subtitle = "Privacy settings and data",
                    onClick = { /* TODO */ }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileItem(
                    icon = Icons.Outlined.Palette,
                    title = "Theme",
                    subtitle = "App appearance",
                    onClick = { /* TODO */ }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProfileSection {
                ProfileItem(
                    icon = Icons.Outlined.Info,
                    title = "About",
                    subtitle = "Version 1.0.0",
                    onClick = { /* TODO */ }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileItem(
                    icon = Icons.Outlined.Star,
                    title = "Rate Us",
                    subtitle = "Rate Calmify on Play Store",
                    onClick = { /* TODO */ }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileItem(
                    icon = Icons.Outlined.Share,
                    title = "Share App",
                    subtitle = "Share Calmify with friends",
                    onClick = { /* TODO */ }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign Out Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                ProfileItem(
                    icon = Icons.Outlined.Logout,
                    title = "Sign Out",
                    subtitle = "Sign out from your account",
                    onClick = { /* TODO: Implement sign out */ },
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileSection(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}