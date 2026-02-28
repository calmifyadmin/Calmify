#include "AudioBridge.h"
#include <android/log.h>

#define LOG_TAG "AudioBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioBridge::AudioBridge()
    : playbackEngine_(std::make_unique<OboePlaybackEngine>())
    , recordingEngine_(std::make_unique<OboeRecordingEngine>())
    , softwareAec_(std::make_unique<SoftwareAecProcessor>(16000, 24000)) {
    LOGI("AudioBridge created (with software AEC fallback ready)");
}

AudioBridge::~AudioBridge() {
    stopPlayback();
    stopRecording();
    LOGI("AudioBridge destroyed");
}

// --- Playback ---

bool AudioBridge::startPlayback(int32_t sessionId, int32_t sampleRate) {
    LOGI("Starting playback: sessionId=%d, sampleRate=%d", sessionId, sampleRate);
    return playbackEngine_->start(sessionId, sampleRate);
}

void AudioBridge::stopPlayback() {
    playbackEngine_->stop();
}

int32_t AudioBridge::writePlaybackData(const int16_t* data, int32_t numFrames) {
    int32_t written = playbackEngine_->writeData(data, numFrames);

    // Feed reference signal to software AEC (resampled internally from 24kHz to 16kHz)
    if (softwareAec_ && softwareAec_->isEnabled()) {
        softwareAec_->feedReferenceSignal(data, numFrames);
    }

    return written;
}

void AudioBridge::flushPlayback() {
    playbackEngine_->flush();
}

void AudioBridge::setPlaybackVolume(float volume) {
    playbackEngine_->setVolume(volume);
}

int32_t AudioBridge::getPlaybackSessionId() const {
    return playbackEngine_->getSessionId();
}

int32_t AudioBridge::getPlaybackBufferLevelMs() const {
    return playbackEngine_->getBufferLevelMs();
}

bool AudioBridge::isPlaybackActive() const {
    return playbackEngine_->isPlaying();
}

// --- Recording ---

bool AudioBridge::startRecording(int32_t sessionId, int32_t sampleRate) {
    LOGI("Starting recording: sessionId=%d, sampleRate=%d", sessionId, sampleRate);
    return recordingEngine_->start(sessionId, sampleRate);
}

void AudioBridge::stopRecording() {
    recordingEngine_->stop();
}

int32_t AudioBridge::readRecordingData(int16_t* data, int32_t maxFrames) {
    int32_t framesRead = recordingEngine_->readData(data, maxFrames);

    // Apply software AEC to captured audio (subtracts echo using reference signal)
    if (framesRead > 0 && softwareAec_ && softwareAec_->isEnabled()) {
        softwareAec_->processCapture(data, framesRead);
    }

    return framesRead;
}

int32_t AudioBridge::getRecordingSessionId() const {
    return recordingEngine_->getSessionId();
}

bool AudioBridge::isRecordingActive() const {
    return recordingEngine_->isRecording();
}

// --- Software AEC ---

void AudioBridge::enableSoftwareAec(bool enable) {
    if (softwareAec_) {
        softwareAec_->setEnabled(enable);
        LOGI("Software AEC: %s", enable ? "ENABLED" : "DISABLED");
    }
}

bool AudioBridge::isSoftwareAecActive() const {
    return softwareAec_ && softwareAec_->isEnabled();
}

// --- Configuration ---

void AudioBridge::setHeadphoneMode(bool isHeadphone) {
    headphoneMode_.store(isHeadphone, std::memory_order_relaxed);
    LOGI("Headphone mode: %s", isHeadphone ? "ON (AEC bypassed)" : "OFF (AEC active)");
}

bool AudioBridge::isHeadphoneMode() const {
    return headphoneMode_.load(std::memory_order_relaxed);
}

// --- Diagnostics ---

int32_t AudioBridge::getPlaybackAudioApi() const {
    return static_cast<int32_t>(playbackEngine_->getAudioApi());
}

int32_t AudioBridge::getRecordingAudioApi() const {
    // Will be available once recording engine is started
    return 0;  // TODO: expose from recording engine
}
