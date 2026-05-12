package com.lifo.mongo.biosignal

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant

/**
 * Android Health Connect implementation of [HealthDataProvider].
 *
 * **Read-only**: this class never writes to Health Connect. The platform
 * source-of-truth (the user's wearable's own app) is protected from
 * Calmify-derived inferences.
 *
 * **Coverage caveat for the worst-case device** (Xiaomi Mi Band 10 via Mi
 * Fitness, the day-1 test target per `memory/user_hardware.md`):
 * - HR / Sleep / Steps / SpO2: typically populated reliably
 * - HRV: sparse and intermittent (Mi Fitness writes one or two RMSSD readings
 *   per day at best; expect [BioSignalDataType.HRV] reads to often return 0–2
 *   samples for a daily window). Honest [DataConfidence] handles this gracefully.
 * - Stress score: NOT exposed via Health Connect (Mi Fitness proprietary).
 *
 * Use [com.lifo.mongo.biosignal.BioCoverageDumper] (debug-only Activity) to
 * validate actual coverage on a real device + wearable pair before scoping
 * features that depend on a specific data type.
 */
class HealthConnectProvider(
    private val context: Context,
) : HealthDataProvider {

    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Availability + permissions
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun checkAvailability(): ProviderStatus = withContext(Dispatchers.IO) {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        Log.i(
            TAG,
            "checkAvailability — Android API ${Build.VERSION.SDK_INT}, manufacturer=${Build.MANUFACTURER}, model=${Build.MODEL}, HC SDK status=$sdkStatus " +
                    "(SDK_AVAILABLE=${HealthConnectClient.SDK_AVAILABLE}, SDK_UNAVAILABLE=${HealthConnectClient.SDK_UNAVAILABLE}, " +
                    "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED=${HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED})",
        )
        when (sdkStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> ProviderStatus.NotSupported
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ProviderStatus.NotInstalled
            HealthConnectClient.SDK_AVAILABLE -> {
                val c = client ?: run {
                    Log.w(TAG, "SDK_AVAILABLE but HealthConnectClient.getOrCreate returned null — treating as NotInstalled")
                    return@withContext ProviderStatus.NotInstalled
                }
                val allTypes = BioSignalDataType.entries.toSet()
                val requested = HealthConnectPermissions.permissionsFor(allTypes)
                val granted = runCatching { c.permissionController.getGrantedPermissions() }
                    .onFailure { Log.e(TAG, "getGrantedPermissions threw", it) }
                    .getOrDefault(emptySet())
                val missingHc = requested - granted
                Log.i(
                    TAG,
                    "permissions — requested=${requested.size}, granted=${granted.size} (${granted.joinToString().take(200)}), missing=${missingHc.size}",
                )
                val missingTypes = HealthConnectPermissions.grantedDataTypes(missingHc.toSet())
                    .ifEmpty {
                        allTypes - HealthConnectPermissions.grantedDataTypes(granted)
                    }
                if (missingHc.isEmpty()) ProviderStatus.Ready
                else ProviderStatus.NeedsPermission(missingTypes)
            }
            else -> ProviderStatus.NotSupported
        }
    }

    private companion object {
        private const val TAG = "HealthConnectProvider"
    }

    override suspend fun requestPermissions(requested: Set<BioSignalDataType>): Set<BioSignalDataType> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptySet()
            // The actual permission UI is launched by the caller via
            // [HealthConnectPermissions.createRequestPermissionResultContract] —
            // this function just queries which were granted after the user returned.
            val grantedHc = c.permissionController.getGrantedPermissions()
            HealthConnectPermissions.grantedDataTypes(grantedHc) intersect requested
        }

    override suspend fun revokePermissions(types: Set<BioSignalDataType>) = withContext(Dispatchers.IO) {
        // Health Connect doesn't expose programmatic revoke for a subset — only
        // revokeAllPermissions() exists. Documented in user UI: revoking even one
        // type sends user to HC settings to revoke at the platform level.
        val c = client ?: return@withContext
        if (types.size == BioSignalDataType.entries.size) {
            c.permissionController.revokeAllPermissions()
        }
        // For partial revoke we surface a CTA in Settings → Bio-signals that
        // deep-links to the Health Connect permissions screen for our package
        // (handled at the UI layer; nothing to do here).
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reads — one per data type. All wrap in withContext(IO) to keep the
    // calling coroutine off the main thread (CLAUDE.md regola 5: no blocking).
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun readHeartRate(from: Instant, until: Instant): List<BioSignal.HeartRateSample> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            response.records.flatMap { HealthConnectMappers.fromHeartRate(it) }
        }

    override suspend fun readHrv(from: Instant, until: Instant): List<BioSignal.HrvSample> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            response.records.map { HealthConnectMappers.fromHrv(it) }
        }

    override suspend fun readSleepSessions(from: Instant, until: Instant): List<BioSignal.SleepSession> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            val tz = TimeZone.currentSystemDefault()
            response.records.map { HealthConnectMappers.fromSleepSession(it, tz) }
        }

    override suspend fun readStepCounts(from: Instant, until: Instant): List<BioSignal.StepCount> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            val tz = TimeZone.currentSystemDefault()
            response.records.map { HealthConnectMappers.fromSteps(it, tz) }
        }

    override suspend fun readRestingHeartRate(from: Instant, until: Instant): List<BioSignal.RestingHeartRate> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            val tz = TimeZone.currentSystemDefault()
            response.records.map { HealthConnectMappers.fromRestingHeartRate(it, tz) }
        }

    override suspend fun readOxygenSaturation(from: Instant, until: Instant): List<BioSignal.OxygenSaturationSample> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            response.records.map { HealthConnectMappers.fromOxygenSaturation(it) }
        }

    override suspend fun readActivitySessions(from: Instant, until: Instant): List<BioSignal.ActivitySession> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptyList()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.toJavaInstant(), until.toJavaInstant()),
                )
            )
            response.records.map { HealthConnectMappers.fromExerciseSession(it) }
        }
}
