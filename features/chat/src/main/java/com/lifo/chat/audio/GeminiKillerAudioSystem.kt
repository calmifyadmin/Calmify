package com.lifo.chat.audio

import android.content.Context
import android.media.*
import android.media.audiofx.*
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class GeminiKillerAudioSystem @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NaturalVoiceSystem"

        // High Quality Audio configuration
        private const val SAMPLE_RATE = 48000 // Studio quality
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Streaming configuration for high quality
        private const val CHUNK_DURATION_MS = 150 // Longer chunks for better quality
        private const val BUFFER_CHUNKS = 3 // More buffer for smooth playback
        private const val CROSSFADE_SAMPLES = 2400 // 100ms at 48kHz
        private const val WRITE_CHUNK_SIZE = 9600 // 50ms stereo at 48kHz (smaller for smoother writes)

        // Network configuration
        private const val AUDIO_API_URL = "https://audio-streaming-api-23546263069.europe-west1.run.app/generateNeuralAudio"
        private const val DATABASE_URL = "https://calmify-388723-default-rtdb.europe-west1.firebasedatabase.app"

        // Firebase paths
        private const val AUDIO_STREAMS_PATH = "audio_streams"
    }

    // Firebase
    private val database = Firebase.database(DATABASE_URL)

    // Audio components
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    // Audio processors
    private val spatialProcessor = Spatial3DAudioProcessor()

    // Threading
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State management
    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    // Audio buffering
    private val audioQueue = ConcurrentLinkedQueue<ProcessedAudioData>()
    private val isPlaying = AtomicBoolean(false)
    private val isStopRequested = AtomicBoolean(false) // NEW: Flag for stop request
    private val chunksReceived = AtomicInteger(0)
    private val chunksPlayed = AtomicInteger(0)

    // Crossfade variables
    private var lastChunkTail: ShortArray? = null
    private var previousEmotion: VoiceEmotion = VoiceEmotion.NEUTRAL

    // Jobs
    private var playbackJob: Job? = null
    private var firebaseListener: ChildEventListener? = null
    private var currentStreamRef: DatabaseReference? = null
    private var timeoutJob: Job? = null // NEW: Separate timeout job

    // Network
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class AudioState(
        val isStreaming: Boolean = false,
        val isPlaying: Boolean = false,
        val currentMessageId: String? = null,
        val bufferLevel: Int = 0,
        val chunksReceived: Int = 0,
        val chunksPlayed: Int = 0,
        val currentEmotion: VoiceEmotion = VoiceEmotion.NEUTRAL,
        val naturalness: Float = 1.0f,
        val emotionalIntensity: Float = 0.5f,
        val error: String? = null,
        val audioQuality: String = "Studio 48kHz Stereo"
    )

    data class ProcessedAudioData(
        val chunkId: String,
        val pcmDataLeft: ShortArray,  // Stereo channels
        val pcmDataRight: ShortArray,
        val sequenceNumber: Int,
        val emotion: VoiceEmotion,
        val naturalness: NaturalnessMetadata,
        val isLast: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ProcessedAudioData
            return chunkId == other.chunkId
        }

        override fun hashCode(): Int = chunkId.hashCode()
    }

    data class NaturalnessMetadata(
        val hasBreathing: Boolean = true,
        val hasProsody: Boolean = true,
        val hasEmphasis: Boolean = true,
        val emotionalTransition: Boolean = false,
        val spatialMovement: Boolean = true
    )

    data class AudioChunk(
        val id: String = "",
        val data: String = "",
        val sequence: Int = 0,
        val emotion: String = "neutral",
        val naturalness: Map<String, Any>? = null,
        val isLast: Boolean = false,
        val timestamp: Long = 0
    )

    enum class VoiceEmotion {
        NEUTRAL, HAPPY, SAD, EXCITED, CALM, EMPHATIC, WHISPERING, THOUGHTFUL
    }

    data class SpatialPosition(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 0f
    ) {
        companion object {
            val CENTER = SpatialPosition(0f, 0f, 0f)
            val INTIMATE = SpatialPosition(0f, 0f, -0.3f)
            val CONVERSATIONAL = SpatialPosition(0f, 0f, -0.6f)
            val DISTANT = SpatialPosition(0f, 0f, -1.2f)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    suspend fun startUltraLowLatencyStreaming(
        messageId: String,
        text: String,
        emotion: VoiceEmotion = VoiceEmotion.NEUTRAL,
        spatialPosition: SpatialPosition = SpatialPosition.CONVERSATIONAL,
        conversationHistory: List<String> = emptyList()
    ) {
        Log.d(TAG, "🎙️ Starting HIGH QUALITY voice streaming for message: $messageId")
        Log.d(TAG, "📝 Text length: ${text.length} chars")
        Log.d(TAG, "🎭 Base emotion: $emotion")
        Log.d(TAG, "🎧 Audio quality: 48kHz Stereo")

        try {
            // Cleanup precedente COMPLETO
            stopStreaming()

            // Wait a bit to ensure cleanup is complete
            delay(100)

            // Reset all flags and counters
            isStopRequested.set(false)
            chunksReceived.set(0)
            chunksPlayed.set(0)
            audioQueue.clear()
            lastChunkTail = null

            // Update state with new message ID
            _audioState.update {
                it.copy(
                    isStreaming = true,
                    currentMessageId = messageId,
                    currentEmotion = emotion,
                    naturalness = 1.0f,
                    error = null,
                    chunksReceived = 0,
                    chunksPlayed = 0,
                    audioQuality = "Studio 48kHz Stereo"
                )
            }

            // Initialize high quality audio
            initializeStudioQualityAudioTrack()

            // Get auth token
            val token = getAuthToken()

            // Setup Firebase listener BEFORE sending request
            setupFirebaseListener(messageId)

            // Send request to server with high quality params
            requestHighQualityAudioGeneration(messageId, text, emotion, spatialPosition, conversationHistory, token)

            // Start playback
            startHighQualityPlayback()

        } catch (e: Exception) {
            Log.e(TAG, "❌ High quality voice streaming error", e)
            _audioState.update {
                it.copy(
                    isStreaming = false,
                    error = e.message
                )
            }
            stopStreaming()
        }
    }

    private fun initializeStudioQualityAudioTrack() {
        Log.d(TAG, "🎵 Initializing STUDIO QUALITY AudioTrack")

        // Release any existing audio track
        audioTrack?.apply {
            try {
                stop()
                flush()
                release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing existing AudioTrack", e)
            }
        }
        audioTrack = null

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        // Larger buffer for high quality
        val bufferSize = max(
            minBufferSize * 6, // 6x for studio quality
            (SAMPLE_RATE * 2 * 2 * 1000) / 1000 // 1 second buffer stereo
        )

        Log.d(TAG, "📊 Studio buffer size: $bufferSize bytes (min: $minBufferSize)")

        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA) // Changed to MEDIA for quality
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
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

        audioTrack?.setVolume(1.0f)
        applyStudioQualityEffects()
        audioTrack?.play()
        Log.d(TAG, "✅ Studio quality AudioTrack ready")
    }

    private fun applyStudioQualityEffects() {
        val sessionId = audioTrack?.audioSessionId ?: return

        // Release any existing effects first
        releaseAudioEffects()

        try {
            // Professional EQ curve
            equalizer = Equalizer(0, sessionId).apply {
                val bands = numberOfBands.toInt()
                for (band in 0 until bands) {
                    val freq = getCenterFreq(band.toShort())
                    val gain = when {
                        freq < 80 -> -200      // Cut sub-bass
                        freq < 250 -> 300      // Boost bass for warmth
                        freq < 500 -> 200      // Boost low-mids
                        freq < 1000 -> 100     // Slight mid boost
                        freq < 2000 -> 150     // Presence boost
                        freq < 4000 -> 200     // Clarity boost
                        freq < 8000 -> 100     // Air frequencies
                        else -> 50             // Gentle high boost
                    }
                    setBandLevel(band.toShort(), gain.toShort())
                }
                setEnabled(true)
            }

            // Studio reverb
            environmentalReverb = EnvironmentalReverb(0, sessionId).apply {
                // Professional vocal booth settings
                setRoomLevel(-600)
                setRoomHFLevel(-100)
                setDecayTime(600)
                setDecayHFRatio(800)
                setReflectionsLevel(-1000)
                setReflectionsDelay(5)
                setReverbLevel(-1500)
                setReverbDelay(10)
                setDensity(950)
                setDiffusion(950)
                setEnabled(true)
            }

            // Enhanced bass
            bassBoost = BassBoost(0, sessionId).apply {
                setStrength(400) // More bass for richness
                setEnabled(true)
            }

            // Stereo widening
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                virtualizer = Virtualizer(0, sessionId).apply {
                    setStrength(600) // Wide stereo image
                    setEnabled(true)
                }
            }

            // Dynamics processing for broadcast quality
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setupDynamicsProcessing(sessionId)
            }

            Log.d(TAG, "✅ Studio quality audio effects applied")

        } catch (e: Exception) {
            Log.e(TAG, "Error applying studio effects", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupDynamicsProcessing(sessionId: Int) {
        try {
            val channelCount = 2 // Stereo

            dynamicsProcessing = DynamicsProcessing(
                0,
                sessionId,
                DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channelCount,
                    true, // Pre-EQ
                    1,    // Pre-EQ bands
                    true, // MBC
                    1,    // MBC bands
                    true, // Post-EQ
                    1,    // Post-EQ bands
                    true  // Limiter
                ).build()
            ).apply {
                // Set compressor for vocal presence
                for (channel in 0 until channelCount) {
                    val mbc = getMbcByChannelIndex(channel)
                    mbc?.apply {
                        setEnabled(true)
                        getBand(0)?.apply {
                            setAttackTime(3f)
                            setReleaseTime(80f)
                            setRatio(4f)
                            setThreshold(-20f)
                            setKneeWidth(5f)
                            setNoiseGateThreshold(-60f)
                            setExpanderRatio(2f)
                            setPreGain(2f)
                            setPostGain(1f)
                        }
                    }
                    setMbcByChannelIndex(channel, mbc)

                    // Set limiter to prevent clipping
                    val limiter = getLimiterByChannelIndex(channel)
                    limiter?.apply {
                        setEnabled(true)
                        setLinkGroup(0) // Link stereo channels
                        setAttackTime(0.1f)
                        setReleaseTime(50f)
                        setRatio(10f)
                        setThreshold(-2f)
                        setPostGain(0f)
                    }
                    setLimiterByChannelIndex(channel, limiter)
                }

                setEnabled(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up dynamics processing", e)
        }
    }

    private suspend fun requestHighQualityAudioGeneration(
        messageId: String,
        text: String,
        emotion: VoiceEmotion,
        spatialPosition: SpatialPosition,
        conversationHistory: List<String>,
        token: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "📤 Requesting HIGH QUALITY audio generation")

        val requestBody = JSONObject().apply {
            put("messageId", messageId)
            put("text", text)
            put("voice", JSONObject().apply {
                put("languageCode", "it-IT")
                put("name", "it-IT-Neural2-A") // Best quality female voice
                put("ssmlGender", "FEMALE")
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", "LINEAR16")
                put("sampleRateHertz", 48000) // High quality 48kHz
                put("audioChannelCount", 2) // Stereo
            })
            put("streaming", JSONObject().apply {
                put("chunkDurationMs", CHUNK_DURATION_MS)
                put("overlapMs", 20) // More overlap for quality
            })
            put("spatial", JSONObject().apply {
                put("x", spatialPosition.x)
                put("y", spatialPosition.y)
                put("z", spatialPosition.z)
                put("enable3D", true)
            })
            put("emotion", emotion.name)
            put("quality", "MAXIMUM") // Request maximum quality
            if (conversationHistory.isNotEmpty()) {
                put("conversationHistory", conversationHistory.joinToString("|"))
            }
        }.toString()

        val request = Request.Builder()
            .url(AUDIO_API_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = response.body?.string() ?: "Unknown error"
            throw Exception("Server error ${response.code}: $error")
        }

        val responseBody = response.body?.string()
        Log.d(TAG, "✅ High quality audio request successful: $responseBody")
    }

    private fun processAudioChunk(chunk: AudioChunk) {
        // Check if stop was requested
        if (isStopRequested.get()) {
            Log.d(TAG, "🛑 Stop requested, ignoring chunk ${chunk.id}")
            return
        }

        try {
            Log.d(TAG, "🎵 Processing HIGH QUALITY chunk: ${chunk.id} (seq: ${chunk.sequence})")

            if (chunk.data.isEmpty()) {
                Log.e(TAG, "❌ Empty audio data in chunk ${chunk.id}")
                return
            }

            // Decode Base64 -> PCM bytes
            val pcmData = try {
                Base64.decode(chunk.data, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Base64 decode error for chunk ${chunk.id}", e)
                return
            }

            Log.d(TAG, "📊 Decoded ${pcmData.size} bytes from chunk ${chunk.id}")

            // Convert to stereo 16-bit PCM samples
            val (leftChannel, rightChannel) = convertToStereo(pcmData)
            Log.d(TAG, "📊 Converted to ${leftChannel.size} stereo samples")

            // Determine emotion
            val chunkEmotion = try {
                VoiceEmotion.valueOf(chunk.emotion.uppercase())
            } catch (e: Exception) {
                Log.w(TAG, "Unknown emotion: ${chunk.emotion}, using NEUTRAL")
                VoiceEmotion.NEUTRAL
            }

            // Extract naturalness metadata
            val naturalness = chunk.naturalness?.let {
                NaturalnessMetadata(
                    hasBreathing = it["hasBreathing"] as? Boolean ?: true,
                    hasProsody = it["hasProsody"] as? Boolean ?: true,
                    hasEmphasis = it["hasEmphasis"] as? Boolean ?: true,
                    emotionalTransition = chunkEmotion != previousEmotion,
                    spatialMovement = true
                )
            } ?: NaturalnessMetadata()

            // Apply spatial processing
            val (processedLeft, processedRight) = spatialProcessor.process3DAudio(
                leftChannel, rightChannel,
                spatialPosition = SpatialPosition.CONVERSATIONAL,
                emotion = chunkEmotion
            )

            // Apply crossfade if we have a tail
            val (finalLeft, finalRight) = if (lastChunkTail != null && chunksReceived.get() > 0) {
                applyStereoFade(processedLeft to processedRight, lastChunkTail!!)
            } else {
                processedLeft to processedRight
            }

            // Save tail for next chunk (use left channel for simplicity)
            if (finalLeft.size >= CROSSFADE_SAMPLES) {
                lastChunkTail = finalLeft.takeLast(CROSSFADE_SAMPLES).toShortArray()
            }
            previousEmotion = chunkEmotion

            // Add to queue only if not stopping
            if (!isStopRequested.get()) {
                val audioData = ProcessedAudioData(
                    chunkId = chunk.id,
                    pcmDataLeft = finalLeft,
                    pcmDataRight = finalRight,
                    sequenceNumber = chunk.sequence,
                    emotion = chunkEmotion,
                    naturalness = naturalness,
                    isLast = chunk.isLast
                )

                audioQueue.offer(audioData)
                val received = chunksReceived.incrementAndGet()

                Log.d(TAG, "✅ Added HIGH QUALITY chunk to queue. Total: $received")

                // Update state
                _audioState.update {
                    it.copy(
                        bufferLevel = audioQueue.size,
                        chunksReceived = received,
                        currentEmotion = chunkEmotion,
                        emotionalIntensity = calculateEmotionalIntensity(chunk)
                    )
                }

                // Handle last chunk
                if (chunk.isLast) {
                    Log.d(TAG, "🏁 Last chunk received - total chunks: $received")
                    audioScope.launch {
                        while (!isStopRequested.get() &&
                            (chunksPlayed.get() < received || audioQueue.isNotEmpty())) {
                            delay(50)
                        }
                        if (!isStopRequested.get()) {
                            fadeOutAndStop()
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing chunk ${chunk.id}", e)
        }
    }

    private fun startHighQualityPlayback() {
        if (isPlaying.get()) {
            Log.w(TAG, "Playback already started")
            return
        }

        playbackJob = audioScope.launch {
            Log.d(TAG, "🎧 Starting HIGH QUALITY playback")
            isPlaying.set(true)

            // Wait for initial buffer
            var waitTime = 0
            while (audioQueue.size < BUFFER_CHUNKS &&
                _audioState.value.isStreaming &&
                waitTime < 5000 &&
                !isStopRequested.get()) {
                delay(50)
                waitTime += 50
            }

            if ((audioQueue.isEmpty() && waitTime >= 5000) || isStopRequested.get()) {
                if (!isStopRequested.get()) {
                    Log.e(TAG, "❌ No audio chunks received after 5 seconds")
                    _audioState.update { it.copy(error = "No audio received") }
                }
                stopStreaming()
                return@launch
            }

            _audioState.update { it.copy(isPlaying = true) }

            // Larger buffer for smoother playback
            val stereoBuffer = ShortArray(WRITE_CHUNK_SIZE * 2)
            val audioBuffer = mutableListOf<Short>()

            // Playback loop
            while (isActive && !isStopRequested.get() &&
                (isPlaying.get() || audioQueue.isNotEmpty())) {

                // Accumulate multiple chunks for smoother playback
                while (audioBuffer.size < WRITE_CHUNK_SIZE * 2 && audioQueue.isNotEmpty()) {
                    val audioData = audioQueue.poll()
                    audioData?.let {
                        val leftData = it.pcmDataLeft
                        val rightData = it.pcmDataRight
                        val totalSamples = minOf(leftData.size, rightData.size)

                        // Add interleaved stereo data to buffer
                        for (i in 0 until totalSamples) {
                            audioBuffer.add(leftData[i])
                            audioBuffer.add(rightData[i])
                        }

                        Log.d(TAG, "📊 Added chunk ${it.chunkId} to buffer (${totalSamples} samples)")

                        // Update state
                        val played = chunksPlayed.incrementAndGet()
                        _audioState.update { state ->
                            state.copy(
                                bufferLevel = audioQueue.size,
                                chunksPlayed = played,
                                naturalness = 1.0f
                            )
                        }
                    }
                }

                // Write accumulated audio in larger chunks
                if (audioBuffer.isNotEmpty()) {
                    val samplesToWrite = minOf(audioBuffer.size, stereoBuffer.size)

                    // Copy from buffer to array
                    for (i in 0 until samplesToWrite) {
                        stereoBuffer[i] = audioBuffer.removeAt(0)
                    }

                    // Write to AudioTrack
                    val written = audioTrack?.write(
                        stereoBuffer,
                        0,
                        samplesToWrite,
                        AudioTrack.WRITE_NON_BLOCKING // Use non-blocking for smoother playback
                    ) ?: 0

                    if (written < 0) {
                        Log.e(TAG, "AudioTrack write error: $written")
                    }
                } else {
                    // No data available, wait a bit
                    delay(10)
                }
            }

            Log.d(TAG, "🛑 High quality playback ended")
            isPlaying.set(false)
            _audioState.update { it.copy(isPlaying = false) }
        }
    }

    private fun convertToStereo(pcmData: ByteArray): Pair<ShortArray, ShortArray> {
        val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val totalShorts = pcmData.size / 2 // Total 16-bit samples

        // Log for debugging
        Log.d(TAG, "Converting ${pcmData.size} bytes = $totalShorts samples")

        // Assume mono input from Google TTS (they don't output true stereo)
        // We'll create stereo from mono with slight variations
        val monoSamples = ShortArray(totalShorts)
        for (i in 0 until totalShorts) {
            monoSamples[i] = buffer.short
        }

        // Create stereo from mono with subtle differences for depth
        val leftChannel = ShortArray(totalShorts)
        val rightChannel = ShortArray(totalShorts)

        for (i in monoSamples.indices) {
            // Add very subtle stereo imaging
            val sample = monoSamples[i]

            // Slight delay and phase shift for stereo width
            if (i > 0) {
                // Left channel: original + tiny bit of previous sample
                leftChannel[i] = ((sample * 0.98f) + (monoSamples[i-1] * 0.02f)).toInt().toShort()
                // Right channel: original with slight attenuation
                rightChannel[i] = (sample * 0.96f).toInt().toShort()
            } else {
                leftChannel[i] = sample
                rightChannel[i] = (sample * 0.98f).toInt().toShort()
            }
        }

        Log.d(TAG, "Created stereo: ${leftChannel.size} samples per channel")
        return leftChannel to rightChannel
    }

    // Spatial audio processor
    inner class Spatial3DAudioProcessor {
        fun process3DAudio(
            leftChannel: ShortArray,
            rightChannel: ShortArray,
            spatialPosition: SpatialPosition,
            emotion: VoiceEmotion
        ): Pair<ShortArray, ShortArray> {
            // For now, just return the channels with slight panning
            val pan = spatialPosition.x.coerceIn(-1f, 1f)
            val leftGain = (1f - pan * 0.3f).coerceIn(0.5f, 1f)
            val rightGain = (1f + pan * 0.3f).coerceIn(0.5f, 1f)

            val processedLeft = ShortArray(leftChannel.size)
            val processedRight = ShortArray(rightChannel.size)

            for (i in leftChannel.indices) {
                processedLeft[i] = (leftChannel[i] * leftGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                processedRight[i] = (rightChannel[i] * rightGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            return processedLeft to processedRight
        }
    }

    private suspend fun fadeOutAndStop() {
        Log.d(TAG, "🔇 Studio quality fade out starting")

        val fadeSteps = 40 // Smoother fade
        val fadeDelay = 25L

        for (i in fadeSteps downTo 0) {
            if (isStopRequested.get()) break

            val volume = (i.toFloat() / fadeSteps).pow(2) // Exponential fade
            audioTrack?.setVolume(volume)
            delay(fadeDelay)
        }

        stopStreaming()
    }

    private fun calculateEmotionalIntensity(chunk: AudioChunk): Float {
        return when (chunk.emotion.uppercase()) {
            "EXCITED" -> 0.9f
            "HAPPY" -> 0.8f
            "EMPHATIC" -> 0.85f
            "SAD" -> 0.4f
            "THOUGHTFUL" -> 0.3f
            else -> 0.5f
        }
    }

    private suspend fun getAuthToken(): String {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

        return try {
            currentUser.getIdToken(false).await().token
                ?: throw Exception("Failed to get auth token")
        } catch (e: Exception) {
            Log.e(TAG, "Auth error", e)
            throw Exception("Authentication failed: ${e.message}")
        }
    }

    private fun setupFirebaseListener(messageId: String) {
        Log.d(TAG, "🔥 Setting up Firebase listener for HIGH QUALITY audio")

        // Clean up any existing listener first
        firebaseListener?.let { listener ->
            currentStreamRef?.removeEventListener(listener)
        }

        val streamRef = database.getReference("$AUDIO_STREAMS_PATH/$messageId/chunks")
        currentStreamRef = streamRef

        val processedChunks = mutableSetOf<String>()

        firebaseListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (isStopRequested.get()) return

                val chunkId = snapshot.key ?: return

                if (processedChunks.contains(chunkId)) {
                    Log.d(TAG, "Skipping already processed chunk: $chunkId")
                    return
                }
                processedChunks.add(chunkId)

                try {
                    Log.d(TAG, "📥 HIGH QUALITY chunk received: $chunkId")

                    val chunkData = snapshot.getValue(object : GenericTypeIndicator<Map<String, Any>>() {})
                    if (chunkData == null) {
                        Log.e(TAG, "Null chunk data for: $chunkId")
                        return
                    }

                    val audioChunk = AudioChunk(
                        id = chunkId,
                        data = chunkData["data"] as? String ?: "",
                        sequence = (chunkData["sequence"] as? Long)?.toInt() ?:
                        chunkId.substringAfter("chunk_").toIntOrNull() ?: 0,
                        emotion = chunkData["emotion"] as? String ?: "neutral",
                        naturalness = chunkData["naturalness"] as? Map<String, Any>,
                        isLast = chunkData["isLast"] as? Boolean ?: false,
                        timestamp = chunkData["timestamp"] as? Long ?: 0
                    )

                    processAudioChunk(audioChunk)

                    // Remove after delay
                    audioScope.launch {
                        delay(10000)
                        try {
                            snapshot.ref.removeValue()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to remove chunk", e)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing Firebase chunk", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase error: ${error.message}")
                _audioState.update {
                    it.copy(
                        isStreaming = false,
                        error = error.message
                    )
                }
            }
        }

        streamRef.addChildEventListener(firebaseListener!!)
        Log.d(TAG, "✅ Firebase listener attached for HIGH QUALITY audio")

        // Safety timeout with separate job
        timeoutJob?.cancel()
        timeoutJob = audioScope.launch {
            delay(60000)
            if (!isStopRequested.get() &&
                _audioState.value.currentMessageId == messageId &&
                chunksReceived.get() == 0) {
                Log.w(TAG, "⏱️ Timeout - no chunks received")
                _audioState.update { it.copy(error = "Timeout waiting for audio") }
                stopStreaming()
            }
        }
    }

    private fun applyStereoFade(
        incoming: Pair<ShortArray, ShortArray>,
        outgoingTail: ShortArray
    ): Pair<ShortArray, ShortArray> {
        val (inLeft, inRight) = incoming
        val fadeLength = minOf(CROSSFADE_SAMPLES, outgoingTail.size, inLeft.size)

        if (fadeLength <= 0) return incoming

        val resultLeft = inLeft.copyOf()
        val resultRight = inRight.copyOf()

        // Crossfade più aggressivo per transizioni più fluide
        for (i in 0 until fadeLength) {
            val fadeIn = (i.toFloat() / fadeLength).pow(0.5f) // Curva più veloce
            val fadeOut = 1f - fadeIn

            // Mix con tail
            resultLeft[i] = ((resultLeft[i] * fadeIn) + (outgoingTail[i] * fadeOut)).toInt().toShort()
            resultRight[i] = ((resultRight[i] * fadeIn) + (outgoingTail[i] * fadeOut * 0.8f)).toInt().toShort()
        }

        return resultLeft to resultRight
    }

    fun stopStreaming() {
        Log.d(TAG, "🛑 Stopping high quality voice streaming")

        // Set stop flag first
        isStopRequested.set(true)
        isPlaying.set(false)

        // Cancel all jobs
        playbackJob?.cancel()
        timeoutJob?.cancel()

        // Remove Firebase listener
        firebaseListener?.let { listener ->
            currentStreamRef?.removeEventListener(listener)
            Log.d(TAG, "Firebase listener removed")
        }
        firebaseListener = null
        currentStreamRef = null

        // Clear audio queue and reset counters
        audioQueue.clear()
        chunksReceived.set(0)
        chunksPlayed.set(0)
        lastChunkTail = null
        previousEmotion = VoiceEmotion.NEUTRAL

        // Release audio effects
        releaseAudioEffects()

        // Stop and release AudioTrack
        audioTrack?.apply {
            try {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                flush()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            }
        }
        audioTrack = null

        // Reset state completely
        _audioState.update {
            AudioState()
        }

        Log.d(TAG, "✅ Audio system stopped and cleaned up")
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            environmentalReverb?.release()
            bassBoost?.release()
            virtualizer?.release()
            dynamicsProcessing?.release()

            equalizer = null
            environmentalReverb = null
            bassBoost = null
            virtualizer = null
            dynamicsProcessing = null

            Log.d(TAG, "Audio effects released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
    }

    fun cleanup() {
        stopStreaming()
        audioScope.cancel()
    }
}

class AudioStreamException(message: String, cause: Throwable? = null) : Exception(message, cause)