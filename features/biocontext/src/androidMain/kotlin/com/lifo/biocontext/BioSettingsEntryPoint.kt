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
 * Entry point for [BioSettingsScreen] (Phase 9.1.3, 2026-05-17).
 *
 * Effects handled:
 * - NavigateToBioContext → delegates to [navigateToBioContext]
 * - NavigateToBioOnboarding → delegates to [navigateToBioOnboarding]
 * - NavigateToSubscription → delegates to [navigateToSubscription]
 * - ShareExport → standard ACTION_SEND with FileProvider
 * - Toast → resolves Strings.BioContext.toast* keys
 * - OpenHealthConnectSettings → deep-link to HC settings
 */
@Composable
fun BioSettingsRouteContent(
    navigateBack: () -> Unit,
    navigateToBioContext: () -> Unit,
    navigateToBioOnboarding: () -> Unit,
    navigateToSubscription: () -> Unit,
    navigateToPatternFeed: () -> Unit = {},
) {
    val viewModel: BioSettingsViewModel = koinViewModel(key = "biosettings_vm")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BioSettingsContract.Effect.NavigateToBioContext -> navigateToBioContext()
                BioSettingsContract.Effect.NavigateToBioOnboarding -> navigateToBioOnboarding()
                BioSettingsContract.Effect.NavigateToSubscription -> navigateToSubscription()
                BioSettingsContract.Effect.NavigateToPatternFeed -> navigateToPatternFeed()
                is BioSettingsContract.Effect.ShareExport -> {
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
                is BioSettingsContract.Effect.Toast -> {
                    val text = when (effect.messageKey) {
                        "bio_ingest_success" -> getString(Strings.BioContext.toastSynced)
                        "bio_ingest_partial" -> getString(Strings.BioContext.toastPartial)
                        "bio_delete_success" -> getString(Strings.BioContext.toastDeleted)
                        "bio_delete_partial_todo" -> "Phase 9.3 — per-type wipe arriving"
                        else -> effect.messageKey
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                BioSettingsContract.Effect.OpenHealthConnectSettings -> {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                    } else {
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

    BioSettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = navigateBack,
        onOpenPlayStore = { packageId ->
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageId"))
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val webFallback = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageId"),
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            runCatching { context.startActivity(market) }
                .onFailure { runCatching { context.startActivity(webFallback) } }
        },
    )
}
