package com.lifo.biocontext

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

/**
 * Entry point for the Bio-Signal transparency dashboard.
 *
 * Effects handled:
 * - [BioContextContract.Effect.ShareExport] → writes the JSON to a temp file
 *   under the app's cache, then launches an `ACTION_SEND` intent so the user
 *   can save it anywhere (Drive, Files, email, etc.). Uses our existing
 *   FileProvider authority `${applicationId}.provider`.
 * - [BioContextContract.Effect.Toast] → resolves the message key and shows
 *   the standard Android Toast.
 * - [BioContextContract.Effect.OpenHealthConnectSettings] → launches the
 *   platform Health Connect permissions screen for our package.
 */
@Composable
fun BioContextRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: BioContextViewModel = koinViewModel(key = "biocontext_vm")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BioContextContract.Effect.ShareExport -> {
                    val cacheDir = File(context.cacheDir, "bio-export").apply { mkdirs() }
                    val file = File(cacheDir, "calmify-bio-${System.currentTimeMillis()}.json")
                    file.writeText(effect.jsonPayload)
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file,
                    )
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, null))
                }
                is BioContextContract.Effect.Toast -> {
                    val text = when (effect.messageKey) {
                        "bio_ingest_success" -> getString(Strings.BioContext.toastSynced)
                        "bio_ingest_partial" -> getString(Strings.BioContext.toastPartial)
                        "bio_delete_success" -> getString(Strings.BioContext.toastDeleted)
                        else -> effect.messageKey
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                BioContextContract.Effect.OpenHealthConnectSettings -> {
                    // The platform-level Health Connect permissions screen for our app.
                    // Android 14+ uses a settings deep-link via ACTION_HEALTH_CONNECT_SETTINGS;
                    // pre-14 falls back to opening the Health Connect app directly.
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                    } else {
                        // Fallback: open the HC provider package directly
                        context.packageManager
                            .getLaunchIntentForPackage("com.google.android.apps.healthdata")
                            ?: Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    BioContextScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = navigateBack,
    )
}
