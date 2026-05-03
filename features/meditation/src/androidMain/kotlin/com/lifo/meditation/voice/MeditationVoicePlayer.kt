package com.lifo.meditation.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.lifo.util.model.MeditationAudio
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Plays meditation voice utterances from APK assets, gated on
 * [MeditationAudio.VOICE]. Resolves to
 * `assets/meditation/voice/{lang}/{utterance.assetKey}.mp3`.
 *
 * Concurrency model:
 * - **One utterance at a time** — calling [play] while a prior utterance is in
 *   flight cancels the prior playback (last-call-wins). Avoids overlap that
 *   would muddy the audio.
 * - Playback runs on [Dispatchers.IO]. The caller (entry point) is a
 *   `LaunchedEffect` on the main scope; we don't block it.
 * - [stop] cancels in-flight playback synchronously.
 *
 * Asset resolution falls back EN if the requested locale's asset is missing —
 * keeps voice working for langs we haven't generated yet (degraded but functional).
 *
 * Audio focus uses [AudioAttributes.USAGE_ASSISTANCE_SONIFICATION] +
 * [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK] which auto-ducks the
 * meditation chime (and any other media) for the duration of the utterance.
 *
 * Lifecycle: instantiate on session entry, call [release] on session exit.
 */
class MeditationVoicePlayer(
    private val context: Context,
) {
    private val tag = "MeditationVoice"
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var currentJob: Job? = null
    @Volatile private var currentPlayer: MediaPlayer? = null
    @Volatile private var currentFocusRequest: AudioFocusRequest? = null

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Speak one utterance. Cancels any in-flight playback first.
     * No-op if the asset for the requested locale (and EN fallback) is missing.
     */
    fun play(utterance: VoiceUtterance, locale: String) {
        currentJob?.cancel()
        currentJob = playbackScope.launch {
            mutex.withLock {
                val assetPath = resolveAsset(locale, utterance.assetKey) ?: run {
                    Log.d(tag, "no asset for ${utterance.assetKey} (locale=$locale or en fallback)")
                    return@withLock
                }
                playInternal(assetPath)
            }
        }
    }

    /** Stops any in-flight playback and abandons audio focus. */
    fun stop() {
        currentJob?.cancel()
        releaseCurrentPlayer()
    }

    /** Permanently releases all resources. Call on session exit. */
    fun release() {
        stop()
        playbackScope.cancel()
    }

    /**
     * Returns the path of the first asset that exists, or null if neither does.
     * Tries `meditation/voice/{locale}/{key}.mp3` then EN fallback.
     */
    private fun resolveAsset(locale: String, key: String): String? {
        val candidates = listOf(
            "meditation/voice/$locale/$key.mp3",
            "meditation/voice/en/$key.mp3",
        )
        return candidates.firstOrNull { exists(it) }
    }

    private fun exists(assetPath: String): Boolean {
        return try {
            context.assets.openFd(assetPath).use { true }
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun playInternal(assetPath: String) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { /* focus changes during ducking are tolerated */ }
            .build()

        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        currentFocusRequest = focusRequest

        try {
            withContext(Dispatchers.IO) {
                val player = MediaPlayer().apply { setAudioAttributes(attrs) }
                currentPlayer = player

                context.assets.openFd(assetPath).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    player.prepare()
                }

                suspendCancellableCoroutine<Unit> { cont ->
                    val onComplete = MediaPlayer.OnCompletionListener {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    val onError = MediaPlayer.OnErrorListener { _, what, extra ->
                        Log.w(tag, "MediaPlayer error what=$what extra=$extra path=$assetPath")
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    player.setOnCompletionListener(onComplete)
                    player.setOnErrorListener(onError)

                    cont.invokeOnCancellation {
                        try { player.stop() } catch (_: IllegalStateException) {}
                    }
                    player.start()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "playback failed for $assetPath", e)
        } finally {
            releaseCurrentPlayer()
            if (focusGranted) {
                try { audioManager.abandonAudioFocusRequest(focusRequest) } catch (_: Exception) {}
            }
            if (currentFocusRequest === focusRequest) currentFocusRequest = null
        }
    }

    private fun releaseCurrentPlayer() {
        currentPlayer?.let { p ->
            try { p.stop() } catch (_: IllegalStateException) {}
            try { p.release() } catch (_: IllegalStateException) {}
        }
        currentPlayer = null
    }
}
