package com.lifo.chat.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Composable helper for handling audio permissions in LiveChat
 */
@Composable
fun RequestAudioPermission(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit,
    content: @Composable (requestPermission: () -> Unit, hasPermission: Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(checkAudioPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                onPermissionGranted()
            } else {
                onPermissionDenied()
            }
        }
    )

    val requestPermission = remember {
        {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Update permission status on composition
    LaunchedEffect(Unit) {
        hasPermission = checkAudioPermission(context)
    }

    content(requestPermission, hasPermission)
}

/**
 * Check if audio permission is granted
 */
fun checkAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Data class for permission state
 */
data class PermissionState(
    val hasPermission: Boolean,
    val isRequesting: Boolean = false,
    val wasRequested: Boolean = false,
    val isDenied: Boolean = false
)

/**
 * Remember permission state with lifecycle management
 */
@Composable
fun rememberPermissionState(
    permission: String = Manifest.permission.RECORD_AUDIO
): PermissionState {
    val context = LocalContext.current
    var state by remember {
        mutableStateOf(
            PermissionState(
                hasPermission = ContextCompat.checkSelfPermission(context, permission) == 
                    PackageManager.PERMISSION_GRANTED
            )
        )
    }

    // Update on recomposition
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, permission) == 
            PackageManager.PERMISSION_GRANTED
        state = state.copy(hasPermission = hasPermission)
    }

    return state
}