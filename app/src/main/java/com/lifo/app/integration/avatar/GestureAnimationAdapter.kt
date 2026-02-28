package com.lifo.app.integration.avatar

import com.lifo.chat.presentation.viewmodel.LiveChatViewModel
import com.lifo.humanoid.api.HumanoidController
import com.lifo.humanoid.api.HumanoidIntegrationHelper

/**
 * Gesture Animation Adapter - Connects Gemini AI to Avatar Animations
 *
 * This adapter bridges the LiveChatViewModel (which receives animation requests
 * from Gemini AI via function calling) to the HumanoidController (which plays
 * the actual avatar animations).
 *
 * ## Architecture:
 * - LiveChatViewModel receives `play_animation` function calls from Gemini
 * - This adapter converts animation names to avatar gestures
 * - HumanoidController plays the corresponding VRMA animations
 *
 * ## Usage:
 * ```kotlin
 * // In your composable where both ViewModels are available
 * val gestureAdapter = remember { GestureAnimationAdapter() }
 *
 * LaunchedEffect(humanoidController, liveChatViewModel) {
 *     gestureAdapter.connect(humanoidController, liveChatViewModel)
 * }
 *
 * DisposableEffect(Unit) {
 *     onDispose { gestureAdapter.disconnect() }
 * }
 * ```
 */
class GestureAnimationAdapter {

    private var isConnected = false

    /**
     * Connect Gemini AI animation requests to HumanoidController.
     *
     * @param humanoidController The avatar controller
     * @param liveChatViewModel The chat ViewModel that receives AI animation requests
     */
    fun connect(
        humanoidController: HumanoidController,
        liveChatViewModel: LiveChatViewModel
    ) {
        if (isConnected) {
            println("[GestureAnimationAdapter] Already connected")
            return
        }

        println("[GestureAnimationAdapter] Connecting Gemini AI to Avatar animations")

        // Create gesture callback using the helper
        val gestureCallback = HumanoidIntegrationHelper.createGestureCallback(
            controller = humanoidController,
            onUnknownAnimation = { animationName ->
                println("[GestureAnimationAdapter] WARNING: Unknown animation requested by AI: $animationName")
            }
        )

        // Attach callback to LiveChatViewModel
        liveChatViewModel.attachGestureCallback(gestureCallback)

        isConnected = true
        println("[GestureAnimationAdapter] Gesture animations connected successfully")
    }

    /**
     * Disconnect and cleanup.
     */
    fun disconnect() {
        println("[GestureAnimationAdapter] Disconnecting gesture animations")
        isConnected = false
    }

    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean = isConnected
}
