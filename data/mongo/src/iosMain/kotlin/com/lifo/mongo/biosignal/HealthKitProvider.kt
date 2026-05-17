package com.lifo.mongo.biosignal

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.ProviderStatus
import kotlinx.datetime.Instant

/**
 * iOS HealthKit provider — Phase 10.0 scaffold (2026-05-17).
 *
 * This is the iOS-side `actual` for the [HealthDataProvider] contract. Today
 * it returns [ProviderStatus.NotInstalled] for every call and an empty list
 * for every read — enough to (a) prove the KMP abstraction crosses platforms
 * cleanly and (b) keep the data layer compiling against the iOS targets.
 *
 * Why a stub now and not the full HealthKit binding?
 *
 * The bigger Phase 10 work has two prerequisites this scaffold doesn't:
 *  1. No iOS app shell exists yet (no `*.xcodeproj`, no `iosApp/` module),
 *     so even a perfect HealthKit reader would have nowhere to render the
 *     bio screens. The Compose Multiplatform iOS entry point + cocoapods
 *     setup is itself a separate workstream.
 *  2. SQLDelight native driver isn't wired into `data/mongo` iOS either.
 *     The repository impl that consumes [HealthDataProvider] is Android-only
 *     today (the whole `BioSignalRepositoryImpl` is in androidMain).
 *
 * Phase 10.1 — real HealthKit reads — needs:
 *  - Cocoapods plugin + `HealthKit.framework` interop
 *  - HKHealthStore.requestAuthorization wrapper (suspend bridge over the
 *     callback-based ObjC API)
 *  - HKSampleQuery → BioSignal mapper per type (7 types: HR / HRV / Sleep /
 *     Steps / RestingHR / SpO2 / Activity), mirroring [HealthConnectMappers]
 *     on Android
 *  - `BioSignalSource` populated from `HKSource.bundleIdentifier` +
 *     `HKDevice.name`
 *  - Confidence floor heuristic — Apple Watch / Whoop / Oura → HIGH,
 *     iPhone-only steps → MEDIUM, manual → LOW
 *
 * Until then, an iOS app instance would see `ProviderStatus.NotInstalled`
 * and the UI's existing CTA path ("Install Health Connect" — needs a
 * platform-aware string) would surface. That's honest: nothing is faked.
 */
class HealthKitProvider : HealthDataProvider {

    override suspend fun checkAvailability(): ProviderStatus = ProviderStatus.NotInstalled

    override suspend fun requestPermissions(
        requested: Set<BioSignalDataType>,
    ): Set<BioSignalDataType> = emptySet()

    override suspend fun revokePermissions(types: Set<BioSignalDataType>) = Unit

    override suspend fun readHeartRate(
        from: Instant,
        until: Instant,
    ): List<BioSignal.HeartRateSample> = emptyList()

    override suspend fun readHrv(
        from: Instant,
        until: Instant,
    ): List<BioSignal.HrvSample> = emptyList()

    override suspend fun readSleepSessions(
        from: Instant,
        until: Instant,
    ): List<BioSignal.SleepSession> = emptyList()

    override suspend fun readStepCounts(
        from: Instant,
        until: Instant,
    ): List<BioSignal.StepCount> = emptyList()

    override suspend fun readRestingHeartRate(
        from: Instant,
        until: Instant,
    ): List<BioSignal.RestingHeartRate> = emptyList()

    override suspend fun readOxygenSaturation(
        from: Instant,
        until: Instant,
    ): List<BioSignal.OxygenSaturationSample> = emptyList()

    override suspend fun readActivitySessions(
        from: Instant,
        until: Instant,
    ): List<BioSignal.ActivitySession> = emptyList()
}
