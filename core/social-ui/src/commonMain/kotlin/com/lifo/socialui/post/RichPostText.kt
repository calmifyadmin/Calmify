package com.lifo.socialui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * Rich text rendering for social post content.
 *
 * Supports:
 * - #hashtags (primary color, clickable)
 * - @mentions (primary color, clickable)
 * - URLs (clickable links)
 * - **bold** and *italic* markdown
 * - `inline code` (monospace + surfaceContainerLow bg)
 * - Expand/collapse for long text
 */
@Composable
fun RichPostText(
    text: String,
    maxLines: Int = 6,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    val annotated = remember(text, primaryColor, codeColor) {
        parseRichText(text, primaryColor, codeColor, textColor)
    }

    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ClickableText(
            text = annotated,
            style = style.copy(color = textColor),
            maxLines = if (isExpanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!isExpanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
            onClick = { offset ->
                annotated.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let {
                    onHashtagClick(it.item)
                    return@ClickableText
                }
                annotated.getStringAnnotations("mention", offset, offset).firstOrNull()?.let {
                    onMentionClick(it.item)
                    return@ClickableText
                }
                annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
                    onUrlClick(it.item)
                    return@ClickableText
                }
            },
        )

        if (hasOverflow && !isExpanded) {
            Text(
                text = "more",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { isExpanded = true },
                ),
            )
        }
    }
}

/**
 * Parses raw text into an AnnotatedString with styled spans for
 * hashtags, mentions, URLs, bold, italic, and inline code.
 */
private fun parseRichText(
    text: String,
    primaryColor: Color,
    codeColor: Color,
    textColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Inline code: `code`
                text[i] == '`' && !text.startsWith("```", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Italic: *text* (single asterisk, not followed by another)
                text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Hashtag: #word
                text[i] == '#' && (i == 0 || text[i - 1].isWhitespace()) -> {
                    val end = findWordEnd(text, i + 1)
                    if (end > i + 1) {
                        val hashtag = text.substring(i, end)
                        pushStringAnnotation("hashtag", hashtag)
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                            append(hashtag)
                        }
                        pop()
                        i = end
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Mention: @word
                text[i] == '@' && (i == 0 || text[i - 1].isWhitespace()) -> {
                    val end = findWordEnd(text, i + 1)
                    if (end > i + 1) {
                        val mention = text.substring(i, end)
                        val userId = text.substring(i + 1, end)
                        pushStringAnnotation("mention", userId)
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                            append(mention)
                        }
                        pop()
                        i = end
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // URL detection: http:// or https://
                text.startsWith("http://", i) || text.startsWith("https://", i) -> {
                    val end = findUrlEnd(text, i)
                    val url = text.substring(i, end)
                    pushStringAnnotation("url", url)
                    withStyle(SpanStyle(color = primaryColor)) {
                        append(url)
                    }
                    pop()
                    i = end
                }

                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

private fun findWordEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) {
        i++
    }
    return i
}

private fun findUrlEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length && !text[i].isWhitespace()) {
        i++
    }
    // Strip trailing punctuation that's likely not part of the URL
    while (i > start && text[i - 1] in ".,;:!?)\"'") {
        i--
    }
    return i
}
