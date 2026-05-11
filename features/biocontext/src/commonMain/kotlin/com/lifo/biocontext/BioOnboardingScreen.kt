package com.lifo.biocontext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.ProviderStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 5-step Bio-Signal onboarding pager.
 *
 * Visual reference: `design/biosignal/Calmify Bio Onboarding.html`.
 * Framing: empathic, observational, never optimization-driven.
 * Per `memory/feedback_calmify_values.md` dogma 3: helpful, not optimizing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioOnboardingScreen(
    state: BioOnboardingContract.State,
    onIntent: (BioOnboardingContract.Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            PagerTop(
                stepIndex = state.stepIndex,
                totalSteps = state.totalSteps,
                onBack = { onIntent(BioOnboardingContract.Intent.Back) },
                onSkip = { onIntent(BioOnboardingContract.Intent.Skip) },
                hideSkipOnLast = state.currentStep == BioOnboardingContract.Step.Confirm,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "biosignal-onboarding-step",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CalmifySpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg),
            ) {
                Spacer(Modifier.height(CalmifySpacing.md))
                when (step) {
                    BioOnboardingContract.Step.Intro -> StepIntro(onIntent)
                    BioOnboardingContract.Step.DataTypes -> StepDataTypes(state, onIntent)
                    BioOnboardingContract.Step.Why -> StepWhy(onIntent)
                    BioOnboardingContract.Step.Permission -> StepPermission(state, onIntent)
                    BioOnboardingContract.Step.Confirm -> StepConfirm(state, onIntent)
                }
                Spacer(Modifier.height(CalmifySpacing.xxxl))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Pager chrome
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun PagerTop(
    stepIndex: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    hideSkipOnLast: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (stepIndex > 0) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Strings.BioOnboarding.back),
                )
            }
        } else {
            // placeholder for layout alignment
            Spacer(Modifier.size(48.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm)) {
            repeat(totalSteps) { i ->
                val isActive = i == stepIndex
                val isPassed = i < stepIndex
                val color = when {
                    isActive -> colorScheme.primary
                    isPassed -> colorScheme.primary.copy(alpha = 0.4f)
                    else -> colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
                Box(
                    modifier = Modifier
                        .size(if (isActive) 12.dp else 8.dp)   // active dot slightly larger
                        .background(color = color, shape = CircleShape),
                )
            }
        }

        if (!hideSkipOnLast) {
            TextButton(onClick = onSkip) {
                Text(stringResource(Strings.BioOnboarding.skip))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Step 1 — Intro
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StepIntro(onIntent: (BioOnboardingContract.Intent) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg)) {
        // Hero decorative waveform
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),                          // hero decorative section
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = colorScheme.primaryContainer.copy(alpha = 0.4f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Waves,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),  // hero icon
                        tint = colorScheme.primary,
                    )
                }
            }
        }

        Text(
            text = stringResource(Strings.BioOnboarding.introTitle),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
        )
        Text(
            text = stringResource(Strings.BioOnboarding.introBody),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CalmifySpacing.sm))

        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.introCta),
            onClick = { onIntent(BioOnboardingContract.Intent.Next) },
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Step 2 — DataTypes
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StepDataTypes(
    state: BioOnboardingContract.State,
    onIntent: (BioOnboardingContract.Intent) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg)) {
        Text(
            text = stringResource(Strings.BioOnboarding.typesTitle),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
        )
        Text(
            text = stringResource(Strings.BioOnboarding.typesSubtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )

        BioSignalDataType.entries.forEach { type ->
            DataTypeRow(
                type = type,
                enabled = type in state.enabledTypes,
                onToggle = { onIntent(BioOnboardingContract.Intent.ToggleType(type, it)) },
            )
        }

        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.cont),
            onClick = { onIntent(BioOnboardingContract.Intent.Next) },
            enabled = state.canGoNext,
        )
    }
}

@Composable
private fun DataTypeRow(
    type: BioSignalDataType,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(CalmifySpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Icon(
                imageVector = typeIcon(type),
                contentDescription = null,
                modifier = Modifier.size(24.dp),                  // M3 default icon size
                tint = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(typeLabel(type)),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(typeExplain(type)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Step 3 — Why
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StepWhy(onIntent: (BioOnboardingContract.Intent) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg)) {
        Text(
            text = stringResource(Strings.BioOnboarding.whyTitle),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
        )

        WhyCard(
            title = Strings.BioOnboarding.whyCard1Title,
            body = Strings.BioOnboarding.whyCard1Body,
            icon = Icons.Outlined.Bedtime,
        )
        WhyCard(
            title = Strings.BioOnboarding.whyCard2Title,
            body = Strings.BioOnboarding.whyCard2Body,
            icon = Icons.Outlined.Spa,
        )
        WhyCard(
            title = Strings.BioOnboarding.whyCard3Title,
            body = Strings.BioOnboarding.whyCard3Body,
            icon = Icons.Outlined.MyLocation,
        )

        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.cont),
            onClick = { onIntent(BioOnboardingContract.Intent.Next) },
        )
    }
}

@Composable
private fun WhyCard(title: StringResource, body: StringResource, icon: ImageVector) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(CalmifySpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),                 // icon badge
                shape = CircleShape,
                color = colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),         // M3 medium icon
                        tint = colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(CalmifySpacing.xs))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Step 4 — Permission
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StepPermission(
    state: BioOnboardingContract.State,
    onIntent: (BioOnboardingContract.Intent) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val status = state.providerStatus
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg)) {
        Text(
            text = stringResource(Strings.BioOnboarding.permTitle),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
        )
        Text(
            text = stringResource(Strings.BioOnboarding.permBody),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )

        // Provider status surface — install card OR grant card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CalmifyRadius.xxl),
            color = colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
                when (status) {
                    ProviderStatus.NotInstalled, ProviderStatus.NeedsUpdate -> {
                        Text(
                            text = stringResource(Strings.BioOnboarding.permInstallNeeded),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(CalmifySpacing.md))
                        PrimaryCta(
                            label = stringResource(Strings.BioOnboarding.permInstallCta),
                            onClick = { onIntent(BioOnboardingContract.Intent.InstallHealthConnect) },
                        )
                    }
                    ProviderStatus.NotSupported -> {
                        Text(
                            text = stringResource(Strings.BioContext.providerNotSupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.error,
                        )
                    }
                    is ProviderStatus.NeedsPermission -> {
                        Text(
                            text = stringResource(Strings.BioOnboarding.permGrantedSummary, state.grantedTypes.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(CalmifySpacing.md))
                        PrimaryCta(
                            label = stringResource(Strings.BioOnboarding.permGrantCta),
                            onClick = { onIntent(BioOnboardingContract.Intent.RequestPermission) },
                        )
                    }
                    ProviderStatus.Ready -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(Strings.BioContext.providerReady),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = colorScheme.primary,
                            )
                        }
                    }
                }

                if (state.isProviderCheckInProgress) {
                    Spacer(Modifier.height(CalmifySpacing.md))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.cont),
            onClick = { onIntent(BioOnboardingContract.Intent.Next) },
            enabled = state.canGoNext,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Step 5 — Confirm
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StepConfirm(
    state: BioOnboardingContract.State,
    onIntent: (BioOnboardingContract.Intent) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isConnected = state.providerStatus is ProviderStatus.Ready
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),                          // hero confirmation visual
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = if (isConnected) colorScheme.primary.copy(alpha = 0.15f) else colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isConnected) Icons.Outlined.CheckCircle else Icons.Outlined.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = if (isConnected) colorScheme.primary else colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = stringResource(
                if (isConnected) Strings.BioOnboarding.confirmTitle
                else Strings.BioOnboarding.confirmSkippedTitle
            ),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                if (isConnected) Strings.BioOnboarding.confirmBody
                else Strings.BioOnboarding.confirmSkippedBody
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )

        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.confirmCta),
            onClick = { onIntent(BioOnboardingContract.Intent.Done) },
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Shared atom — primary CTA
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xl),
        contentPadding = PaddingValues(vertical = CalmifySpacing.md, horizontal = CalmifySpacing.xl),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Helpers — type → icon / label / explain
// ──────────────────────────────────────────────────────────────────────────

private fun typeIcon(type: BioSignalDataType): ImageVector = when (type) {
    BioSignalDataType.HEART_RATE -> Icons.Outlined.MonitorHeart
    BioSignalDataType.HRV -> Icons.Outlined.Waves
    BioSignalDataType.SLEEP -> Icons.Outlined.Bedtime
    BioSignalDataType.STEPS -> Icons.Outlined.DirectionsRun
    BioSignalDataType.RESTING_HEART_RATE -> Icons.Outlined.Favorite
    BioSignalDataType.OXYGEN_SATURATION -> Icons.Outlined.Spa
    BioSignalDataType.ACTIVITY -> Icons.Outlined.DirectionsRun
}

private fun typeLabel(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioContext.typeHeartRate
    BioSignalDataType.HRV -> Strings.BioContext.typeHrv
    BioSignalDataType.SLEEP -> Strings.BioContext.typeSleep
    BioSignalDataType.STEPS -> Strings.BioContext.typeSteps
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioContext.typeRestingHeartRate
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioContext.typeOxygenSaturation
    BioSignalDataType.ACTIVITY -> Strings.BioContext.typeActivity
}

private fun typeExplain(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioOnboarding.typeHrExplain
    BioSignalDataType.HRV -> Strings.BioOnboarding.typeHrvExplain
    BioSignalDataType.SLEEP -> Strings.BioOnboarding.typeSleepExplain
    BioSignalDataType.STEPS -> Strings.BioOnboarding.typeStepsExplain
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioOnboarding.typeRestingHrExplain
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioOnboarding.typeSpo2Explain
    BioSignalDataType.ACTIVITY -> Strings.BioOnboarding.typeActivityExplain
}
