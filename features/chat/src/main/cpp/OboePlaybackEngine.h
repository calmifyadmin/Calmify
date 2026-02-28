#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include "RingBuffer.h"

/**
 * Low-latency audio playback engine using Oboe (AAudio/OpenSL ES).
 *
 * Oboe provides:
 * - AAudio on API 27+ (lowest latency, exclusive mode)
 * - Automatic fallback to OpenSL ES on API 26
 * - Better HAL AEC integration than Java AudioTrack
 *
 * Uses pull-model: Oboe's callback requests audio from our ring buffer.
 * The JNI layer pushes decoded PCM into the ring buffer from Kotlin.
 *
 * Audio flow:
 *   Kotlin → JNI → writePlaybackData() → RingBuffer → onAudioReady() → Speaker
 */
class OboePlaybackEngine : public oboe::AudioStreamDataCallback,
                           public oboe::AudioStreamErrorCallback {
public:
    OboePlaybackEngine();
    ~OboePlaybackEngine();

    /**
     * Start the playback stream.
     * @param sessionId Audio session ID for AEC binding (shared with recording)
     * @param sampleRate Output sample rate (typically 24000 Hz)
     * @return true if stream opened successfully
     */
    bool start(int32_t sessionId, int32_t sampleRate);

    /** Stop and close the stream. */
    void stop();

    /**
     * Push audio data into the playback ring buffer.
     * Called from JNI thread when Kotlin provides decoded PCM.
     * @return Number of frames actually written.
     */
    int32_t writeData(const int16_t* data, int32_t numFrames);

    /** Flush the ring buffer (e.g., on barge-in). */
    void flush();

    /** Set playback volume (0.0 - 1.0). Thread-safe. */
    void setVolume(float volume);

    /** Get the Oboe stream's session ID (for AEC linking). */
    int32_t getSessionId() const;

    /** Get current buffer level in milliseconds. */
    int32_t getBufferLevelMs() const;

    /** Check if the stream is currently active. */
    bool isPlaying() const;

    /** Get which audio API Oboe selected (AAudio or OpenSL ES). */
    oboe::AudioApi getAudioApi() const;

    // --- Oboe callbacks ---

    /** Called by Oboe when it needs audio data (pull model). */
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    /** Called by Oboe on stream error (e.g., device disconnect). */
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result result) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) override;

private:
    static constexpr int32_t RING_BUFFER_SECONDS = 3;
    static constexpr int32_t CHANNEL_COUNT = 1;  // Mono

    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<RingBuffer<int16_t>> ringBuffer_;

    int32_t sampleRate_ = 24000;
    int32_t sessionId_ = 0;
    std::atomic<float> volume_{1.0f};
    std::atomic<bool> isPlaying_{false};

    bool openStream(int32_t sessionId, int32_t sampleRate);
};
