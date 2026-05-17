package com.lifo.home.presentation.components.expressive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lifo.home.domain.usecase.NarrativeFlavor
import com.lifo.home.domain.usecase.WeeklyBioNarrative
import com.lifo.ui.components.biosignal.BioNarrativeCard
import com.lifo.ui.components.biosignal.BioSource
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

/**
 * Home wrapper for the Phase 8.2 weekly bio narrative card.
 *
 * Renders [com.lifo.ui.components.biosignal.BioNarrativeCard] with the
 * locale-resolved title + freshness sub-label + the appropriate narrative
 * flavor template populated with the user's actual values. PRO-only —
 * `HomeContent` gates rendering on `isPro` before reaching this composable.
 */
@Composable
internal fun ExpressiveWeeklyNarrative(
    narrative: WeeklyBioNarrative,
    modifier: Modifier = Modifier,
) {
    val absDelta = if (narrative.deltaPercent < 0) -narrative.deltaPercent else narrative.deltaPercent
    // Phase 8.4 — prefer Gemini-generated narrative when present (BIO_NARRATIVE_REST on
    // + server returned a non-blank response). Local template is the safety net.
    val narrativeText = narrative.aiNarrative ?: when (narrative.flavor) {
        NarrativeFlavor.HIGHER -> stringResource(
            Strings.BioNarrative.higher,
            narrative.weekAvgMs, absDelta, narrative.baselineMedianMs,
        )
        NarrativeFlavor.LOWER -> stringResource(
            Strings.BioNarrative.lower,
            narrative.weekAvgMs, absDelta, narrative.baselineMedianMs,
        )
        NarrativeFlavor.STEADY -> stringResource(
            Strings.BioNarrative.steady,
            narrative.weekAvgMs, absDelta, narrative.baselineMedianMs,
        )
    }
    val sources = listOf(
        BioSource(
            label = stringResource(Strings.BioNarrative.sourceHrv),
            icon = Icons.Outlined.GraphicEq,
        ),
        BioSource(
            label = stringResource(Strings.BioNarrative.sourceBaseline),
            icon = Icons.Outlined.Calculate,
        ),
    )
    BioNarrativeCard(
        title = stringResource(Strings.BioNarrative.cardTitle),
        freshness = stringResource(Strings.BioNarrative.freshness, narrative.daysCovered),
        narrative = narrativeText,
        sources = sources,
        confidenceLevel = narrative.confidence,
        confidenceSource = narrative.source,
        modifier = modifier,
    )
}
