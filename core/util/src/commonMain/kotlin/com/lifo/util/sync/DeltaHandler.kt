package com.lifo.util.sync

import com.lifo.shared.api.GenericDeltaResponse

/**
 * Interface for applying server delta responses to the local database.
 *
 * Implemented by DeltaApplier in data/mongo, injected via Koin.
 * This interface lives in core/util to avoid circular dependencies
 * between data/network and data/mongo.
 */
interface DeltaHandler {
    suspend fun apply(entityType: String, delta: GenericDeltaResponse)
}
