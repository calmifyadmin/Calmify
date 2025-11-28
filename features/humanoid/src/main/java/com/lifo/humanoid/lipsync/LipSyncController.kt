package com.lifo.humanoid.lipsync

import android.util.Log
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.domain.model.Viseme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main controller for lip-sync animation.
 * Coordinates text-to-phoneme conversion, phoneme-to-viseme mapping, and animation.
 *
 * Features:
 * - Text-based lip-sync generation
 * - Smooth transitions between visemes
 * - Coarticulation for natural speech
 * - Priority-based integration with other blend shapes
 */
class LipSyncController(
    private val phonemeConverter: PhonemeConverter,
    private val blendShapeController: VrmBlendShapeController
) {

    companion object {
        private const val TAG = "LipSyncController"
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentViseme = MutableStateFlow(Viseme.SILENCE)
    val currentViseme: StateFlow<Viseme> = _currentViseme.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var lipSyncJob: Job? = null

    // Configuration for viseme transitions
    private var config = LipSyncConfig()

    /**
     * Lip-sync configuration
     */
    data class LipSyncConfig(
        val fadeInMs: Long = 40L,           // Time to reach target viseme
        val fadeOutMs: Long = 60L,          // Time to return to neutral
        val minVisemeDurationMs: Long = 30L, // Minimum viseme duration
        val coarticulationStrength: Float = 0.25f, // Influence of previous viseme
        val intensity: Float = 1.0f          // Overall lip movement intensity
    )

    /**
     * Start lip-sync for a text with specified duration
     *
     * @param text The text to speak
     * @param durationMs Total duration of the speech in milliseconds
     * @param scope CoroutineScope for execution
     */
    fun speak(text: String, durationMs: Long, scope: CoroutineScope) {
        stop()

        if (text.isBlank() || durationMs <= 0) {
            Log.w(TAG, "Invalid input: text='$text', duration=$durationMs")
            return
        }

        _isSpeaking.value = true
        Log.d(TAG, "Starting lip-sync for: '$text', duration: ${durationMs}ms")

        // Convert text to phonemes
        val phonemeTimings = phonemeConverter.textToPhonemes(text, durationMs)
        Log.d(TAG, "Generated ${phonemeTimings.size} phonemes")

        if (phonemeTimings.isEmpty()) {
            _isSpeaking.value = false
            return
        }

        // Convert phonemes to visemes
        val visemeTimings = VisemeMapper.phonemesToVisemes(phonemeTimings)

        lipSyncJob = scope.launch {
            try {
                playVisemeSequence(visemeTimings, durationMs)
            } finally {
                _isSpeaking.value = false
                _currentViseme.value = Viseme.SILENCE
                _progress.value = 0f
            }
        }
    }

    /**
     * Stop current lip-sync
     */
    fun stop() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        _isSpeaking.value = false
        _currentViseme.value = Viseme.SILENCE
        _progress.value = 0f

        // Clear lip-sync blend shapes
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
    }

    /**
     * Set configuration
     */
    fun setConfig(config: LipSyncConfig) {
        this.config = config
    }

    /**
     * Play the viseme sequence with smooth transitions
     */
    private suspend fun playVisemeSequence(visemes: List<VisemeTiming>, totalDurationMs: Long) {
        if (visemes.isEmpty()) return

        var previousViseme: Viseme? = null
        val startTime = System.currentTimeMillis()

        for (visemeTiming in visemes) {
            // Wait until the correct start time
            val targetTime = startTime + visemeTiming.startTimeMs
            val waitTime = targetTime - System.currentTimeMillis()
            if (waitTime > 0) {
                delay(waitTime)
            }

            // Check if cancelled
            if (!_isSpeaking.value) break

            // Update progress
            _progress.value = visemeTiming.startTimeMs.toFloat() / totalDurationMs

            // Apply viseme with coarticulation
            applyVisemeWithCoarticulation(
                visemeTiming.viseme,
                previousViseme,
                visemeTiming.durationMs
            )

            _currentViseme.value = visemeTiming.viseme
            previousViseme = visemeTiming.viseme

            // Hold for duration (minus fade time)
            val holdTime = visemeTiming.durationMs - config.fadeInMs
            if (holdTime > 0) {
                delay(holdTime)
            }
        }

        // Final fade out
        fadeToNeutral()
        _progress.value = 1f
    }

    /**
     * Apply a viseme with coarticulation (influence from previous viseme)
     */
    private suspend fun applyVisemeWithCoarticulation(
        viseme: Viseme,
        previousViseme: Viseme?,
        durationMs: Long
    ) {
        // Get base blend shapes for this viseme
        val blendShapes = VisemeMapper.getVisemeBlendShapes(viseme, config.intensity).toMutableMap()

        // Apply coarticulation from previous viseme
        if (previousViseme != null) {
            val prevShapes = VisemeMapper.getVisemeBlendShapes(previousViseme, config.intensity)
            prevShapes.forEach { (name, prevWeight) ->
                val currentWeight = blendShapes[name] ?: 0f
                blendShapes[name] = currentWeight + prevWeight * config.coarticulationStrength
            }
        }

        // Normalize weights if any exceed 1.0
        val maxWeight = blendShapes.values.maxOrNull() ?: 1f
        if (maxWeight > 1f) {
            blendShapes.forEach { (name, weight) ->
                blendShapes[name] = weight / maxWeight
            }
        }

        // Apply with smooth fade-in
        fadeToViseme(blendShapes, config.fadeInMs.coerceAtMost(durationMs / 2))
    }

    /**
     * Smoothly transition to a set of blend shapes
     */
    private suspend fun fadeToViseme(targetShapes: Map<String, Float>, durationMs: Long) {
        if (durationMs <= 0) {
            blendShapeController.setCategoryWeights(
                VrmBlendShapeController.CATEGORY_LIPSYNC,
                targetShapes
            )
            return
        }

        val steps = (durationMs / 8).toInt().coerceAtLeast(1) // ~120fps max
        val stepDelay = durationMs / steps

        for (step in 1..steps) {
            val progress = step.toFloat() / steps
            val easedProgress = easeOutQuad(progress)

            val interpolatedShapes = targetShapes.mapValues { (_, targetWeight) ->
                targetWeight * easedProgress
            }

            blendShapeController.setCategoryWeights(
                VrmBlendShapeController.CATEGORY_LIPSYNC,
                interpolatedShapes
            )

            delay(stepDelay)
        }
    }

    /**
     * Fade to neutral mouth position
     */
    private suspend fun fadeToNeutral() {
        val steps = (config.fadeOutMs / 8).toInt().coerceAtLeast(1)
        val stepDelay = config.fadeOutMs / steps

        // Get current weights and fade them out
        val currentWeights = blendShapeController.currentWeights.value

        for (step in 1..steps) {
            val progress = step.toFloat() / steps
            val easedProgress = easeOutQuad(progress)

            val fadedWeights = currentWeights.mapValues { (_, weight) ->
                weight * (1f - easedProgress)
            }

            blendShapeController.setCategoryWeights(
                VrmBlendShapeController.CATEGORY_LIPSYNC,
                fadedWeights
            )

            delay(stepDelay)
        }

        // Clear completely
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
    }

    /**
     * Easing function for smooth transitions
     */
    private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

    /**
     * Easing function for smooth in-out transitions
     */
    private fun easeInOutQuad(t: Float): Float {
        return if (t < 0.5f) {
            2f * t * t
        } else {
            1f - (-2f * t + 2f).let { it * it } / 2f
        }
    }

    /**
     * Check if lip-sync is active
     */
    fun isActive(): Boolean = _isSpeaking.value
}
