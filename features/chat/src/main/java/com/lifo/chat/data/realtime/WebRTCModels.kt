package com.lifo.chat.data.realtime

import androidx.compose.runtime.Stable

/**
 * WebRTC connection states
 */
enum class WebRTCConnectionState {
    New,
    Connecting, 
    Connected,
    Disconnected,
    Failed,
    Closed
}

/**
 * ICE connection states
 */
enum class IceConnectionState {
    New,
    Checking,
    Connected,
    Completed,
    Failed,
    Disconnected,
    Closed
}

/**
 * WebRTC peer connection configuration
 */
@Stable
data class WebRTCConfig(
    val iceServers: List<String> = listOf("stun:stun.l.google.com:19302"),
    val enableAudioProcessing: Boolean = true,
    val enableEchoCancellation: Boolean = true,
    val enableAutoGainControl: Boolean = true,
    val enableNoiseSuppression: Boolean = true,
    val audioSampleRate: Int = 48000,
    val audioChannels: Int = 1
)

/**
 * Audio constraints for WebRTC
 */
data class AudioConstraints(
    val echoCancellation: Boolean = true,
    val autoGainControl: Boolean = true,
    val noiseSuppression: Boolean = true,
    val sampleRate: Int = 48000,
    val channelCount: Int = 1
)

/**
 * WebRTC session state
 */
@Stable
data class WebRTCSessionState(
    val connectionState: WebRTCConnectionState = WebRTCConnectionState.New,
    val iceConnectionState: IceConnectionState = IceConnectionState.New,
    val sessionId: String? = null,
    val ephemeralKey: String? = null,
    val isAudioEnabled: Boolean = false,
    val localSdpOffer: String? = null,
    val remoteSdpAnswer: String? = null,
    val audioLevel: Float = 0f, // 0.0 to 1.0 RMS level
    val error: String? = null,
    val isInitialized: Boolean = false
)

/**
 * SDP offer/answer data
 */
data class SdpData(
    val type: SdpType,
    val sdp: String
)

/**
 * SDP types
 */
enum class SdpType {
    OFFER,
    ANSWER
}

/**
 * Audio frame data for RMS calculation
 */
data class AudioFrame(
    val data: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFrame

        if (!data.contentEquals(other.data)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * WebRTC client callbacks
 */
interface WebRTCClientListener {
    fun onConnectionStateChanged(state: WebRTCConnectionState)
    fun onIceConnectionStateChanged(state: IceConnectionState)
    fun onAudioLevelChanged(level: Float)
    fun onRemoteAudioReceived(audioFrame: AudioFrame)
    fun onError(error: String)
}

/**
 * OpenAI Realtime SDP answer response
 */
data class OpenAIRealtimeAnswer(
    val sdp: String,
    val type: String = "answer"
)