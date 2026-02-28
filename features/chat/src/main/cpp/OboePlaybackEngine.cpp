#include "OboePlaybackEngine.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "OboePlayback"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

OboePlaybackEngine::OboePlaybackEngine() = default;

OboePlaybackEngine::~OboePlaybackEngine() {
    stop();
}

bool OboePlaybackEngine::start(int32_t sessionId, int32_t sampleRate) {
    stop();  // Clean up any existing stream

    sampleRate_ = sampleRate;
    sessionId_ = sessionId;

    // Create ring buffer: 3 seconds of audio at the given sample rate
    const int32_t bufferCapacity = sampleRate * RING_BUFFER_SECONDS * CHANNEL_COUNT;
    ringBuffer_ = std::make_unique<RingBuffer<int16_t>>(bufferCapacity);

    return openStream(sessionId, sampleRate);
}

bool OboePlaybackEngine::openStream(int32_t sessionId, int32_t sampleRate) {
    oboe::AudioStreamBuilder builder;

    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(CHANNEL_COUNT)
           ->setSampleRate(sampleRate)
           ->setUsage(oboe::Usage::Media)
           ->setContentType(oboe::ContentType::Speech)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    // Set session ID for AEC binding (shared with recording stream)
    if (sessionId > 0) {
        builder.setSessionId(static_cast<oboe::SessionId>(sessionId));
        LOGI("Session ID set to %d for AEC binding", sessionId);
    }

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(result));

        // Retry with Shared mode if Exclusive failed
        if (builder.getSharingMode() == oboe::SharingMode::Exclusive) {
            LOGW("Retrying with SharingMode::Shared");
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(stream_);
            if (result != oboe::Result::OK) {
                LOGE("Failed to open shared output stream: %s", oboe::convertToText(result));
                return false;
            }
        } else {
            return false;
        }
    }

    // Log stream properties
    LOGI("Output stream opened:");
    LOGI("  API: %s", oboe::convertToText(stream_->getAudioApi()));
    LOGI("  Sample rate: %d Hz", stream_->getSampleRate());
    LOGI("  Frames per burst: %d", stream_->getFramesPerBurst());
    LOGI("  Buffer capacity: %d frames", stream_->getBufferCapacityInFrames());
    LOGI("  Sharing mode: %s", oboe::convertToText(stream_->getSharingMode()));
    LOGI("  Performance mode: %s", oboe::convertToText(stream_->getPerformanceMode()));
    LOGI("  Session ID: %d", stream_->getSessionId());

    // Start playback
    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start output stream: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }

    isPlaying_.store(true, std::memory_order_release);
    LOGI("Playback started successfully");
    return true;
}

void OboePlaybackEngine::stop() {
    isPlaying_.store(false, std::memory_order_release);

    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
        LOGI("Playback stopped");
    }

    if (ringBuffer_) {
        ringBuffer_->flush();
    }
}

int32_t OboePlaybackEngine::writeData(const int16_t* data, int32_t numFrames) {
    if (!ringBuffer_ || !isPlaying_.load(std::memory_order_acquire)) {
        return 0;
    }
    return ringBuffer_->write(data, numFrames);
}

void OboePlaybackEngine::flush() {
    if (ringBuffer_) {
        ringBuffer_->flush();
        LOGI("Ring buffer flushed");
    }
}

void OboePlaybackEngine::setVolume(float volume) {
    volume_.store(std::max(0.0f, std::min(1.0f, volume)), std::memory_order_relaxed);
}

int32_t OboePlaybackEngine::getSessionId() const {
    if (stream_) {
        return stream_->getSessionId();
    }
    return sessionId_;
}

int32_t OboePlaybackEngine::getBufferLevelMs() const {
    if (!ringBuffer_) return 0;
    return ringBuffer_->getBufferLevelMs(sampleRate_);
}

bool OboePlaybackEngine::isPlaying() const {
    return isPlaying_.load(std::memory_order_acquire);
}

oboe::AudioApi OboePlaybackEngine::getAudioApi() const {
    if (stream_) {
        return stream_->getAudioApi();
    }
    return oboe::AudioApi::Unspecified;
}

// --- Oboe Audio Callback (runs on high-priority audio thread) ---

oboe::DataCallbackResult OboePlaybackEngine::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    auto* output = static_cast<int16_t*>(audioData);

    if (!ringBuffer_) {
        std::memset(output, 0, numFrames * CHANNEL_COUNT * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
    }

    const int32_t framesRead = ringBuffer_->read(output, numFrames * CHANNEL_COUNT);

    // Fill remainder with silence if we didn't have enough data
    if (framesRead < numFrames * CHANNEL_COUNT) {
        std::memset(output + framesRead, 0,
                    (numFrames * CHANNEL_COUNT - framesRead) * sizeof(int16_t));
    }

    // Apply volume
    const float vol = volume_.load(std::memory_order_relaxed);
    if (vol < 0.999f) {
        const int32_t totalSamples = numFrames * CHANNEL_COUNT;
        for (int32_t i = 0; i < totalSamples; ++i) {
            output[i] = static_cast<int16_t>(output[i] * vol);
        }
    }

    return oboe::DataCallbackResult::Continue;
}

// --- Oboe Error Callbacks ---

void OboePlaybackEngine::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result result) {
    LOGW("Output stream error before close: %s", oboe::convertToText(result));
}

void OboePlaybackEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) {
    LOGW("Output stream error after close: %s, attempting restart", oboe::convertToText(result));

    // Attempt to reopen the stream (e.g., after device disconnect/reconnect)
    if (isPlaying_.load(std::memory_order_acquire)) {
        if (openStream(sessionId_, sampleRate_)) {
            LOGI("Output stream successfully restarted");
        } else {
            LOGE("Failed to restart output stream");
            isPlaying_.store(false, std::memory_order_release);
        }
    }
}
