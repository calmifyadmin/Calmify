package com.lifo.app.integration.avatar

import android.util.Log
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.humanoid.api.HumanoidController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * TTS Lip Sync Adapter - SYNCHRONIZED VERSION
 *
 * Synchronizes VRM lip sync animations with actual TTS audio playback in real-time.
 *
 * Instead of estimating duration, this adapter listens to actual audio playback events
 * from GeminiNativeVoiceSystem and controls lip-sync with precise timing.
 *
 * ## Synchronization Strategy:
 * 1. **STARTED**: Start lip-sync when AudioTrack.play() is called
 * 2. **PLAYING**: Continue lip-sync while audio is being written
 * 3. **FINISHING**: Use actual duration calculated from bytes played
 * 4. **ENDED**: Stop lip-sync when audio completes
 *
 * This ensures perfect sync between audio and animation.
 */
class TTSLipSyncAdapter(
    private val humanoidController: HumanoidController,
    private val voiceStateFlow: StateFlow<GeminiNativeVoiceSystem.VoiceState>,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TTSLipSyncAdapter"
    }

    private var syncJob: Job? = null

    /**
     * Start synchronized lip-sync.
     *
     * This function starts monitoring TTS playback events and controls lip-sync in real-time.
     */
    fun startSyncedLipSync(text: String) {
        Log.d(TAG, "🎬 Starting synced lip-sync for: ${text.take(50)}...")

        val cleanText = cleanTextForLipSync(text)

        // Cancel any previous sync job
        syncJob?.cancel()

        // Start monitoring playback state
        syncJob = scope.launch {
            voiceStateFlow.collect { state ->
                when (state.playbackState) {
                    GeminiNativeVoiceSystem.PlaybackState.STARTED -> {
                        // 🎬 Audio playback started - start lip-sync
                        Log.d(TAG, "🎵 STARTED - Initiating lip-sync")
                        // Use estimated duration initially (will be updated in FINISHING)
                        val estimatedDuration = estimateInitialDuration(cleanText)
                        humanoidController.speakText(cleanText, estimatedDuration)
                    }

                    GeminiNativeVoiceSystem.PlaybackState.PLAYING -> {
                        // 🎵 Audio is playing - lip-sync should be active
                        Log.d(TAG, "🎵 PLAYING - Bytes: ${state.totalBytesPlayed}")
                    }

                    GeminiNativeVoiceSystem.PlaybackState.FINISHING -> {
                        // 🎵 Audio data complete, use ACTUAL duration
                        val actualDuration = state.estimatedDurationMs
                        Log.d(TAG, "🎵 FINISHING - Actual duration: ${actualDuration}ms")

                        // Restart lip-sync with precise duration
                        humanoidController.stopSpeaking()
                        humanoidController.speakText(cleanText, actualDuration)
                    }

                    GeminiNativeVoiceSystem.PlaybackState.ENDED -> {
                        // 🎵 Audio playback ended - stop lip-sync
                        Log.d(TAG, "🎵 ENDED - Stopping lip-sync")
                        humanoidController.stopSpeaking()
                        syncJob?.cancel() // Stop monitoring
                    }

                    GeminiNativeVoiceSystem.PlaybackState.IDLE -> {
                        // No action needed in IDLE
                    }
                }
            }
        }
    }

    /**
     * Stop lip sync and cancel synchronization.
     */
    fun stopLipSync() {
        Log.d(TAG, "🛑 Stopping lip-sync")
        syncJob?.cancel()
        humanoidController.stopSpeaking()
    }

    /**
     * Cleans text for lip sync processing.
     *
     * Removes emotion tags, markdown formatting, and other non-speech elements.
     */
    private fun cleanTextForLipSync(text: String): String {
        return text
            .replace(Regex("""\[.*?\]"""), "") // Remove [emotion] tags
            .replace(Regex("""[*_~`]"""), "") // Remove markdown
            .trim()
    }

    /**
     * Estimates initial duration from text.
     *
     * This is only used for the initial lip-sync start.
     * Actual duration from audio bytes will override this.
     */
    private fun estimateInitialDuration(text: String): Long {
        val wordCount = text.split(Regex("\\s+")).size
        return (wordCount * 400L * 1.1).toLong() // ~150 WPM + 10% buffer
    }
}
