package com.lifo.biocontext

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.lifo.mongo.biosignal.HealthConnectPermissions
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the Bio-Signal 5-step onboarding pager.
 *
 * Effects handled:
 * - [BioOnboardingContract.Effect.LaunchPermissionRequest] → launches the
 *   Health Connect permission ActivityResultContract via
 *   [HealthConnectPermissions.createRequestPermissionResultContract], then
 *   feeds the result back as [BioOnboardingContract.Intent.PermissionResult].
 * - [BioOnboardingContract.Effect.OpenHealthConnectInstall] → Play Store
 *   deep-link to Health Connect (`com.google.android.apps.healthdata`),
 *   falls back to a generic web URL if Play Store is unavailable.
 * - [BioOnboardingContract.Effect.Finished] / Skipped → navigates back via
 *   the supplied `navigateBack` lambda (caller decides what "back" means —
 *   typically Settings → Bio-signals, or app start).
 *
 * `navigateBack` is the only side-channel out of this screen — once the
 * pager completes (Done) or skips, control returns to whoever pushed
 * onboarding onto the Decompose stack.
 */
@Composable
fun BioOnboardingRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: BioOnboardingViewModel = koinViewModel(key = "bioonb_vm")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Health Connect permission ActivityResultContract — exposed by data/mongo's
    // HealthConnectPermissions facade. Returns a Set<String> of granted HC
    // permission identifiers, which we filter back to BioSignalDataType.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = HealthConnectPermissions.createRequestPermissionResultContract(),
    ) { grantedHc: Set<String> ->
        val grantedTypes = HealthConnectPermissions.grantedDataTypes(grantedHc)
        viewModel.onIntent(BioOnboardingContract.Intent.PermissionResult(grantedTypes))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BioOnboardingContract.Effect.LaunchPermissionRequest -> {
                    val hcPermissions = HealthConnectPermissions.permissionsFor(effect.types)
                    permissionLauncher.launch(hcPermissions)
                }
                BioOnboardingContract.Effect.OpenHealthConnectInstall -> {
                    val playStore = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    val fallback = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                        ),
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    runCatching { context.startActivity(playStore) }
                        .onFailure { context.startActivity(fallback) }
                }
                BioOnboardingContract.Effect.Finished,
                BioOnboardingContract.Effect.Skipped -> {
                    scope.launch { navigateBack() }
                }
            }
        }
    }

    BioOnboardingScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}
