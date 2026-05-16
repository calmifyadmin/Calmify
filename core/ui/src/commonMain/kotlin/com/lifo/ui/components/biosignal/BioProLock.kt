package com.lifo.ui.components.biosignal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import org.jetbrains.compose.resources.stringResource

/**
 * Non-hostile PRO gate — Phase 5.3 reusable atom.
 *
 * Used inline beneath FREE-tier bio cards (Meditation outro HRV layer, Insight
 * cross-signal correlation, etc.) to signal that a deeper layer exists behind
 * the PRO tier WITHOUT being punitive.
 *
 * 1:1 with `.hrv-gate` in `design/biosignal/Calmify BioContextual Cards.html`:
 * dashed accent-tinted border, accent-at-8% background, leading lock icon
 * in a small accent circle, copy in the middle, "PRO" chip on the right.
 *
 * Per `memory/feedback_calmify_values.md` dogma #3 (helpful, not optimizing):
 * the lock is decorative, never alarming. The copy is observational ("HRV
 * improved during the session — see the detail with PRO"), never demanding.
 */
@Composable
fun BioProLock(
    copy: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val proLabel = stringResource(Strings.BioProLock.proChip)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.md))
            .background(accent.copy(alpha = 0.08f))
            .dashedRoundedBorder(
                color = accent.copy(alpha = 0.36f),
                cornerRadius = CalmifyRadius.md,
            )
            .clickable(role = Role.Button, onClick = onUpgrade)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.pill))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = copy,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(CalmifyRadius.pill))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = proLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    ),
                    color = accent,
                )
            }
        }
    }
}

// ── Dashed rounded border helper — Compose's Modifier.border has no PathEffect ──

private fun Modifier.dashedRoundedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
    dashOn: Dp = 4.dp,
    dashOff: Dp = 4.dp,
): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashOn.toPx(), dashOff.toPx()), 0f,
        ),
    )
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = stroke,
    )
}
