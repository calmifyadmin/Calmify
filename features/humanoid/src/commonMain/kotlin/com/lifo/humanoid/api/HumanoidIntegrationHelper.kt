package com.lifo.humanoid.api

/**
 * Helper class for integrating HumanoidController with external modules.
 *
 * This class provides utilities for connecting avatar animations to external
 * systems like Gemini AI, without requiring direct dependencies.
 *
 * Usage at app module level:
 * ```kotlin
 * // In your Activity/Fragment where both ViewModels are available
 * val gestureCallback = HumanoidIntegrationHelper.createGestureCallback(humanoidController)
 * liveChatViewModel.attachGestureCallback(gestureCallback)
 * ```
 */
object HumanoidIntegrationHelper {

    /**
     * Create a gesture callback that can be attached to any ViewModel
     * that needs to trigger avatar animations via string names.
     *
     * @param controller The HumanoidController instance
     * @return A callback function that accepts animation names and triggers gestures
     */
    fun createGestureCallback(controller: HumanoidController): (String) -> Unit {
        return { animationName ->
            println("[HumanoidIntegration] Gesture callback triggered: $animationName")
            controller.playAnimationByName(animationName)
        }
    }

    /**
     * Create a gesture callback with custom error handling.
     *
     * @param controller The HumanoidController instance
     * @param onUnknownAnimation Called when animation name is not recognized
     * @return A callback function that accepts animation names and triggers gestures
     */
    fun createGestureCallback(
        controller: HumanoidController,
        onUnknownAnimation: ((String) -> Unit)? = null
    ): (String) -> Unit {
        return { animationName ->
            println("[HumanoidIntegration] Gesture callback triggered: $animationName")
            val success = controller.playAnimationByName(animationName)
            if (!success) {
                onUnknownAnimation?.invoke(animationName)
            }
        }
    }

    /**
     * Get all available animation names that can be used with Gemini AI.
     * Useful for building system prompts or documentation.
     */
    fun getAvailableAnimationNames(): List<String> = GestureType.getAllNames()

    /**
     * Get animation names grouped by category for documentation.
     */
    fun getAnimationsByCategory(): Map<String, List<String>> = mapOf(
        "greetings" to listOf("greeting", "hello"),
        "agreement" to listOf("yes_with_head", "i_agree"),
        "disagreement" to listOf("no_with_head", "i_dont_think_so"),
        "uncertainty" to listOf("i_dont_know"),
        "emotions" to listOf("angry", "sad", "dancing_happy", "you_are_crazy"),
        "actions" to listOf("dance", "peace_sign", "shoot", "pointing_thing", "showFullBody")
    )
}
