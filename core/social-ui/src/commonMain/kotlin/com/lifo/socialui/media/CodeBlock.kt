package com.lifo.socialui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

/**
 * Code block rendering for posts containing triple-backtick code.
 *
 * Features:
 * - Monospace font
 * - surfaceContainerLow background with rounded corners
 * - Horizontal scroll for long lines
 * - Copy-to-clipboard button
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        // Code content (horizontally scrollable)
        Text(
            text = code.trimEnd(),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
                .padding(end = 32.dp), // Room for copy button
        )

        // Copy button
        IconButton(
            onClick = { clipboardManager.setText(AnnotatedString(code)) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp),
        ) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(Strings.SharedA11y.copyCode),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Language label
        if (!language.isNullOrBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
    }
}
