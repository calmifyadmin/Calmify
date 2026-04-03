package com.lifo.ui.tutorial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

/**
 * A small ⓘ icon that opens a ModalBottomSheet explaining a concept in plain Italian.
 *
 * Usage:
 * ```
 * Row(verticalAlignment = Alignment.CenterVertically) {
 *     Text("Pattern cognitivi")
 *     InfoTooltip(
 *         title = "Pattern cognitivi",
 *         description = "I pattern cognitivi sono...",
 *     )
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltip(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartialExpansion = true)

    IconButton(
        onClick = { showSheet = true },
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Informazioni su $title",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = CalmifyRadius.xxl, topEnd = CalmifyRadius.xxl),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CalmifySpacing.xl,
                        end = CalmifySpacing.xl,
                        top = CalmifySpacing.md,
                        bottom = CalmifySpacing.xxl,
                    ),
                verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 30.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Explanation
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 26.sp,
                )

                Spacer(Modifier.height(CalmifySpacing.lg))

                // Dismiss button
                FilledTonalButton(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CalmifyRadius.lg),
                ) {
                    Text("Ho capito")
                }
            }
        }
    }
}
