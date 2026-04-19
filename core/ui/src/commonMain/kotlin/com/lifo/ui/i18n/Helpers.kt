package com.lifo.ui.i18n

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Composable helpers that enforce i18n correctness at the call site.
 *
 * These replace direct `Text("literal")` / `Icon(..., contentDescription = "literal")`
 * usage with typed StringResource keys from [Strings]. The Detekt rule
 * `NoHardcodedUiString` flags the raw variants — use these instead.
 *
 * Examples:
 * ```
 * AppText(Strings.Action.save)
 * AppText(Strings.Screen.Home.greeting, username)   // with parameters
 * LocalizedTextButton(Strings.Action.save, onClick = ::onSave)
 * LocalizedIconButton(Strings.A11y.menu, Icons.Default.Menu, onClick = ::openDrawer)
 * ```
 */

/**
 * Localized [Text] — preferred over `Text(stringResource(Res.string.xxx))` at call site.
 * Signature mirrors [androidx.compose.material3.Text] for drop-in replacement.
 */
@Composable
fun AppText(
    key: StringResource,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = stringResource(key),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style,
    )
}

/**
 * Localized [Text] with format arguments.
 * Keys must be declared with `%1$s`, `%2$d` etc. in their `strings.xml`.
 */
@Composable
fun AppText(
    key: StringResource,
    vararg formatArgs: Any,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = stringResource(key, *formatArgs),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style,
    )
}

/**
 * Localized [IconButton] where the a11y contentDescription is required (not nullable).
 * Use [Strings.A11y.*] keys. For decorative icons (no semantic meaning for screen readers),
 * use the regular [IconButton] with `contentDescription = null`.
 */
@Composable
fun LocalizedIconButton(
    descriptionKey: StringResource,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(descriptionKey),
            tint = tint,
        )
    }
}

/**
 * Localized [TextButton] — the label is a required [StringResource].
 * Use [Strings.Action.*] for generic verbs; use screen-scoped keys otherwise.
 */
@Composable
fun LocalizedTextButton(
    labelKey: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        AppText(labelKey)
    }
}
