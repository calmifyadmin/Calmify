package com.lifo.socialprofile

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lifo.socialui.avatar.UserAvatar
import org.koin.compose.viewmodel.koinViewModel
import java.io.ByteArrayOutputStream

/**
 * Public entry point for the dedicated Edit Profile screen.
 * Navigated to via Decompose from SocialProfileScreen's "Edit profile" button.
 */
@Composable
fun EditProfileRouteContent(
    userId: String,
    onNavigateBack: () -> Unit = {},
) {
    val viewModel: SocialProfileViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                // Scale down to max 512x512 for avatar
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    512.coerceAtMost(bitmap.width),
                    512.coerceAtMost(bitmap.height),
                    true,
                )
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                viewModel.onIntent(
                    SocialProfileContract.Intent.SetAvatarBytes(
                        bytes = stream.toByteArray(),
                        previewUri = uri.toString(),
                    )
                )
            } catch (_: Exception) { }
        }
    }

    // Load profile and open edit mode
    LaunchedEffect(userId) {
        viewModel.onIntent(SocialProfileContract.Intent.LoadProfile(userId))
        viewModel.onIntent(SocialProfileContract.Intent.OpenEditProfile)
    }

    // Collect effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SocialProfileContract.Effect.ProfileSaved -> onNavigateBack()
                is SocialProfileContract.Effect.ShowError -> { /* handled in UI */ }
                is SocialProfileContract.Effect.LaunchImagePicker -> imagePickerLauncher.launch("image/*")
                else -> { /* ignore other effects */ }
            }
        }
    }

    EditProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onPickImage = { imagePickerLauncher.launch("image/*") },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    state: SocialProfileContract.State,
    onIntent: (SocialProfileContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    onPickImage: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onIntent(SocialProfileContract.Intent.SaveProfile) },
                        enabled = !state.isSaving,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "Save",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // -- Avatar Card --
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.clickable { onPickImage() },
                        ) {
                            UserAvatar(
                                avatarUrl = state.editAvatarUri,
                                displayName = state.profile?.displayName,
                                size = 96.dp,
                                showBorder = true,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Change photo",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "Tocca per cambiare foto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // -- Name & Bio Card --
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Identita'",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    OutlinedTextField(
                        value = state.editUsername,
                        onValueChange = { if (it.length <= 20) onIntent(SocialProfileContract.Intent.UpdateEditUsername(it.lowercase())) },
                        label = { Text("Username") },
                        prefix = { Text("@") },
                        singleLine = true,
                        isError = state.editUsernameError != null,
                        supportingText = {
                            Text(
                                text = state.editUsernameError ?: "Lettere, numeri, . e _ (3-20 caratteri)",
                                color = if (state.editUsernameError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    OutlinedTextField(
                        value = state.editName,
                        onValueChange = { if (it.length <= 30) onIntent(SocialProfileContract.Intent.UpdateEditName(it)) },
                        label = { Text("Nome visualizzato") },
                        singleLine = true,
                        supportingText = { Text("${state.editName.length}/30") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    OutlinedTextField(
                        value = state.editBio,
                        onValueChange = { if (it.length <= 150) onIntent(SocialProfileContract.Intent.UpdateEditBio(it)) },
                        label = { Text("Bio") },
                        maxLines = 4,
                        supportingText = { Text("${state.editBio.length}/150") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    OutlinedTextField(
                        value = state.editLink,
                        onValueChange = { onIntent(SocialProfileContract.Intent.UpdateEditLink(it)) },
                        label = { Text("Website / Link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            // -- Interests Card --
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Interests",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // Current interest chips
                    if (state.editInterests.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.editInterests.forEach { interest ->
                                InputChip(
                                    selected = false,
                                    onClick = { onIntent(SocialProfileContract.Intent.RemoveInterest(interest)) },
                                    label = { Text(interest, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ),
                                )
                            }
                        }
                    }

                    // Add interest input
                    var newInterest by remember { mutableStateOf("") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = newInterest,
                            onValueChange = { newInterest = it },
                            label = { Text("Add interest") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (newInterest.isNotBlank()) {
                                        onIntent(SocialProfileContract.Intent.AddInterest(newInterest))
                                        newInterest = ""
                                    }
                                },
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newInterest.isNotBlank()) {
                                    onIntent(SocialProfileContract.Intent.AddInterest(newInterest))
                                    newInterest = ""
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // -- Save button (bottom) --
            Button(
                onClick = { onIntent(SocialProfileContract.Intent.SaveProfile) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save changes", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
