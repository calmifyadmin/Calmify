package com.lifo.meditation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.i18n.labelRes
import com.lifo.ui.i18n.subRes
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.MeditationRiskFlag
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 1 Screening screen — medical safety check before configuration.
 *
 * Anatomy (matches design):
 * - Top bar: back button + "Safety check" title
 * - Display title "Before we begin." + lede
 * - Info banner "Stop anytime if you feel dizzy, lightheaded, ..."
 * - Card with 8 risk flag rows (M3 Checkbox-styled, with optional clinical sub-text)
 * - "None of these apply to me right now" reset row at the bottom
 * - Conditional warn banner ("Gentle track only.") when any flag is ticked
 * - Fineprint medical disclaimer
 * - Bottom bar: Back (text) + Continue (primary)
 *
 * Risk screening is **enforcing**: any flag toggled on sets the state's
 * `restricted = true` flag, which the Configure screen uses to lock the
 * technique override to gentle techniques only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeditationScreeningScreen(
    selectedRisks: Set<MeditationRiskFlag>,
    onToggleRisk: (MeditationRiskFlag) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val anyFlagged = selectedRisks.isNotEmpty()

    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Strings.Meditation.Screening.topbar),
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
                        .padding(horizontal = CalmifySpacing.xl, vertical = CalmifySpacing.lg), // was 20+16 → xl(24)+lg(16)
                    horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),  // was 12.dp ✓
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.height(52.dp),                            // CTA secondary height
                    ) {
                        Text(
                            text = stringResource(Strings.Action.back),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),                                            // CTA standard height
                        shape = RoundedCornerShape(CalmifyRadius.xl),                  // was 20.dp ✓
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(Strings.Meditation.Screening.continueBtn),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.size(6.dp))                                    // micro gap text↔icon (between xs/sm)
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),                           // M3 small icon size
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
                .padding(horizontal = CalmifySpacing.xl),                  // was 20.dp → xl (24)
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg), // was 16.dp ✓
        ) {
            Spacer(Modifier.height(CalmifySpacing.sm))                     // was 8.dp ✓

            // ── Title + lede ────────────────────────────────────────────
            Text(
                text = stringResource(Strings.Meditation.Screening.title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                ),
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(Strings.Meditation.Screening.lede),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant,
            )

            // ── Info banner ─────────────────────────────────────────────
            InfoBanner(
                lead = stringResource(Strings.Meditation.Screening.bannerLead),
                body = stringResource(Strings.Meditation.Screening.bannerBody),
                tone = BannerTone.INFO,
            )

            // ── Risk flags card ─────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CalmifyRadius.xxl),             // was 24.dp → xxl (28)
                color = colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(CalmifySpacing.sm)) {    // was 8.dp ✓
                    MeditationRiskFlag.entries.forEach { flag ->
                        RiskFlagRow(
                            flag = flag,
                            checked = flag in selectedRisks,
                            onToggle = { onToggleRisk(flag) },
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = CalmifySpacing.sm, vertical = CalmifySpacing.xs), // was 8+4 ✓
                        color = colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    NoneApplyRow(
                        checked = !anyFlagged,
                        onClear = onClearAll,
                    )
                }
            }

            // ── Warn banner (conditional) ───────────────────────────────
            if (anyFlagged) {
                InfoBanner(
                    lead = stringResource(Strings.Meditation.Screening.warnLead),
                    body = stringResource(Strings.Meditation.Screening.warnBody),
                    tone = BannerTone.WARN,
                )
            }

            // ── Fineprint ───────────────────────────────────────────────
            Text(
                text = stringResource(Strings.Meditation.Screening.fineprint),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp,                                    // custom — tighter fineprint
                    letterSpacing = 0.25.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = CalmifySpacing.sm, bottom = CalmifySpacing.lg), // was 8+16 ✓
            )
        }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────

@Composable
private fun RiskFlagRow(
    flag: MeditationRiskFlag,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val labelText = stringResource(flag.labelRes)
    val subRes = flag.subRes
    val subText = subRes?.let { stringResource(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.md))                    // was 12.dp ✓
            .clickable(
                onClick = onToggle,
                role = Role.Checkbox,
            )
            .semantics {
                contentDescription = labelText + (subText?.let { " — $it" } ?: "")
            }
            .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.lg), // was 14+14 → lg (16) snap to scale
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.lg),    // was 14.dp → lg (16) snap
        verticalAlignment = Alignment.Top,
    ) {
        // Custom checkbox box matching design
        CheckBox(checked = checked)

        Column(modifier = Modifier.padding(top = 1.dp)) {                   // 1.dp baseline micro-adjust
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurface,
            )
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        letterSpacing = 0.25.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun NoneApplyRow(
    checked: Boolean,
    onClear: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.md))                    // was 12.dp ✓
            .clickable(onClick = onClear, role = Role.Button)
            .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.lg), // was 14+14 → lg snap
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.lg),    // was 14.dp → lg snap
        verticalAlignment = Alignment.Top,
    ) {
        CheckBox(checked = checked)
        Column(modifier = Modifier.padding(top = 1.dp)) {                   // 1.dp baseline micro-adjust
            Text(
                text = stringResource(Strings.Meditation.Screening.noneApply),
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.15.sp),
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(Strings.Meditation.Screening.noneApplySub),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp,
                    letterSpacing = 0.25.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (checked) colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colorScheme.onPrimary,
            )
        } else {
            // Empty box with border emulation via a same-shape stroke
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorScheme.outline.copy(alpha = 0.0f))
            ) {
                // Border via a thin inset surface
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        colorScheme.outline.copy(alpha = 0.6f),
                    ),
                ) {}
            }
        }
    }
}

private enum class BannerTone { INFO, WARN }

@Composable
private fun InfoBanner(
    lead: String,
    body: String,
    tone: BannerTone,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (bg, fg, icon: ImageVector) = when (tone) {
        BannerTone.INFO -> Triple(
            colorScheme.primaryContainer.copy(alpha = 0.3f),
            colorScheme.primary,
            Icons.Outlined.Info,
        )
        BannerTone.WARN -> Triple(
            colorScheme.errorContainer.copy(alpha = 0.3f),
            colorScheme.error,
            Icons.Outlined.ShieldMoon,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),                       // was 16.dp ✓
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md), // was 16+12 ✓
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),    // was 12.dp ✓
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(28.dp),                                // icon container — content-driven
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),                        // M3 medium icon size
                        tint = fg,
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = lead,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.15.sp,
                    ),
                    color = colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        letterSpacing = 0.25.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
