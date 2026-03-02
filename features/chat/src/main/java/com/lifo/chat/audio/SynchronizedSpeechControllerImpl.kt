package com.lifo.chat.audio


import com.lifo.util.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Implementation of SynchronizedSpeechController that bridges audio sources
 * to animation targets for ultra-synchronized lip-sync.
 *
 * This controller:
 * - Collects playback events from the audio source
 * - Forwards them to the animation target (HumanoidController)
 * - Manages the synchronization lifecycle
 * - Provides real-time audio level updates for intensity modulation
 *
 * Usage:
 * 1. Attach an audio source (GeminiVoiceAudioSource or GeminiLiveAudioSource)
 * 2. Attach an animation target (HumanoidController)
 * 3. Call speakSynchronized() to start synchronized speech
 */
class SynchronizedSpeechControllerImpl : SynchronizedSpeechController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var audioSource: SpeechAudioSource? = null
    private var animationTarget: SpeechAnimationTarget? = null

    private var eventCollectionJob: Job? = null
    private var audioLevelJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    override fun attachAudioSource(source: SpeechAudioSource) {
        println("[SyncSpeechController] Attaching audio source: ${source::class.simpleName}")

        // Detach existing source
        detachAudioSource()

        audioSource = source

        // Start collecting events
        startEventCollection()
        startAudioLevelSync()
    }

    override fun attachAnimationTarget(target: SpeechAnimationTarget) {
        println("[SyncSpeechController] Attaching animation target: ${target::class.simpleName}")
        animationTarget = target
    }

    override fun detachAudioSource() {
        eventCollectionJob?.cancel()
        eventCollectionJob = null
        audioLevelJob?.cancel()
        audioLevelJob = null
        audioSource = null
        println("[SyncSpeechController] Audio source detached")
    }

    override fun detachAnimationTarget() {
        animationTarget = null
        println("[SyncSpeechController] Animation target detached")
    }

    private fun startEventCollection() {
        val source = audioSource ?: return

        eventCollectionJob = scope.launch {
            source.playbackEvents.collect { event ->
                handlePlaybackEvent(event)
            }
        }
    }

    private fun startAudioLevelSync() {
        val source = audioSource ?: return

        audioLevelJob = scope.launch {
            source.audioLevel.collect { level ->
                animationTarget?.updateAudioIntensity(level)
            }
        }
    }

    private fun handlePlaybackEvent(event: SpeechPlaybackEvent) {
        val target = animationTarget

        if (target == null) {
            println("[SyncSpeechController] WARNING: No animation target attached, ignoring event: ${event::class.simpleName}")
            return
        }

        println("[SyncSpeechController] Forwarding event to animation target: ${event::class.simpleName}")

        // Update speaking state
        _isSpeaking.value = when (event) {
            is SpeechPlaybackEvent.Preparing,
            is SpeechPlaybackEvent.Started,
            is SpeechPlaybackEvent.Playing,
            is SpeechPlaybackEvent.Finishing -> true
            is SpeechPlaybackEvent.Ended,
            is SpeechPlaybackEvent.Interrupted,
            is SpeechPlaybackEvent.Idle -> false
        }

        // Forward to animation target
        target.onPlaybackEvent(event)
    }

    override fun speakSynchronized(request: SpeechRequest) {
        val source = audioSource
        val target = animationTarget

        if (source == null) {
            println("[SyncSpeechController] ERROR: Cannot speak: No audio source attached")
            return
        }

        if (target == null) {
            println("[SyncSpeechController] WARNING: No animation target attached, speaking without lip-sync")
        }

        println("[SyncSpeechController] Starting synchronized speech: '${request.text.take(30)}...'")

        // Set emotion on animation target
        target?.setEmotion(request.emotion)

        // Start speech on audio source (events will be forwarded automatically)
        source.speak(request)

        _isSpeaking.value = true
    }

    override fun stopSynchronized() {
        println("[SyncSpeechController] Stopping synchronized speech")

        audioSource?.stop()
        _isSpeaking.value = false
    }

    override fun release() {
        println("[SyncSpeechController] Releasing SynchronizedSpeechController")

        stopSynchronized()
        detachAudioSource()
        detachAnimationTarget()
        scope.cancel()
    }
}
