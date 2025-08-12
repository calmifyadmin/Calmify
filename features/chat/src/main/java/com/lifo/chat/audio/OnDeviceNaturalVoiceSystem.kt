package com.lifo.chat.audio

import android.content.Context
import android.media.*
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sistema di voce naturale completamente on-device per latenza zero
 * SEMPLIFICATO: pronuncia tutto il messaggio in una volta
 */
@Singleton
class OnDeviceNaturalVoiceSystem @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "OnDeviceVoiceSystem"
        private const val SPEECH_RATE_NORMAL = 1.0f
        private const val PITCH_NORMAL = 1.0f
    }

    // Core components
    private var textToSpeech: TextToSpeech? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Track what we've already spoken to avoid repetition
    private val spokenTexts = mutableSetOf<String>()

    // State management
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class VoiceState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val currentEmotion: Emotion = Emotion.NEUTRAL,
        val currentSpeakingMessageId: String? = null,
        val naturalness: Float = 1.0f,
        val latencyMs: Long = 0
    )

    enum class Emotion {
        NEUTRAL, HAPPY, SAD, EXCITED, THOUGHTFUL, EMPATHETIC, CURIOUS
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initialize() = withContext(Dispatchers.Main) {
        Log.d(TAG, "🎙️ Initializing on-device natural voice system")

        // Request audio focus for voice
        requestAudioFocus()

        // Initialize on-device TTS with neural voice
        initializeNeuralTTS()

        _voiceState.update {
            it.copy(isInitialized = true)
        }

        Log.d(TAG, "✅ On-device voice system initialized")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> resumeSpeaking()
                    AudioManager.AUDIOFOCUS_LOSS -> pauseSpeaking()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseSpeaking()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duckVolume()
                }
            }
            .build()

        audioManager.requestAudioFocus(audioFocusRequest!!)
    }

    private suspend fun initializeNeuralTTS() = withContext(Dispatchers.IO) {
        val ttsInitialized = CompletableDeferred<Boolean>()

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupNeuralVoice()
                ttsInitialized.complete(true)
            } else {
                Log.e(TAG, "TTS initialization failed")
                ttsInitialized.complete(false)
            }
        }

        ttsInitialized.await()
    }

    private fun setupNeuralVoice() {
        textToSpeech?.apply {
            // Set Italian neural voice
            val locale = Locale.ITALIAN

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Get available voices and select the best quality neural voice
                val voices = voices?.filter {
                    it.locale == locale &&
                            !it.isNetworkConnectionRequired &&
                            it.quality >= Voice.QUALITY_HIGH
                }

                // Prefer neural/enhanced voices
                val neuralVoice = voices?.firstOrNull { voice ->
                    voice.name.contains("neural", ignoreCase = true) ||
                            voice.name.contains("enhanced", ignoreCase = true)
                }

                neuralVoice?.let {
                    setVoice(it)
                    Log.d(TAG, "✅ Using neural voice: ${it.name}")
                } ?: run {
                    // Fallback to best available voice
                    voices?.maxByOrNull { it.quality }?.let { setVoice(it) }
                }
            } else {
                setLanguage(locale)
            }

            // Configure TTS parameters
            setSpeechRate(SPEECH_RATE_NORMAL)
            setPitch(PITCH_NORMAL)

            // Set up utterance progress listener for real-time feedback
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "🔊 TTS Started: $utteranceId")
                    _voiceState.update { it.copy(isSpeaking = true) }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "✅ TTS Completed: $utteranceId")
                    _voiceState.update {
                        it.copy(
                            isSpeaking = false,
                            currentSpeakingMessageId = null
                        )
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "❌ TTS error for utterance: $utteranceId")
                    _voiceState.update {
                        it.copy(
                            isSpeaking = false,
                            currentSpeakingMessageId = null
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError(utteranceId)
                }
            })
        }
    }

    /**
     * Speak the ENTIRE text at once with emotion
     * No chunking, no queue, just speak the whole message
     */
    fun speakWithEmotion(
        text: String,
        emotion: Emotion = Emotion.NEUTRAL,
        messageId: String? = null
    ) {
        val startTime = System.currentTimeMillis()

        // Clean the text first
        val cleanedText = cleanTextForTTS(text)

        // Check if we've already spoken this exact text to avoid repetition
        val textKey = "${messageId}_${cleanedText.hashCode()}"
        if (spokenTexts.contains(textKey)) {
            Log.d(TAG, "🔄 Already spoken, skipping: ${cleanedText.take(50)}...")
            return
        }

        Log.d(TAG, "🎤 Speaking ENTIRE message: ${cleanedText.take(50)}...")

        // Add to spoken texts with a limit
        spokenTexts.add(textKey)
        if (spokenTexts.size > 100) {
            // Remove oldest entries
            spokenTexts.toList().take(50).forEach { spokenTexts.remove(it) }
        }

        // Update state
        _voiceState.update {
            it.copy(
                currentSpeakingMessageId = messageId,
                currentEmotion = emotion
            )
        }

        // Apply emotion-based voice modulation
        applyEmotionalVoiceParameters(emotion)

        // Speak the ENTIRE text in one go
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f) // Center
            }

            textToSpeech?.speak(
                cleanedText,
                TextToSpeech.QUEUE_FLUSH,
                params,
                messageId ?: cleanedText.hashCode().toString()
            )
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(
                cleanedText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                messageId ?: cleanedText.hashCode().toString()
            )
        }

        // Measure latency
        val latency = System.currentTimeMillis() - startTime
        _voiceState.update { it.copy(latencyMs = latency) }
        Log.d(TAG, "⚡ Speech started in ${latency}ms")
    }

    private fun applyEmotionalVoiceParameters(emotion: Emotion) {
        val (rate, pitch) = when (emotion) {
            Emotion.EXCITED -> 1.15f to 1.1f
            Emotion.HAPPY -> 1.05f to 1.05f
            Emotion.SAD -> 0.9f to 0.95f
            Emotion.THOUGHTFUL -> 0.85f to 1.0f
            Emotion.EMPATHETIC -> 0.95f to 0.98f
            Emotion.CURIOUS -> 1.0f to 1.08f
            Emotion.NEUTRAL -> 1.0f to 1.0f
        }

        textToSpeech?.apply {
            setSpeechRate(rate)
            setPitch(pitch)
        }

        _voiceState.update { it.copy(currentEmotion = emotion) }
    }

    private fun cleanTextForTTS(text: String): String {
        return text
            // Remove HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Remove markdown
            .replace("**", "")
            .replace("*", "")
            .replace("_", "")
            .replace("`", "")
            .replace("```", "")
            .replace("#", "")
            // Remove SSML tags if any
            .replace(Regex("</?[^>]+(>|$)"), "")
            // Remove multiple spaces
            .replace(Regex("\\s+"), " ")
            // Remove special characters that TTS might read literally
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            // Clean up
            .trim()
    }

    fun pause() {
        textToSpeech?.stop()
        _voiceState.update { it.copy(isSpeaking = false) }
    }

    fun resume() {
        // TTS doesn't support resume, would need to restart
        Log.d(TAG, "Resume not supported by TTS")
    }

    fun stop() {
        Log.d(TAG, "🛑 Stopping TTS")
        textToSpeech?.stop()
        spokenTexts.clear() // Clear spoken texts when stopping
        _voiceState.update {
            it.copy(
                isSpeaking = false,
                currentSpeakingMessageId = null
            )
        }
    }

    private fun pauseSpeaking() {
        textToSpeech?.stop()
    }

    private fun resumeSpeaking() {
        // TTS doesn't support resume
    }

    private fun duckVolume() {
        // Could implement volume control if needed
    }

    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up voice system")
        textToSpeech?.stop()
        textToSpeech?.shutdown()

        audioFocusRequest?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }

        scope.cancel()
    }
}