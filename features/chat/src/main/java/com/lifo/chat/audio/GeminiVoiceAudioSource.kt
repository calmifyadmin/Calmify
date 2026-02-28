package com.lifo.chat.audio


import com.lifo.util.speech.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that wraps GeminiNativeVoiceSystem and implements SpeechAudioSource.
 *
 * This bridge enables synchronized audio-lipsync by:
 * - Converting GeminiNativeVoiceSystem.VoiceState to SpeechPlaybackEvent
 * - Emitting events when audio playback starts, progresses, and ends
 * - Providing real-time audio level for lip-sync intensity modulation
 *
 * Usage:
 * 1. Inject this alongside GeminiNativeVoiceSystem
 * 2. Connect to HumanoidController via SynchronizedSpeechController
 * 3. Call speak() instead of directly calling voiceSystem.speakWithEmotion()
 */
@Singleton
class GeminiVoiceAudioSource @Inject constructor(
    private val voiceSystem: GeminiNativeVoiceSystem
) : SpeechAudioSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Mutable state for playback events
    private val _playbackEvents = MutableSharedFlow<SpeechPlaybackEvent>(
        replay = 1,
        extraBufferCapacity = 10
    )
    override val playbackEvents: Flow<SpeechPlaybackEvent> = _playbackEvents.asSharedFlow()

    // Speaking state from voice system
    override val isSpeaking: StateFlow<Boolean> = voiceSystem.voiceState
        .map { it.isSpeaking }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    // Audio level (simulated based on playback state and progress)
    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Track current speech request
    private var currentRequest: SpeechRequest? = null
    private var playbackStartTime: Long = 0L

    init {
        // Observe voice system state changes and emit playback events
        observeVoiceState()
    }

    private fun observeVoiceState() {
        scope.launch {
            var lastPlaybackState = GeminiNativeVoiceSystem.PlaybackState.IDLE

            voiceSystem.voiceState.collect { state ->
                val request = currentRequest ?: return@collect

                when (state.playbackState) {
                    GeminiNativeVoiceSystem.PlaybackState.STARTED -> {
                        if (lastPlaybackState != GeminiNativeVoiceSystem.PlaybackState.STARTED) {
                            playbackStartTime = System.currentTimeMillis()
                            println("[GeminiVoiceAudioSource] Playback started for message: ${request.messageId}")

                            _playbackEvents.emit(
                                SpeechPlaybackEvent.Started(
                                    messageId = request.messageId,
                                    timestamp = playbackStartTime
                                )
                            )
                        }
                    }

                    GeminiNativeVoiceSystem.PlaybackState.PLAYING -> {
                        // Calculate progress and emit playing event
                        val elapsed = System.currentTimeMillis() - playbackStartTime
                        val totalDuration = state.estimatedDurationMs.takeIf { it > 0 }
                            ?: (request.estimatedDurationMs.takeIf { it > 0 } ?: 5000L)

                        val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

                        // Simulate audio level based on progress (wave pattern for natural variation)
                        val simulatedLevel = calculateSimulatedAudioLevel(progress, state.totalBytesPlayed)
                        _audioLevel.value = simulatedLevel

                        _playbackEvents.emit(
                            SpeechPlaybackEvent.Playing(
                                messageId = request.messageId,
                                progressMs = elapsed,
                                totalDurationMs = totalDuration,
                                audioLevel = simulatedLevel
                            )
                        )
                    }

                    GeminiNativeVoiceSystem.PlaybackState.FINISHING -> {
                        if (lastPlaybackState != GeminiNativeVoiceSystem.PlaybackState.FINISHING) {
                            val remaining = state.estimatedDurationMs -
                                (System.currentTimeMillis() - playbackStartTime)

                            println("[GeminiVoiceAudioSource] Playback finishing, remaining: ${remaining}ms")

                            _playbackEvents.emit(
                                SpeechPlaybackEvent.Finishing(
                                    messageId = request.messageId,
                                    remainingMs = remaining.coerceAtLeast(0)
                                )
                            )
                        }
                    }

                    GeminiNativeVoiceSystem.PlaybackState.ENDED -> {
                        if (lastPlaybackState != GeminiNativeVoiceSystem.PlaybackState.ENDED &&
                            lastPlaybackState != GeminiNativeVoiceSystem.PlaybackState.IDLE) {

                            val actualDuration = System.currentTimeMillis() - playbackStartTime

                            println("[GeminiVoiceAudioSource] Playback ended, actual duration: ${actualDuration}ms")

                            _playbackEvents.emit(
                                SpeechPlaybackEvent.Ended(
                                    messageId = request.messageId,
                                    actualDurationMs = actualDuration
                                )
                            )

                            // Reset audio level
                            _audioLevel.value = 0f
                            currentRequest = null
                        }
                    }

                    GeminiNativeVoiceSystem.PlaybackState.IDLE -> {
                        _audioLevel.value = 0f

                        // Emit idle event if we were previously speaking
                        if (lastPlaybackState != GeminiNativeVoiceSystem.PlaybackState.IDLE) {
                            _playbackEvents.emit(SpeechPlaybackEvent.Idle)
                        }
                    }
                }

                lastPlaybackState = state.playbackState
            }
        }
    }

    /**
     * Calculate a simulated audio level for natural lip movement.
     * Uses a combination of progress and bytes played to create variation.
     */
    private fun calculateSimulatedAudioLevel(progress: Float, bytesPlayed: Int): Float {
        // Base level from progress (rises then falls)
        val progressCurve = kotlin.math.sin(progress * kotlin.math.PI.toFloat()) * 0.5f

        // Add variation based on bytes (creates natural "speaking" pattern)
        val variation = kotlin.math.sin((bytesPlayed / 1000f) * 3.14159f) * 0.3f

        // Random micro-variations for naturalness
        val microVariation = (kotlin.math.abs((bytesPlayed * 7) % 100 - 50) / 100f - 0.25f) * 0.2f

        return (0.4f + progressCurve + variation + microVariation).coerceIn(0.2f, 1f)
    }

    override fun speak(request: SpeechRequest) {
        currentRequest = request

        println("[GeminiVoiceAudioSource] Speaking request: '${request.text.take(30)}...' [${request.emotion}]")

        // Emit preparing event immediately
        scope.launch {
            _playbackEvents.emit(
                SpeechPlaybackEvent.Preparing(
                    text = request.text,
                    estimatedDurationMs = request.estimatedDurationMs,
                    messageId = request.messageId
                )
            )
        }

        // Map SpeechEmotion to GeminiNativeVoiceSystem.Emotion
        val geminiEmotion = when (request.emotion) {
            SpeechEmotion.NEUTRAL -> GeminiNativeVoiceSystem.Emotion.NEUTRAL
            SpeechEmotion.HAPPY -> GeminiNativeVoiceSystem.Emotion.HAPPY
            SpeechEmotion.SAD -> GeminiNativeVoiceSystem.Emotion.SAD
            SpeechEmotion.EXCITED -> GeminiNativeVoiceSystem.Emotion.EXCITED
            SpeechEmotion.THOUGHTFUL -> GeminiNativeVoiceSystem.Emotion.THOUGHTFUL
            SpeechEmotion.EMPATHETIC -> GeminiNativeVoiceSystem.Emotion.EMPATHETIC
            SpeechEmotion.CURIOUS -> GeminiNativeVoiceSystem.Emotion.CURIOUS
        }

        // Start actual TTS
        voiceSystem.speakWithEmotion(
            text = request.text,
            emotion = geminiEmotion,
            messageId = request.messageId
        )
    }

    override fun stop() {
        println("[GeminiVoiceAudioSource] Stopping speech")

        currentRequest?.let { request ->
            scope.launch {
                _playbackEvents.emit(
                    SpeechPlaybackEvent.Interrupted(
                        messageId = request.messageId,
                        reason = InterruptionReason.MANUAL_STOP
                    )
                )
            }
        }

        voiceSystem.stop()
        _audioLevel.value = 0f
        currentRequest = null
    }

    /**
     * Estimate speech duration from text length.
     * Approximately 150 words per minute, 5 characters per word.
     */
    fun estimateDuration(text: String): Long {
        val words = text.split(Regex("\\s+")).size
        val wordsPerMinute = 150
        val millisPerWord = 60_000L / wordsPerMinute
        return (words * millisPerWord).coerceIn(1000L, 60_000L)
    }
}
