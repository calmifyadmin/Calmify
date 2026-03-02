package com.lifo.chat.audio


import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.util.speech.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Adapter that wraps GeminiLiveAudioManager for streaming audio and implements SpeechAudioSource.
 *
 * This bridge enables synchronized audio-lipsync in Live chat mode by:
 * - Detecting when AI audio streaming starts and ends
 * - Providing real-time audio level from actual playback for lip-sync intensity
 * - Generating playback events for synchronization with avatar lip-sync
 *
 * Unlike GeminiVoiceAudioSource (for non-streaming TTS), this adapter:
 * - Doesn't know the text being spoken (it's streaming audio from WebSocket)
 * - Uses actual audio level measurements from the audio manager
 * - Works with continuous streaming rather than discrete messages
 */
class GeminiLiveAudioSource constructor(
    private val audioManager: GeminiLiveAudioManager
) : SpeechAudioSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Mutable state for playback events
    private val _playbackEvents = MutableSharedFlow<SpeechPlaybackEvent>(
        replay = 1,
        extraBufferCapacity = 10
    )
    override val playbackEvents: Flow<SpeechPlaybackEvent> = _playbackEvents.asSharedFlow()

    // Speaking state from audio manager's playback state
    override val isSpeaking: StateFlow<Boolean> = audioManager.playbackState

    // Real-time audio level from actual audio playback
    override val audioLevel: StateFlow<Float> = audioManager.aiAudioLevel

    // Track streaming session
    private var currentSessionId: String? = null
    private var streamStartTime: Long = 0L
    private var lastPlaybackState: Boolean = false
    private var currentText: String = ""
    private var messageIdCounter: Int = 0

    init {
        // Observe playback state changes
        observePlaybackState()
    }

    private fun observePlaybackState() {
        scope.launch {
            audioManager.playbackState.collect { isPlaying ->
                handlePlaybackStateChange(isPlaying)
            }
        }
    }

    private suspend fun handlePlaybackStateChange(isPlaying: Boolean) {
        if (isPlaying == lastPlaybackState) return

        if (isPlaying && !lastPlaybackState) {
            // Streaming started
            streamStartTime = System.currentTimeMillis()

            // If no session prepared yet (text not received), create one for audio-reactive mode
            val messageId = if (currentSessionId == null) {
                val newId = "live-audio-${System.currentTimeMillis()}"
                currentSessionId = newId
                println("[GeminiLiveAudioSource] Live audio streaming started (AUDIO-REACTIVE mode): $newId")

                // Emit Preparing with empty text - LipSyncController will use audio-reactive mode
                _playbackEvents.emit(
                    SpeechPlaybackEvent.Preparing(
                        text = "", // Empty = pure audio-reactive lip-sync
                        estimatedDurationMs = 5000L,
                        messageId = newId
                    )
                )
                newId
            } else {
                println("[GeminiLiveAudioSource] Live audio streaming started (TEXT-PREPARED): $currentSessionId")
                currentSessionId!!
            }

            // Emit Started event - lip-sync begins NOW
            _playbackEvents.emit(
                SpeechPlaybackEvent.Started(
                    messageId = messageId,
                    timestamp = streamStartTime
                )
            )

            // Start continuous progress updates with real audio level
            launchProgressUpdates(messageId)

        } else if (!isPlaying && lastPlaybackState) {
            // Streaming ended
            val messageId = currentSessionId ?: "live-unknown"
            val actualDuration = System.currentTimeMillis() - streamStartTime

            println("[GeminiLiveAudioSource] Live audio streaming ended: $messageId (${actualDuration}ms)")

            _playbackEvents.emit(
                SpeechPlaybackEvent.Ended(
                    messageId = messageId,
                    actualDurationMs = actualDuration
                )
            )

            currentSessionId = null
            currentText = ""
        }

        lastPlaybackState = isPlaying
    }

    private fun launchProgressUpdates(messageId: String) {
        scope.launch {
            // Emit progress events while playing
            while (audioManager.playbackState.value && currentSessionId == messageId) {
                val elapsed = System.currentTimeMillis() - streamStartTime
                val currentLevel = audioManager.aiAudioLevel.value
                val currentVisemeWeights = audioManager.aiVisemeWeights.value

                _playbackEvents.emit(
                    SpeechPlaybackEvent.Playing(
                        messageId = messageId,
                        progressMs = elapsed,
                        totalDurationMs = elapsed + 1000L, // Unknown total duration in streaming
                        audioLevel = currentLevel,
                        visemeWeights = currentVisemeWeights
                    )
                )

                kotlinx.coroutines.delay(33) // ~30fps updates for smooth lip-sync
            }
        }
    }

    /**
     * Prepare for speech with known text.
     * In Live mode, this is called when we receive text from the AI before audio starts.
     */
    fun prepareWithText(text: String) {
        currentText = text
        val messageId = "live-${++messageIdCounter}-${System.currentTimeMillis()}"
        currentSessionId = messageId

        println("[GeminiLiveAudioSource] Preparing live speech: '${text.take(30)}...'")

        // Estimate duration from text
        val estimatedDuration = estimateDuration(text)

        scope.launch {
            _playbackEvents.emit(
                SpeechPlaybackEvent.Preparing(
                    text = text,
                    estimatedDurationMs = estimatedDuration,
                    messageId = messageId
                )
            )
        }
    }

    /**
     * Handle interruption (barge-in or stop).
     */
    fun handleInterruption(reason: InterruptionReason = InterruptionReason.USER_BARGE_IN) {
        currentSessionId?.let { messageId ->
            println("[GeminiLiveAudioSource] Live audio interrupted: $messageId ($reason)")

            scope.launch {
                _playbackEvents.emit(
                    SpeechPlaybackEvent.Interrupted(
                        messageId = messageId,
                        reason = reason
                    )
                )
            }

            currentSessionId = null
        }
    }

    override fun speak(request: SpeechRequest) {
        // In Live mode, we don't initiate speech - audio comes from WebSocket
        // But we can prepare the lip-sync system with the text
        currentText = request.text
        prepareWithText(request.text)
    }

    override fun stop() {
        handleInterruption(InterruptionReason.MANUAL_STOP)
    }

    /**
     * Estimate speech duration from text length.
     * Approximately 150 words per minute.
     */
    private fun estimateDuration(text: String): Long {
        if (text.isBlank()) return 3000L
        val words = text.split(Regex("\\s+")).size
        val wordsPerMinute = 150
        val millisPerWord = 60_000L / wordsPerMinute
        return (words * millisPerWord).coerceIn(1000L, 30_000L)
    }

    /**
     * Reset state (called when session ends)
     */
    fun reset() {
        currentSessionId = null
        currentText = ""
        streamStartTime = 0L
        lastPlaybackState = false

        scope.launch {
            _playbackEvents.emit(SpeechPlaybackEvent.Idle)
        }
    }
}
