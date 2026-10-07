// AAudioStreamBuilder_setInputPreset is API 28 and this library is built for minSdk 26. With this defined
// the NDK declares functions newer than minSdk as weak references instead of refusing to compile them,
// and every such call below sits behind __builtin_available. It has to come before every include: the
// NDK reads it from bionic's own headers.
#ifndef __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__
#define __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__
#endif

#include "audio/capture/MicStream.hpp"

#include <aaudio/AAudio.h>

#include <algorithm>
#include <thread>

#include "audio/capture/MicPcm.hpp"
#include "util/Log.hpp"

namespace geode::audio::capture {
namespace {

constexpr const char* kTag = "geode.mic";
constexpr int kMaxReadFrames = 4096;
constexpr int kMaxChannels = 8;
constexpr int32_t kPresetPlatformDefault = 0;

bool supportedFormat(int32_t format) {
    return format == AAUDIO_FORMAT_PCM_FLOAT || format == AAUDIO_FORMAT_PCM_I16;
}

void applyInputPreset(AAudioStreamBuilder* builder, int32_t preset) {
    if (preset == kPresetPlatformDefault) return;
    if (__builtin_available(android 28, *)) {
        AAudioStreamBuilder_setInputPreset(builder, static_cast<aaudio_input_preset_t>(preset));
    }
}

void configure(AAudioStreamBuilder* builder, int32_t sharing, int32_t preset) {
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, sharing);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    applyInputPreset(builder, preset);
}

}  // namespace

MicStream::MicStream(bool preferUnprocessed) : preferUnprocessed_(preferUnprocessed) {}

MicStream::~MicStream() {
    stop();
}

bool MicStream::start() {
    wanted_ = true;
    reopenAttempts_ = 0;
    if (stream_ != nullptr) return true;
    if (openAndStart()) return true;
    wanted_ = false;
    return false;
}

void MicStream::stop() {
    wanted_ = false;
    closeStream();
}

bool MicStream::openAndStart() {
    const int32_t wanted =
        preferUnprocessed_ ? AAUDIO_INPUT_PRESET_UNPROCESSED : AAUDIO_INPUT_PRESET_VOICE_RECOGNITION;
    std::vector<OpenAttempt> attempts;
    auto add = [&attempts](int32_t sharing, int32_t preset) { attempts.push_back(OpenAttempt{sharing, preset}); };
    add(AAUDIO_SHARING_MODE_EXCLUSIVE, wanted);
    add(AAUDIO_SHARING_MODE_SHARED, wanted);
    if (wanted != AAUDIO_INPUT_PRESET_VOICE_RECOGNITION) {
        add(AAUDIO_SHARING_MODE_SHARED, AAUDIO_INPUT_PRESET_VOICE_RECOGNITION);
    }
    add(AAUDIO_SHARING_MODE_SHARED, kPresetPlatformDefault);
    for (const OpenAttempt& attempt : attempts) {
        if (tryAttempt(attempt)) return true;
    }
    return false;
}

bool MicStream::tryAttempt(const OpenAttempt& attempt) {
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || builder == nullptr) {
        lastError_.store(AAUDIO_ERROR_NO_MEMORY);
        return false;
    }
    configure(builder, attempt.sharing, attempt.preset);
    AAudioStream* stream = nullptr;
    const aaudio_result_t opened = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (opened != AAUDIO_OK || stream == nullptr) {
        lastError_.store(opened);
        GEODE_LOGW(kTag, "open failed (sharing %d, preset %d): %s", attempt.sharing, attempt.preset,
                   AAudio_convertResultToText(opened));
        return false;
    }
    if (adopt(stream)) return true;
    AAudioStream_close(stream);
    return false;
}

bool MicStream::adopt(AAudioStream* stream) {
    const int32_t format = AAudioStream_getFormat(stream);
    const int channels = AAudioStream_getChannelCount(stream);
    const int rate = AAudioStream_getSampleRate(stream);
    if (!supportedFormat(format) || channels < 1 || channels > kMaxChannels || rate <= 0) {
        lastError_.store(AAUDIO_ERROR_INVALID_FORMAT);
        GEODE_LOGW(kTag, "unusable stream: format %d, %d ch, %d Hz", format, channels, rate);
        return false;
    }
    const int burst = AAudioStream_getFramesPerBurst(stream);
    const int sized = bufferFramesFor(burst, AAudioStream_getBufferCapacityInFrames(stream));
    if (sized > 0) AAudioStream_setBufferSizeInFrames(stream, sized);
    const aaudio_result_t started = AAudioStream_requestStart(stream);
    if (started != AAUDIO_OK) {
        lastError_.store(started);
        GEODE_LOGW(kTag, "start failed: %s", AAudio_convertResultToText(started));
        return false;
    }
    prepareScratch(format, channels);
    stream_ = stream;
    format_ = format;
    streamChannels_ = channels;
    publish(stream, rate, channels, burst);
    return true;
}

void MicStream::prepareScratch(int32_t format, int channels) {
    const size_t samples = static_cast<size_t>(kMaxReadFrames) * static_cast<size_t>(channels);
    if (format == AAUDIO_FORMAT_PCM_I16 && i16Scratch_.size() < samples) i16Scratch_.resize(samples);
    if (channels > 1 && floatScratch_.size() < samples) floatScratch_.resize(samples);
}

void MicStream::publish(AAudioStream* stream, int rate, int channels, int burst) {
    sampleRate_.store(rate);
    channels_.store(channels);
    framesPerBurst_.store(burst);
    bufferFrames_.store(AAudioStream_getBufferSizeInFrames(stream));
    sharingMode_.store(AAudioStream_getSharingMode(stream));
    performanceMode_.store(AAudioStream_getPerformanceMode(stream));
    generation_.fetch_add(1);
    GEODE_LOGI(kTag, "open: %d Hz, %d ch, burst %d, buffer %d of %d frames, sharing %d, performance %d", rate,
               channels, burst, bufferFrames_.load(), AAudioStream_getBufferCapacityInFrames(stream),
               sharingMode_.load(), performanceMode_.load());
}

void MicStream::closeStream() {
    if (stream_ == nullptr) return;
    AAudioStream_requestStop(stream_);
    AAudioStream_close(stream_);
    stream_ = nullptr;
}

int MicStream::read(float* dst, int maxFrames, int64_t timeoutNanos) {
    if (dst == nullptr || maxFrames <= 0) return 0;
    if (!wanted_) return AAUDIO_ERROR_INVALID_STATE;
    if (stream_ == nullptr) return awaitReopen(timeoutNanos);
    const int result = readStream(dst, std::min(maxFrames, kMaxReadFrames), timeoutNanos);
    if (result < 0) return handleReadError(result);
    if (result > 0) lastPeak_.store(peakAbs(dst, static_cast<size_t>(result)));
    return result;
}

int MicStream::readStream(float* dst, int frames, int64_t timeoutNanos) {
    if (format_ == AAUDIO_FORMAT_PCM_FLOAT) return readFloat(dst, frames, timeoutNanos);
    return readI16(dst, frames, timeoutNanos);
}

int MicStream::readFloat(float* dst, int frames, int64_t timeoutNanos) {
    if (streamChannels_ == 1) return AAudioStream_read(stream_, dst, frames, timeoutNanos);
    const int framesRead = AAudioStream_read(stream_, floatScratch_.data(), frames, timeoutNanos);
    if (framesRead > 0) downmixToMono(floatScratch_.data(), dst, static_cast<size_t>(framesRead), streamChannels_);
    return framesRead;
}

int MicStream::readI16(float* dst, int frames, int64_t timeoutNanos) {
    const int framesRead = AAudioStream_read(stream_, i16Scratch_.data(), frames, timeoutNanos);
    if (framesRead <= 0) return framesRead;
    const size_t count = static_cast<size_t>(framesRead);
    if (streamChannels_ == 1) {
        i16ToFloat(i16Scratch_.data(), dst, count);
        return framesRead;
    }
    i16ToFloat(i16Scratch_.data(), floatScratch_.data(), count * static_cast<size_t>(streamChannels_));
    downmixToMono(floatScratch_.data(), dst, count, streamChannels_);
    return framesRead;
}

bool MicStream::isRecoverable(int result) const {
    if (result == AAUDIO_ERROR_DISCONNECTED || result == AAUDIO_ERROR_NO_SERVICE) return true;
    return result == AAUDIO_ERROR_INVALID_STATE && stream_ != nullptr &&
           AAudioStream_getState(stream_) == AAUDIO_STREAM_STATE_DISCONNECTED;
}

int MicStream::handleReadError(int result) {
    lastError_.store(result);
    if (!isRecoverable(result)) return result;
    GEODE_LOGW(kTag, "read failed (%s); reopening", AAudio_convertResultToText(result));
    closeStream();
    lastPeak_.store(0.0f);
    generation_.fetch_add(1);
    reopenAttempts_ = 0;
    nextReopenAt_ = Clock::now();
    return 0;
}

int MicStream::awaitReopen(int64_t timeoutNanos) {
    const Clock::time_point now = Clock::now();
    if (now < nextReopenAt_) {
        const Clock::duration limit = std::chrono::nanoseconds(std::max<int64_t>(timeoutNanos, 0));
        std::this_thread::sleep_for(std::min<Clock::duration>(nextReopenAt_ - now, limit));
        return 0;
    }
    if (openAndStart()) {
        reopenAttempts_ = 0;
        return 0;
    }
    if (++reopenAttempts_ > kMaxReopenAttempts) {
        GEODE_LOGE(kTag, "giving up after %d reopen attempts", kMaxReopenAttempts);
        const int last = lastError_.load();
        return last < 0 ? last : AAUDIO_ERROR_UNAVAILABLE;
    }
    nextReopenAt_ = Clock::now() + std::chrono::milliseconds(reopenBackoffMs(reopenAttempts_));
    return 0;
}

}  // namespace geode::audio::capture
