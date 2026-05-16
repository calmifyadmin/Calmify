package com.lifo.home.presentation.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.domain.model.HomeBioContext
import com.lifo.ui.components.biosignal.BioConfidenceChip
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Home · Today bio narrative card — Phase 5 surgical insertion in `:features:home`.
 *
 * 1:1 grammar with `design/biosignal/Calmify BioContextual Cards.html` (Card 3,
 * `.home-bio`):
 * - Single observational sentence (no scores, no targets, no streaks)
 * - 2–3 chip row (bio data + confidence chip)
 * - Trailing chevron — tap opens the full Bio Context dashboard
 * - Absent when [HomeBioContext.hasSignal] is false (silence-by-default)
 *
 * Sits between the [ExpressiveHero] (untouched per standing user exclusion
 * 2026-05-11) and [ExpressiveQuickActions] in the LazyColumn.
 */
@Composable
internal fun ExpressiveBioToday(
    bio: HomeBioContext,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!bio.hasSignal) return

    val narrative = composeNarrative(bio)
    val openLabel = stringResource(Strings.BioCard.homeOpenA11y)

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(CalmifyRadius.xxl),
            )
            .semantics { contentDescription = "$openLabel: $narrative" },
    ) {
        Box(modifier = Modifier.padding(CalmifySpacing.lg + 2.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md)) {
                Text(
                    text = narrative,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        lineHeight = 22.sp, // matches .home-bio .narrative in calmify.css
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 28.dp), // breathing room for the trailing chevron
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    bio.sleepDurationMinutes?.let { mins ->
                        val hours = mins / 60
                        val rest = mins % 60
                        BioChip(
                            icon = Icons.Outlined.Bedtime,
                            label = stringResource(Strings.BioCard.homeChipSleepDuration, hours, rest),
                        )
                    }
                    bio.heartRateBpm?.let { bpm ->
                        BioChip(
                            icon = Icons.Outlined.Favorite,
                            label = stringResource(Strings.BioCard.homeChipHrBpm, bpm),
                        )
                    }
                    bio.stepsToday?.let { steps ->
                        BioChip(
                            icon = Icons.Outlined.DirectionsWalk,
                            label = stringResource(Strings.BioCard.homeChipSteps, steps),
                        )
                    }
                    BioConfidenceChip(level = bio.confidenceFloor)
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun BioChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CalmifyRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun composeNarrative(bio: HomeBioContext): String {
    val hasSleep = bio.sleepDurationMinutes != null
    val hasHr = bio.heartRateBpm != null
    val hasSteps = bio.stepsToday != null

    val sleepText = bio.sleepDurationMinutes?.let {
        stringResource(Strings.BioCard.homeChipSleepDuration, it / 60, it % 60)
    }
    val hrText = bio.heartRateBpm?.let {
        stringResource(Strings.BioCard.homeChipHrBpm, it)
    }
    val stepsText = bio.stepsToday?.let {
        stringResource(Strings.BioCard.homeChipSteps, it)
    }

    val base = when {
        hasSleep && hasHr -> stringResource(Strings.BioCard.homeNarrativeSleepHr, sleepText!!, hrText!!)
        hasSleep -> stringResource(Strings.BioCard.homeNarrativeSleepOnly, sleepText!!)
        hasHr -> stringResource(Strings.BioCard.homeNarrativeHrOnly, hrText!!)
        hasSteps -> stringResource(Strings.BioCard.homeNarrativeStepsOnly, stepsText!!)
        else -> "" // unreachable — hasSignal gate above guarantees one of the four branches
    }

    // Phase 6.2 — append the personalized range hint from the metric that's
    // anchoring the narrative. Hint is null when no baseline exists yet
    // (cold start); narrative stays at universal copy in that case.
    val hint = when {
        hasSleep && hasHr -> bio.sleepHint ?: bio.heartRateHint
        hasSleep -> bio.sleepHint
        hasHr -> bio.heartRateHint
        hasSteps -> bio.stepsHint
        else -> null
    } ?: return base
    return base + stringResource(Strings.BioCard.homeHintFor(hint))
}

