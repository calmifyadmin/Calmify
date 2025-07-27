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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Sistema di voce naturale completamente on-device per latenza zero
 * Simile all'esperienza di Gemini
 */
@Singleton
class OnDeviceNaturalVoiceSystem @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "OnDeviceVoiceSystem"

        // Audio configuration for maximum quality
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Voice parameters
        private const val SPEECH_RATE_NORMAL = 1.0f
        private const val PITCH_NORMAL = 1.0f
        private const val VOICE_LATENCY_CLASS = "very-low-latency"
    }

    // Core components
    private var textToSpeech: TextToSpeech? = null
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Advanced audio processing
    private val audioProcessor = NeuralAudioProcessor()
    private val voiceAnalyzer = RealTimeVoiceAnalyzer()
    private val prosodyEngine = AdvancedProsodyEngine()

    // State management
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Audio pipeline
    private val audioQueue = ConcurrentLinkedQueue<AudioChunk>()
    private val isProcessing = AtomicBoolean(false)
    private var processingJob: Job? = null
    private val spokenTexts = mutableSetOf<String>() // Track what we've already spoken

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class VoiceState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val currentEmotion: Emotion = Emotion.NEUTRAL,
        val currentSpeakingMessageId: String? = null,
        val naturalness: Float = 1.0f,
        val latencyMs: Long = 0,
        val voiceQuality: VoiceQuality = VoiceQuality.NEURAL
    )

    enum class Emotion {
        NEUTRAL, HAPPY, SAD, EXCITED, THOUGHTFUL, EMPATHETIC, CURIOUS
    }

    enum class VoiceQuality {
        STANDARD, HIGH, NEURAL, ULTRA
    }

    data class AudioChunk(
        val text: String,
        val emotion: Emotion,
        val priority: Int = 0,
        val prosodyHints: ProsodyHints? = null
    )

    data class ProsodyHints(
        val emphasis: List<EmphasisPoint> = emptyList(),
        val pauses: List<PausePoint> = emptyList(),
        val intonation: IntonationCurve? = null,
        val breathingPoints: List<Int> = emptyList()
    )

    data class EmphasisPoint(val startIndex: Int, val endIndex: Int, val level: Float)
    data class PausePoint(val index: Int, val durationMs: Long)
    data class IntonationCurve(val points: List<Pair<Float, Float>>) // position -> pitch

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initialize() = withContext(Dispatchers.Main) {
        Log.d(TAG, "🎙️ Initializing on-device natural voice system")

        // Request audio focus for voice
        requestAudioFocus()

        // Initialize high-performance AudioTrack
        initializeAudioTrack()

        // Initialize on-device TTS with neural voice
        initializeNeuralTTS()

        // Start audio processing pipeline
        startAudioProcessingPipeline()

        _voiceState.update {
            it.copy(
                isInitialized = true,
                voiceQuality = VoiceQuality.NEURAL
            )
        }
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

    private fun initializeAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        // Use larger buffer for smooth playback
        val bufferSize = minBufferSize * 4

        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(
                            AudioAttributes.FLAG_AUDIBILITY_ENFORCED or
                                    AudioAttributes.FLAG_LOW_LATENCY
                        )
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }

        audioTrack?.play()
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
                            voice.name.contains("enhanced", ignoreCase = true) ||
                            voice.features?.contains(VOICE_LATENCY_CLASS) == true
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
                    _voiceState.update { it.copy(isSpeaking = true) }
                }

                override fun onDone(utteranceId: String?) {
                    _voiceState.update {
                        it.copy(
                            isSpeaking = false,
                            currentSpeakingMessageId = null
                        )
                    }
                    processNextChunk()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error for utterance: $utteranceId")
                    processNextChunk()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?, errorCode: Int) {
                    onError(utteranceId)
                }
            })
        }
    }

    /**
     * Speak text with ultra-low latency and natural prosody
     */
    fun speakWithEmotion(
        text: String,
        emotion: Emotion = Emotion.NEUTRAL,
        priority: Int = 0,
        messageId: String? = null
    ) {
        val startTime = System.currentTimeMillis()

        // Clean the text first
        val cleanedText = cleanTextForTTS(text)

        // If message ID changed, clear spoken texts (new message context)
        messageId?.let { newId ->
            val currentId = _voiceState.value.currentSpeakingMessageId
            if (currentId != null && currentId != newId) {
                Log.d(TAG, "📱 New message context, clearing spoken texts")
                spokenTexts.clear()
            }
            _voiceState.update { state ->
                state.copy(currentSpeakingMessageId = newId)
            }
        }

        // Check if we've already spoken this EXACT text in this message context
        val textKey = "${messageId}_$cleanedText"
        if (spokenTexts.contains(textKey)) {
            Log.d(TAG, "🔄 Already spoken in this context, skipping: ${cleanedText.take(50)}...")
            return
        }

        Log.d(TAG, "🎤 Speaking new text: ${cleanedText.take(50)}...")

        // Add to spoken texts with message context
        spokenTexts.add(textKey)
        if (spokenTexts.size > 50) { // Increase limit for longer conversations
            spokenTexts.remove(spokenTexts.first())
        }

        // Pre-process text for natural speech
        val processedChunks = prosodyEngine.processTextForNaturalSpeech(
            text = cleanedText,
            emotion = emotion
        )

        // Add chunks to queue with prosody hints
        processedChunks.forEach { chunk ->
            audioQueue.offer(
                AudioChunk(
                    text = chunk.text,
                    emotion = emotion,
                    priority = priority,
                    prosodyHints = chunk.prosodyHints
                )
            )
        }

        // Start processing immediately if not already processing
        if (!isProcessing.get()) {
            processNextChunk()
        }

        // Measure latency
        val latency = System.currentTimeMillis() - startTime
        _voiceState.update { it.copy(latencyMs = latency) }
        Log.d(TAG, "⚡ Speech queued in ${latency}ms")
    }

    private fun processNextChunk() {
        if (audioQueue.isEmpty()) {
            _voiceState.update {
                it.copy(
                    isSpeaking = false,
                    currentSpeakingMessageId = null
                )
            }
            isProcessing.set(false)
            return
        }

        isProcessing.set(true)
        val chunk = audioQueue.poll() ?: return

        // Apply emotion-based voice modulation
        applyEmotionalVoiceParameters(chunk.emotion)

        // Clean text before speaking (no SSML for Android TTS)
        val cleanText = cleanTextForTTS(chunk.text)

        // Speak with ultra-low latency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f) // Center

                // Request low latency if available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, audioTrack?.audioSessionId ?: 0)
                }
            }

            textToSpeech?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                params,
                chunk.hashCode().toString()
            )
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                chunk.hashCode().toString()
            )
        }
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

    private fun generateAdvancedSSML(chunk: AudioChunk): String {
        // IMPORTANT: Most Android TTS engines don't support SSML well
        // So we just clean the text and return it plain
        return cleanTextForTTS(chunk.text)
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

    private fun startAudioProcessingPipeline() {
        processingJob = scope.launch {
            while (isActive) {
                // Real-time audio processing pipeline
                audioProcessor.processAudioStream()
                delay(10) // 10ms processing interval
            }
        }
    }

    /**
     * Advanced audio processor for neural-quality voice
     */
    inner class NeuralAudioProcessor {
        private val harmonicsEnhancer = HarmonicsEnhancer()
        private val formantShifter = FormantShifter()
        private val dynamicEqualizer = DynamicEqualizer()

        suspend fun processAudioStream() {
            // This would process audio in real-time
            // For now, it's a placeholder for future enhancements
        }

        fun enhanceNaturalness(audioData: ShortArray): ShortArray {
            var enhanced = audioData

            // Apply harmonic enhancement for richer voice
            enhanced = harmonicsEnhancer.process(enhanced)

            // Subtle formant shifting for more natural timbre
            enhanced = formantShifter.process(enhanced)

            // Dynamic EQ for clarity
            enhanced = dynamicEqualizer.process(enhanced)

            return enhanced
        }
    }

    /**
     * Real-time voice analyzer for adaptive processing
     */
    inner class RealTimeVoiceAnalyzer {
        fun analyzeVoiceCharacteristics(audioData: ShortArray): VoiceCharacteristics {
            // Analyze fundamental frequency, formants, etc.
            return VoiceCharacteristics(
                fundamentalFrequency = estimatePitch(audioData),
                spectralCentroid = calculateSpectralCentroid(audioData),
                voiceQuality = assessVoiceQuality(audioData)
            )
        }

        private fun estimatePitch(audioData: ShortArray): Float {
            // Simplified pitch estimation
            return 150f // Placeholder
        }

        private fun calculateSpectralCentroid(audioData: ShortArray): Float {
            // Simplified spectral analysis
            return 1500f // Placeholder
        }

        private fun assessVoiceQuality(audioData: ShortArray): Float {
            // Voice quality assessment
            return 0.9f // Placeholder
        }
    }

    /**
     * Advanced prosody engine for natural speech rhythm
     */
    inner class AdvancedProsodyEngine {
        fun processTextForNaturalSpeech(
            text: String,
            emotion: Emotion
        ): List<ProcessedChunk> {
            val chunks = mutableListOf<ProcessedChunk>()

            // Clean text first using the outer class method
            val cleanText = this@OnDeviceNaturalVoiceSystem.cleanTextForTTS(text)

            // Smart chunking based on linguistic analysis
            val sentences = cleanText.split(Regex("[.!?]+")).filter { it.isNotBlank() }

            sentences.forEach { sentence ->
                if (sentence.trim().length > 3) { // Skip very short fragments
                    // Analyze sentence structure
                    val prosodyHints = analyzeProsody(sentence.trim(), emotion)

                    // Create natural chunks
                    chunks.add(
                        ProcessedChunk(
                            text = sentence.trim(),
                            prosodyHints = prosodyHints
                        )
                    )
                }
            }

            // If no valid sentences, process as single chunk
            if (chunks.isEmpty() && cleanText.isNotBlank()) {
                chunks.add(
                    ProcessedChunk(
                        text = cleanText,
                        prosodyHints = ProsodyHints()
                    )
                )
            }

            return chunks
        }

        private fun analyzeProsody(text: String, emotion: Emotion): ProsodyHints {
            val emphasis = detectEmphasisPoints(text, emotion)
            val pauses = detectNaturalPauses(text)
            val breathing = detectBreathingPoints(text)

            return ProsodyHints(
                emphasis = emphasis,
                pauses = pauses,
                breathingPoints = breathing
            )
        }

        private fun detectEmphasisPoints(text: String, emotion: Emotion): List<EmphasisPoint> {
            val emphasisPoints = mutableListOf<EmphasisPoint>()

            // Keywords that should be emphasized based on emotion
            val emotionKeywords = when (emotion) {
                Emotion.EXCITED -> listOf("fantastico", "incredibile", "wow")
                Emotion.HAPPY -> listOf("felice", "bene", "ottimo")
                Emotion.THOUGHTFUL -> listOf("penso", "credo", "forse")
                else -> emptyList()
            }

            emotionKeywords.forEach { keyword ->
                var index = text.indexOf(keyword, ignoreCase = true)
                while (index != -1) {
                    emphasisPoints.add(
                        EmphasisPoint(
                            startIndex = index,
                            endIndex = index + keyword.length,
                            level = 0.8f
                        )
                    )
                    index = text.indexOf(keyword, index + 1, ignoreCase = true)
                }
            }

            return emphasisPoints
        }

        private fun detectNaturalPauses(text: String): List<PausePoint> {
            val pauses = mutableListOf<PausePoint>()

            // Add pauses after commas
            text.forEachIndexed { index, char ->
                when (char) {
                    ',' -> pauses.add(PausePoint(index + 1, 200))
                    ';' -> pauses.add(PausePoint(index + 1, 300))
                    ':' -> pauses.add(PausePoint(index + 1, 400))
                }
            }

            return pauses
        }

        private fun detectBreathingPoints(text: String): List<Int> {
            // Simplified breathing point detection
            val words = text.split(" ")
            val breathingPoints = mutableListOf<Int>()

            // Add breathing points every 10-15 words
            var wordCount = 0
            var charCount = 0

            words.forEach { word ->
                wordCount++
                charCount += word.length + 1

                if (wordCount >= 12) {
                    breathingPoints.add(charCount)
                    wordCount = 0
                }
            }

            return breathingPoints
        }
    }

    data class ProcessedChunk(
        val text: String,
        val prosodyHints: ProsodyHints
    )

    data class VoiceCharacteristics(
        val fundamentalFrequency: Float,
        val spectralCentroid: Float,
        val voiceQuality: Float
    )

    // Audio effect helpers
    inner class HarmonicsEnhancer {
        fun process(audioData: ShortArray): ShortArray {
            // Placeholder for harmonic enhancement
            return audioData
        }
    }

    inner class FormantShifter {
        fun process(audioData: ShortArray): ShortArray {
            // Placeholder for formant shifting
            return audioData
        }
    }

    inner class DynamicEqualizer {
        fun process(audioData: ShortArray): ShortArray {
            // Placeholder for dynamic EQ
            return audioData
        }
    }

    // Control methods
    fun pause() {
        textToSpeech?.stop()
        _voiceState.update { it.copy(isSpeaking = false) }
    }

    fun resume() {
        processNextChunk()
    }

    fun stop() {
        audioQueue.clear()
        textToSpeech?.stop()
        spokenTexts.clear() // Clear spoken texts when stopping
        _voiceState.update {
            it.copy(
                isSpeaking = false,
                currentSpeakingMessageId = null
            )
        }
        isProcessing.set(false)
    }

    private fun pauseSpeaking() {
        audioTrack?.pause()
    }

    private fun resumeSpeaking() {
        audioTrack?.play()
    }

    private fun duckVolume() {
        audioTrack?.setVolume(0.3f)
    }

    fun cleanup() {
        processingJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        audioTrack?.stop()
        audioTrack?.release()

        audioFocusRequest?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }
    }
}