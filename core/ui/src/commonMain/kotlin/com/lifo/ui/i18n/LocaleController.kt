package com.lifo.ui.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls the active UI locale at runtime.
 *
 * Calmify supports 12 languages; this controller lets the Settings screen
 * change the active locale without restarting the app. The selected value
 * is persisted via [LocalePreferences] (platform-specific storage).
 *
 * Usage at app root:
 * ```
 * val controller = koinInject<LocaleController>()
 * val locale by controller.current.collectAsState()
 * CompositionLocalProvider(
 *     LocalLayoutDirection provides locale.layoutDirection,
 * ) { AppContent() }
 * ```
 *
 * TODO(i18n phase B): wire [LocalePreferences] to DataStore/UserDefaults via
 * `expect/actual`, and apply `LocalLayoutDirection` for RTL locales (ar, he).
 */
class LocaleController(
    private val preferences: LocalePreferences,
) {
    private val _current = MutableStateFlow(preferences.loaded() ?: SupportedLocale.default())
    val current: StateFlow<SupportedLocale> = _current.asStateFlow()

    suspend fun setLocale(locale: SupportedLocale) {
        _current.value = locale
        preferences.save(locale)
    }

    /** Cycle through supported locales — useful for debug menu / testing. */
    suspend fun cycleNext() {
        val all = SupportedLocale.entries
        val next = all[(all.indexOf(_current.value) + 1) % all.size]
        setLocale(next)
    }
}

/**
 * The 12 locales Calmify officially supports.
 *
 * `tag` is the BCP 47 language tag used by Compose Resources to pick
 * `values-<tag>/` files. `displayName` is shown in the Settings language dialog
 * (always in its own script — e.g. "Italiano", "العربية", "日本語").
 * `isRtl` flag drives `LocalLayoutDirection` to mirror the UI.
 */
enum class SupportedLocale(
    val tag: String,
    val displayName: String,
    val isRtl: Boolean = false,
) {
    EN(tag = "en", displayName = "English"),
    IT(tag = "it", displayName = "Italiano"),
    ES(tag = "es", displayName = "Español"),
    FR(tag = "fr", displayName = "Français"),
    DE(tag = "de", displayName = "Deutsch"),
    PT(tag = "pt", displayName = "Português"),
    AR(tag = "ar", displayName = "العربية", isRtl = true),
    ZH(tag = "zh", displayName = "中文"),
    JA(tag = "ja", displayName = "日本語"),
    KO(tag = "ko", displayName = "한국어"),
    HI(tag = "hi", displayName = "हिन्दी"),
    TH(tag = "th", displayName = "ไทย");

    companion object {
        /** Default fallback — English, per i18n_strategy decision 2026-04-19. */
        fun default(): SupportedLocale = EN

        /** Lookup by BCP 47 tag, falling back to [default] for unsupported tags. */
        fun fromTagOrDefault(tag: String?): SupportedLocale =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: default()
    }
}

/**
 * Platform-specific storage for the user's locale preference.
 *
 * Android: backed by DataStore Preferences (`androidMain`).
 * iOS:     backed by NSUserDefaults (`iosMain`, future).
 * Web:     backed by window.localStorage (`wasmJsMain`, future).
 *
 * TODO(i18n phase B): provide `expect/actual` implementations.
 * For now, a no-op fallback keeps compilation green.
 */
interface LocalePreferences {
    fun loaded(): SupportedLocale?
    suspend fun save(locale: SupportedLocale)
}

/**
 * In-memory only implementation — safe default until DataStore wiring lands.
 * The user's choice survives process-lifetime only.
 */
class InMemoryLocalePreferences : LocalePreferences {
    private var value: SupportedLocale? = null
    override fun loaded(): SupportedLocale? = value
    override suspend fun save(locale: SupportedLocale) {
        value = locale
    }
}
