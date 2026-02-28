#pragma once

#include <memory>
#include <atomic>
#include "OboePlaybackEngine.h"
#include "OboeRecordingEngine.h"
#include "SoftwareAecProcessor.h"

/**
 * Central coordinator for the native audio pipeline.
 *
 * Manages:
 * - Playback engine (Oboe output, 24kHz)
 * - Recording engine (Oboe input, 16kHz)
 * - Shared session ID for AEC binding
 * - Reference signal routing for future software AEC (Phase 3)
 * - Headphone mode awareness
 *
 * This is the single entry point for JNI calls.
 */
class AudioBridge {
public:
    AudioBridge();
    ~AudioBridge();

    // --- Playback ---

    bool startPlayback(int32_t sessionId, int32_t sampleRate);
    void stopPlayback();
    int32_t writePlaybackData(const int16_t* data, int32_t numFrames);
    void flushPlayback();
    void setPlaybackVolume(float volume);
    int32_t getPlaybackSessionId() const;
    int32_t getPlaybackBufferLevelMs() const;
    bool isPlaybackActive() const;

    // --- Recording ---

    bool startRecording(int32_t sessionId, int32_t sampleRate);
    void stopRecording();
    int32_t readRecordingData(int16_t* data, int32_t maxFrames);
    int32_t getRecordingSessionId() const;
    bool isRecordingActive() const;

    // --- Software AEC ---

    void enableSoftwareAec(bool enable);
    bool isSoftwareAecActive() const;

    // --- Configuration ---

    void setHeadphoneMode(bool isHeadphone);
    bool isHeadphoneMode() const;

    // --- Diagnostics ---

    /** Get which audio API Oboe selected for playback. */
    int32_t getPlaybackAudioApi() const;

    /** Get which audio API Oboe selected for recording. */
    int32_t getRecordingAudioApi() const;

private:
    std::unique_ptr<OboePlaybackEngine> playbackEngine_;
    std::unique_ptr<OboeRecordingEngine> recordingEngine_;
    std::unique_ptr<SoftwareAecProcessor> softwareAec_;
    std::atomic<bool> headphoneMode_{false};
};
