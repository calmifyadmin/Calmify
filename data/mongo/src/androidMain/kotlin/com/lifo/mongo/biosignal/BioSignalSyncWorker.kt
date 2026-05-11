package com.lifo.mongo.biosignal

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.ProviderStatus
import kotlinx.datetime.Clock
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days

/**
 * Periodic worker that pulls fresh bio-signal data from Health Connect into the
 * local SQLDelight cache, computes aggregates, and pushes aggregates to the
 * server.
 *
 * **Schedule**: every 24h with a 4h flex window — Android can opportunistically
 * run earlier if the device is idle + charging + on unmetered network. The
 * 24h period is chosen to:
 * - Avoid waking the wearable's companion app too frequently
 * - Stay well under any provider rate limits
 * - Match the daily-cadence rhythm of HRV / sleep / resting-HR data
 *
 * **Idempotency**: the underlying [BioSignalRepository.ingestFromProvider] is
 * idempotent on (timestamp, type, source) — safe to re-run for the same
 * window. WorkManager retry policy is OK with this.
 *
 * **Failure handling**: any uncaught exception → `Result.retry()` with
 * WorkManager exponential backoff. Repeated failures don't cascade — each run
 * is independent.
 */
class BioSignalSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
            ?: return Result.failure().also {
                Log.w(TAG, "Koin not initialized — worker cannot resolve dependencies")
            }

        val provider: HealthDataProvider = runCatching {
            koin.get<HealthDataProvider>()
        }.getOrElse {
            Log.w(TAG, "HealthDataProvider not registered: ${it.message}")
            return Result.failure()
        }
        val repository: BioSignalRepository = runCatching {
            koin.get<BioSignalRepository>()
        }.getOrElse {
            Log.w(TAG, "BioSignalRepository not registered: ${it.message}")
            return Result.failure()
        }

        // Skip work if user has not granted permission yet — surface stat to UI
        // via repository's audit log, not via Worker failure.
        val status = provider.checkAvailability()
        if (status !is ProviderStatus.Ready) {
            Log.i(TAG, "Provider not ready ($status) — skipping this run")
            return Result.success()
        }

        return try {
            // Reconcile the last 25h of data (1h overlap with previous run to
            // catch late-arriving samples from the wearable companion app).
            val since = Clock.System.now().minus(WINDOW_DURATION)
            val result = repository.ingestFromProvider(provider, since)
            Log.i(
                TAG,
                "Ingest: raw=${result.rawSamplesIngested}, " +
                    "aggregates=${result.aggregatesComputed}, " +
                    "pushed=${result.aggregatesPushed}, " +
                    "errors=${result.errors.size}",
            )
            // Periodic TTL prune piggy-backs on this run to keep local DB lean.
            val pruned = repository.pruneExpiredSamples()
            if (pruned > 0) Log.i(TAG, "Pruned $pruned expired samples")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Worker failed — will retry", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BioSignalSync"
        const val UNIQUE_NAME = "calmify-biosignal-sync"
        private val WINDOW_DURATION = 25.days / 24    // 25h overlap window

        /**
         * Enqueue (or re-enqueue) the periodic worker. Idempotent — safe to call
         * on every app start.
         *
         * Call from `MainActivity.onCreate` after Koin is initialized, OR from
         * onboarding completion (whichever comes first). [ExistingPeriodicWorkPolicy.KEEP]
         * ensures we don't reset the schedule on every app open.
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)   // prefer wifi for aggregate upload
                .setRequiresBatteryNotLow(true)                  // be respectful of user's device
                .build()

            val request = PeriodicWorkRequestBuilder<BioSignalSyncWorker>(
                repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 4, flexTimeIntervalUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Cancel the worker — invoke when user revokes the bio integration entirely. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
