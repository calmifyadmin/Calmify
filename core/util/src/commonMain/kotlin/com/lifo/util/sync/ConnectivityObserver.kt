package com.lifo.util.sync

import kotlinx.coroutines.flow.Flow

/**
 * Observes network connectivity state.
 * expect/actual — Android uses ConnectivityManager, iOS uses NWPathMonitor.
 */
expect class ConnectivityObserver {
    fun observe(): Flow<ConnectivityStatus>
    fun isOnline(): Boolean
}

enum class ConnectivityStatus {
    Available,
    Unavailable,
    Losing,
}
