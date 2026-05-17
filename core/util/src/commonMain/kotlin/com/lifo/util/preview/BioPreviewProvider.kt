package com.lifo.util.preview

import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.model.SourceKind

/**
 * Centralized mock data factory for bio-signal surfaces — Phase 9.2.4 (2026-05-17).
 *
 * **Why**: the four Phase 5 cards (+ Phase 8.2 narrative) are silence-by-default.
 * A fresh-install user sees NOTHING until 7+ days of wearable data accumulate.
 * That made the architectural work of Phases 5-8 visually invisible.
 *
 * **What**: when [enabled] is `true` AND a use case can't compute a real
 * result, it returns a preview from one of the `preview*()` factories here.
 * Same shape as real data, same atom call-sites — but the user sees the
 * design work IMMEDIATELY without waiting for Mi Band sync.
 *
 * **Discipline**: the preview surfaces are decorated with confidence = LOW
 * so the user understands what they're looking at is illustrative. Honesty
 * is preserved at the data-confidence layer (Decision 2 in
 * `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`).
 *
 * **Toggle**: today the flag is on in debug builds via Koin
 * (`bioPreviewModule`). Phase 9.1.3 adds a runtime Settings toggle so
 * non-PRO users can preview the PRO weekly narrative card without paying.
 */
interface BioPreviewProvider {
    val enabled: Boolean

    /** Mock device source used across previews — generic so it reads as illustrative. */
    val previewSource: BioSignalSource
}

/**
 * Default preview provider — enabled. Bundled in [bioPreviewModule] for debug
 * builds; release builds can swap with [NoopBioPreviewProvider] via the same
 * Koin override pattern as `BIO_REST` etc.
 */
class DefaultBioPreviewProvider(
    override val enabled: Boolean = true,
) : BioPreviewProvider {
    override val previewSource: BioSignalSource = BioSignalSource(
        kind = SourceKind.WEARABLE,
        deviceName = "Preview wearable",
        appName = "Preview",
    )
}

/** No-op for release builds where we DON'T want mock cards leaking into production. */
class NoopBioPreviewProvider : BioPreviewProvider {
    override val enabled: Boolean = false
    override val previewSource: BioSignalSource = BioSignalSource(SourceKind.WEARABLE, "", "")
}

/**
 * Shared confidence floor used by every preview surface. LOW so the
 * BioConfidenceFooter / chip honestly tells the user the data is illustrative.
 */
val PreviewConfidence: ConfidenceLevel = ConfidenceLevel.LOW
