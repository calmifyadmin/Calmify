package com.lifo.humanoid.animation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Controller for natural eye blinking animation.
 * Implements realistic patterns based on human blinking studies.
 *
 * Human blinking characteristics:
 * - Average frequency: 15-20 blinks/minute
 * - Blink duration: 100-400ms
 * - Clustering: blinks tend to occur in groups
 * - Variability: irregular intervals between 2-10 seconds
 */
class BlinkController {

    private val _blinkWeight = MutableStateFlow(0f)
    val blinkWeight: StateFlow<Float> = _blinkWeight.asStateFlow()

    private val _isBlinking = MutableStateFlow(false)
    val isBlinking: StateFlow<Boolean> = _isBlinking.asStateFlow()

    private var isRunning = false
    private var blinkJob: Job? = null

    // Configuration
    private var config = BlinkConfig()

    /**
     * Blink animation configuration
     */
    data class BlinkConfig(
        val minIntervalMs: Long = 2000L,       // Minimum 2 seconds between blinks
        val maxIntervalMs: Long = 8000L,       // Maximum 8 seconds between blinks
        val blinkDurationMs: Long = 150L,      // Duration of a complete blink
        val doubleBlinkChance: Float = 0.15f,  // 15% chance of double blink
        val halfBlinkChance: Float = 0.1f,     // 10% chance of half blink (squint)
        val randomVariation: Float = 0.2f      // ±20% random variation in timing
    )

    /**
     * Start the natural blinking loop
     *
     * @param scope CoroutineScope for the blinking loop
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("[BlinkController] BlinkController already running")
            return
        }

        isRunning = true
        println("[BlinkController] Starting BlinkController")

        blinkJob = scope.launch {
            while (isActive && isRunning) {
                // Wait for random interval
                val baseInterval = Random.nextLong(config.minIntervalMs, config.maxIntervalMs)
                val variation = (baseInterval * config.randomVariation * (Random.nextFloat() - 0.5f)).toLong()
                val interval = (baseInterval + variation).coerceAtLeast(config.minIntervalMs)

                delay(interval)

                if (!isActive || !isRunning) break

                // Perform blink
                performBlink()

                // Possible double blink
                if (Random.nextFloat() < config.doubleBlinkChance) {
                    delay(100L + Random.nextLong(50L))
                    performBlink()
                }
            }
        }
    }

    /**
     * Stop the blinking loop
     */
    fun stop() {
        println("[BlinkController] Stopping BlinkController")
        isRunning = false
        blinkJob?.cancel()
        blinkJob = null
        _blinkWeight.value = 0f
        _isBlinking.value = false
    }

    /**
     * Update configuration
     */
    fun setConfig(config: BlinkConfig) {
        this.config = config
    }

    /**
     * Trigger a single blink immediately (useful for reactions)
     */
    suspend fun triggerBlink() {
        if (_isBlinking.value) return
        performBlink()
    }

    /**
     * Trigger a double blink immediately
     */
    suspend fun triggerDoubleBlink() {
        if (_isBlinking.value) return
        performBlink()
        delay(100L)
        performBlink()
    }

    /**
     * Perform a single blink with smooth animation
     */
    private suspend fun performBlink() {
        if (_isBlinking.value) return

        _isBlinking.value = true
        val duration = config.blinkDurationMs
        val halfDuration = duration / 2

        // Determine if this is a half blink (squint)
        val maxWeight = if (Random.nextFloat() < config.halfBlinkChance) {
            0.5f + Random.nextFloat() * 0.2f // 0.5-0.7 for half blink
        } else {
            1f
        }

        try {
            // Close eyes (ease-in - starts slow, accelerates)
            animateWeight(0f, maxWeight, halfDuration, EasingType.EASE_IN)

            // Open eyes (ease-out - starts fast, decelerates, slightly faster than close)
            animateWeight(maxWeight, 0f, (halfDuration * 0.8).toLong(), EasingType.EASE_OUT)
        } finally {
            _blinkWeight.value = 0f
            _isBlinking.value = false
        }
    }

    /**
     * Animate weight with specified easing
     */
    private suspend fun animateWeight(
        from: Float,
        to: Float,
        durationMs: Long,
        easing: EasingType
    ) {
        val steps = (durationMs / 8).toInt().coerceAtLeast(1) // ~120fps update rate
        val stepDelay = durationMs / steps

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val easedProgress = when (easing) {
                EasingType.LINEAR -> progress
                EasingType.EASE_IN -> easeInCubic(progress)
                EasingType.EASE_OUT -> easeOutCubic(progress)
                EasingType.EASE_IN_OUT -> easeInOutCubic(progress)
            }
            _blinkWeight.value = from + (to - from) * easedProgress
            delay(stepDelay)
        }
        _blinkWeight.value = to
    }

    /**
     * Easing types for animation curves
     */
    private enum class EasingType {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT
    }

    /**
     * Cubic ease-in: slow start, fast end
     */
    private fun easeInCubic(t: Float): Float = t * t * t

    /**
     * Cubic ease-out: fast start, slow end
     */
    private fun easeOutCubic(t: Float): Float {
        val t1 = 1f - t
        return 1f - t1 * t1 * t1
    }

    /**
     * Cubic ease-in-out: smooth start and end
     */
    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            1f - ((-2f * t + 2f).let { it * it * it }) / 2f
        }
    }

    /**
     * Pause blinking temporarily (useful during intense animations)
     */
    fun pause() {
        isRunning = false
    }

    /**
     * Resume blinking after pause
     */
    fun resume(scope: CoroutineScope) {
        if (!isRunning && blinkJob != null) {
            start(scope)
        }
    }

    /**
     * Check if blinking is currently active
     */
    fun isActive(): Boolean = isRunning
}
