package com.lifo.ui.components.biosignal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import org.jetbrains.compose.resources.stringResource

/**
 * Slim, dismissable bio-signal banner — Phase 5 reusable atom.
 *
 * 1:1 grammar with `design/biosignal/Calmify BioContextual Cards.html` (Card 1,
 * `.bio-banner`). Used inside host features (journal / meditation / insight)
 * above their primary writing or content surface.
 *
 * Per `memory/feedback_biosignal_plan_as_compass.md` and `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`:
 * - Observational copy, never prescriptive
 * - Optional CTA (no demand)
 * - Always dismissable — never modal, never blocking
 * - Source + confidence baked into the [BioConfidenceFooter] under the copy
 *
 * Layout (mirrors CSS grid 36px / 1fr / 32px):
 *   [icon] [copy + footer] [dismiss]
 */
@Composable
fun BioBanner(
    icon: ImageVector,
    copy: String,
    confidenceLevel: ConfidenceLevel,
    source: BioSignalSource?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissA11y = stringResource(Strings.BioBanner.dismissA11y)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(CalmifyRadius.lg),
            )
            .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Leading icon — accent-tinted rounded square (CSS .heart 36×36 r12)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.md))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Copy + footer column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = copy,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp, // matches .bio-banner .copy in calmify.css
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (source != null) {
                    BioConfidenceFooter(level = confidenceLevel, source = source)
                }
            }

            // Trailing dismiss — 32dp pill matching CSS .dismiss
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = dismissA11y,
                        onClick = onDismiss,
                    )
                    .semantics { contentDescription = dismissA11y },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
