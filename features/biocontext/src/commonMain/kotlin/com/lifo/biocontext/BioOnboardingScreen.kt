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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbTwilight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onOpenPlayStore: (packageId: String) -> Unit = {},
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
                    BioOnboardingContract.Step.Permission -> StepPermission(state, onIntent, onOpenPlayStore)
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
    val cs = MaterialTheme.colorScheme
    var openInfoFor by remember { mutableStateOf<BioSignalDataType?>(null) }
    val allOn = state.enabledTypes.size == BioSignalDataType.entries.size
    val someOn = state.enabledTypes.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ── Eyebrow + display title + lede ──────────────────────────────────
        EyebrowTag(
            icon = Icons.Outlined.Dataset,
            label = stringResource(Strings.BioOnboarding.typesEyebrow),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Strings.BioOnboarding.typesTitle),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            color = cs.onSurface,
        )
        Spacer(Modifier.height(CalmifySpacing.sm))
        Text(
            text = stringResource(Strings.BioOnboarding.typesSubtitle),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, letterSpacing = 0.15.sp),
            color = cs.onSurfaceVariant,
        )

        // ── Quick toggle: all on / off ──────────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.lg))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CalmifyRadius.lg),
            color = cs.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            allOn -> stringResource(Strings.BioOnboarding.typesQuickAllOn)
                            someOn -> stringResource(
                                Strings.BioOnboarding.typesQuickSomeOn,
                                state.enabledTypes.size,
                                BioSignalDataType.entries.size,
                            )
                            else -> stringResource(Strings.BioOnboarding.typesQuickAllOff)
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(Strings.BioOnboarding.typesQuickSub),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = cs.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = allOn,
                    onCheckedChange = { newAll ->
                        BioSignalDataType.entries.forEach { type ->
                            val currentlyOn = type in state.enabledTypes
                            if (currentlyOn != newAll) {
                                onIntent(BioOnboardingContract.Intent.ToggleType(type, newAll))
                            }
                        }
                    },
                )
            }
        }

        // ── Data list card ──────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CalmifyRadius.xxl),
            color = cs.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(CalmifySpacing.md)) {
                BioSignalDataType.entries.forEachIndexed { idx, type ->
                    if (idx > 0) Spacer(Modifier.height(6.dp))
                    DataTypeRow(
                        type = type,
                        enabled = type in state.enabledTypes,
                        infoOpen = openInfoFor == type,
                        onToggle = { onIntent(BioOnboardingContract.Intent.ToggleType(type, it)) },
                        onToggleInfo = { openInfoFor = if (openInfoFor == type) null else type },
                    )
                }
            }
        }

        // ── Fineprint ───────────────────────────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.md))
        Text(
            text = stringResource(Strings.BioOnboarding.typesFineprint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )

        // ── Footer: Continue + Skip ────────────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.xl))
        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.cont),
            onClick = { onIntent(BioOnboardingContract.Intent.Next) },
            enabled = state.canGoNext,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
        TextButton(
            onClick = { onIntent(BioOnboardingContract.Intent.Skip) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Strings.BioOnboarding.skip),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataTypeRow(
    type: BioSignalDataType,
    enabled: Boolean,
    infoOpen: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleInfo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.md))
            .background(
                if (enabled) cs.primary.copy(alpha = 0.06f)
                else cs.surfaceContainerHigh,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.md))
                    .background(
                        if (enabled) cs.primary.copy(alpha = 0.14f)
                        else cs.surfaceContainerHighest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeIcon(type),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) cs.primary else cs.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(typeLabel(type)),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Text(
                    text = stringResource(typeWhy(type)),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = cs.onSurfaceVariant,
                )
            }
            // Info button (toggles expandable explain panel below)
            IconButton(onClick = onToggleInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (infoOpen) Icons.Outlined.Close else Icons.Outlined.HelpOutline,
                    contentDescription = stringResource(Strings.BioOnboarding.typesInfoA11y),
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (infoOpen) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CalmifyRadius.sm),
                color = cs.background.copy(alpha = 0.4f),
            ) {
                Text(
                    text = stringResource(typeExplain(type)),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.15.sp,
                    ),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Step 3 — Why
// ──────────────────────────────────────────────────────────────────────────

private data class UseCaseMockup(
    val icon: ImageVector,
    val titleRes: StringResource,
    val quoteRes: StringResource,
    val metaRes: StringResource,
)

@Composable
private fun StepWhy(onIntent: (BioOnboardingContract.Intent) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val useCases = listOf(
        UseCaseMockup(
            icon = Icons.Outlined.EditNote,
            titleRes = Strings.BioOnboarding.whyCard1Title,
            quoteRes = Strings.BioOnboarding.whyCard1Body,
            metaRes = Strings.BioOnboarding.whyCard1Meta,
        ),
        UseCaseMockup(
            icon = Icons.Outlined.SelfImprovement,
            titleRes = Strings.BioOnboarding.whyCard2Title,
            quoteRes = Strings.BioOnboarding.whyCard2Body,
            metaRes = Strings.BioOnboarding.whyCard2Meta,
        ),
        UseCaseMockup(
            icon = Icons.Outlined.WbTwilight,
            titleRes = Strings.BioOnboarding.whyCard3Title,
            quoteRes = Strings.BioOnboarding.whyCard3Body,
            metaRes = Strings.BioOnboarding.whyCard3Meta,
        ),
    )

    Column {
        EyebrowTag(
            icon = Icons.Outlined.Lightbulb,
            label = stringResource(Strings.BioOnboarding.whyEyebrow),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Strings.BioOnboarding.whyTitle),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            color = cs.onSurface,
        )
        Spacer(Modifier.height(CalmifySpacing.sm))
        Text(
            text = stringResource(Strings.BioOnboarding.whyLede),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, letterSpacing = 0.15.sp),
            color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(CalmifySpacing.xl))
        useCases.forEachIndexed { idx, uc ->
            if (idx > 0) Spacer(Modifier.height(12.dp))
            UseCaseCard(uc)
        }

        Spacer(Modifier.height(CalmifySpacing.md))
        Text(
            text = stringResource(Strings.BioOnboarding.whyFineprint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(CalmifySpacing.xl))
        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.cont),
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
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UseCaseCard(uc: UseCaseMockup) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(CalmifySpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = uc.icon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(uc.titleRes),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(uc.quoteRes),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.1.sp,
                    ),
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Insights,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(uc.metaRes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                }
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
    onOpenPlayStore: (packageId: String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val status = state.providerStatus
    val isReady = status is ProviderStatus.Ready
    val notInstalled = status is ProviderStatus.NotInstalled || status is ProviderStatus.NeedsUpdate

    Column {
        EyebrowTag(
            icon = Icons.Outlined.Lock,
            label = stringResource(Strings.BioOnboarding.permEyebrow),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Strings.BioOnboarding.permTitle),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            color = cs.onSurface,
        )
        Spacer(Modifier.height(CalmifySpacing.sm))
        Text(
            text = stringResource(Strings.BioOnboarding.permBody),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, letterSpacing = 0.15.sp),
            color = cs.onSurfaceVariant,
        )

        // ── Flow diagram: Wearable → Health hub → Calmify ──────────────────
        Spacer(Modifier.height(CalmifySpacing.xl))
        FlowDiagram()

        // ── Phase 9.4 — wearable sync tip card ─────────────────────────────
        // The flow diagram above shows the indirect chain (Wearable → HC → Calmify)
        // visually. Users with non-Samsung wearables still routinely miss that the
        // FIRST step (enable HC sync in the vendor app) must happen in their wearable
        // app — not here. Surface it explicitly + give them Play Store shortcuts.
        Spacer(Modifier.height(CalmifySpacing.lg))
        WearableSyncTipCard(onOpenPlayStore = onOpenPlayStore)

        // ── 3 promise rows ─────────────────────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.xl))
        PromiseRow(
            icon = Icons.Outlined.VisibilityOff,
            mainRes = Strings.BioOnboarding.permPromise1Main,
            subRes = Strings.BioOnboarding.permPromise1Sub,
        )
        Spacer(Modifier.height(8.dp))
        PromiseRow(
            icon = Icons.Outlined.CloudOff,
            mainRes = Strings.BioOnboarding.permPromise2Main,
            subRes = Strings.BioOnboarding.permPromise2Sub,
        )
        Spacer(Modifier.height(8.dp))
        PromiseRow(
            icon = Icons.Outlined.ToggleOff,
            mainRes = Strings.BioOnboarding.permPromise3Main,
            subRes = Strings.BioOnboarding.permPromise3Sub,
        )

        // ── Install card (only when HC not installed) ──────────────────────
        if (notInstalled) {
            Spacer(Modifier.height(CalmifySpacing.lg))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CalmifyRadius.lg),
                color = cs.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(CalmifyRadius.md))
                            .background(cs.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Strings.BioOnboarding.permInstallCardTitle),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                            color = cs.onSurface,
                        )
                        Text(
                            text = stringResource(Strings.BioOnboarding.permInstallCardSub),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = cs.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onIntent(BioOnboardingContract.Intent.InstallHealthConnect) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(Strings.BioOnboarding.permInstallCardCta),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ),
                            color = cs.primary,
                        )
                    }
                }
            }
        }

        // ── Permission granted state ───────────────────────────────────────
        if (isReady) {
            Spacer(Modifier.height(CalmifySpacing.lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CalmifyRadius.lg))
                    .background(cs.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(Strings.BioOnboarding.permGrantedState),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = cs.primary,
                )
            }
        }

        if (state.isProviderCheckInProgress) {
            Spacer(Modifier.height(CalmifySpacing.md))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        // ── Fineprint ───────────────────────────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.md))
        Text(
            text = stringResource(Strings.BioOnboarding.permFineprint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )

        // ── Footer: Grant primary (or Continue if ready) + Not now ─────────
        Spacer(Modifier.height(CalmifySpacing.xl))
        if (isReady) {
            PrimaryCta(
                label = stringResource(Strings.BioOnboarding.cont),
                onClick = { onIntent(BioOnboardingContract.Intent.Next) },
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
            )
        } else {
            PrimaryCta(
                label = stringResource(Strings.BioOnboarding.permGrantCta),
                onClick = { onIntent(BioOnboardingContract.Intent.RequestPermission) },
                enabled = !notInstalled,
                trailingIcon = Icons.Outlined.CheckCircle,
            )
        }
        TextButton(
            onClick = { onIntent(BioOnboardingContract.Intent.Skip) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Strings.BioOnboarding.permSkipCta),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowDiagram() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = cs.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FlowNode(
                    icon = Icons.Outlined.Watch,
                    label = stringResource(Strings.BioOnboarding.flowWearable),
                    sub = stringResource(Strings.BioOnboarding.flowSource),
                    accent = false,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
                FlowNode(
                    icon = Icons.Outlined.Hub,
                    label = stringResource(Strings.BioOnboarding.flowHealthHub),
                    sub = stringResource(Strings.BioOnboarding.flowOnDevice),
                    accent = false,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
                FlowNode(
                    icon = Icons.Outlined.Spa,
                    label = stringResource(Strings.BioOnboarding.flowCalmify),
                    sub = stringResource(Strings.BioOnboarding.flowReadsOnly),
                    accent = true,
                )
            }
            Spacer(Modifier.height(CalmifySpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .background(cs.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(Strings.BioOnboarding.flowCaption),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                        ),
                        color = cs.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowNode(icon: ImageVector, label: String, sub: String, accent: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (accent) cs.primary.copy(alpha = 0.16f)
                    else cs.surfaceContainerHigh,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = cs.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
        )
        Text(
            text = sub.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
            color = cs.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Phase 9.4 — wearable sync tip card shown in StepPermission. Tells users with
 * non-Samsung wearables (Mi Band, Fitbit, Garmin, …) that they need to enable
 * Health Connect sync inside the wearable's vendor app FIRST, then come back.
 * 3 quick-link chips deep-link to Play Store via [onOpenPlayStore].
 */
@Composable
private fun WearableSyncTipCard(onOpenPlayStore: (packageId: String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(cs.surfaceContainerLow)
            .padding(CalmifySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.md))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Watch,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(Strings.BioWearable.tipTitle),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = cs.onSurface,
            )
        }
        Text(
            text = stringResource(Strings.BioWearable.tipBody),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = cs.onSurfaceVariant,
        )
        Text(
            text = stringResource(Strings.BioWearable.tipStep1),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Text(
            text = stringResource(Strings.BioWearable.tipStep2),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Text(
            text = stringResource(Strings.BioWearable.tipStep3),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // 3 quick-link chips — Mi Fitness, Fitbit, Garmin. Add more brands later if data shows demand.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WearableChip(label = stringResource(Strings.BioWearable.chipMi), packageId = "com.mi.health", onOpen = onOpenPlayStore)
            WearableChip(label = stringResource(Strings.BioWearable.chipFitbit), packageId = "com.fitbit.FitbitMobile", onOpen = onOpenPlayStore)
            WearableChip(label = stringResource(Strings.BioWearable.chipGarmin), packageId = "com.garmin.android.apps.connectmobile", onOpen = onOpenPlayStore)
        }
    }
}

@Composable
private fun WearableChip(
    label: String,
    packageId: String,
    onOpen: (packageId: String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val a11y = stringResource(Strings.BioWearable.chipA11y, label)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CalmifyRadius.pill))
            .background(cs.primary.copy(alpha = 0.12f))
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = a11y,
            ) { onOpen(packageId) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = cs.primary,
        )
    }
}

@Composable
private fun PromiseRow(icon: ImageVector, mainRes: StringResource, subRes: StringResource) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.md))
            .background(cs.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(mainRes),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(subRes),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = cs.onSurfaceVariant,
            )
        }
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
    val cs = MaterialTheme.colorScheme
    val isConnected = state.providerStatus is ProviderStatus.Ready &&
        state.enabledTypes.isNotEmpty()

    Column {
        // ── Confirm hero (orb + h1 + sub) ──────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.lg))
        ConfirmOrb()
        Spacer(Modifier.height(CalmifySpacing.xl))
        Text(
            text = stringResource(
                if (isConnected) Strings.BioOnboarding.confirmTitle
                else Strings.BioOnboarding.confirmSkippedTitle,
            ),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            ),
            color = cs.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CalmifySpacing.sm))
        Text(
            text = stringResource(
                if (isConnected) Strings.BioOnboarding.confirmBody
                else Strings.BioOnboarding.confirmSkippedBody,
            ),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp, letterSpacing = 0.15.sp),
            color = cs.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Summary card 1: enabled types as chips OR skipped state ─────────
        Spacer(Modifier.height(CalmifySpacing.xl))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CalmifyRadius.xxl),
            color = cs.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
                Text(
                    text = stringResource(
                        if (isConnected) Strings.BioOnboarding.confirmSummaryTitle
                        else Strings.BioOnboarding.confirmSummarySkippedTitle,
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (isConnected) Strings.BioOnboarding.confirmSummarySub
                        else Strings.BioOnboarding.confirmSummarySkippedSub,
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(CalmifySpacing.md))
                if (isConnected) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.enabledTypes.forEach { type ->
                            ConfirmChip(typeIcon(type), stringResource(typeLabel(type)), accent = true)
                        }
                    }
                } else {
                    ConfirmChip(
                        icon = Icons.Outlined.SensorsOff,
                        label = stringResource(Strings.BioOnboarding.confirmChipNoSignals),
                        accent = false,
                    )
                }
            }
        }

        // ── Summary card 2: where to find this again ────────────────────────
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CalmifyRadius.xxl),
            color = cs.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
                Text(
                    text = stringResource(Strings.BioOnboarding.confirmWhereTitle),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Strings.BioOnboarding.confirmWhereSub),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = cs.onSurfaceVariant,
                )
            }
        }

        // ── Source-attribution fineprint ────────────────────────────────────
        Spacer(Modifier.height(CalmifySpacing.lg))
        Text(
            text = stringResource(Strings.BioOnboarding.confirmAttribution),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(CalmifySpacing.xl))
        PrimaryCta(
            label = stringResource(Strings.BioOnboarding.confirmCta),
            onClick = { onIntent(BioOnboardingContract.Intent.Done) },
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
    }
}

@Composable
private fun ConfirmOrb() {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            cs.primary.copy(alpha = 0.35f),
                            cs.primary.copy(alpha = 0.12f),
                            cs.primary.copy(alpha = 0.0f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(cs.primary),
        )
    }
}

@Composable
private fun ConfirmChip(icon: ImageVector, label: String, accent: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CalmifyRadius.pill))
            .background(
                if (accent) cs.primary.copy(alpha = 0.12f)
                else cs.surfaceContainerHigh,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (accent) cs.onSurface else cs.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Shared atom — eyebrow tag (icon + uppercase label)
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun EyebrowTag(icon: ImageVector, label: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.2.sp,
            ),
            color = cs.primary,
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

/** Short "why" copy shown inline on each data-row (vs the expandable "explain" panel). */
private fun typeWhy(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioOnboarding.typeHrWhy
    BioSignalDataType.HRV -> Strings.BioOnboarding.typeHrvWhy
    BioSignalDataType.SLEEP -> Strings.BioOnboarding.typeSleepWhy
    BioSignalDataType.STEPS -> Strings.BioOnboarding.typeStepsWhy
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioOnboarding.typeRestingHrWhy
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioOnboarding.typeSpo2Why
    BioSignalDataType.ACTIVITY -> Strings.BioOnboarding.typeActivityWhy
}
