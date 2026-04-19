package com.lifo.ui.i18n

import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Typed facade over Compose Multiplatform string resources.
 *
 * Single source of truth for every UI string in Calmify. Always prefer
 * `Strings.<Group>.<key>` over raw `Res.string.*` access — the facade gives
 * compile-safe autocompletion, rename-in-one-point, and semantic grouping.
 *
 * Call sites:
 * ```
 * AppText(Strings.Action.save)
 * stringResource(Strings.Action.save)
 * ```
 *
 * Adding a new string:
 * 1. Add key to `core/ui/src/commonMain/composeResources/values/strings.xml` (EN default).
 * 2. Add translation to the 11 other `values-<lang>/strings.xml` files.
 * 3. Add entry here in the matching group.
 *
 * See `memory/i18n_strategy.md` for naming conventions and patterns.
 */
object Strings {

    /** App-wide branding and constants. */
    object App {
        val name: StringResource get() = Res.string.app_name
    }

    /**
     * Reusable action verbs (button labels, menu items).
     * These are generic — specific actions belong in their screen group.
     */
    object Action {
        val save: StringResource get() = Res.string.save
        val cancel: StringResource get() = Res.string.cancel
        val close: StringResource get() = Res.string.close
        val confirm: StringResource get() = Res.string.confirm
        val delete: StringResource get() = Res.string.delete
        val retry: StringResource get() = Res.string.retry
        val back: StringResource get() = Res.string.back
    }

    /**
     * Error messages shown in UI (snackbars, dialogs, inline).
     * Do NOT use for exceptions logged — those stay as raw strings.
     *
     * TODO(i18n phase B): populate with error_* keys after migration pass.
     */
    object Error {
        // Populated incrementally as hardcoded errors are migrated from features/*.
    }

    /**
     * UI states (loading, empty, error placeholders).
     *
     * TODO(i18n phase B): populate with state_* keys after migration pass.
     */
    object State {
        // Populated incrementally.
    }

    /**
     * Accessibility contentDescription strings.
     * All icon/image contentDescription MUST come from here — never hardcoded,
     * never English-only. Screen readers read these.
     *
     * TODO(i18n phase B): populate with a11y_* keys after migration pass.
     */
    object A11y {
        // Populated incrementally.
    }

    /**
     * Screen-specific strings, grouped by feature/screen.
     * Use when a string has no reusable semantics (it belongs to one place).
     *
     * TODO(i18n phase C): populate per-module during migration sweep.
     */
    object Screen {
        // Sub-objects added per-feature during Fase C migration.
    }
}
