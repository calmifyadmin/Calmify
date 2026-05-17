package com.lifo.util.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS `actual` for [ConnectivityObserver] — Phase 10.0 minimal stub
 * (2026-05-17). Always reports [ConnectivityStatus.Available].
 *
 * This unblocks the iOS compile for everything downstream of `core/util`
 * (data/mongo + the `features` modules). It is NOT a real implementation: the SyncEngine
 * on iOS will believe the network is always online and skip its offline
 * backoff path.
 *
 * Phase 10.1 — real connectivity sensing — will wrap Apple's
 * `nw_path_monitor_t` (from `platform.Network`) into a callbackFlow,
 * mirroring the Android `ConnectivityManager.NetworkCallback` shape.
 * Skipped here so this scaffold commit stays surgical (no Kotlin/Native
 * C-interop noise + a real impl would require a real consumer to test against,
 * which doesn't exist yet because there's no iOS app shell).
 */
actual class ConnectivityObserver {

    private val state = MutableStateFlow(ConnectivityStatus.Available)

    actual fun observe(): Flow<ConnectivityStatus> = state.asStateFlow()

    actual fun isOnline(): Boolean = true
}
