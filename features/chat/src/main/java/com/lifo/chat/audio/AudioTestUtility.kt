package com.lifo.chat.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Utility per testare rapidamente il sistema audio
 */
class AudioTestUtility @Inject constructor(
    private val context: Context,
    private val voiceSystem: GeminiNativeVoiceSystem
) {
    companion object {
        private const val TAG = "AudioTest"
    }

    /**
     * Esegue un test rapido del sistema audio
     */
    suspend fun runQuickTest(): TestResult {
        Log.d(TAG, "🧪 Starting quick audio test...")

        return withContext(Dispatchers.IO) {
            val results = mutableListOf<String>()
            var success = true

            try {
                // 1. Check system volume
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                results.add("Volume: $volume/$maxVolume")
                if (volume == 0) {
                    results.add("⚠️ VOLUME IS MUTED!")
                    success = false
                }

                // 2. Check voice system initialization
                if (!voiceSystem.voiceState.value.isInitialized) {
                    results.add("❌ Voice system not initialized")
                    success = false
                } else {
                    results.add("✅ Voice system initialized")
                }

                // 3. Test short phrase
                if (success) {
                    val testText = "Test audio"
                    results.add("Testing with: \"$testText\"")

                    voiceSystem.speakWithEmotion(
                        text = testText,
                        emotion = GeminiNativeVoiceSystem.Emotion.NEUTRAL,
                        messageId = "test_${System.currentTimeMillis()}"
                    )

                    // Wait for audio to start
                    var audioStarted = false
                    val startTime = System.currentTimeMillis()

                    while (!audioStarted && System.currentTimeMillis() - startTime < 5000) {
                        if (voiceSystem.voiceState.value.isSpeaking ) {
                            audioStarted = true
                            results.add("✅ Audio started successfully")
                        }
                        delay(100)
                    }

                    if (!audioStarted) {
                        results.add("❌ Audio failed to start within 5 seconds")
                        success = false
                    }

                    // Wait for completion
                    while (voiceSystem.voiceState.value.isSpeaking) {
                        delay(100)
                    }
                }

            } catch (e: Exception) {
                results.add("❌ Exception: ${e.message}")
                success = false
            }

            TestResult(success, results)
        }
    }

    data class TestResult(
        val success: Boolean,
        val details: List<String>
    ) {
        fun getReport(): String {
            return buildString {
                appendLine("=== AUDIO TEST REPORT ===")
                appendLine("Result: ${if (success) "✅ PASSED" else "❌ FAILED"}")
                appendLine("\nDetails:")
                details.forEach { appendLine(it) }
                appendLine("========================")
            }
        }
    }
}

// Uso nel ViewModel o Activity:
/*
class ChatViewModel {
    // Aggiungi questo metodo per debug
    fun testAudio() {
        viewModelScope.launch {
            val tester = AudioTestUtility(context, voiceSystem)
            val result = tester.runQuickTest()
            Log.d("ChatViewModel", result.getReport())

            if (!result.success) {
                _uiState.update {
                    it.copy(error = "Audio test failed. Check logs.")
                }
            }
        }
    }
}
*/