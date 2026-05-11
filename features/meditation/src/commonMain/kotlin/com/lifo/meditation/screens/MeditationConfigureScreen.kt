package com.lifo.meditation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.meditation.MeditationContract
import com.lifo.ui.i18n.Strings
import com.lifo.ui.i18n.labelRes
import com.lifo.ui.i18n.nameRes
import com.lifo.ui.i18n.shortRes
import com.lifo.ui.i18n.summaryRes
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationAudio
import com.lifo.util.model.MeditationExperience
import com.lifo.util.model.MeditationGoal
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 1 Configure screen — duration / goal / experience / audio / technique.
 *
 * Anatomy:
 * - Top bar: back button + "Set up your session" title
 * - Display title "How long, what for." + optional "Gentle track" lockpill (when restricted)
 * - 5 cards (each is a [SectionCard] with grid choices):
 *   1. Duration: 5 buttons (3/5/10/15/20)
 *   2. Goal: 5 buttons with icons (5 cols × 1 row, fits via 3 cols + wrap)
 *   3. Experience: 3 buttons + first-time conditional banner
 *   4. Audio: 3 buttons with icons
 *   5. Technique: 6 (or 2 in restricted mode) tiles + auto-pill + selected summary box + 4-cycle-max pill
 * - Fineprint with "Re-do safety check" link
 * - Bottom bar: Back + "Begin breathing"
 *
 * Technique override is locked for first-time users (COHERENT only) and
 * restricted users (BELLY/SCAN only). The state's [MeditationContract.State.techniqueOverridable]
 * computed property gates the override interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeditationConfigureScreen(
    state: MeditationContract.State,
    onBack: () -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetGoal: (MeditationGoal) -> Unit,
    onSetExperience: (MeditationExperience) -> Unit,
    onSetAudio: (MeditationAudio) -> Unit,
    onSetTechniqueOverride: (BreathingPattern?) -> Unit,
    onRedoScreening: () -> Unit,
    onBegin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val cfg = state.config
    val effectiveTech = state.effectiveTechnique
    val techniqueOptions: List<BreathingPattern> = if (state.restricted) {
        listOf(BreathingPattern.BELLY_NATURAL, BreathingPattern.BODY_SCAN_NATURAL)
    } else {
        // Order matches design: coherent / exhale / box / 478 / belly / scan
        listOf(
            BreathingPattern.COHERENT,
            BreathingPattern.EXTENDED_EXHALE,
            BreathingPattern.BOX_BREATHING,
            BreathingPattern.RELAXATION_478,
            BreathingPattern.BELLY_NATURAL,
            BreathingPattern.BODY_SCAN_NATURAL,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Strings.Meditation.Configure.topbar),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.Action.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CalmifySpacing.xl, vertical = CalmifySpacing.lg), // was 20+16 → xl+lg
                    horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),  // was 12.dp ✓
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.height(52.dp)) {  // CTA height
                        Text(
                            text = stringResource(Strings.Action.back),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Button(
                        onClick = onBegin,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(CalmifyRadius.xl),                  // was 20.dp ✓
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(Strings.Meditation.Configure.cta),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))                                   // micro gap text↔icon
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),                            // M3 medium icon
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CalmifySpacing.xl),                 // was 20.dp → xl (24)
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md),// was 12.dp ✓
        ) {
            Spacer(Modifier.height(CalmifySpacing.sm))                    // was 8.dp ✓

            // ── Title + lockpill ────────────────────────────────────────
            Text(
                text = stringResource(Strings.Meditation.Configure.title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                ),
                color = colorScheme.onSurface,
            )
            if (state.restricted) {
                LockPill()
            }

            Spacer(Modifier.height(CalmifySpacing.sm))                    // was 8.dp ✓

            // ── 1. Duration ─────────────────────────────────────────────
            SectionCard(
                title = stringResource(Strings.Meditation.Configure.cardDuration),
                subtitle = stringResource(Strings.Meditation.Configure.cardDurationSub),
            ) {
                EvenChoiceRow(items = listOf(3, 5, 10, 15, 20)) { mins ->
                    DurationTile(
                        minutes = mins,
                        selected = cfg.duration == mins,
                        onClick = { onSetDuration(mins) },
                    )
                }
            }

            // ── 2. Goal ─────────────────────────────────────────────────
            SectionCard(
                title = stringResource(Strings.Meditation.Configure.cardGoal),
                subtitle = stringResource(Strings.Meditation.Configure.cardGoalSub),
            ) {
                ChoiceFlow(
                    items = MeditationGoal.entries,
                    chunkSize = 3,
                ) { goal ->
                    GoalTile(
                        goal = goal,
                        selected = cfg.goal == goal,
                        onClick = { onSetGoal(goal) },
                    )
                }
            }

            // ── 3. Experience ───────────────────────────────────────────
            SectionCard(
                title = stringResource(Strings.Meditation.Configure.cardExperience),
                subtitle = null,
            ) {
                EvenChoiceRow(items = MeditationExperience.entries.toList()) { exp ->
                    ExperienceTile(
                        exp = exp,
                        selected = cfg.experience == exp,
                        onClick = { onSetExperience(exp) },
                    )
                }
                if (cfg.experience == MeditationExperience.FIRST) {
                    Spacer(Modifier.height(CalmifySpacing.md))            // was 12.dp ✓
                    InfoChip(
                        text = stringResource(Strings.Meditation.Configure.firstTimeBanner),
                        icon = Icons.Outlined.Favorite,
                    )
                }
            }

            // ── 4. Audio ────────────────────────────────────────────────
            SectionCard(
                title = stringResource(Strings.Meditation.Configure.cardAudio),
                subtitle = null,
            ) {
                EvenChoiceRow(items = MeditationAudio.entries.toList()) { audio ->
                    AudioTile(
                        audio = audio,
                        selected = cfg.audio == audio,
                        onClick = { onSetAudio(audio) },
                    )
                }
            }

            // ── 5. Technique ────────────────────────────────────────────
            TechniqueCard(
                state = state,
                techniqueOptions = techniqueOptions,
                effectiveTechnique = effectiveTech,
                onSelect = { selected ->
                    // If user picks the auto-resolved one, treat as "clear override"
                    val autoForCurrentConfig = state.copy(config = state.config.copy(techniqueOverride = null)).effectiveTechnique
                    onSetTechniqueOverride(if (selected == autoForCurrentConfig) null else selected)
                },
            )

            // ── Fineprint ───────────────────────────────────────────────
            FineprintWithLink(onLinkClick = onRedoScreening)
            Spacer(Modifier.height(CalmifySpacing.sm))                    // was 8.dp ✓
        }
    }
}

// ── Sub-components: layout primitives ────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),                    // was 24.dp → xxl (28)
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {           // was 20.dp → xl (24)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,                                // custom — fineprint
                        letterSpacing = 0.25.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = CalmifySpacing.xs), // was 4.dp ✓
                )
            }
            Spacer(Modifier.height(CalmifySpacing.lg))                     // was 16.dp ✓
            content()
        }
    }
}

/** Even-distribution row: each item gets equal width via `weight(1f)`. */
@Composable
private fun <T> EvenChoiceRow(
    items: List<T>,
    item: @Composable (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),   // was 8.dp ✓
    ) {
        items.forEach { value ->
            Box(modifier = Modifier.weight(1f)) {
                item(value)
            }
        }
    }
}

/** FlowRow-like helper: chunks items into rows of [chunkSize], spaced. */
@Composable
private fun <T> ChoiceFlow(
    items: List<T>,
    chunkSize: Int,
    item: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.sm)) {     // was 8.dp ✓
        items.chunked(chunkSize).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm)) { // was 8.dp ✓
                rowItems.forEach { value ->
                    Box(modifier = Modifier.weight(1f)) {
                        item(value)
                    }
                }
                // Pad incomplete row to maintain consistent width
                repeat(chunkSize - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Sub-components: tiles ────────────────────────────────────────────────

@Composable
private fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = when {
        !enabled -> colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        selected -> colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    }
    val border = if (selected) {
        androidx.compose.foundation.BorderStroke(1.5.dp, colorScheme.primary)
    } else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))                    // was 16.dp ✓
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(CalmifyRadius.lg),                      // was 16.dp ✓
        color = bg,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CalmifySpacing.lg, horizontal = CalmifySpacing.md), // was 14+12 → lg+md snap
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun DurationTile(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    ChoiceTile(selected = selected, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$minutes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(Strings.Meditation.Configure.durationMinSuffix),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun GoalTile(
    goal: MeditationGoal,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when (goal) {
        MeditationGoal.STRESS -> Icons.Outlined.SelfImprovement
        MeditationGoal.FOCUS -> Icons.Outlined.Visibility
        MeditationGoal.SLEEP -> Icons.Outlined.Bedtime
        MeditationGoal.ANXIETY -> Icons.Outlined.Spa
        MeditationGoal.GROUNDING -> Icons.Outlined.Public
    }
    val colorScheme = MaterialTheme.colorScheme
    ChoiceTile(selected = selected, onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(goal.labelRes),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExperienceTile(
    exp: MeditationExperience,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    ChoiceTile(selected = selected, onClick = onClick) {
        Text(
            text = stringResource(exp.labelRes),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AudioTile(
    audio: MeditationAudio,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when (audio) {
        MeditationAudio.VOICE -> Icons.Outlined.RecordVoiceOver
        MeditationAudio.CHIMES -> Icons.Outlined.NotificationsActive
        MeditationAudio.SILENT -> Icons.AutoMirrored.Outlined.VolumeOff
    }
    val colorScheme = MaterialTheme.colorScheme
    ChoiceTile(selected = selected, onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(audio.labelRes),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Technique card (special) ─────────────────────────────────────────────

@Composable
private fun TechniqueCard(
    state: MeditationContract.State,
    techniqueOptions: List<BreathingPattern>,
    effectiveTechnique: BreathingPattern,
    onSelect: (BreathingPattern) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val subText = when {
        state.restricted -> Strings.Meditation.Configure.techSubRestricted
        state.config.experience == MeditationExperience.FIRST -> Strings.Meditation.Configure.techSubFirstTime
        else -> Strings.Meditation.Configure.techSubOverridable
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),                     // was 24.dp → xxl (28)
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {            // was 20.dp → xl (24)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md), // was 12.dp ✓
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Strings.Meditation.Configure.cardTechnique),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(subText),
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,                            // custom — tighter fineprint
                            letterSpacing = 0.25.sp,
                        ),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = CalmifySpacing.xs), // was 4.dp ✓
                    )
                }
                if (state.config.techniqueOverride == null && !state.restricted) {
                    AutoPill()
                }
            }

            Spacer(Modifier.height(CalmifySpacing.lg))                     // was 16.dp ✓

            // Technique grid: 2 columns
            Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.sm)) { // was 8.dp ✓
                techniqueOptions.chunked(2).forEach { rowTechs ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm)) { // was 8.dp ✓
                        rowTechs.forEach { tech ->
                            Box(modifier = Modifier.weight(1f)) {
                                TechniqueTile(
                                    technique = tech,
                                    selected = effectiveTechnique == tech,
                                    enabled = state.techniqueOverridable || tech == effectiveTechnique,
                                    onClick = { onSelect(tech) },
                                )
                            }
                        }
                        if (rowTechs.size < 2) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Selected technique summary box
            Spacer(Modifier.height(CalmifySpacing.lg))                     // was 16.dp ✓
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CalmifyRadius.lg),              // was 16.dp ✓
                color = colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(CalmifySpacing.lg)) {    // was 16.dp ✓
                    Text(
                        text = stringResource(effectiveTechnique.nameRes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(effectiveTechnique.summaryRes),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), // custom line-height
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = CalmifySpacing.xs), // was 4.dp ✓
                    )
                }
            }
        }
    }
}

@Composable
private fun TechniqueTile(
    technique: BreathingPattern,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = when {
        !enabled -> colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        selected -> colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    }
    val border = if (selected) {
        androidx.compose.foundation.BorderStroke(1.5.dp, colorScheme.primary)
    } else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))                    // was 16.dp ✓
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(CalmifyRadius.lg),                      // was 16.dp ✓
        color = bg,
        border = border,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.lg)) {            // was 14.dp → lg (16) snap
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),         // micro gap text↔cap-pill
            ) {
                Text(
                    text = stringResource(technique.nameRes),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                )
                if (technique.cycleCap != null) {
                    CapPill()
                }
            }
            Text(
                text = stringResource(technique.shortRes),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = CalmifySpacing.xs),       // was 4.dp ✓
            )
        }
    }
}

// ── Pills + chips ────────────────────────────────────────────────────────

@Composable
private fun AutoPill() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(CalmifyRadius.pill),                   // was 999.dp ✓
        color = colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = colorScheme.primary,
            )
            Text(
                text = stringResource(Strings.Meditation.Configure.techAutoPill).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                ),
                color = colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CapPill() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(CalmifyRadius.pill),                   // was 999.dp ✓
        color = colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(Strings.Meditation.Configure.tech478Cap),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LockPill() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(CalmifyRadius.pill),                   // was 999.dp ✓
        color = colorScheme.errorContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ShieldMoon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colorScheme.error,
            )
            Text(
                text = stringResource(Strings.Meditation.Configure.lockpill).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                ),
                color = colorScheme.error,
            )
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    icon: ImageVector,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),                      // was 16.dp ✓
        color = colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md), // was 16+12 ✓
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),// was 12.dp ✓
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),                            // M3 medium icon size
                tint = colorScheme.primary,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun FineprintWithLink(onLinkClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val fineprintText = stringResource(Strings.Meditation.Configure.fineprint)
    val linkText = stringResource(Strings.Meditation.Configure.redoScreeningLink)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLinkClick)
            .padding(top = CalmifySpacing.sm, bottom = CalmifySpacing.lg), // was 8+16 ✓
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f))) {
                    append(fineprintText)
                    append(" ")
                }
                withStyle(
                    SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append(linkText)
                }
                withStyle(SpanStyle(color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f))) {
                    append(".")
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
                letterSpacing = 0.25.sp,
            ),
        )
    }
}
