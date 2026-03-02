package com.lifo.app.integration.avatar

import com.lifo.humanoid.lipsync.LipSyncController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Koin-based entry point for accessing LipSyncController in avatar integration scenarios.
 *
 * This allows features/chat (which can't depend on features/humanoid) to access
 * humanoid components through the app module.
 */
object AvatarIntegrationEntryPoint : KoinComponent {
    val lipSyncController: LipSyncController by inject()
}
