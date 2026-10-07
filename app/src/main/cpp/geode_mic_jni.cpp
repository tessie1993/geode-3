#include <jni.h>

#include <algorithm>
#include <array>

#include "api/geode_api.h"

namespace {

// One blocking read is at most this many frames; the buffer lives on the reader thread's stack.
constexpr jint kMaxReadFrames = 4096;

geode_mic* micOf(jlong handle) { return reinterpret_cast<geode_mic*>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micCreate(JNIEnv*, jobject, jboolean preferUnprocessed) {
    return reinterpret_cast<jlong>(geode_mic_create(preferUnprocessed == JNI_TRUE ? 1 : 0));
}

JNIEXPORT jboolean JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micStart(JNIEnv*, jobject, jlong handle) {
    return geode_mic_start(micOf(handle)) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micRead(JNIEnv* env, jobject, jlong handle, jfloatArray dst, jint maxFrames,
                                                 jlong timeoutNanos) {
    if (!dst) return 0;
    const jint limit = std::min({maxFrames, env->GetArrayLength(dst), kMaxReadFrames});
    if (limit <= 0) return 0;
    std::array<float, kMaxReadFrames> block;
    const jint frames = geode_mic_read(micOf(handle), block.data(), limit, timeoutNanos);
    if (frames > 0) env->SetFloatArrayRegion(dst, 0, frames, block.data());
    return frames;
}

JNIEXPORT void JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micStop(JNIEnv*, jobject, jlong handle) {
    geode_mic_stop(micOf(handle));
}

JNIEXPORT void JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micDestroy(JNIEnv*, jobject, jlong handle) {
    geode_mic_destroy(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micSampleRate(JNIEnv*, jobject, jlong handle) {
    return geode_mic_sample_rate(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micChannels(JNIEnv*, jobject, jlong handle) {
    return geode_mic_channels(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micFramesPerBurst(JNIEnv*, jobject, jlong handle) {
    return geode_mic_frames_per_burst(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micBufferFrames(JNIEnv*, jobject, jlong handle) {
    return geode_mic_buffer_frames(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micSharingMode(JNIEnv*, jobject, jlong handle) {
    return geode_mic_sharing_mode(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micPerformanceMode(JNIEnv*, jobject, jlong handle) {
    return geode_mic_performance_mode(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micLastError(JNIEnv*, jobject, jlong handle) {
    return geode_mic_last_error(micOf(handle));
}

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micGeneration(JNIEnv*, jobject, jlong handle) {
    return geode_mic_generation(micOf(handle));
}

JNIEXPORT jfloat JNICALL
Java_dev_geode_engine_bridge_GeodeNative_micLastPeak(JNIEnv*, jobject, jlong handle) {
    return geode_mic_last_peak(micOf(handle));
}

}  // extern "C"
