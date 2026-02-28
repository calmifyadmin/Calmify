#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include "RingBuffer.h"

/**
 * Low-latency audio recording engine using Oboe (AAudio/OpenSL ES).
 *
 * Uses InputPreset::VoiceCommunication to activate HAL preprocessing
 * (AEC, AGC, NS) — equivalent to Java's MediaRecorder.AudioSource.VOICE_COMMUNICATION.
 *
 * Audio flow:
 *   Microphone → HAL AEC → onAudioReady() → RingBuffer → readData() → JNI → Kotlin
 */
class OboeRecordingEngine : public oboe::AudioStreamDataCallback,
                            public oboe::AudioStreamErrorCallback {
public:
    OboeRecordingEngine();
    ~OboeRecordingEngine();

    /**
     * Start the recording stream.
     * @param sessionId Audio session ID for AEC binding (shared with playback)
     * @param sampleRate Input sample rate (typically 16000 Hz)
     * @return true if stream opened successfully
     */
    bool start(int32_t sessionId, int32_t sampleRate);

    /** Stop and close the stream. */
    void stop();

    /**
     * Read recorded audio from the ring buffer.
     * Called from JNI thread when Kotlin needs audio data.
     * @return Number of frames actually read.
     */
    int32_t readData(int16_t* data, int32_t maxFrames);

    /** Get the Oboe stream's session ID. */
    int32_t getSessionId() const;

    /** Check if the stream is currently recording. */
    bool isRecording() const;

    // --- Oboe callbacks ---
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result result) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) override;

private:
    static constexpr int32_t RING_BUFFER_SECONDS = 2;  // 2 sec recording buffer
    static constexpr int32_t CHANNEL_COUNT = 1;  // Mono

    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<RingBuffer<int16_t>> ringBuffer_;

    int32_t sampleRate_ = 16000;
    int32_t sessionId_ = 0;
    std::atomic<bool> isRecording_{false};

    bool openStream(int32_t sessionId, int32_t sampleRate);
};
