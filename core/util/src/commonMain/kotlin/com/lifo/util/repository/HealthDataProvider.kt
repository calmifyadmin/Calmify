package com.lifo.util.repository

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import kotlinx.datetime.Instant

/**
 * Read-only contract for sourcing bio-signal data from the platform's health
 * aggregator (Health Connect on Android, HealthKit on iOS).
 *
 * **Calmify never writes back**: this interface exposes only `read*` functions.
 * Refusing write capability is intentional — protects the user's source-of-truth
 * (their wearable's own app) from being polluted by Calmify-derived inferences.
 *
 * Provider availability is platform-specific: Health Connect requires a
 * separate installation on Android, HealthKit is built into iOS. Call
 * [checkAvailability] before any read to handle the not-installed / permission-
 * missing paths gracefully.
 *
 * See `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` §3 (Architecture) for the full
 * picture of where this fits in the KMP layout.
 */
interface HealthDataProvider {

    /**
     * Check current provider availability + permission state.
     * Always call this before any read operation.
     */
    suspend fun checkAvailability(): ProviderStatus

    /**
     * Request the user's permission for the given data types.
     * Triggers the platform-native permission UI (Health Connect contract on
     * Android, HealthKit prompt on iOS).
     *
     * @return the set of data types the user actually granted (may be a subset
     * of [requested] — user can pick-and-choose per type).
     */
    suspend fun requestPermissions(requested: Set<BioSignalDataType>): Set<BioSignalDataType>

    /**
     * Revoke all Calmify permissions and clear locally cached data for the
     * given types. Idempotent — safe to call multiple times.
     */
    suspend fun revokePermissions(types: Set<BioSignalDataType>)

    // ──────────────────────────────────────────────────────────────────────
    // Read functions — one per data type. All return time-windowed lists.
    // ──────────────────────────────────────────────────────────────────────

    suspend fun readHeartRate(from: Instant, until: Instant): List<BioSignal.HeartRateSample>

    suspend fun readHrv(from: Instant, until: Instant): List<BioSignal.HrvSample>

    suspend fun readSleepSessions(from: Instant, until: Instant): List<BioSignal.SleepSession>

    suspend fun readStepCounts(from: Instant, until: Instant): List<BioSignal.StepCount>

    suspend fun readRestingHeartRate(from: Instant, until: Instant): List<BioSignal.RestingHeartRate>

    suspend fun readOxygenSaturation(from: Instant, until: Instant): List<BioSignal.OxygenSaturationSample>

    suspend fun readActivitySessions(from: Instant, until: Instant): List<BioSignal.ActivitySession>
}

// ──────────────────────────────────────────────────────────────────────────
// Provider status
// ──────────────────────────────────────────────────────────────────────────

/**
 * Reflects the user-facing state of the platform health aggregator.
 * The UI uses this to decide what to show on [BioContextScreen] /
 * [BioOnboardingScreen] (e.g. "Install Health Connect" CTA, permission rationale,
 * or just the metrics directly).
 */
sealed class ProviderStatus {

    /** The provider app is not installed at all (Android only — Health Connect package missing). */
    data object NotInstalled : ProviderStatus()

    /** The provider exists but doesn't support the platform (e.g. Wear OS too old). */
    data object NotSupported : ProviderStatus()

    /** Provider installed but needs an update (API version mismatch). */
    data object NeedsUpdate : ProviderStatus()

    /** Provider ready but Calmify lacks permission for one or more data types. */
    data class NeedsPermission(val missing: Set<BioSignalDataType>) : ProviderStatus()

    /** Provider installed, app permitted — ready to read. */
    data object Ready : ProviderStatus()
}
