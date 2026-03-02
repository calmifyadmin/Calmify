package com.lifo.chat.di

import android.content.Context
import com.lifo.chat.audio.GeminiLiveAudioSource
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.audio.GeminiVoiceAudioSource
import com.lifo.chat.audio.SynchronizedSpeechControllerImpl
import com.lifo.chat.audio.engine.AAAudioEngine
import com.lifo.chat.audio.oboe.NativeAudioEngine
import com.lifo.chat.audio.vad.SileroVadEngine
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.domain.audio.AdaptiveBargeinDetector
import com.lifo.chat.domain.audio.AecReliabilityDetector
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.audio.FullDuplexAudioSession
import com.lifo.chat.domain.audio.HeadphoneDetector
import com.lifo.chat.domain.audio.ReferenceSignalBargeInDetector
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import com.lifo.chat.presentation.viewmodel.LiveChatViewModel
import com.lifo.util.speech.SynchronizedSpeechController
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val chatKoinModule = module {
    // === ChatModule ===
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // === AudioModule ===
    single { ApiConfigManager(get()) }
    single { GeminiNativeVoiceSystem(get()) }
    single { GeminiVoiceAudioSource(get<GeminiNativeVoiceSystem>()) }
    single { GeminiLiveAudioSource(get<GeminiLiveAudioManager>()) }
    single<SynchronizedSpeechController> { SynchronizedSpeechControllerImpl() }

    // === GeminiLiveModule ===
    single { GeminiLiveWebSocketClient(get()) }
    single { AdaptiveBargeinDetector() }
    single { ReferenceSignalBargeInDetector() }
    single { AudioQualityAnalyzer() }
    single { ConversationContextManager() }
    single { SileroVadEngine(get()) }
    single { AAAudioEngine(get()) }
    single { FullDuplexAudioSession(get()) }
    single { HeadphoneDetector(get()) }
    single { AecReliabilityDetector() }
    single { NativeAudioEngine() }
    single {
        GeminiLiveAudioManager(
            get(), // context
            get(), // adaptiveBargeinDetector
            get(), // sileroVadEngine
            get(), // referenceSignalDetector
            get()  // fullDuplexSession
        )
    }
    single { GeminiLiveCameraManager(get()) }

    // === ViewModels ===
    // ChatViewModel(repository, context, voiceSystem, voiceAudioSource,
    //   synchronizedSpeechController, apiConfigManager, auth, savedStateHandle)
    viewModel { ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }

    // LiveChatViewModel(context, apiConfigManager, geminiWebSocketClient, geminiAudioManager,
    //   geminiCameraManager, liveAudioSource, synchronizedSpeechController,
    //   audioQualityAnalyzer, conversationContextManager, chatRepository,
    //   diaryRepository, firebaseAuth, savedStateHandle)
    viewModel { LiveChatViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
