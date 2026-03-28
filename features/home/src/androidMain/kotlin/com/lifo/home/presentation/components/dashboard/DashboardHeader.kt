package com.lifo.home.presentation.components.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lifo.home.domain.model.TrendDirection
import com.lifo.home.domain.model.TodayPulse
import com.lifo.home.util.EmotionAwareColors
import com.lifo.util.model.SentimentLabel

private const val PHOTO_ID = "profile_photo"

@Composable
internal fun DashboardHeader(
    todayPulse: TodayPulse?,
    userName: String,
    userPhotoUrl: String? = null,
    onProfileClick: () -> Unit = {},
    onJourneyClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val hour = java.time.LocalTime.now().hour
            val displayName = if (userName.isNotBlank()) userName.replaceFirstChar { it.uppercase() } else ""

            // --- Inline greeting with photo embedded in text ---
            val scoreLabel = if (todayPulse != null) getScoreLabel(todayPulse.score) else null
            val scoreLabelColor = if (todayPulse != null)
                EmotionAwareColors.getWellbeingScoreColor(todayPulse.score)
            else colorScheme.primary

            val bigTextStyle = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp,
                lineHeight = 38.sp
            )

            val highlightBg = scoreLabelColor.copy(alpha = 0.15f)

            val annotatedGreeting = buildAnnotatedString {
                withStyle(SpanStyle(
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Light
                )) {
                    append("Ciao, ")
                }
                if (displayName.isNotBlank()) {
                    withStyle(SpanStyle(
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )) {
                        append(displayName)
                    }
                    append("\n")
                }
                appendInlineContent(PHOTO_ID, "[foto]")
                if (scoreLabel != null) {
                    withStyle(SpanStyle(
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Light
                    )) {
                        append(" il tuo benessere e' ")
                    }
                    withStyle(SpanStyle(
                        color = scoreLabelColor,
                        fontWeight = FontWeight.SemiBold,
                        background = highlightBg
                    )) {
                        append(scoreLabel.lowercase())
                    }
                } else {
                    val contextMessage = when {
                        hour < 10 -> " come stai stamattina?"
                        hour < 14 -> " prenditi un momento per te."
                        hour < 19 -> " com'e' andato il pomeriggio?"
                        else -> " come ti senti stasera?"
                    }
                    withStyle(SpanStyle(
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Normal
                    )) {
                        append(contextMessage)
                    }
                }
            }

            val photoSize = 36.sp
            val inlineContent = mapOf(
                PHOTO_ID to InlineTextContent(
                    placeholder = Placeholder(
                        width = photoSize,
                        height = photoSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    InlineProfilePhoto(
                        userPhotoUrl = userPhotoUrl,
                        displayName = displayName,
                        onClick = onProfileClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )

            Text(
                text = annotatedGreeting,
                style = bigTextStyle,
                inlineContent = inlineContent,
                modifier = Modifier.fillMaxWidth()
            )

            if (todayPulse != null) {
                // --- Score bar + trend (clickable → journey) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onJourneyClick)
                ) {
                    ScoreGradientBar(
                        score = todayPulse.score,
                        maxScore = 10f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- Bottom stats row ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Trend chip
                    val trendIcon = when (todayPulse.trend) {
                        TrendDirection.UP -> Icons.AutoMirrored.Filled.TrendingUp
                        TrendDirection.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
                        TrendDirection.STABLE -> Icons.AutoMirrored.Filled.TrendingFlat
                    }
                    val trendColor = when (todayPulse.trend) {
                        TrendDirection.UP -> EmotionAwareColors.veryPositiveLight
                        TrendDirection.DOWN -> EmotionAwareColors.negativeLight
                        TrendDirection.STABLE -> EmotionAwareColors.neutralLight
                    }
                    val trendText = when (todayPulse.trend) {
                        TrendDirection.UP -> "Crescita: +${String.format("%.1f", todayPulse.trendDelta)}"
                        TrendDirection.DOWN -> "Calo: ${String.format("%.1f", -todayPulse.trendDelta)}"
                        TrendDirection.STABLE -> "Stabile"
                    }

                    StatChip(
                        icon = trendIcon,
                        label = trendText,
                        color = trendColor
                    )

                    StatChip(
                        label = "${todayPulse.entriesCount}/${todayPulse.entriesCount + 2} Entries",
                        color = colorScheme.onSurfaceVariant
                    )

                    // Arrow icon at the end
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InlineProfilePhoto(
    userPhotoUrl: String?,
    displayName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(
                width = 1.5.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (userPhotoUrl != null) {
            AsyncImage(
                model = userPhotoUrl,
                contentDescription = "Profile",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (displayName.isNotBlank()) {
                        Text(
                            text = displayName.first().uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreGradientBar(
    score: Float,
    maxScore: Float,
    modifier: Modifier = Modifier
) {
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animatedFraction.animateTo(
            targetValue = (score / maxScore).coerceIn(0f, 1f),
            animationSpec = tween(1400, easing = FastOutSlowInEasing)
        )
    }

    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val barHeight = 8.dp
    val indicatorRadius = 6.dp

    val gradientColors = listOf(
        Color(0xFFEF5350),
        Color(0xFFFF7043),
        Color(0xFFFFCA28),
        Color(0xFF66BB6A),
        Color(0xFF4CAF50)
    )

    val trackColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(indicatorRadius * 2 + 2.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .align(Alignment.Center)
            ) {
                val barCornerRadius = CornerRadius(size.height / 2f)

                drawRoundRect(
                    color = trackColor,
                    cornerRadius = barCornerRadius,
                    size = Size(size.width, size.height)
                )

                val fillWidth = size.width * animatedFraction.value
                if (fillWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(gradientColors),
                        cornerRadius = barCornerRadius,
                        size = Size(fillWidth, size.height)
                    )
                }
            }

            val indicatorRadiusPx = with(density) { indicatorRadius.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(indicatorRadius * 2)
                    .align(Alignment.Center)
            ) {
                val cx = (size.width - indicatorRadiusPx * 2) * animatedFraction.value + indicatorRadiusPx
                val cy = size.height / 2f

                drawCircle(
                    color = Color.White,
                    radius = indicatorRadiusPx,
                    center = Offset(cx, cy)
                )
                val indicatorColor = interpolateGradientColor(gradientColors, animatedFraction.value)
                drawCircle(
                    color = indicatorColor,
                    radius = indicatorRadiusPx - with(density) { 2.dp.toPx() },
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = indicatorColor.copy(alpha = 0.3f),
                    radius = indicatorRadiusPx + with(density) { 1.dp.toPx() },
                    center = Offset(cx, cy),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() })
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
            Text(
                text = "10",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = color
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = color,
                maxLines = 1
            )
        }
    }
}

private fun getScoreLabel(score: Float): String {
    return when {
        score >= 8.5f -> "Eccellente"
        score >= 7f -> "Ottimo"
        score >= 5.5f -> "Buono"
        score >= 4f -> "Nella media"
        score >= 2.5f -> "Sotto la media"
        else -> "Da migliorare"
    }
}

private fun interpolateGradientColor(colors: List<Color>, fraction: Float): Color {
    if (colors.isEmpty()) return Color.Gray
    if (fraction <= 0f) return colors.first()
    if (fraction >= 1f) return colors.last()

    val scaledFraction = fraction * (colors.size - 1)
    val index = scaledFraction.toInt().coerceIn(0, colors.size - 2)
    val localFraction = scaledFraction - index

    val from = colors[index]
    val to = colors[index + 1]
    return Color(
        red = from.red + (to.red - from.red) * localFraction,
        green = from.green + (to.green - from.green) * localFraction,
        blue = from.blue + (to.blue - from.blue) * localFraction,
        alpha = 1f
    )
}
