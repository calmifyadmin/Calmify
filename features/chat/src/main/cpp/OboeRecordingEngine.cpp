#include "OboeRecordingEngine.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "OboeRecording"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

OboeRecordingEngine::OboeRecordingEngine() = default;

OboeRecordingEngine::~OboeRecordingEngine() {
    stop();
}

bool OboeRecordingEngine::start(int32_t sessionId, int32_t sampleRate) {
    stop();

    sampleRate_ = sampleRate;
    sessionId_ = sessionId;

    const int32_t bufferCapacity = sampleRate * RING_BUFFER_SECONDS * CHANNEL_COUNT;
    ringBuffer_ = std::make_unique<RingBuffer<int16_t>>(bufferCapacity);

    return openStream(sessionId, sampleRate);
}

bool OboeRecordingEngine::openStream(int32_t sessionId, int32_t sampleRate) {
    oboe::AudioStreamBuilder builder;

    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(CHANNEL_COUNT)
           ->setSampleRate(sampleRate)
           ->setInputPreset(oboe::InputPreset::VoiceCommunication)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    // Set session ID for AEC binding (shared with playback stream)
    if (sessionId > 0) {
        builder.setSessionId(static_cast<oboe::SessionId>(sessionId));
        LOGI("Session ID set to %d for AEC binding", sessionId);
    }

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(result));

        // Retry with Shared mode
        if (builder.getSharingMode() == oboe::SharingMode::Exclusive) {
            LOGW("Retrying with SharingMode::Shared");
            builder.setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(stream_);
            if (result != oboe::Result::OK) {
                LOGE("Failed to open shared input stream: %s", oboe::convertToText(result));
                return false;
            }
        } else {
            return false;
        }
    }

    LOGI("Input stream opened:");
    LOGI("  API: %s", oboe::convertToText(stream_->getAudioApi()));
    LOGI("  Sample rate: %d Hz", stream_->getSampleRate());
    LOGI("  Frames per burst: %d", stream_->getFramesPerBurst());
    LOGI("  Buffer capacity: %d frames", stream_->getBufferCapacityInFrames());
    LOGI("  Sharing mode: %s", oboe::convertToText(stream_->getSharingMode()));
    LOGI("  Session ID: %d", stream_->getSessionId());

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start input stream: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }

    isRecording_.store(true, std::memory_order_release);
    LOGI("Recording started successfully");
    return true;
}

void OboeRecordingEngine::stop() {
    isRecording_.store(false, std::memory_order_release);

    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
        LOGI("Recording stopped");
    }

    if (ringBuffer_) {
        ringBuffer_->flush();
    }
}

int32_t OboeRecordingEngine::readData(int16_t* data, int32_t maxFrames) {
    if (!ringBuffer_ || !isRecording_.load(std::memory_order_acquire)) {
        return 0;
    }
    return ringBuffer_->read(data, maxFrames);
}

int32_t OboeRecordingEngine::getSessionId() const {
    if (stream_) {
        return stream_->getSessionId();
    }
    return sessionId_;
}

bool OboeRecordingEngine::isRecording() const {
    return isRecording_.load(std::memory_order_acquire);
}

// --- Oboe Audio Callback (runs on high-priority audio thread) ---

oboe::DataCallbackResult OboeRecordingEngine::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    if (!ringBuffer_) {
        return oboe::DataCallbackResult::Continue;
    }

    auto* input = static_cast<const int16_t*>(audioData);
    ringBuffer_->write(input, numFrames * CHANNEL_COUNT);

    return oboe::DataCallbackResult::Continue;
}

// --- Oboe Error Callbacks ---

void OboeRecordingEngine::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result result) {
    LOGW("Input stream error before close: %s", oboe::convertToText(result));
}

void OboeRecordingEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) {
    LOGW("Input stream error after close: %s, attempting restart", oboe::convertToText(result));

    if (isRecording_.load(std::memory_order_acquire)) {
        if (openStream(sessionId_, sampleRate_)) {
            LOGI("Input stream successfully restarted");
        } else {
            LOGE("Failed to restart input stream");
            isRecording_.store(false, std::memory_order_release);
        }
    }
}
