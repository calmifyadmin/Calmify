package com.lifo.mongo.biosignal

import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.lifo.util.model.BioSignalDataType

/**
 * Bridge between Calmify's [BioSignalDataType] enum and Health Connect's
 * permission string-set model.
 *
 * **Read-only by design**: only `READ` permissions are requested. Calmify
 * never writes back to Health Connect.
 *
 * **Visibility**: `public` so [com.lifo.biocontext.BioOnboardingRouteContent]
 * can resolve the ActivityResultContract + map permission strings back to
 * [BioSignalDataType]. The contract creator is the only platform-bridge that
 * crosses the data → feature boundary by design (Android permission UIs
 * require an Activity host that ViewModels cannot provide).
 */
object HealthConnectPermissions {

    /** Set of HC permission strings for a single Calmify data type. */
    fun permissionsFor(type: BioSignalDataType): Set<String> = when (type) {
        BioSignalDataType.HEART_RATE -> setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
        BioSignalDataType.HRV -> setOf(
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        )
        BioSignalDataType.SLEEP -> setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )
        BioSignalDataType.STEPS -> setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
        )
        BioSignalDataType.RESTING_HEART_RATE -> setOf(
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        )
        BioSignalDataType.OXYGEN_SATURATION -> setOf(
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        )
        BioSignalDataType.ACTIVITY -> setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }

    /** Union of HC permission strings for multiple Calmify data types. */
    fun permissionsFor(types: Set<BioSignalDataType>): Set<String> =
        types.flatMap { permissionsFor(it) }.toSet()

    /** Reverse map: which [BioSignalDataType] does an HC permission string represent? */
    fun dataTypeFor(hcPermission: String): BioSignalDataType? = when {
        hcPermission == HealthPermission.getReadPermission(HeartRateRecord::class) -> BioSignalDataType.HEART_RATE
        hcPermission == HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) -> BioSignalDataType.HRV
        hcPermission == HealthPermission.getReadPermission(SleepSessionRecord::class) -> BioSignalDataType.SLEEP
        hcPermission == HealthPermission.getReadPermission(StepsRecord::class) -> BioSignalDataType.STEPS
        hcPermission == HealthPermission.getReadPermission(RestingHeartRateRecord::class) -> BioSignalDataType.RESTING_HEART_RATE
        hcPermission == HealthPermission.getReadPermission(OxygenSaturationRecord::class) -> BioSignalDataType.OXYGEN_SATURATION
        hcPermission == HealthPermission.getReadPermission(ExerciseSessionRecord::class) -> BioSignalDataType.ACTIVITY
        else -> null
    }

    /** Filter a granted-permissions set back to the Calmify enum view. */
    fun grantedDataTypes(grantedHc: Set<String>): Set<BioSignalDataType> =
        grantedHc.mapNotNull { dataTypeFor(it) }.toSet()

    /** ActivityResultContract for requesting permissions — exposed to onboarding UI. */
    fun createRequestPermissionResultContract() =
        PermissionController.createRequestPermissionResultContract()
}
