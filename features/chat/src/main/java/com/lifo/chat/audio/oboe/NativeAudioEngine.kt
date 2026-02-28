package com.lifo.chat.audio.oboe

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin wrapper for the Oboe C++ audio engine (via JNI).
 *
 * Provides low-latency audio playback and recording using AAudio/OpenSL ES
 * through Google's Oboe library. Falls back gracefully if native initialization fails.
 *
 * ## Usage
 * ```
 * val engine = NativeAudioEngine()
 * if (engine.isAvailable) {
 *     engine.startPlayback(sessionId, 24000)
 *     engine.writePlaybackData(shortArray, numFrames)
 *     engine.stopPlayback()
 *     engine.release()
 * }
 * ```
 *
 * ## Thread Safety
 * All methods are thread-safe. The C++ layer uses atomic operations and
 * Oboe manages its own high-priority callback thread.
 */
@Singleton
class NativeAudioEngine @Inject constructor() {

    private var nativeHandle: Long = 0L

    /**
     * Whether the native library loaded successfully.
     * If false, the caller should fall back to Java AudioTrack/AudioRecord.
     */
    var isAvailable: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("calmify_audio")
            nativeHandle = nativeCreate()
            isAvailable = nativeHandle != 0L
            if (isAvailable) {
                println("[NativeAudioEngine] Oboe engine initialized (handle=$nativeHandle)")
            } else {
                println("[NativeAudioEngine] WARNING: nativeCreate returned null handle")
            }
        } catch (e: UnsatisfiedLinkError) {
            println("[NativeAudioEngine] WARNING: Native library not available: ${e.message}")
            println("[NativeAudioEngine] Falling back to Java AudioTrack/AudioRecord")
            isAvailable = false
        }
    }

    // --- Playback ---

    /**
     * Start the Oboe playback stream.
     * @param sessionId Shared audio session ID for AEC binding
     * @param sampleRate Output sample rate (default 24000 Hz)
     * @return true if stream opened successfully
     */
    fun startPlayback(sessionId: Int, sampleRate: Int = 24000): Boolean {
        if (!isAvailable) return false
        return nativeStartPlayback(nativeHandle, sessionId, sampleRate)
    }

    fun stopPlayback() {
        if (!isAvailable) return
        nativeStopPlayback(nativeHandle)
    }

    /**
     * Push decoded PCM data into the playback ring buffer.
     * Oboe's callback thread pulls from this buffer for speaker output.
     * @return Number of frames actually written
     */
    fun writePlaybackData(data: ShortArray, numFrames: Int): Int {
        if (!isAvailable) return 0
        return nativeWritePlaybackData(nativeHandle, data, numFrames)
    }

    /** Flush the playback buffer (e.g., on barge-in). */
    fun flushPlayback() {
        if (!isAvailable) return
        nativeFlushPlayback(nativeHandle)
    }

    /** Set playback volume (0.0 - 1.0). */
    fun setVolume(volume: Float) {
        if (!isAvailable) return
        nativeSetVolume(nativeHandle, volume)
    }

    /** Get the Oboe stream's session ID (for AEC linking). */
    fun getPlaybackSessionId(): Int {
        if (!isAvailable) return 0
        return nativeGetPlaybackSessionId(nativeHandle)
    }

    /** Get current playback buffer level in milliseconds. */
    fun getBufferLevelMs(): Int {
        if (!isAvailable) return 0
        return nativeGetBufferLevelMs(nativeHandle)
    }

    fun isPlaybackActive(): Boolean {
        if (!isAvailable) return false
        return nativeIsPlaybackActive(nativeHandle)
    }

    // --- Recording ---

    /**
     * Start the Oboe recording stream.
     * Uses InputPreset::VoiceCommunication for HAL AEC preprocessing.
     * @param sessionId Shared audio session ID for AEC binding
     * @param sampleRate Input sample rate (default 16000 Hz)
     * @return true if stream opened successfully
     */
    fun startRecording(sessionId: Int, sampleRate: Int = 16000): Boolean {
        if (!isAvailable) return false
        return nativeStartRecording(nativeHandle, sessionId, sampleRate)
    }

    fun stopRecording() {
        if (!isAvailable) return
        nativeStopRecording(nativeHandle)
    }

    /**
     * Read recorded audio from the native ring buffer.
     * @return Number of frames actually read
     */
    fun readRecordingData(buffer: ShortArray, maxFrames: Int): Int {
        if (!isAvailable) return 0
        return nativeReadRecordingData(nativeHandle, buffer, maxFrames)
    }

    fun isRecordingActive(): Boolean {
        if (!isAvailable) return false
        return nativeIsRecordingActive(nativeHandle)
    }

    // --- Software AEC ---

    /**
     * Enable/disable software AEC (NLMS adaptive filter, future: WebRTC AEC3).
     * Call when [AecReliabilityDetector] determines hardware AEC is failing.
     */
    fun enableSoftwareAec(enable: Boolean) {
        if (!isAvailable) return
        nativeEnableSoftwareAec(nativeHandle, enable)
        println("[NativeAudioEngine] Software AEC: ${if (enable) "ENABLED" else "DISABLED"}")
    }

    fun isSoftwareAecActive(): Boolean {
        if (!isAvailable) return false
        return nativeIsSoftwareAecActive(nativeHandle)
    }

    // --- Configuration ---

    fun setHeadphoneMode(isHeadphone: Boolean) {
        if (!isAvailable) return
        nativeSetHeadphoneMode(nativeHandle, isHeadphone)
    }

    // --- Diagnostics ---

    /**
     * Get which audio API Oboe selected for playback.
     * 0 = Unspecified, 1 = OpenSL ES, 2 = AAudio
     */
    fun getPlaybackAudioApi(): Int {
        if (!isAvailable) return -1
        return nativeGetPlaybackAudioApi(nativeHandle)
    }

    fun getPlaybackAudioApiName(): String {
        return when (getPlaybackAudioApi()) {
            0 -> "Unspecified"
            1 -> "OpenSL ES"
            2 -> "AAudio"
            else -> "Unknown"
        }
    }

    /** Release all native resources. Must be called when no longer needed. */
    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
            isAvailable = false
            println("[NativeAudioEngine] Released")
        }
    }

    // --- Native methods (JNI) ---

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)

    // Playback
    private external fun nativeStartPlayback(handle: Long, sessionId: Int, sampleRate: Int): Boolean
    private external fun nativeStopPlayback(handle: Long)
    private external fun nativeWritePlaybackData(handle: Long, data: ShortArray, numFrames: Int): Int
    private external fun nativeFlushPlayback(handle: Long)
    private external fun nativeSetVolume(handle: Long, volume: Float)
    private external fun nativeGetPlaybackSessionId(handle: Long): Int
    private external fun nativeGetBufferLevelMs(handle: Long): Int
    private external fun nativeIsPlaybackActive(handle: Long): Boolean

    // Recording
    private external fun nativeStartRecording(handle: Long, sessionId: Int, sampleRate: Int): Boolean
    private external fun nativeStopRecording(handle: Long)
    private external fun nativeReadRecordingData(handle: Long, buffer: ShortArray, maxFrames: Int): Int
    private external fun nativeIsRecordingActive(handle: Long): Boolean

    // Software AEC
    private external fun nativeEnableSoftwareAec(handle: Long, enable: Boolean)
    private external fun nativeIsSoftwareAecActive(handle: Long): Boolean

    // Configuration
    private external fun nativeSetHeadphoneMode(handle: Long, isHeadphone: Boolean)

    // Diagnostics
    private external fun nativeGetPlaybackAudioApi(handle: Long): Int
}
