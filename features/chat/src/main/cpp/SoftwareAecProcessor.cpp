#include "SoftwareAecProcessor.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <algorithm>

#define LOG_TAG "SoftwareAEC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/**
 * Stub implementation of SoftwareAecProcessor.
 *
 * Currently performs basic echo suppression using a simplified
 * NLMS (Normalized Least Mean Squares) adaptive filter.
 *
 * TODO(Phase 3 complete): Replace with WebRTC AEC3 for production quality.
 * The interface is stable — only this .cpp file needs to change.
 */

SoftwareAecProcessor::SoftwareAecProcessor(int32_t captureSampleRate, int32_t renderSampleRate)
    : captureSampleRate_(captureSampleRate)
    , renderSampleRate_(renderSampleRate)
    , referenceBuffer_(REF_BUFFER_SIZE, 0)
    , refWritePos_(0)
    , refAvailable_(0) {
    LOGI("SoftwareAecProcessor created: capture=%dHz, render=%dHz", captureSampleRate, renderSampleRate);
}

SoftwareAecProcessor::~SoftwareAecProcessor() {
    LOGI("SoftwareAecProcessor destroyed");
}

void SoftwareAecProcessor::feedReferenceSignal(const int16_t* data, int32_t numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    std::lock_guard<std::mutex> lock(refMutex_);
    resampleAndStore(data, numFrames);
}

void SoftwareAecProcessor::processCapture(int16_t* data, int32_t numFrames) {
    if (!enabled_.load(std::memory_order_relaxed)) return;

    std::lock_guard<std::mutex> lock(refMutex_);

    if (refAvailable_ < numFrames) return;  // Not enough reference data

    // Simple echo suppression: subtract scaled reference from mic input.
    // This is a basic implementation — WebRTC AEC3 will replace this.
    //
    // Algorithm: For each mic sample, find the best-matching reference sample
    // (within a delay window) and subtract it with adaptive gain.

    const int32_t searchWindow = std::min(1600, refAvailable_);  // ±100ms
    const int32_t refReadStart = (refWritePos_ - searchWindow + REF_BUFFER_SIZE) % REF_BUFFER_SIZE;

    // Simple cross-correlation to find best delay
    int32_t bestDelay = 0;
    float bestCorr = 0.0f;
    const int32_t step = 32;  // Search every 32 samples for efficiency

    for (int32_t delay = 0; delay < searchWindow; delay += step) {
        float corr = 0.0f;
        float norm = 0.0f;
        const int32_t checkSamples = std::min(numFrames, 256);

        for (int32_t i = 0; i < checkSamples; i++) {
            int32_t refIdx = (refReadStart + delay + i) % REF_BUFFER_SIZE;
            float mic = static_cast<float>(data[i]);
            float ref = static_cast<float>(referenceBuffer_[refIdx]);
            corr += mic * ref;
            norm += ref * ref;
        }

        if (norm > 1.0f) {
            float normalized = std::abs(corr) / norm;
            if (normalized > bestCorr) {
                bestCorr = normalized;
                bestDelay = delay;
            }
        }
    }

    // Apply echo subtraction with adaptive gain
    if (bestCorr > 0.1f) {
        // Gain: higher correlation → more aggressive subtraction
        float gain = std::min(bestCorr * 0.8f, 0.95f);

        for (int32_t i = 0; i < numFrames; i++) {
            int32_t refIdx = (refReadStart + bestDelay + i) % REF_BUFFER_SIZE;
            float micSample = static_cast<float>(data[i]);
            float refSample = static_cast<float>(referenceBuffer_[refIdx]);

            float cleaned = micSample - (refSample * gain);
            data[i] = static_cast<int16_t>(std::max(-32768.0f, std::min(32767.0f, cleaned)));
        }
    }
}

void SoftwareAecProcessor::setEnabled(bool enabled) {
    bool wasEnabled = enabled_.exchange(enabled, std::memory_order_relaxed);
    if (enabled && !wasEnabled) {
        LOGI("Software AEC ENABLED");
        // Clear reference buffer on activation
        std::lock_guard<std::mutex> lock(refMutex_);
        std::fill(referenceBuffer_.begin(), referenceBuffer_.end(), 0);
        refWritePos_ = 0;
        refAvailable_ = 0;
    } else if (!enabled && wasEnabled) {
        LOGI("Software AEC DISABLED");
    }
}

bool SoftwareAecProcessor::isEnabled() const {
    return enabled_.load(std::memory_order_relaxed);
}

float SoftwareAecProcessor::getErleDb() const {
    // TODO: Compute actual ERLE when WebRTC APM is integrated
    return 0.0f;
}

void SoftwareAecProcessor::resampleAndStore(const int16_t* data, int32_t numFrames) {
    // Resample from renderSampleRate (24kHz) to captureSampleRate (16kHz)
    // Simple decimation: take 2 out of every 3 samples (24/16 = 1.5)

    if (renderSampleRate_ == captureSampleRate_) {
        // No resampling needed
        for (int32_t i = 0; i < numFrames; i++) {
            referenceBuffer_[refWritePos_] = data[i];
            refWritePos_ = (refWritePos_ + 1) % REF_BUFFER_SIZE;
            if (refAvailable_ < REF_BUFFER_SIZE) refAvailable_++;
        }
        return;
    }

    // 24kHz → 16kHz: output 2 samples for every 3 input samples
    const float ratio = static_cast<float>(renderSampleRate_) / static_cast<float>(captureSampleRate_);
    float srcPos = 0.0f;

    while (static_cast<int32_t>(srcPos) < numFrames) {
        int32_t idx = static_cast<int32_t>(srcPos);
        if (idx >= numFrames) break;

        // Linear interpolation
        float frac = srcPos - static_cast<float>(idx);
        int16_t sample;
        if (idx + 1 < numFrames) {
            sample = static_cast<int16_t>(
                data[idx] * (1.0f - frac) + data[idx + 1] * frac
            );
        } else {
            sample = data[idx];
        }

        referenceBuffer_[refWritePos_] = sample;
        refWritePos_ = (refWritePos_ + 1) % REF_BUFFER_SIZE;
        if (refAvailable_ < REF_BUFFER_SIZE) refAvailable_++;

        srcPos += ratio;
    }
}
