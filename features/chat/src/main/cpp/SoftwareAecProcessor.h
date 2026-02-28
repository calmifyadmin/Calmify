#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>
#include <mutex>

/**
 * Software AEC processor using WebRTC Audio Processing Module (APM).
 *
 * This is the fallback for devices where hardware AEC is non-functional.
 * Uses WebRTC's AEC3 algorithm — the same echo canceller used by Chrome,
 * PulseAudio, and PipeWire.
 *
 * ## Signal Flow
 * ```
 * Speaker output (24kHz) → feedReferenceSignal() → [resample to 16kHz] → reference buffer
 *                                                                              ↓
 * Mic input (16kHz) → processCapture() → [AEC3 subtracts echo] → clean audio output
 * ```
 *
 * ## Phase 3 Implementation Status
 * This file contains the interface and a passthrough stub implementation.
 * Full WebRTC APM integration requires either:
 * (a) Pre-built libwebrtc_audio_processing.so (~2MB per ABI)
 * (b) Building AEC3 from WebRTC source (~15 .cc files)
 *
 * The stub allows the rest of the pipeline to compile and function.
 * When WebRTC APM is integrated, only the implementation changes — the
 * interface stays the same.
 */
class SoftwareAecProcessor {
public:
    SoftwareAecProcessor(int32_t captureSampleRate, int32_t renderSampleRate);
    ~SoftwareAecProcessor();

    /**
     * Feed reference signal (what the speaker is playing).
     * The audio is resampled internally from renderSampleRate to captureSampleRate.
     */
    void feedReferenceSignal(const int16_t* data, int32_t numFrames);

    /**
     * Process captured mic audio — removes echo using the reference signal.
     * Audio is modified in-place.
     */
    void processCapture(int16_t* data, int32_t numFrames);

    /** Enable or disable software AEC processing. */
    void setEnabled(bool enabled);
    bool isEnabled() const;

    /** Get the estimated echo return loss enhancement (ERLE) in dB. */
    float getErleDb() const;

private:
    int32_t captureSampleRate_;  // Mic sample rate (typically 16000)
    int32_t renderSampleRate_;   // Speaker sample rate (typically 24000)
    std::atomic<bool> enabled_{false};

    // Reference signal buffer (at capture sample rate, after resampling)
    static constexpr int32_t REF_BUFFER_SIZE = 32000;  // 2 seconds at 16kHz
    std::vector<int16_t> referenceBuffer_;
    int32_t refWritePos_ = 0;
    int32_t refAvailable_ = 0;
    std::mutex refMutex_;

    // WebRTC APM instance (Phase 3 — nullptr until WebRTC APM is integrated)
    // std::unique_ptr<webrtc::AudioProcessing> apm_;

    // Simple resampling (24kHz → 16kHz)
    void resampleAndStore(const int16_t* data, int32_t numFrames);
};
