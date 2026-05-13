package com.lifo.biocontext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Sensors
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.accessibility.isReducedMotionEnabled
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.ProviderStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin

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
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md)) {
        // Eyebrow tag — uppercase, accent, with sensor icon (1:1 with HTML .eyebrow class)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),                        // 6dp gap matches HTML
        ) {
            Icon(
                imageVector = Icons.Outlined.Sensors,
                contentDescription = null,
                modifier = Modifier.size(14.dp),                                       // 14sp icon per HTML
                tint = colorScheme.primary,
            )
            Text(
                text = stringResource(Strings.BioOnboarding.introEyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,                                            // matches HTML letter-spacing
                ),
                color = colorScheme.primary,
            )
        }

        Spacer(Modifier.height(CalmifySpacing.md))                                     // ~12dp margin-top per HTML

        // Display title — 32sp/40sp SemiBold, letter-spacing -0.3
        Text(
            text = stringResource(Strings.BioOnboarding.introTitle),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            color = colorScheme.onSurface,
        )

        // Lede — 16sp/26sp regular, fg-subtle
        Text(
            text = stringResource(Strings.BioOnboarding.introBody),
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 26.sp,                                                    // matches HTML 16/26
                letterSpacing = 0.15.sp,
            ),
            color = colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CalmifySpacing.md))

        // Hero animated breath-wave visual — 220dp, radial gradient, rounded-2xl
        BreathWaveVisual(modifier = Modifier.fillMaxWidth())

        // Fineprint — 12sp/18sp, fg-subtle, margin-top 20dp
        Spacer(Modifier.height(CalmifySpacing.md))                                     // gap before fineprint
        Text(
            text = stringResource(Strings.BioOnboarding.introFineprint),
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
                letterSpacing = 0.25.sp,
            ),
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(CalmifySpacing.xl))

        // Footer: Continue primary + Skip text (matches HTML footer)
        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.introCta),
            onClick = { onIntent(BioOnboardingContract.Intent.Next) },
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
        TextButton(
            onClick = { onIntent(BioOnboardingContract.Intent.Skip) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Strings.BioOnboarding.skip),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Animated breath-waveform visual (1:1 port of HTML mockup's `BreathWaveVisual`).
 *
 * Renders 3 sine-wave paths (2 faint background + 1 main accent line) animated
 * with a slow 5.5s breath modulation + a small glowing bead traveling along the
 * main wave. Inside a radial-gradient rounded-2xl container, 220dp tall.
 *
 * Respects `prefers-reduced-motion` — falls back to a static rendering with
 * t = 0 to preserve the visual but stop the animation.
 */
@Composable
private fun BreathWaveVisual(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val reducedMotion = isReducedMotionEnabled()

    val t: Float = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "breathWave")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 11f,                          // 2 breath cycles before loop
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "breathT",
        )
        animated
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(CalmifyRadius.xxl))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            fun buildPath(phase: Float, amp: Float): Path {
                val path = Path()
                val breath = sin((t + phase).toDouble() * (PI / 5.5)).toFloat()
                val ampMod = amp * (0.5f + 0.5f * breath)
                var x = 0f
                while (x <= w) {
                    val k = x / w
                    val y = midY +
                        (sin((k * PI * 4 + t * 1.2 + phase).toDouble()).toFloat()) * ampMod
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 6f
                }
                return path
            }

            // 2 faint waves behind
            drawPath(
                path = buildPath(phase = 2.2f, amp = h * 0.20f),
                color = accent.copy(alpha = 0.25f),
                style = Stroke(width = 1f),
            )
            drawPath(
                path = buildPath(phase = 1.0f, amp = h * 0.18f),
                color = accent.copy(alpha = 0.25f),
                style = Stroke(width = 1f),
            )
            // Main wave on top
            drawPath(
                path = buildPath(phase = 0f, amp = h * 0.14f),
                color = accent.copy(alpha = 0.85f),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Glowing bead traveling along the main wave
            val beadX = ((t * 60f) % (w + 40f)) - 20f
            val beadK = beadX / w
            val beadBreath = sin(t.toDouble() * (PI / 5.5)).toFloat()
            val beadY = midY +
                (sin((beadK * PI * 4 + t * 1.2).toDouble()).toFloat()) *
                    (h * 0.14f * (0.5f + 0.5f * beadBreath))
            // Soft halo
            drawCircle(
                color = accent.copy(alpha = 0.30f),
                radius = 12f,
                center = Offset(beadX, beadY),
            )
            // Bright center
            drawCircle(
                color = Color(0xFFA8F0CB),                   // accent-bright tone 90 from theme
                radius = 4.5.dp.toPx(),
                center = Offset(beadX, beadY),
            )
        }
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
    trailingIcon: ImageVector? = null,
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
        if (trailingIcon != null) {
            Spacer(Modifier.size(CalmifySpacing.sm))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),                            // 18sp icon per HTML
            )
        }
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
