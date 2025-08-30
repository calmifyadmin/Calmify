package com.lifo.chat.data.realtime

import java.util.UUID

/**
 * Simplified OpenAI Realtime API Event Models
 * Based on OpenAI Realtime API documentation
 */
sealed class RealtimeEvent {
    abstract val eventId: String
    abstract val type: String
    
    // Client Events (sent to server)
    sealed class Client : RealtimeEvent() {
        data class SessionUpdate(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "session.update",
            val session: Map<String, Any>
        ) : Client()
        
        data class InputAudioBufferAppend(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "input_audio_buffer.append",
            val audio: String // Base64 encoded audio
        ) : Client()
        
        data class InputAudioBufferCommit(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "input_audio_buffer.commit"
        ) : Client()
        
        data class InputAudioBufferClear(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "input_audio_buffer.clear"
        ) : Client()
        
        data class ConversationItemCreate(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "conversation.item.create",
            val item: Map<String, Any>
        ) : Client()
        
        data class ResponseCreate(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "response.create",
            val response: Map<String, Any>? = null
        ) : Client()
        
        data class ResponseCancel(
            override val eventId: String = UUID.randomUUID().toString(),
            override val type: String = "response.cancel"
        ) : Client()
    }
    
    // Server Events (received from server)  
    sealed class Server : RealtimeEvent() {
        data class Error(
            override val eventId: String,
            override val type: String = "error",
            val error: ErrorDetails
        ) : Server()
        
        data class SessionCreated(
            override val eventId: String,
            override val type: String = "session.created",
            val session: Map<String, Any>
        ) : Server()
        
        data class SessionUpdated(
            override val eventId: String,
            override val type: String = "session.updated",
            val session: Map<String, Any>
        ) : Server()
        
        data class InputAudioBufferCommitted(
            override val eventId: String,
            override val type: String = "input_audio_buffer.committed",
            val previousItemId: String?,
            val itemId: String
        ) : Server()
        
        data class InputAudioBufferSpeechStarted(
            override val eventId: String,
            override val type: String = "input_audio_buffer.speech_started",
            val audioStartMs: Int,
            val itemId: String
        ) : Server()
        
        data class InputAudioBufferSpeechStopped(
            override val eventId: String,
            override val type: String = "input_audio_buffer.speech_stopped",
            val audioEndMs: Int,
            val itemId: String
        ) : Server()
        
        data class ResponseCreated(
            override val eventId: String,
            override val type: String = "response.created",
            val response: Map<String, Any>
        ) : Server()
        
        data class ResponseDone(
            override val eventId: String,
            override val type: String = "response.done",
            val response: Map<String, Any>
        ) : Server()
        
        data class ResponseTextDelta(
            override val eventId: String,
            override val type: String = "response.text.delta",
            val responseId: String,
            val itemId: String,
            val outputIndex: Int,
            val contentIndex: Int,
            val delta: String
        ) : Server()
        
        data class ResponseTextDone(
            override val eventId: String,
            override val type: String = "response.text.done",
            val responseId: String,
            val itemId: String,
            val outputIndex: Int,
            val contentIndex: Int,
            val text: String
        ) : Server()
        
        data class ResponseAudioDelta(
            override val eventId: String,
            override val type: String = "response.audio.delta",
            val responseId: String,
            val itemId: String,
            val outputIndex: Int,
            val contentIndex: Int,
            val delta: String // Base64 encoded audio
        ) : Server()
        
        data class ResponseAudioDone(
            override val eventId: String,
            override val type: String = "response.audio.done",
            val responseId: String,
            val itemId: String,
            val outputIndex: Int,
            val contentIndex: Int
        ) : Server()
        
        data class ResponseAudioTranscriptDelta(
            override val eventId: String,
            override val type: String = "response.audio_transcript.delta",
            val responseId: String,
            val itemId: String,
            val outputIndex: Int,
            val contentIndex: Int,
            val delta: String
        ) : Server()
        
        data class ResponseAudioTranscriptDone(
            override val eventId: String,
            override val type: String = "response.audio_transcript.done",
            val responseId: String,
            val itemId: String,
            val outputIndex: Int,
            val contentIndex: Int,
            val transcript: String
        ) : Server()
    }
}

data class ErrorDetails(
    val type: String,
    val code: String,
    val message: String,
    val param: String? = null,
    val eventId: String? = null
)