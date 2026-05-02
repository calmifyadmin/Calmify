package com.lifo.meditation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 1 Welcome screen — first surface of the meditation flow.
 *
 * Anatomy (matches design):
 * - Top bar: leaf logo + "Breathe" title (centered)
 * - Hero: "Guided breath" pill + display title + lede
 * - Decorative animated breath preview (looping, smaller than the session pacer)
 * - "What you can expect" card with 3 bullets (icons: health, spa, sparkle)
 * - Fineprint medical disclaimer
 * - Single full-width "Begin" CTA in the footer
 *
 * The pacer preview is `aria-hidden` (decorative) — the lede + bullets carry
 * the semantic content for screen readers.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun MeditationWelcomeScreen(
    onBegin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Strings.Meditation.Welcome.topbar),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = { LeafLogo() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = colorScheme.background, tonalElevation = 0.dp) {
                Button(
                    onClick = onBegin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(Strings.Meditation.Welcome.cta),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Pill ────────────────────────────────────────────────────
            HeroPill(label = stringResource(Strings.Meditation.Welcome.pill))

            // ── Display title ───────────────────────────────────────────
            Text(
                text = stringResource(Strings.Meditation.Welcome.title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.2).sp,
                ),
                color = colorScheme.onSurface,
            )

            // ── Lede ────────────────────────────────────────────────────
            Text(
                text = stringResource(Strings.Meditation.Welcome.lede),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant,
            )

            // ── Decorative breathing preview ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .semantics { hideFromAccessibility() },
                contentAlignment = Alignment.Center,
            ) {
                AmbientBreathPreview()
            }

            // ── What you can expect card ────────────────────────────────
            ExpectCard()

            // ── Fineprint ───────────────────────────────────────────────
            Text(
                text = stringResource(Strings.Meditation.Welcome.fineprint),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp,
                    letterSpacing = 0.25.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────

@Composable
private fun HeroPill(label: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Spa,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colorScheme.primary,
            )
            Text(
                text = label.uppercase(),
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
private fun ExpectCard() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(Strings.Meditation.Welcome.cardTitle),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            ExpectBullet(
                icon = Icons.Outlined.HealthAndSafety,
                title = Strings.Meditation.Welcome.b1Title,
                sub = Strings.Meditation.Welcome.b1Sub,
            )
            ExpectBullet(
                icon = Icons.Outlined.Spa,
                title = Strings.Meditation.Welcome.b2Title,
                sub = Strings.Meditation.Welcome.b2Sub,
            )
            ExpectBullet(
                icon = Icons.Outlined.AutoAwesome,
                title = Strings.Meditation.Welcome.b3Title,
                sub = Strings.Meditation.Welcome.b3Sub,
            )
        }
    }
}

@Composable
private fun ExpectBullet(
    icon: ImageVector,
    title: StringResource,
    sub: StringResource,
) {
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
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(sub),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LeafLogo() {
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

/**
 * Decorative looping breath preview — a 3-element ambient pulse (halo +
 * outer ring + inner circle) that breathes at a coherent (~5.5s in / 5.5s out)
 * rhythm. Marked `invisibleToUser` since it carries no semantic content;
 * the lede + bullets convey the meaning.
 *
 * Phase 2 will replace this with the canonical `BreathingPacer` composable
 * shared with the Session screen.
 */
@Composable
private fun AmbientBreathPreview() {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "welcomePacer")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Halo
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = 0.8f + 0.4f * scale
                    scaleY = 0.8f + 0.4f * scale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        radius = 240f,
                    )
                )
        )
        // Outer ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = 0.7f + 0.4f * scale
                    scaleY = 0.7f + 0.4f * scale
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.08f))
        )
        // Inner circle
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primaryContainer,
                            colorScheme.primary,
                        ),
                    )
                )
        )
    }
}

