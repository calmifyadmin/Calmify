package com.lifo.meditation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.meditation.MeditationContract
import com.lifo.ui.i18n.Strings
import com.lifo.ui.i18n.mechanismRes
import com.lifo.ui.i18n.nameRes
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 1 Overview screen — post-session reflection.
 *
 * Anatomy (matches design):
 * - Top bar: leaf logo + "Reflection" title (no back button — flow is forward-only here)
 * - Hero: "Session complete" pill + display title (success vs stopped) + lede with technique+duration+cycles
 * - 4 cards:
 *   1. Mechanism explainer (technique name + research-grounded explanation)
 *   2. "What you may notice now" — 3 bullets (favorite / psychology / bedtime icons)
 *   3. "With regular practice (4-8 weeks)" — body paragraph
 *   4. "Try it in daily life" — 2 bullets (alarm + warning icons), second uses technique-name template
 * - Medical disclaimer banner
 * - Bottom bar: "Different session" (text) + "Redo" (primary)
 *
 * The screen is dynamic — title + lede + mechanism + daily-life template all
 * depend on the [MeditationContract.SessionRuntime] passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeditationOverviewScreen(
    runtime: MeditationContract.SessionRuntime,
    onDifferent: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val techniqueName = stringResource(runtime.technique.nameRes)
    val titleRes = if (runtime.stopped) {
        Strings.Meditation.Overview.titleStopped
    } else {
        Strings.Meditation.Overview.titleDone
    }

    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Strings.Meditation.Overview.topbar),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = { OverviewLeafLogo() },
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
                    TextButton(onClick = onDifferent, modifier = Modifier.height(52.dp)) { // CTA height
                        Text(
                            text = stringResource(Strings.Meditation.Overview.btnDifferent),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Button(
                        onClick = onRedo,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),                                            // CTA standard height
                        shape = RoundedCornerShape(CalmifyRadius.xl),                  // was 20.dp ✓
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Strings.Meditation.Overview.btnRedo),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
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
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg), // was 16.dp ✓
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Pill ────────────────────────────────────────────────────
            CompletePill()

            // ── Title ───────────────────────────────────────────────────
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.2).sp,
                ),
                color = colorScheme.onSurface,
            )

            // ── Lede with technique + duration + optional cycles ────────
            val durationText = formatDuration(runtime.elapsedSeconds)
            val cycles = runtime.cyclesCompleted.takeIf { it > 0 && runtime.technique.hasPattern }
            val ledeText = if (cycles != null) {
                stringResource(Strings.Meditation.Overview.ledeWithCycles, techniqueName, durationText, cycles)
            } else {
                stringResource(Strings.Meditation.Overview.ledeNoCycles, techniqueName, durationText)
            }
            Text(
                text = ledeText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // ── Card 1: Mechanism ──────────────────────────────────────
            OverviewCard(
                title = techniqueName,
                body = stringResource(runtime.technique.mechanismRes),
            )

            // ── Card 2: What you may notice now ────────────────────────
            BulletCard(
                title = stringResource(Strings.Meditation.Overview.cardNoticeTitle),
                bullets = listOf(
                    BulletData(Icons.Outlined.Favorite, Strings.Meditation.Overview.noticeB1, null),
                    BulletData(Icons.Outlined.Psychology, Strings.Meditation.Overview.noticeB2, null),
                    BulletData(Icons.Outlined.Bedtime, Strings.Meditation.Overview.noticeB3, null),
                ),
            )

            // ── Card 3: Regular practice ────────────────────────────────
            OverviewCard(
                title = stringResource(Strings.Meditation.Overview.cardPracticeTitle),
                body = stringResource(Strings.Meditation.Overview.cardPracticeBody),
            )

            // ── Card 4: Try in daily life ───────────────────────────────
            BulletCard(
                title = stringResource(Strings.Meditation.Overview.cardDailyTitle),
                bullets = listOf(
                    BulletData(
                        icon = Icons.Outlined.Alarm,
                        titleRes = Strings.Meditation.Overview.dailyB1Title,
                        sub = stringResource(Strings.Meditation.Overview.dailyB1Sub),
                    ),
                    BulletData(
                        icon = Icons.Outlined.Warning,
                        titleRes = Strings.Meditation.Overview.dailyB2Title,
                        // Lower-case the technique name for natural sentence flow
                        sub = stringResource(
                            Strings.Meditation.Overview.dailyB2SubTemplate,
                            techniqueName.lowercase(),
                        ),
                    ),
                ),
            )

            // ── Disclaimer banner ──────────────────────────────────────
            DisclaimerBanner()

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────

@Composable
private fun OverviewLeafLogo() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 16.dp)
            .size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Spa,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = colorScheme.primary,
        )
    }
}

@Composable
private fun CompletePill() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(CalmifyRadius.pill), // was 999.dp ✓
        color = colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colorScheme.primary,
            )
            Text(
                text = stringResource(Strings.Meditation.Overview.pill).uppercase(),
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
private fun OverviewCard(
    title: String,
    body: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl), // was 24.dp → xxl (28)
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) { // was 20.dp → xl (24)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private data class BulletData(
    val icon: ImageVector,
    val titleRes: StringResource,
    val sub: String?,
)

@Composable
private fun BulletCard(
    title: String,
    bullets: List<BulletData>,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl), // was 24.dp → xxl (28)
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) { // was 20.dp → xl (24)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            bullets.forEach { bullet ->
                BulletRow(bullet)
            }
        }
    }
}

@Composable
private fun BulletRow(bullet: BulletData) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = colorScheme.primaryContainer.copy(alpha = 0.4f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = bullet.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = stringResource(bullet.titleRes),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurface,
            )
            if (bullet.sub != null) {
                Text(
                    text = bullet.sub,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        letterSpacing = 0.25.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DisclaimerBanner() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
        color = colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.primary,
            )
            Text(
                text = stringResource(Strings.Meditation.Overview.banner),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Formats elapsed seconds as locale-aware "X min Y s" or "X min" or "Y s".
 * Mirrors the design's `minStr` JS helper.
 */
@Composable
private fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val rem = safe % 60
    return when {
        mins == 0 -> stringResource(Strings.Meditation.Time.secondsOnly, rem)
        rem == 0 -> stringResource(Strings.Meditation.Time.minutesOnly, mins)
        else -> stringResource(Strings.Meditation.Time.minSec, mins, rem)
    }
}
