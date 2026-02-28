#include <jni.h>
#include <android/log.h>
#include "AudioBridge.h"

#define LOG_TAG "CalmifyAudioJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * JNI bridge between Kotlin (NativeAudioEngine.kt) and C++ (AudioBridge).
 *
 * Package: com.lifo.chat.audio.oboe
 * Class:   NativeAudioEngine
 *
 * The native handle (jlong) is a pointer to AudioBridge, cast via reinterpret_cast.
 * All JNI functions are thread-safe because AudioBridge uses atomic operations
 * and Oboe manages its own callback thread.
 */

static inline AudioBridge* getBridge(jlong handle) {
    return reinterpret_cast<AudioBridge*>(handle);
}

extern "C" {

// --- Lifecycle ---

JNIEXPORT jlong JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeCreate(
    JNIEnv* env, jobject thiz) {
    auto* bridge = new AudioBridge();
    LOGI("Native AudioBridge created: %p", bridge);
    return reinterpret_cast<jlong>(bridge);
}

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    if (bridge) {
        LOGI("Destroying native AudioBridge: %p", bridge);
        delete bridge;
    }
}

// --- Playback ---

JNIEXPORT jboolean JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeStartPlayback(
    JNIEnv* env, jobject thiz, jlong handle, jint sessionId, jint sampleRate) {
    auto* bridge = getBridge(handle);
    if (!bridge) return JNI_FALSE;
    return bridge->startPlayback(sessionId, sampleRate) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeStopPlayback(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    if (bridge) bridge->stopPlayback();
}

JNIEXPORT jint JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeWritePlaybackData(
    JNIEnv* env, jobject thiz, jlong handle, jshortArray data, jint numFrames) {
    auto* bridge = getBridge(handle);
    if (!bridge) return 0;

    jshort* elements = env->GetShortArrayElements(data, nullptr);
    if (!elements) return 0;

    jint written = bridge->writePlaybackData(elements, numFrames);

    // JNI_ABORT: don't copy back changes (read-only from Java side)
    env->ReleaseShortArrayElements(data, elements, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeFlushPlayback(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    if (bridge) bridge->flushPlayback();
}

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeSetVolume(
    JNIEnv* env, jobject thiz, jlong handle, jfloat volume) {
    auto* bridge = getBridge(handle);
    if (bridge) bridge->setPlaybackVolume(volume);
}

JNIEXPORT jint JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeGetPlaybackSessionId(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    return bridge ? bridge->getPlaybackSessionId() : 0;
}

JNIEXPORT jint JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeGetBufferLevelMs(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    return bridge ? bridge->getPlaybackBufferLevelMs() : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeIsPlaybackActive(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    return (bridge && bridge->isPlaybackActive()) ? JNI_TRUE : JNI_FALSE;
}

// --- Recording ---

JNIEXPORT jboolean JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeStartRecording(
    JNIEnv* env, jobject thiz, jlong handle, jint sessionId, jint sampleRate) {
    auto* bridge = getBridge(handle);
    if (!bridge) return JNI_FALSE;
    return bridge->startRecording(sessionId, sampleRate) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeStopRecording(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    if (bridge) bridge->stopRecording();
}

JNIEXPORT jint JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeReadRecordingData(
    JNIEnv* env, jobject thiz, jlong handle, jshortArray buffer, jint maxFrames) {
    auto* bridge = getBridge(handle);
    if (!bridge) return 0;

    jshort* elements = env->GetShortArrayElements(buffer, nullptr);
    if (!elements) return 0;

    jint framesRead = bridge->readRecordingData(elements, maxFrames);

    // 0: copy back changes to Java array (recording data needs to be visible to Kotlin)
    env->ReleaseShortArrayElements(buffer, elements, 0);
    return framesRead;
}

JNIEXPORT jboolean JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeIsRecordingActive(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    return (bridge && bridge->isRecordingActive()) ? JNI_TRUE : JNI_FALSE;
}

// --- Software AEC ---

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeEnableSoftwareAec(
    JNIEnv* env, jobject thiz, jlong handle, jboolean enable) {
    auto* bridge = getBridge(handle);
    if (bridge) bridge->enableSoftwareAec(enable == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeIsSoftwareAecActive(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    return (bridge && bridge->isSoftwareAecActive()) ? JNI_TRUE : JNI_FALSE;
}

// --- Configuration ---

JNIEXPORT void JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeSetHeadphoneMode(
    JNIEnv* env, jobject thiz, jlong handle, jboolean isHeadphone) {
    auto* bridge = getBridge(handle);
    if (bridge) bridge->setHeadphoneMode(isHeadphone == JNI_TRUE);
}

// --- Diagnostics ---

JNIEXPORT jint JNICALL
Java_com_lifo_chat_audio_oboe_NativeAudioEngine_nativeGetPlaybackAudioApi(
    JNIEnv* env, jobject thiz, jlong handle) {
    auto* bridge = getBridge(handle);
    return bridge ? bridge->getPlaybackAudioApi() : -1;
}

}  // extern "C"
