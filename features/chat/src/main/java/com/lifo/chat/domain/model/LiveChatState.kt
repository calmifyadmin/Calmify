package com.lifo.chat.domain.model

import androidx.compose.runtime.Stable

/**
 * Connection status for LiveChat
 */
enum class ConnectionStatus {
    Disconnected,
    Connecting, 
    Connected,
    Error
}

/**
 * Push-to-talk states
 */
enum class PTTState {
    Idle,
    Listening,
    Processing
}

/**
 * AI emotion states for visual globe effects
 */
enum class AIEmotion {
    Neutral,
    Happy,
    Thinking,
    Speaking
}

/**
 * Main UI state for LiveChatScreen
 */
@Stable
data class LiveChatUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val hasAudioPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val pushToTalkState: PTTState = PTTState.Idle,
    val aiEmotion: AIEmotion = AIEmotion.Neutral,
    val audioLevel: Float = 0f, // 0.0 to 1.0
    val sessionId: String? = null,
    val transcript: String = "",
    val error: String? = null,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val isCameraActive: Boolean = false
)