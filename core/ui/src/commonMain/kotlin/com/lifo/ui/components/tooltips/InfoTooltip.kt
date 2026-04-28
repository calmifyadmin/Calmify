package com.lifo.ui.components.tooltips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.info_tooltip_cd
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A small ⓘ icon that opens a [ModalBottomSheet] with a title and detailed
 * explanation when tapped.
 *
 * Designed for psychological / wellness concepts that users may not be familiar
 * with.  All text should be in Italian, warm and non-judgmental.
 *
 * Usage:
 * ```kotlin
 * Row(verticalAlignment = Alignment.CenterVertically) {
 *     Text("Pattern cognitivi")
 *     InfoTooltip(
 *         title       = "Cosa sono i pattern cognitivi?",
 *         description = "Sono schemi ricorrenti nel tuo modo di pensare. ..."
 *     )
 * }
 * ```
 *
 * @param title       Header shown inside the bottom sheet.
 * @param description Body text — keep it 2-4 sentences, plain Italian.
 * @param iconSize    Size of the ⓘ icon (default 18.dp).
 * @param iconTint    Tint for the icon (defaults to onSurfaceVariant).
 * @param modifier    Applied to the clickable icon container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltip(
    title: StringResource,
    description: StringResource,
    iconSize: Dp = 18.dp,
    iconTint: Color? = null,
    modifier: Modifier = Modifier,
) {
    val titleText = stringResource(title)
    val descText = stringResource(description)
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Icon(
        imageVector         = Icons.Outlined.Info,
        contentDescription  = stringResource(Res.string.info_tooltip_cd, titleText),
        tint                = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier            = modifier
            .size(iconSize)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                role              = Role.Button,
                onClick           = { showSheet = true },
            ),
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest  = { showSheet = false },
            sheetState        = sheetState,
            containerColor    = MaterialTheme.colorScheme.surfaceContainerLow,
            shape             = RoundedCornerShape(topStart = CalmifyRadius.xxl, topEnd = CalmifyRadius.xxl),
        ) {
            InfoTooltipSheetContent(title = titleText, description = descText)
        }
    }
}

/**
 * Legacy String-based overload kept for callers that mix localized resources
 * with inline text fragments (e.g. dashboard cards still using ad-hoc body text).
 * Prefer the [StringResource] variant in new code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltip(
    title: String,
    description: String,
    iconSize: Dp = 18.dp,
    iconTint: Color? = null,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Icon(
        imageVector         = Icons.Outlined.Info,
        contentDescription  = stringResource(Res.string.info_tooltip_cd, title),
        tint                = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier            = modifier
            .size(iconSize)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                role              = Role.Button,
                onClick           = { showSheet = true },
            ),
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest  = { showSheet = false },
            sheetState        = sheetState,
            containerColor    = MaterialTheme.colorScheme.surfaceContainerLow,
            shape             = RoundedCornerShape(topStart = CalmifyRadius.xxl, topEnd = CalmifyRadius.xxl),
        ) {
            InfoTooltipSheetContent(title = title, description = description)
        }
    }
}

@Composable
private fun InfoTooltipSheetContent(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CalmifySpacing.xl, vertical = CalmifySpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(CalmifySpacing.sm))
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(CalmifySpacing.md))

        Text(
            text  = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CalmifySpacing.xl))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pre-built tooltip definitions for the key psychological concepts in Calmify
// All entries are Pair<title StringResource, body StringResource> resolved at
// render time inside [InfoTooltip] (which calls stringResource on each).
// ─────────────────────────────────────────────────────────────────────────────

object TooltipContent {

    // ── Insight / Percorso screen ─────────────────────────────────────────────

    val cognitivePatterns = Pair(
        Strings.Tooltip.cognitivePatternsTitle,
        Strings.Tooltip.cognitivePatternsBody,
    )

    val sleepMoodCorrelation = Pair(
        Strings.Tooltip.sleepMoodTitle,
        Strings.Tooltip.sleepMoodBody,
    )

    val selfDeterminationTheory = Pair(
        Strings.Tooltip.sdtTitle,
        Strings.Tooltip.sdtBody,
    )

    val wellbeingTrend = Pair(
        Strings.Tooltip.wellbeingTrendTitle,
        Strings.Tooltip.wellbeingTrendBody,
    )

    // ── Write / Diario screen ─────────────────────────────────────────────────

    val emotionalIntensity = Pair(
        Strings.Tooltip.emotionalIntensityTitle,
        Strings.Tooltip.emotionalIntensityBody,
    )

    val stressLevel = Pair(
        Strings.Tooltip.stressLevelTitle,
        Strings.Tooltip.stressLevelBody,
    )

    val calmAnxietyLevel = Pair(
        Strings.Tooltip.calmAnxietyTitle,
        Strings.Tooltip.calmAnxietyBody,
    )

    // ── Meditazione screen ────────────────────────────────────────────────────

    val guidedBreathing = Pair(
        Strings.Tooltip.guidedBreathingTitle,
        Strings.Tooltip.guidedBreathingBody,
    )

    // ── Abitudini screen ─────────────────────────────────────────────────────

    val habitStacking = Pair(
        Strings.Tooltip.habitStackingTitle,
        Strings.Tooltip.habitStackingBody,
    )

    val minimumAction = Pair(
        Strings.Tooltip.minimumActionTitle,
        Strings.Tooltip.minimumActionBody,
    )
}
