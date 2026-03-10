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
 * Server turn states
 */
enum class TurnState {
    UserTurn,      // User is speaking
    AgentTurn,     // Agent is responding
    WaitingForUser // Waiting for user to speak
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
    val isMuted: Boolean = false, // Mute/Unmute state
    val turnState: TurnState = TurnState.WaitingForUser, // Server-driven turn state
    val aiEmotion: AIEmotion = AIEmotion.Neutral,
    val audioLevel: Float = 0f, // 0.0 to 1.0
    val sessionId: String? = null,
    val transcript: String = "",
    val partialTranscript: String = "", // Partial transcript from server
    val error: String? = null,
    val isChannelOpen: Boolean = false, // Channel always open state
    val isCameraActive: Boolean = false,
    // Session time limit (subscription gating)
    val sessionElapsedSeconds: Long = 0L,
    val sessionTimeLimitSeconds: Long = 0L, // 0 = no limit (PRO)
    val showTimeLimitReached: Boolean = false
)