package com.lifo.humanoid.lipsync

import android.util.Log
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.domain.model.Viseme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Main controller for lip-sync animation.
 * Coordinates text-to-phoneme conversion, phoneme-to-viseme mapping, and animation.
 *
 * Features:
 * - Text-based lip-sync generation
 * - Smooth transitions between visemes
 * - Coarticulation for natural speech
 * - Priority-based integration with other blend shapes
 * - SYNCHRONIZED MODE: Real-time audio-driven lip-sync for ultra-accurate sync
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

    // ==================== Synchronized Speech State ====================

    /**
     * State for synchronized audio-driven lip-sync
     */
    private data class SyncState(
        val text: String = "",
        val messageId: String = "",
        val visemeTimings: List<VisemeTiming> = emptyList(),
        val totalDurationMs: Long = 0L,
        val audioStartTime: Long = 0L,
        val isPrepared: Boolean = false,
        val isPlaying: Boolean = false
    )

    private val syncState = AtomicReference(SyncState())
    private var syncJob: Job? = null
    private var audioIntensityMultiplier = 1.0f

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

    // ==================== Synchronized Speech Methods ====================

    /**
     * Prepare lip-sync for synchronized playback.
     * Generates viseme sequence but does NOT start animation until startSynchronized() is called.
     *
     * @param text The text to speak
     * @param estimatedDurationMs Estimated duration (will be adjusted when audio starts)
     * @param messageId Unique identifier for this speech instance
     * @param scope CoroutineScope for execution
     */
    fun prepareSynchronized(
        text: String,
        estimatedDurationMs: Long,
        messageId: String,
        scope: CoroutineScope
    ) {
        stop() // Clear any existing lip-sync

        val durationToUse = if (estimatedDurationMs > 0) estimatedDurationMs else 5000L

        // If text is empty, prepare for AUDIO-REACTIVE mode (pure amplitude-based lip-sync)
        if (text.isBlank()) {
            Log.d(TAG, "🎬 Preparing AUDIO-REACTIVE lip-sync (no text): $messageId")

            syncState.set(SyncState(
                text = "",
                messageId = messageId,
                visemeTimings = emptyList(), // No pre-calculated visemes
                totalDurationMs = durationToUse,
                isPrepared = true,
                isPlaying = false
            ))
            return
        }

        Log.d(TAG, "🎬 Preparing TEXT-BASED lip-sync: '${text.take(50)}...' (${durationToUse}ms)")

        // Generate phonemes and visemes from text
        val phonemeTimings = phonemeConverter.textToPhonemes(text, durationToUse)
        val visemeTimings = VisemeMapper.phonemesToVisemes(phonemeTimings)

        Log.d(TAG, "📝 Generated ${visemeTimings.size} visemes for synchronized playback")

        syncState.set(SyncState(
            text = text,
            messageId = messageId,
            visemeTimings = visemeTimings,
            totalDurationMs = durationToUse,
            isPrepared = true,
            isPlaying = false
        ))
    }

    /**
     * Start synchronized lip-sync playback NOW.
     * Call this when audio playback actually starts (e.g., AudioTrack.play()).
     *
     * @param actualDurationMs The actual audio duration (if known, otherwise 0 to use estimate)
     * @param scope CoroutineScope for execution
     */
    fun startSynchronized(actualDurationMs: Long = 0, scope: CoroutineScope) {
        val state = syncState.get()

        if (!state.isPrepared) {
            Log.w(TAG, "startSynchronized: Not prepared, call prepareSynchronized first")
            return
        }

        if (state.isPlaying) {
            Log.w(TAG, "startSynchronized: Already playing")
            return
        }

        // Use actual duration if provided, otherwise use estimate
        val durationToUse = if (actualDurationMs > 0) actualDurationMs else state.totalDurationMs

        // Re-calculate timings if duration changed significantly
        val visemeTimings = if (actualDurationMs > 0 &&
            kotlin.math.abs(actualDurationMs - state.totalDurationMs) > 500) {
            Log.d(TAG, "⏱️ Recalculating visemes for actual duration: ${actualDurationMs}ms (was ${state.totalDurationMs}ms)")
            val phonemeTimings = phonemeConverter.textToPhonemes(state.text, actualDurationMs)
            VisemeMapper.phonemesToVisemes(phonemeTimings)
        } else {
            state.visemeTimings
        }

        val startTime = System.currentTimeMillis()
        syncState.set(state.copy(
            audioStartTime = startTime,
            totalDurationMs = durationToUse,
            visemeTimings = visemeTimings,
            isPlaying = true
        ))

        _isSpeaking.value = true
        _progress.value = 0f

        // Determine if we're in audio-reactive mode (no text/visemes)
        val isAudioReactiveMode = visemeTimings.isEmpty()

        if (isAudioReactiveMode) {
            Log.d(TAG, "▶️ Starting AUDIO-REACTIVE lip-sync (amplitude-driven)")
        } else {
            Log.d(TAG, "▶️ Starting TEXT-BASED lip-sync (${visemeTimings.size} visemes, ${durationToUse}ms)")
        }

        syncJob = scope.launch {
            try {
                if (isAudioReactiveMode) {
                    playAudioReactiveLoop(startTime)
                } else {
                    playSynchronizedSequence(visemeTimings, durationToUse, startTime)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "⏹️ Synchronized lip-sync cancelled")
            } finally {
                _isSpeaking.value = false
                _progress.value = 0f
                syncState.set(SyncState())
            }
        }
    }

    /**
     * Update synchronized playback progress based on audio position.
     * Call this regularly during playback for real-time sync.
     *
     * @param progressMs Current audio playback position in milliseconds
     * @param totalDurationMs Total audio duration (may update as streaming continues)
     */
    fun updateSyncProgress(progressMs: Long, totalDurationMs: Long) {
        val state = syncState.get()
        if (!state.isPlaying) return

        // Update progress
        val normalizedProgress = (progressMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        _progress.value = normalizedProgress

        // If duration changed significantly, we may need to adjust timing
        if (totalDurationMs > 0 && kotlin.math.abs(totalDurationMs - state.totalDurationMs) > 1000) {
            syncState.set(state.copy(totalDurationMs = totalDurationMs))
            Log.d(TAG, "📊 Updated total duration: ${totalDurationMs}ms")
        }
    }

    /**
     * Update lip-sync intensity based on real-time audio amplitude.
     * Call this frequently for natural lip movement that matches audio volume.
     *
     * @param audioLevel Audio amplitude (0.0-1.0)
     */
    fun updateAudioIntensity(audioLevel: Float) {
        // Map audio level to intensity multiplier (0.3-1.5 range for natural variation)
        audioIntensityMultiplier = 0.3f + (audioLevel * 1.2f)
    }

    /**
     * Stop synchronized lip-sync immediately.
     * Call this when audio is interrupted or stopped.
     */
    fun stopSynchronized() {
        syncJob?.cancel()
        syncJob = null
        syncState.set(SyncState())
        _isSpeaking.value = false
        _progress.value = 0f
        audioIntensityMultiplier = 1.0f

        // Clear lip-sync blend shapes with quick fade
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)

        Log.d(TAG, "⏹️ Synchronized lip-sync stopped")
    }

    /**
     * Play audio-reactive lip-sync loop.
     * Maps real-time audio amplitude directly to mouth blend shapes.
     * Used when text is not available (streaming audio mode).
     */
    private suspend fun playAudioReactiveLoop(startTime: Long) {
        Log.d(TAG, "🎤 Audio-reactive loop started")

        // Define mouth blend shapes for audio-reactive mode
        // We'll cycle through AA (open mouth) based on audio intensity
        val baseViseme = Viseme.AA // Open mouth for speaking

        var lastIntensity = 0f
        val smoothingFactor = 0.3f // Smooth transitions between intensity levels

        while (syncState.get().isPlaying) {
            // Get current audio intensity (updated externally via updateAudioIntensity)
            val targetIntensity = audioIntensityMultiplier.coerceIn(0f, 1.5f)

            // Smooth the intensity change for natural movement
            lastIntensity = lastIntensity + (targetIntensity - lastIntensity) * smoothingFactor

            // Map intensity to viseme blend shapes
            val effectiveIntensity = lastIntensity * config.intensity

            if (effectiveIntensity > 0.1f) {
                // Apply mouth opening based on audio level
                val blendShapes = getAudioReactiveBlendShapes(effectiveIntensity)
                blendShapeController.setCategoryWeights(
                    VrmBlendShapeController.CATEGORY_LIPSYNC,
                    blendShapes
                )
                _currentViseme.value = baseViseme
            } else {
                // Low audio - nearly closed mouth
                blendShapeController.setCategoryWeights(
                    VrmBlendShapeController.CATEGORY_LIPSYNC,
                    mapOf("A" to 0.05f) // Minimal mouth opening
                )
                _currentViseme.value = Viseme.SILENCE
            }

            // Update at ~60fps for smooth animation
            delay(16)
        }

        // Fade out when done
        fadeToNeutral()
        Log.d(TAG, "🎤 Audio-reactive loop ended")
    }

    /**
     * Generate blend shapes for audio-reactive lip-sync based on amplitude.
     * Creates natural-looking mouth movements by combining multiple visemes.
     */
    private fun getAudioReactiveBlendShapes(intensity: Float): Map<String, Float> {
        // For natural speech, we blend between different mouth shapes based on intensity
        // Low intensity: slight mouth opening (like "eh")
        // Medium intensity: open mouth (like "ah")
        // High intensity: wide open (like "AA")

        return when {
            intensity < 0.3f -> mapOf(
                "A" to intensity * 0.5f,           // Slight opening
                "I" to intensity * 0.2f            // Slight smile
            )
            intensity < 0.6f -> mapOf(
                "A" to intensity * 0.7f,           // Medium opening
                "O" to intensity * 0.2f,           // Slight rounding
                "I" to 0.1f                        // Natural expression
            )
            intensity < 0.9f -> mapOf(
                "A" to intensity * 0.85f,          // Wide opening
                "O" to intensity * 0.15f           // Some rounding
            )
            else -> mapOf(
                "A" to 1.0f,                       // Full open
                "O" to 0.2f                        // Rounding for expressiveness
            )
        }
    }

    /**
     * Play viseme sequence synchronized to audio timeline
     */
    private suspend fun playSynchronizedSequence(
        visemes: List<VisemeTiming>,
        totalDurationMs: Long,
        startTime: Long
    ) {
        if (visemes.isEmpty()) return

        var previousViseme: Viseme? = null

        for (visemeTiming in visemes) {
            // Calculate target time relative to audio start
            val targetTime = startTime + visemeTiming.startTimeMs
            val now = System.currentTimeMillis()
            val waitTime = targetTime - now

            if (waitTime > 0) {
                delay(waitTime)
            } else if (waitTime < -100) {
                // We're running behind, skip to catch up
                continue
            }

            // Check if still playing
            if (!syncState.get().isPlaying) break

            // Update progress
            _progress.value = visemeTiming.startTimeMs.toFloat() / totalDurationMs

            // Apply viseme with audio-modulated intensity
            val modulatedIntensity = config.intensity * audioIntensityMultiplier
            applyVisemeWithCoarticulation(
                visemeTiming.viseme,
                previousViseme,
                visemeTiming.durationMs,
                modulatedIntensity
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
     * Apply a viseme with coarticulation and custom intensity
     */
    private suspend fun applyVisemeWithCoarticulation(
        viseme: Viseme,
        previousViseme: Viseme?,
        durationMs: Long,
        intensityOverride: Float = config.intensity
    ) {
        // Get base blend shapes for this viseme
        val blendShapes = VisemeMapper.getVisemeBlendShapes(viseme, intensityOverride).toMutableMap()

        // Apply coarticulation from previous viseme
        if (previousViseme != null) {
            val prevShapes = VisemeMapper.getVisemeBlendShapes(previousViseme, intensityOverride)
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
     * Play the viseme sequence with smooth transitions (legacy method)
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

            // Apply viseme with coarticulation (using default intensity)
            applyVisemeWithCoarticulation(
                visemeTiming.viseme,
                previousViseme,
                visemeTiming.durationMs,
                config.intensity
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
