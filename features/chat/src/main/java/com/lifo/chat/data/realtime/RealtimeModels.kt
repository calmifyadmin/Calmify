package com.lifo.chat.data.realtime

import java.util.UUID

data class SessionConfig(
    val model: String = "gpt-4o-realtime-preview-2024-10-01",
    val modalities: List<String> = listOf("text", "audio"),
    val instructions: String = """
        You are a compassionate mental health companion in the Calmify app.
        Provide supportive, empathetic responses. Keep responses concise but warm.
        Focus on emotional wellness and positive coping strategies.
        Respond in Italian when speaking to Italian users.
    """.trimIndent(),
    val voice: String = "shimmer",
    val inputAudioFormat: String = "pcm16",
    val outputAudioFormat: String = "pcm16",
    val inputAudioTranscription: Map<String, Any>? = null,
    val turnDetection: Map<String, Any> = mapOf(
        "type" to "server_vad",
        "threshold" to 0.5f,
        "prefix_padding_ms" to 300,
        "silence_duration_ms" to 200
    ),
    val tools: List<Map<String, Any>> = emptyList(),
    val toolChoice: String = "auto",
    val temperature: Float = 0.8f,
    val maxResponseOutputTokens: Int? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "model" to model,
            "modalities" to modalities,
            "instructions" to instructions,
            "voice" to voice,
            "input_audio_format" to inputAudioFormat,
            "output_audio_format" to outputAudioFormat,
            "turn_detection" to turnDetection,
            "tools" to tools,
            "tool_choice" to toolChoice,
            "temperature" to temperature
        )
        
        inputAudioTranscription?.let { map["input_audio_transcription"] = it }
        maxResponseOutputTokens?.let { map["max_response_output_tokens"] = it }
        
        return map
    }
}

enum class Voice(val value: String) {
    ALLOY("alloy"),
    ECHO("echo"), 
    SHIMMER("shimmer")
}

data class ConversationItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val role: String? = null,
    val content: List<Map<String, Any>> = emptyList(),
    val status: String? = null
) {
    companion object {
        fun userMessage(text: String, audioData: String? = null): ConversationItem {
            val content = mutableListOf<Map<String, Any>>()
            content.add(mapOf("type" to "text", "text" to text))
            audioData?.let { 
                content.add(mapOf("type" to "input_audio", "audio" to it)) 
            }
            return ConversationItem(
                type = "message",
                role = "user",
                content = content
            )
        }
        
        fun assistantMessage(text: String, audioData: String? = null): ConversationItem {
            val content = mutableListOf<Map<String, Any>>()
            content.add(mapOf("type" to "text", "text" to text))
            audioData?.let { 
                content.add(mapOf("type" to "output_audio", "audio" to it)) 
            }
            return ConversationItem(
                type = "message",
                role = "assistant",
                content = content
            )
        }
    }
    
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "type" to type,
            "content" to content
        )
        
        role?.let { map["role"] = it }
        status?.let { map["status"] = it }
        
        return map
    }
}

data class ResponseConfig(
    val modalities: List<String> = listOf("text", "audio"),
    val instructions: String? = null,
    val voice: String = "shimmer",
    val outputAudioFormat: String = "pcm16",
    val tools: List<Map<String, Any>> = emptyList(),
    val toolChoice: String = "auto",
    val temperature: Float = 0.8f,
    val maxOutputTokens: Int? = null
) {
    companion object {
        fun default() = ResponseConfig()
        
        fun audioOnly() = ResponseConfig(
            modalities = listOf("audio")
        )
        
        fun textOnly() = ResponseConfig(
            modalities = listOf("text")
        )
    }
    
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "modalities" to modalities,
            "voice" to voice,
            "output_audio_format" to outputAudioFormat,
            "tools" to tools,
            "tool_choice" to toolChoice,
            "temperature" to temperature
        )
        
        instructions?.let { map["instructions"] = it }
        maxOutputTokens?.let { map["max_output_tokens"] = it }
        
        return map
    }
}

// Connection states
sealed class RealtimeConnectionState {
    object Disconnected : RealtimeConnectionState()
    object Connecting : RealtimeConnectionState()
    object Connected : RealtimeConnectionState()
    data class Error(val message: String, val cause: Throwable?) : RealtimeConnectionState()
}

// Live Chat states for UI
sealed class LiveChatState {
    object Idle : LiveChatState()
    object Connecting : LiveChatState()
    object Connected : LiveChatState()
    data class Recording(val duration: Long, val audioLevel: Float) : LiveChatState()
    data class Processing(val message: String) : LiveChatState()
    data class Speaking(val transcript: String?, val audioLevel: Float) : LiveChatState()
    data class Error(val message: String, val cause: Throwable?) : LiveChatState()
}

// Push-to-talk state
data class PushToTalkState(
    val isRecording: Boolean = false,
    val audioLevel: Float = 0f,
    val recordingDuration: Long = 0L,
    val error: String? = null
)