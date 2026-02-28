package com.lifo.app.integration.avatar

import com.lifo.humanoid.lipsync.LipSyncController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing LipSyncController from Hilt in avatar integration scenarios.
 *
 * This allows features/chat (which can't depend on features/humanoid) to access
 * humanoid components through the app module.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AvatarIntegrationEntryPoint {
    fun lipSyncController(): LipSyncController
}
