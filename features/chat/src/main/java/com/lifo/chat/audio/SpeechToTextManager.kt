package com.lifo.chat.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpeechToTextManager - Captures voice input and converts to text
 *
 * Uses Android's built-in SpeechRecognizer for high-quality speech-to-text.
 * Provides real-time partial results and audio level feedback.
 *
 * Features:
 * - Real-time partial transcription
 * - Audio level for visualizer (RMS dB)
 * - Automatic end-of-speech detection
 * - Italian language support
 *
 * Usage in Classic Voice Pipeline:
 * 1. User taps mic button → startListening()
 * 2. SpeechRecognizer captures voice
 * 3. onResults() returns final text
 * 4. Text is sent to Gemini for response
 * 5. Response is spoken via TTS with lip-sync
 *
 * @author Jarvis AI Assistant
 */
@Singleton
class SpeechToTextManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Audio level normalization
        private const val MAX_RMS_DB = 10f  // Maximum expected RMS dB
        private const val MIN_RMS_DB = -2f  // Minimum (silence)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null

    // STT State
    private val _sttState = MutableStateFlow(STTState())
    val sttState: StateFlow<STTState> = _sttState.asStateFlow()

    // Real-time audio level for visualizer (0.0 - 1.0)
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Partial transcript for real-time feedback
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    // Final result callback
    var onResultReady: ((String) -> Unit)? = null

    // Error callback
    var onError: ((STTError) -> Unit)? = null

    // Flag to prevent duplicate results when manually stopping
    private var resultAlreadySent = false

    data class STTState(
        val isListening: Boolean = false,
        val isAvailable: Boolean = true,
        val error: String? = null
    )

    sealed class STTError {
        object NoMatch : STTError()
        object NetworkError : STTError()
        object AudioError : STTError()
        object Timeout : STTError()
        object NotAvailable : STTError()
        data class Unknown(val code: Int) : STTError()
    }

    /**
     * Initialize the SpeechRecognizer.
     * Call this once when the component is created.
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            println("[SpeechToTextManager] ERROR: Speech recognition not available on this device")
            _sttState.value = STTState(isAvailable = false, error = "Speech recognition not available")
            return
        }

        println("[SpeechToTextManager] Initializing SpeechToTextManager...")

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())

            // Create intent for speech recognition - Push-to-talk style
            // User controls when to stop, so we use longer timeouts
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ITALIAN.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Longer silence timeout - user will press button to confirm
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }

            println("[SpeechToTextManager] SpeechRecognizer initialized successfully")
            _sttState.value = STTState(isAvailable = true)

        } catch (e: Exception) {
            println("[SpeechToTextManager] ERROR: Failed to initialize SpeechRecognizer: ${e.message}")
            _sttState.value = STTState(isAvailable = false, error = e.message)
        }
    }

    /**
     * Start listening for speech input.
     * Requires RECORD_AUDIO permission.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (!_sttState.value.isAvailable) {
            println("[SpeechToTextManager] WARNING: Speech recognition not available")
            onError?.invoke(STTError.NotAvailable)
            return
        }

        if (_sttState.value.isListening) {
            println("[SpeechToTextManager] WARNING: Already listening")
            return
        }

        println("[SpeechToTextManager] Starting speech recognition...")

        try {
            _partialTranscript.value = ""
            _audioLevel.value = 0f
            resultAlreadySent = false  // Reset flag for new session
            _sttState.value = _sttState.value.copy(isListening = true, error = null)

            speechRecognizer?.startListening(recognizerIntent)

        } catch (e: Exception) {
            println("[SpeechToTextManager] ERROR: Failed to start listening: ${e.message}")
            _sttState.value = _sttState.value.copy(isListening = false, error = e.message)
        }
    }

    /**
     * Stop listening and process the result.
     * Uses partial transcript as fallback if onResults doesn't fire.
     */
    fun stopListening() {
        println("[SpeechToTextManager] stopListening() called - isListening=${_sttState.value.isListening}")

        if (!_sttState.value.isListening) {
            println("[SpeechToTextManager] WARNING: stopListening called but not listening!")
            return
        }

        // Capture partial transcript before stopping (fallback)
        val currentPartial = _partialTranscript.value
        println("[SpeechToTextManager] Stopping speech recognition... partialTranscript='$currentPartial', resultAlreadySent=$resultAlreadySent")

        try {
            speechRecognizer?.stopListening()
            _sttState.value = _sttState.value.copy(isListening = false)
            _audioLevel.value = 0f

            // Use partial transcript as fallback if we have one
            // onResults may not fire when manually stopped
            if (currentPartial.isNotBlank() && !resultAlreadySent) {
                println("[SpeechToTextManager] Using partial transcript as result: '$currentPartial'")
                resultAlreadySent = true
                _partialTranscript.value = ""
                onResultReady?.invoke(currentPartial)
            } else {
                println("[SpeechToTextManager] WARNING: No partial transcript to send or already sent")
            }

        } catch (e: Exception) {
            println("[SpeechToTextManager] ERROR: Error stopping recognition: ${e.message}")
        }
    }

    /**
     * Cancel recognition without processing results.
     */
    fun cancel() {
        println("[SpeechToTextManager] Cancelling speech recognition...")

        try {
            speechRecognizer?.cancel()
            _sttState.value = _sttState.value.copy(isListening = false)
            _partialTranscript.value = ""
            _audioLevel.value = 0f

        } catch (e: Exception) {
            println("[SpeechToTextManager] ERROR: Error cancelling recognition: ${e.message}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                println("[SpeechToTextManager] Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                println("[SpeechToTextManager] Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Convert RMS dB to 0.0-1.0 range for visualizer
                val normalized = ((rmsdB - MIN_RMS_DB) / (MAX_RMS_DB - MIN_RMS_DB))
                    .coerceIn(0f, 1f)
                _audioLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer - could be used for waveform visualization
            }

            override fun onEndOfSpeech() {
                println("[SpeechToTextManager] Speech ended (waiting for results or user confirmation)")
                // Don't set isListening = false here!
                // Let the user confirm with button press, or wait for onResults/onError
            }

            override fun onError(error: Int) {
                val sttError = mapError(error)
                println("[SpeechToTextManager] ERROR: Recognition error: $error -> $sttError, partial='${_partialTranscript.value}'")

                _audioLevel.value = 0f

                // If we have partial transcript, keep listening state so user can still send
                if (_partialTranscript.value.isNotBlank()) {
                    println("[SpeechToTextManager] Error but have partial text - keeping UI active for user to send")
                    // Don't change isListening, let user press Check to send what we have
                } else {
                    _sttState.value = _sttState.value.copy(
                        isListening = false,
                        error = "Recognition error: $error"
                    )
                    // Report error only if no partial and not NO_MATCH
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        onError?.invoke(sttError)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull() ?: ""

                println("[SpeechToTextManager] Final result from recognizer: '$finalText'")

                // Update partial transcript with final result (don't clear it!)
                // User will press Check button to confirm and send
                if (finalText.isNotBlank()) {
                    _partialTranscript.value = finalText
                    println("[SpeechToTextManager] Updated partial transcript with final: '$finalText'")
                }

                // Keep isListening = true so user can still press Check button
                // The button press will handle sending and clearing state
                _audioLevel.value = 0f

                // If user already pressed Check (resultAlreadySent), just clean up
                if (resultAlreadySent) {
                    println("[SpeechToTextManager] Result already sent via button press")
                    _sttState.value = _sttState.value.copy(isListening = false)
                    _partialTranscript.value = ""
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""

                if (partialText.isNotBlank()) {
                    println("[SpeechToTextManager] Partial: '$partialText'")
                    _partialTranscript.value = partialText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                println("[SpeechToTextManager] Event: $eventType")
            }
        }
    }

    private fun mapError(errorCode: Int): STTError {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH -> STTError.NoMatch
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> STTError.NetworkError
            SpeechRecognizer.ERROR_AUDIO -> STTError.AudioError
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> STTError.Timeout
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> STTError.NotAvailable
            else -> STTError.Unknown(errorCode)
        }
    }

    /**
     * Check if currently listening.
     */
    fun isListening(): Boolean = _sttState.value.isListening

    /**
     * Check if speech recognition is available.
     */
    fun isAvailable(): Boolean = _sttState.value.isAvailable

    /**
     * Release resources.
     * Call this when the component is destroyed.
     */
    fun release() {
        println("[SpeechToTextManager] Releasing SpeechToTextManager...")

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            recognizerIntent = null

            _sttState.value = STTState()
            _audioLevel.value = 0f
            _partialTranscript.value = ""

        } catch (e: Exception) {
            println("[SpeechToTextManager] ERROR: Error releasing resources: ${e.message}")
        }
    }
}
