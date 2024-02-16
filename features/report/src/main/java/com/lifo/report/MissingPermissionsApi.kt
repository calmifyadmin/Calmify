package com.lifo.report

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class) // 1.
@Composable
fun MissingPermissionsComponent(
    content: @Composable () -> Unit, // 2.
) {
    var permission1 =
        Manifest.permission.ACCESS_MEDIA_LOCATION
    val permissionState1 = rememberPermissionState(permission = permission1) // 5.
    var permission2 =
        Manifest.permission.ACCESS_MEDIA_LOCATION
    val permissionState2 = rememberPermissionState(permission = permission2) // 5.
    if (permissionState1.status.isGranted &&
        permissionState2.status.isGranted) { // 6.
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Button(
                onClick = {
                    permissionState1.launchPermissionRequest() // 7.
                }
            ) {
                Text(text = "Request permissions")
            }
            Button(
                onClick = {
                    permissionState2.launchPermissionRequest() // 7.
                }
            ) {
                Text(text = "Request permissions")
            }
        }
    }
}