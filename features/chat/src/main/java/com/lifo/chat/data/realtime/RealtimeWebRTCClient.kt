package com.lifo.chat.data.realtime

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import com.lifo.chat.BuildConfig
import com.lifo.chat.domain.audio.AudioLevelExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebRTC client for OpenAI Realtime API integration
 */
@Singleton
class RealtimeWebRTCClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ephemeralKeyManager: EphemeralKeyManager,
    private val audioLevelExtractor: AudioLevelExtractor
) {
    
    companion object {
        private const val TAG = "RealtimeWebRTCClient"
        private const val OPENAI_REALTIME_URL = "https://api.openai.com/v1/realtime"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val _sessionState = MutableStateFlow(WebRTCSessionState())
    val sessionState: StateFlow<WebRTCSessionState> = _sessionState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // WebRTC components
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioStream: MediaStream? = null
    
    // Audio data flow for level extraction
    private val audioDataFlow = MutableSharedFlow<ByteArray>()
    
    // Listener for WebRTC callbacks
    private var listener: WebRTCClientListener? = null
    
    init {
        // Initialize WebRTC
        initializeWebRTC()
        
        // Start audio level extraction
        audioLevelExtractor.startExtracting(audioDataFlow, sampleRate = 48000, channels = 1)
        
        // Observe audio levels and update state
        coroutineScope.launch {
            audioLevelExtractor.audioLevel.collect { level ->
                _sessionState.update { it.copy(audioLevel = level) }
                listener?.onAudioLevelChanged(level)
            }
        }
    }
    
    /**
     * Set listener for WebRTC events
     */
    fun setListener(listener: WebRTCClientListener) {
        this.listener = listener
    }
    
    /**
     * Configure audio for speaker output (not earpiece)
     */
    private fun configureAudioForSpeaker() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Set audio mode to MUSIC for speaker output (not IN_CALL or IN_COMMUNICATION)
            audioManager.mode = AudioManager.MODE_NORMAL
            
            // Force route to speaker
            audioManager.isSpeakerphoneOn = true
            
            // Request audio focus for music streaming
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            }
            
            // Set volume to a reasonable level
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVolume < maxVolume * 0.5) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (maxVolume * 0.7).toInt(),
                    AudioManager.FLAG_SHOW_UI
                )
            }
            
            Log.d(TAG, "🔊 Audio configured for speaker output with MUSIC mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio for speaker", e)
        }
    }
    
    /**
     * Initialize WebRTC PeerConnectionFactory
     */
    private fun initializeWebRTC() {
        try {
            Log.d(TAG, "🚀 Initializing WebRTC...")
            
            // Initialize PeerConnection factory with audio focus
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            
            PeerConnectionFactory.initialize(options)
            
            // Create custom audio device module for speaker output
            val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setAudioSource(android.media.MediaRecorder.AudioSource.MIC) // Use MIC instead of VOICE_COMMUNICATION
                .setAudioFormat(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000)
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.e(TAG, "Audio record init error: $errorMessage")
                    }
                    
                    override fun onWebRtcAudioRecordStartError(
                        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "Audio record start error: $errorMessage")
                    }
                    
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.e(TAG, "Audio record error: $errorMessage")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                        Log.e(TAG, "Audio track init error: $errorMessage")
                    }
                    
                    override fun onWebRtcAudioTrackStartError(
                        errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "Audio track start error: $errorMessage")
                    }
                    
                    override fun onWebRtcAudioTrackError(errorMessage: String?) {
                        Log.e(TAG, "Audio track error: $errorMessage")
                    }
                })
                .setSamplesReadyCallback { samples ->
                    // Process audio samples if needed
                }
                .createAudioDeviceModule()
            
            // Force speaker phone ON to avoid earpiece mode
            audioDeviceModule.setSpeakerMute(false)
            
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()
            
            _sessionState.update { it.copy(isInitialized = true) }
            Log.d(TAG, "✅ WebRTC initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize WebRTC", e)
            _sessionState.update { 
                it.copy(
                    connectionState = WebRTCConnectionState.Failed,
                    error = "WebRTC initialization failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Start WebRTC session with OpenAI
     */
    suspend fun startSession(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "🔌 Starting WebRTC session...")
            
            // Configure audio manager for speaker output
            configureAudioForSpeaker()
            
            _sessionState.update { 
                it.copy(
                    connectionState = WebRTCConnectionState.Connecting,
                    error = null
                )
            }
            
            // Get ephemeral key
            val keyResult = ephemeralKeyManager.getValidEphemeralKey()
            if (keyResult.isFailure) {
                throw keyResult.exceptionOrNull() ?: RuntimeException("Failed to get ephemeral key")
            }
            
            val ephemeralKey = keyResult.getOrThrow()
            val sessionId = ephemeralKeyManager.getCurrentSessionId()
                ?: throw RuntimeException("Session ID not available")
            
            _sessionState.update { 
                it.copy(
                    sessionId = sessionId,
                    ephemeralKey = ephemeralKey
                )
            }
            
            // Create peer connection
            createPeerConnection()
            
            // Add local audio track
            addLocalAudioTrack()
            
            // Create SDP offer
            createAndSendOffer(ephemeralKey)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start WebRTC session", e)
            _sessionState.update { 
                it.copy(
                    connectionState = WebRTCConnectionState.Failed,
                    error = "Failed to start session: ${e.message}"
                )
            }
            Result.failure(e)
        }
    }
    
    /**
     * Create WebRTC PeerConnection
     */
    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }
        
        val observer = object : PeerConnection.Observer {
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "🔗 Connection state changed: $newState")
                val webrtcState = when (newState) {
                    PeerConnection.PeerConnectionState.NEW -> WebRTCConnectionState.New
                    PeerConnection.PeerConnectionState.CONNECTING -> WebRTCConnectionState.Connecting
                    PeerConnection.PeerConnectionState.CONNECTED -> WebRTCConnectionState.Connected
                    PeerConnection.PeerConnectionState.DISCONNECTED -> WebRTCConnectionState.Disconnected
                    PeerConnection.PeerConnectionState.FAILED -> WebRTCConnectionState.Failed
                    PeerConnection.PeerConnectionState.CLOSED -> WebRTCConnectionState.Closed
                }
                
                _sessionState.update { it.copy(connectionState = webrtcState) }
                listener?.onConnectionStateChanged(webrtcState)
            }
            
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d(TAG, "🧊 ICE connection state changed: $newState")
                val iceState = when (newState) {
                    PeerConnection.IceConnectionState.NEW -> IceConnectionState.New
                    PeerConnection.IceConnectionState.CHECKING -> IceConnectionState.Checking
                    PeerConnection.IceConnectionState.CONNECTED -> IceConnectionState.Connected
                    PeerConnection.IceConnectionState.COMPLETED -> IceConnectionState.Completed
                    PeerConnection.IceConnectionState.FAILED -> IceConnectionState.Failed
                    PeerConnection.IceConnectionState.DISCONNECTED -> IceConnectionState.Disconnected
                    PeerConnection.IceConnectionState.CLOSED -> IceConnectionState.Closed
                }
                
                _sessionState.update { it.copy(iceConnectionState = iceState) }
                listener?.onIceConnectionStateChanged(iceState)
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "🧊 ICE connection receiving change: $receiving")
            }
            
            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "🎵 Remote stream added")
                // Handle remote audio stream and disable microphone to prevent feedback
                stream.audioTracks.forEach { audioTrack ->
                    Log.d(TAG, "🎵 Remote audio track received - disabling local microphone")
                    localAudioTrack?.setEnabled(false)
                    _sessionState.update { it.copy(
                        isAudioEnabled = false,
                        isRemoteAudioPlaying = false // TODO: Implement proper audio level detection
                    )}
                    listener?.onRemoteAudioStarted()
                }
            }
            
            override fun onRemoveStream(stream: MediaStream) {
                Log.d(TAG, "🎵 Remote stream removed - microphone can be re-enabled via push-to-talk")
                // Don't auto-enable microphone - wait for push-to-talk
                _sessionState.update { it.copy(isRemoteAudioPlaying = false) }
                listener?.onRemoteAudioEnded()
            }
            
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d(TAG, "🎵 Track added: ${receiver?.track()?.kind()}")
                // Handle incoming audio track
                receiver?.track()?.let { track ->
                    if (track.kind() == "audio") {
                        Log.d(TAG, "🎵 Remote audio track added - disabling local microphone to prevent feedback")
                        // IMPORTANT: Disable local audio when receiving remote audio to prevent feedback
                        localAudioTrack?.setEnabled(false)
                        _sessionState.update { it.copy(
                            isAudioEnabled = false,
                            isRemoteAudioPlaying = false // TODO: Implement proper audio level detection
                        )}
                        listener?.onRemoteAudioStarted()
                    }
                }
            }
            
            override fun onDataChannel(dataChannel: DataChannel) {}
            override fun onIceCandidate(candidate: IceCandidate) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onRenegotiationNeeded() {}
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
            ?: throw RuntimeException("Failed to create PeerConnection")
        
        Log.d(TAG, "✅ PeerConnection created")
    }
    
    /**
     * Add local audio track for microphone input
     */
    private fun addLocalAudioTrack() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
        }
        
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", audioSource)
        
        // IMPORTANT: Start with audio DISABLED to prevent feedback loop
        // Audio will only be enabled when push-to-talk is pressed
        localAudioTrack?.setEnabled(false)
        
        // Create local stream and add audio track
        localAudioStream = peerConnectionFactory?.createLocalMediaStream("local_stream")
        localAudioStream?.addTrack(localAudioTrack)
        
        // Add track to peer connection (Unified Plan API)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local_stream"))
        }
        
        // Start with audio disabled to prevent listening while AI is speaking
        _sessionState.update { it.copy(isAudioEnabled = false) }
        Log.d(TAG, "🎤 Local audio track added (initially muted to prevent feedback)")
    }
    
    /**
     * Create SDP offer and send to OpenAI
     */
    private suspend fun createAndSendOffer(ephemeralKey: String) = withContext(Dispatchers.Main) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        try {
            // Create offer
            val sdpObserver = object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    Log.d(TAG, "📝 SDP offer created successfully")
                    
                    coroutineScope.launch {
                        try {
                            // Set local description
                            val setLocalObserver = object : SdpObserver {
                                override fun onSetSuccess() {
                                    Log.d(TAG, "✅ Local SDP description set")
                                    
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            // Send to OpenAI
                                            sendSdpToOpenAI(ephemeralKey, sessionDescription.description)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Failed to send SDP to OpenAI", e)
                                            _sessionState.update { 
                                                it.copy(
                                                    connectionState = WebRTCConnectionState.Failed,
                                                    error = "Failed to send SDP: ${e.message}"
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                override fun onSetFailure(error: String) {
                                    Log.e(TAG, "❌ Failed to set local SDP: $error")
                                    _sessionState.update { 
                                        it.copy(
                                            connectionState = WebRTCConnectionState.Failed,
                                            error = "Failed to set local SDP: $error"
                                        )
                                    }
                                }
                                
                                override fun onCreateSuccess(sessionDescription: SessionDescription) {}
                                override fun onCreateFailure(error: String) {}
                            }
                            
                            peerConnection?.setLocalDescription(setLocalObserver, sessionDescription)
                            _sessionState.update { it.copy(localSdpOffer = sessionDescription.description) }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error in SDP create success callback", e)
                        }
                    }
                }
                
                override fun onCreateFailure(error: String) {
                    Log.e(TAG, "❌ Failed to create SDP offer: $error")
                    _sessionState.update { 
                        it.copy(
                            connectionState = WebRTCConnectionState.Failed,
                            error = "Failed to create SDP offer: $error"
                        )
                    }
                }
                
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String) {}
            }
            
            peerConnection?.createOffer(sdpObserver, constraints)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception creating SDP offer", e)
            _sessionState.update { 
                it.copy(
                    connectionState = WebRTCConnectionState.Failed,
                    error = "Exception creating SDP offer: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Send SDP offer to OpenAI Realtime API
     */
    private suspend fun sendSdpToOpenAI(ephemeralKey: String, sdpOffer: String) = withContext(Dispatchers.IO) {
        try {
            val url = "$OPENAI_REALTIME_URL?model=gpt-4o-realtime-preview-2024-12-17"
            
            Log.d(TAG, "📡 Sending SDP offer to OpenAI...")
            Log.d(TAG, "🔗 URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $ephemeralKey")
                .addHeader("Content-Type", "application/sdp")
                .post(sdpOffer.toRequestBody("application/sdp".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Failed to send SDP. Status: ${response.code}, Body: $errorBody")
                throw RuntimeException("HTTP ${response.code}: $errorBody")
            }
            
            val responseBody = response.body?.string()
                ?: throw RuntimeException("Empty response body")
            
            Log.d(TAG, "✅ Received SDP answer from OpenAI")
            
            // Set remote description
            withContext(Dispatchers.Main) {
                setRemoteDescription(responseBody)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send SDP to OpenAI", e)
            throw e
        }
    }
    
    /**
     * Set remote SDP answer from OpenAI
     */
    private fun setRemoteDescription(sdpAnswer: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
        
        val sdpObserver = object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "✅ Remote SDP description set successfully")
                _sessionState.update { 
                    it.copy(
                        remoteSdpAnswer = sdpAnswer,
                        connectionState = WebRTCConnectionState.Connected
                    )
                }
            }
            
            override fun onSetFailure(error: String) {
                Log.e(TAG, "❌ Failed to set remote SDP: $error")
                _sessionState.update { 
                    it.copy(
                        connectionState = WebRTCConnectionState.Failed,
                        error = "Failed to set remote SDP: $error"
                    )
                }
            }
            
            override fun onCreateSuccess(sessionDescription: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }
        
        peerConnection?.setRemoteDescription(sdpObserver, sessionDescription)
    }
    
    /**
     * Enable/disable local audio track
     */
    fun setAudioEnabled(enabled: Boolean) {
        Log.d(TAG, if (enabled) "🔊 Enabling audio (microphone)" else "🔇 Disabling audio (microphone)")
        
        // Enable/disable the audio track
        localAudioTrack?.setEnabled(enabled)
        
        _sessionState.update { it.copy(isAudioEnabled = enabled) }
        Log.d(TAG, if (enabled) "✅ Audio enabled" else "✅ Audio disabled")
    }
    
    /**
     * End current WebRTC session
     */
    suspend fun endSession() {
        try {
            Log.d(TAG, "🔚 Ending WebRTC session...")
            
            // Stop audio level extraction
            audioLevelExtractor.stopExtracting()
            
            // Close peer connection
            peerConnection?.close()
            peerConnection = null
            
            // Dispose audio components
            localAudioTrack?.dispose()
            localAudioTrack = null
            
            localAudioStream?.dispose()
            localAudioStream = null
            
            audioSource?.dispose()
            audioSource = null
            
            // Restore audio settings to default
            restoreAudioSettings()
            
            // Clear ephemeral session
            ephemeralKeyManager.clearSession()
            
            _sessionState.value = WebRTCSessionState()
            
            Log.d(TAG, "✅ WebRTC session ended")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error ending WebRTC session", e)
        }
    }
    
    /**
     * Restore audio settings to default
     */
    private fun restoreAudioSettings() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            Log.d(TAG, "🔊 Audio settings restored to default")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio settings", e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        coroutineScope.cancel()
        
        runBlocking {
            endSession()
        }
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        audioLevelExtractor.reset()
    }
}