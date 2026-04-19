# Fonts — Calmify i18n

## Purpose

Calmify supports 12 languages, and some require specific font files because the
device-default system font may not include the required script:

| Locale | Script | Font needed | Why |
|--------|--------|-------------|-----|
| `ar`   | Arabic | Noto Sans Arabic | Android system font coverage is inconsistent |
| `zh`   | Han (Simplified) | Noto Sans SC | CJK glyphs may not render on non-CJK devices |
| `ja`   | Kanji + Kana | Noto Sans JP | Idem |
| `ko`   | Hangul | Noto Sans KR | Idem |
| `hi`   | Devanagari | Noto Sans Devanagari | Devanagari conjuncts need shaping support |
| `th`   | Thai | Noto Sans Thai | Line-breaking requires dedicated font |

Latin locales (`en`, `it`, `es`, `fr`, `de`, `pt`) use the device default font — no extra file needed.

## Bundling process (to run in Fase A''.2 or Fase D)

Download Noto fonts from Google Fonts and drop them in this directory:

```bash
# From repo root. Use the official Noto TTFs (Regular + Medium + Bold weights).
curl -L -o NotoSansArabic-Regular.ttf 'https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSansArabic/NotoSansArabic-Regular.ttf'
curl -L -o NotoSansSC-Regular.otf 'https://github.com/googlefonts/noto-cjk/raw/main/Sans/OTF/SimplifiedChinese/NotoSansSC-Regular.otf'
curl -L -o NotoSansJP-Regular.otf 'https://github.com/googlefonts/noto-cjk/raw/main/Sans/OTF/Japanese/NotoSansJP-Regular.otf'
curl -L -o NotoSansKR-Regular.otf 'https://github.com/googlefonts/noto-cjk/raw/main/Sans/OTF/Korean/NotoSansKR-Regular.otf'
curl -L -o NotoSansDevanagari-Regular.ttf 'https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSansDevanagari/NotoSansDevanagari-Regular.ttf'
curl -L -o NotoSansThai-Regular.ttf 'https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSansThai/NotoSansThai-Regular.ttf'
```

Expected total APK size increase: ~10-15 MB (subsettable with Google Fonts CDN in Web target).

## Wiring in Compose (Fase D)

Once fonts are on disk, expose them via Compose Resources:

```kotlin
// core/ui/src/commonMain/kotlin/com/lifo/ui/theme/Typography.kt
import com.lifo.ui.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun LocaleAwareFontFamily(locale: SupportedLocale): FontFamily = when (locale) {
    SupportedLocale.AR -> FontFamily(Font(Res.font.NotoSansArabic_Regular))
    SupportedLocale.ZH -> FontFamily(Font(Res.font.NotoSansSC_Regular))
    SupportedLocale.JA -> FontFamily(Font(Res.font.NotoSansJP_Regular))
    SupportedLocale.KO -> FontFamily(Font(Res.font.NotoSansKR_Regular))
    SupportedLocale.HI -> FontFamily(Font(Res.font.NotoSansDevanagari_Regular))
    SupportedLocale.TH -> FontFamily(Font(Res.font.NotoSansThai_Regular))
    else -> FontFamily.Default  // EN/IT/ES/FR/DE/PT — device default
}
```

Then inject into theme:

```kotlin
CompositionLocalProvider(
    LocalLayoutDirection provides if (locale.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
) {
    MaterialTheme(
        typography = Typography(defaultFontFamily = LocaleAwareFontFamily(locale)),
    ) { AppContent() }
}
```

## Why not bundle TTFs in this commit

Binary font files (~2-4 MB each) live outside the core i18n scaffold commit to keep it surgical. Deferred to Fase A''.2 or Fase D cleanup. If commit size matters later, consider:

- Google Fonts Downloadable Fonts API (Android-only but zero APK cost)
- Web target: load fonts via CSS `@font-face` from CDN (no bundle cost)
- Maintain TTFs via Git LFS (avoids bloating repo)

## License

Noto fonts are licensed under the SIL Open Font License 1.1. See [https://github.com/googlefonts/noto-fonts/blob/main/LICENSE](https://github.com/googlefonts/noto-fonts/blob/main/LICENSE). Attribution is optional but recommended in app credits.
