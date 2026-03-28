package com.lifo.humanoid.lipsync

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
 * - Smooth transitions between visemes (crossfade from current weights)
 * - Coarticulation for natural speech
 * - Priority-based integration with other blend shapes
 * - SYNCHRONIZED MODE: Real-time audio-driven lip-sync for ultra-accurate sync
 */
class LipSyncController(
    private val phonemeConverter: PhonemeConverter,
    private val blendShapeController: VrmBlendShapeController
) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentViseme = MutableStateFlow(Viseme.SILENCE)
    val currentViseme: StateFlow<Viseme> = _currentViseme.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var lipSyncJob: Job? = null

    // Tracks the actual current blend shape weights for crossfading
    private val currentLipWeights = AtomicReference<Map<String, Float>>(emptyMap())

    // Configuration for viseme transitions
    private var config = LipSyncConfig()

    // ==================== Synchronized Speech State ====================

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
    private val externalVisemeWeights = AtomicReference<Map<String, Float>>(emptyMap())

    /**
     * Lip-sync configuration
     */
    data class LipSyncConfig(
        val fadeInMs: Long = 16L,            // Crossfade duration toward target viseme (short = snappy)
        val fadeOutMs: Long = 80L,           // Time to return to neutral
        val minVisemeDurationMs: Long = 30L, // Minimum viseme duration
        val coarticulationStrength: Float = 0.20f, // Influence of previous viseme
        val intensity: Float = 1.0f,         // Overall lip movement intensity
        // Asymmetric envelope for audio-reactive mode:
        // attack fast (follow rising audio immediately),
        // release slow (decay naturally like real speech formants)
        val lerpAttack: Float = 0.75f,       // per-frame lerp when weight is rising
        val lerpRelease: Float = 0.18f       // per-frame lerp when weight is falling
    )

    // ==================== Synchronized Speech Methods ====================

    fun prepareSynchronized(
        text: String,
        estimatedDurationMs: Long,
        messageId: String,
        scope: CoroutineScope
    ) {
        stop()

        val durationToUse = if (estimatedDurationMs > 0) estimatedDurationMs else 5000L

        if (text.isBlank()) {
            println("[LipSyncController] Preparing AUDIO-REACTIVE lip-sync (no text): $messageId")
            syncState.set(SyncState(
                text = "",
                messageId = messageId,
                visemeTimings = emptyList(),
                totalDurationMs = durationToUse,
                isPrepared = true,
                isPlaying = false
            ))
            return
        }

        println("[LipSyncController] Preparing TEXT-BASED lip-sync: '${text.take(50)}...' (${durationToUse}ms)")

        val phonemeTimings = phonemeConverter.textToPhonemes(text, durationToUse)
        val visemeTimings = VisemeMapper.phonemesToVisemes(phonemeTimings)

        println("[LipSyncController] Generated ${visemeTimings.size} visemes for synchronized playback")

        syncState.set(SyncState(
            text = text,
            messageId = messageId,
            visemeTimings = visemeTimings,
            totalDurationMs = durationToUse,
            isPrepared = true,
            isPlaying = false
        ))
    }

    fun startSynchronized(actualDurationMs: Long = 0, scope: CoroutineScope) {
        val state = syncState.get()

        if (!state.isPrepared) {
            println("[LipSyncController] WARNING: startSynchronized: Not prepared, call prepareSynchronized first")
            return
        }

        if (state.isPlaying) {
            println("[LipSyncController] WARNING: startSynchronized: Already playing")
            return
        }

        val durationToUse = if (actualDurationMs > 0) actualDurationMs else state.totalDurationMs

        val visemeTimings = if (actualDurationMs > 0 &&
            kotlin.math.abs(actualDurationMs - state.totalDurationMs) > 500) {
            println("[LipSyncController] Recalculating visemes for actual duration: ${actualDurationMs}ms (was ${state.totalDurationMs}ms)")
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
        currentLipWeights.set(emptyMap())

        val isAudioReactiveMode = visemeTimings.isEmpty()

        if (isAudioReactiveMode) {
            println("[LipSyncController] Starting AUDIO-REACTIVE lip-sync (amplitude-driven)")
        } else {
            println("[LipSyncController] Starting TEXT-BASED lip-sync (${visemeTimings.size} visemes, ${durationToUse}ms)")
        }

        syncJob = scope.launch {
            try {
                if (isAudioReactiveMode) {
                    playAudioReactiveLoop(startTime)
                } else {
                    playSynchronizedSequence(visemeTimings, durationToUse, startTime)
                }
            } catch (e: CancellationException) {
                println("[LipSyncController] Synchronized lip-sync cancelled")
            } finally {
                _isSpeaking.value = false
                _progress.value = 0f
                currentLipWeights.set(emptyMap())
                syncState.set(SyncState())
            }
        }
    }

    fun updateSyncProgress(progressMs: Long, totalDurationMs: Long) {
        val state = syncState.get()
        if (!state.isPlaying) return

        val normalizedProgress = (progressMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        _progress.value = normalizedProgress

        if (totalDurationMs > 0 && kotlin.math.abs(totalDurationMs - state.totalDurationMs) > 1000) {
            syncState.set(state.copy(totalDurationMs = totalDurationMs))
            println("[LipSyncController] Updated total duration: ${totalDurationMs}ms")
        }
    }

    fun updateAudioIntensity(audioLevel: Float) {
        // No floor offset: allow true zero so the mouth actually closes between syllables.
        // Multiply by 1.3 to compensate for the removed 0.4 boost on loud frames.
        audioIntensityMultiplier = (audioLevel * 1.3f).coerceIn(0f, 1.5f)
    }

    fun updateVisemeWeights(weights: Map<String, Float>) {
        externalVisemeWeights.set(weights)
    }

    fun stopSynchronized() {
        syncJob?.cancel()
        syncJob = null
        syncState.set(SyncState())
        _isSpeaking.value = false
        _progress.value = 0f
        audioIntensityMultiplier = 1.0f
        externalVisemeWeights.set(emptyMap())
        currentLipWeights.set(emptyMap())
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
        println("[LipSyncController] Synchronized lip-sync stopped")
    }

    /**
     * Audio-reactive loop with FFT-derived viseme weights.
     *
     * FIX: lerp factor reduced to 0.18f (was 0.95f) so the mouth
     * glides smoothly toward the target instead of snapping to it.
     */
    private suspend fun playAudioReactiveLoop(startTime: Long) {
        println("[LipSyncController] Audio-reactive loop started (FFT-enhanced)")

        val smoothedWeights = mutableMapOf<String, Float>()

        while (syncState.get().isPlaying) {
            val fftWeights = externalVisemeWeights.get()
            val intensityMultiplier = audioIntensityMultiplier.coerceIn(0f, 1.5f)

            val targetWeights: Map<String, Float> = when {
                fftWeights.isNotEmpty() && intensityMultiplier > 0.12f -> {
                    val vol = (intensityMultiplier * config.intensity).coerceAtMost(0.85f)
                    fftWeights.mapValues { (_, w) -> (w * vol).coerceIn(0f, 1f) }
                }
                intensityMultiplier > 0.12f -> {
                    val eff = (intensityMultiplier * config.intensity).coerceAtMost(1.0f)
                    getAudioReactiveBlendShapes(eff)
                }
                else -> emptyMap() // true silence: mouth fully closed
            }

            // Asymmetric envelope: attack fast, release slow
            val allKeys = (smoothedWeights.keys + targetWeights.keys).toSet()
            allKeys.forEach { name ->
                val current = smoothedWeights[name] ?: 0f
                val target  = targetWeights[name]  ?: 0f
                val factor  = if (target > current) config.lerpAttack else config.lerpRelease
                smoothedWeights[name] = current + (target - current) * factor
            }
            smoothedWeights.keys.removeAll { (smoothedWeights[it] ?: 0f) < 0.005f }

            val snapshot = smoothedWeights.toMap()
            currentLipWeights.set(snapshot)
            blendShapeController.setCategoryWeights(VrmBlendShapeController.CATEGORY_LIPSYNC, snapshot)

            _currentViseme.value = if (intensityMultiplier <= 0.08f) Viseme.SILENCE else {
                when (smoothedWeights.maxByOrNull { it.value }?.key) {
                    "a" -> Viseme.AA; "e" -> Viseme.E; "i" -> Viseme.I
                    "o" -> Viseme.O;  "u" -> Viseme.U; else -> Viseme.SILENCE
                }
            }

            delay(16)
        }

        fadeToNeutral()
        println("[LipSyncController] Audio-reactive loop ended")
    }

    private fun getAudioReactiveBlendShapes(intensity: Float): Map<String, Float> {
        return when {
            intensity < 0.3f -> mapOf("a" to intensity * 0.8f, "i" to intensity * 0.3f)
            intensity < 0.6f -> mapOf("a" to intensity * 0.9f, "o" to intensity * 0.3f, "i" to 0.15f)
            intensity < 0.9f -> mapOf("a" to intensity, "o" to intensity * 0.2f)
            else             -> mapOf("a" to 1.0f, "o" to 0.25f)
        }
    }

    /**
     * Play viseme sequence synchronized to audio timeline.
     *
     * FIX: timing now accounts for both fade-in AND fade-out so
     * adjacent visemes overlap correctly (crossfade, no gaps/jumps).
     */
    private suspend fun playSynchronizedSequence(
        visemes: List<VisemeTiming>,
        totalDurationMs: Long,
        startTime: Long
    ) {
        if (visemes.isEmpty()) return

        var previousViseme: Viseme? = null

        for ((index, visemeTiming) in visemes.withIndex()) {
            val targetTime = startTime + visemeTiming.startTimeMs
            val now = System.currentTimeMillis()
            val waitTime = targetTime - now

            when {
                waitTime > 0 -> delay(waitTime)
                waitTime < -150 -> continue // too late, skip this viseme
            }

            if (!syncState.get().isPlaying) break

            _progress.value = visemeTiming.startTimeMs.toFloat() / totalDurationMs

            val modulatedIntensity = (config.intensity * audioIntensityMultiplier).coerceIn(0f, 1.2f)
            applyVisemeWithCoarticulation(
                visemeTiming.viseme,
                previousViseme,
                visemeTiming.durationMs,
                modulatedIntensity
            )

            _currentViseme.value = visemeTiming.viseme
            previousViseme = visemeTiming.viseme

            // Hold = full duration minus the crossfade-in time.
            // The next viseme's crossfade-in will overlap naturally.
            val holdTime = visemeTiming.durationMs - config.fadeInMs
            if (holdTime > 0) delay(holdTime)
        }

        fadeToNeutral()
        _progress.value = 1f
    }

    /**
     * Apply a viseme crossfading FROM current weights (not from zero).
     */
    private suspend fun applyVisemeWithCoarticulation(
        viseme: Viseme,
        previousViseme: Viseme?,
        durationMs: Long,
        intensityOverride: Float = config.intensity
    ) {
        val targetShapes = VisemeMapper.getVisemeBlendShapes(viseme, intensityOverride).toMutableMap()

        // Coarticulation: blend in a fraction of the previous viseme
        if (previousViseme != null) {
            val prevShapes = VisemeMapper.getVisemeBlendShapes(previousViseme, intensityOverride)
            prevShapes.forEach { (name, prevWeight) ->
                val currentWeight = targetShapes[name] ?: 0f
                targetShapes[name] = currentWeight + prevWeight * config.coarticulationStrength
            }
        }

        // Normalize if any weight exceeds 1.0
        val maxWeight = targetShapes.values.maxOrNull() ?: 1f
        if (maxWeight > 1f) {
            targetShapes.replaceAll { _, weight -> weight / maxWeight }
        }

        val fadeDuration = config.fadeInMs.coerceAtMost(durationMs / 2)
        // Crossfade from wherever we currently are
        crossfadeTo(targetShapes, fadeDuration)
    }

    fun speak(text: String, durationMs: Long, scope: CoroutineScope) {
        stop()

        if (text.isBlank() || durationMs <= 0) {
            println("[LipSyncController] WARNING: Invalid input: text='$text', duration=$durationMs")
            return
        }

        _isSpeaking.value = true
        currentLipWeights.set(emptyMap())
        println("[LipSyncController] Starting lip-sync for: '$text', duration: ${durationMs}ms")

        val phonemeTimings = phonemeConverter.textToPhonemes(text, durationMs)
        println("[LipSyncController] Generated ${phonemeTimings.size} phonemes")

        if (phonemeTimings.isEmpty()) {
            _isSpeaking.value = false
            return
        }

        val visemeTimings = VisemeMapper.phonemesToVisemes(phonemeTimings)

        lipSyncJob = scope.launch {
            try {
                playVisemeSequence(visemeTimings, durationMs)
            } finally {
                _isSpeaking.value = false
                _currentViseme.value = Viseme.SILENCE
                _progress.value = 0f
                currentLipWeights.set(emptyMap())
            }
        }
    }

    fun stop() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        _isSpeaking.value = false
        _currentViseme.value = Viseme.SILENCE
        _progress.value = 0f
        currentLipWeights.set(emptyMap())
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
    }

    fun setConfig(config: LipSyncConfig) {
        this.config = config
    }

    private suspend fun playVisemeSequence(visemes: List<VisemeTiming>, totalDurationMs: Long) {
        if (visemes.isEmpty()) return

        var previousViseme: Viseme? = null
        val startTime = System.currentTimeMillis()

        for (visemeTiming in visemes) {
            val targetTime = startTime + visemeTiming.startTimeMs
            val waitTime = targetTime - System.currentTimeMillis()
            if (waitTime > 0) delay(waitTime)

            if (!_isSpeaking.value) break

            _progress.value = visemeTiming.startTimeMs.toFloat() / totalDurationMs

            applyVisemeWithCoarticulation(
                visemeTiming.viseme,
                previousViseme,
                visemeTiming.durationMs,
                config.intensity
            )

            _currentViseme.value = visemeTiming.viseme
            previousViseme = visemeTiming.viseme

            val holdTime = visemeTiming.durationMs - config.fadeInMs
            if (holdTime > 0) delay(holdTime)
        }

        fadeToNeutral()
        _progress.value = 1f
    }

    /**
     * Crossfade FROM the current lip weights TO the target weights.
     *
     * This is the core fix: instead of always animating 0→target (which
     * produces a "pop" when the mouth is already open), we interpolate
     * from wherever the mouth currently is.
     */
    private suspend fun crossfadeTo(targetShapes: Map<String, Float>, durationMs: Long) {
        if (durationMs <= 0) {
            applyWeights(targetShapes)
            return
        }

        // Snapshot the current weights before we start moving
        val fromShapes = currentLipWeights.get()

        // Collect all blend-shape keys involved in either state
        val allKeys = (fromShapes.keys + targetShapes.keys).toSet()

        val steps = (durationMs / 6).toInt().coerceAtLeast(1)
        val stepDelayMs = durationMs / steps

        for (step in 1..steps) {
            val t = step.toFloat() / steps
            val easedT = easeOutQuad(t) // fast attack: 75% reached in first half

            val interpolated = allKeys.associateWith { key ->
                val from = fromShapes[key] ?: 0f
                val to   = targetShapes[key] ?: 0f
                from + (to - from) * easedT
            }.filter { it.value > 0.001f }

            applyWeights(interpolated)
            delay(stepDelayMs)
        }

        // Ensure we land exactly on target
        applyWeights(targetShapes)
    }

    /**
     * Apply weights to the blend shape controller and update local cache.
     */
    private fun applyWeights(weights: Map<String, Float>) {
        currentLipWeights.set(weights)
        blendShapeController.setCategoryWeights(
            VrmBlendShapeController.CATEGORY_LIPSYNC,
            weights
        )
    }

    /**
     * Fade the current lip shape back to neutral (mouth closed).
     * Reads from the tracked currentLipWeights so it always starts
     * from the real current state, never from stale data.
     */
    private suspend fun fadeToNeutral() {
        val fromWeights = currentLipWeights.get()
        if (fromWeights.isEmpty()) return

        val steps = (config.fadeOutMs / 8).toInt().coerceAtLeast(1)
        val stepDelayMs = config.fadeOutMs / steps

        for (step in 1..steps) {
            val t = step.toFloat() / steps
            val easedT = easeOutQuad(t)

            val fadedWeights = fromWeights.mapValues { (_, weight) ->
                weight * (1f - easedT)
            }.filter { it.value > 0.001f }

            applyWeights(fadedWeights)
            delay(stepDelayMs)
        }

        applyWeights(emptyMap())
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
    }

    private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun easeInOutQuad(t: Float): Float =
        if (t < 0.5f) 2f * t * t
        else 1f - (-2f * t + 2f).let { it * it } / 2f

    fun isActive(): Boolean = _isSpeaking.value
}