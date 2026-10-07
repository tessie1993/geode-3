#pragma once
#include <atomic>
#include <chrono>
#include <cstdint>
#include <vector>

struct AAudioStreamStruct;

namespace geode::audio::capture {

// A mono microphone stream over an AAudio input stream, read with blocking calls. There is no data
// callback, so no code here runs on a real-time thread: the owner's reader thread calls read(), and that
// same thread closes and reopens the stream when the route changes underneath it.
//
// Threading: start(), read() and stop() belong to one thread at a time (start before the reader begins,
// stop after it ends). The info getters read atomics and are safe from any thread.
class MicStream {
public:
    // preferUnprocessed asks for the UNPROCESSED input preset (API 28+); otherwise VOICE_RECOGNITION is
    // requested. Below API 28 the platform default, which is also VOICE_RECOGNITION, applies.
    explicit MicStream(bool preferUnprocessed);
    ~MicStream();

    MicStream(const MicStream&) = delete;
    MicStream& operator=(const MicStream&) = delete;

    // Opens the stream (EXCLUSIVE, then SHARED) and starts it. false when no configuration opened.
    bool start();

    // Stops and closes the stream. Safe to call twice; read() fails after it.
    void stop();

    // Reads up to maxFrames mono float frames, waiting up to timeoutNanos for them. Returns the frames
    // read, which is 0 when none arrived in time or while the stream is being reopened, and a negative
    // AAudio result when the stream is gone for good. A disconnect makes the failing call close the stream
    // and return 0; the calls after it reopen and restart the stream with backoff, and generation()
    // changes at disconnect and again once the reopened stream is in place, possibly at another rate.
    int read(float* dst, int maxFrames, int64_t timeoutNanos);

    int sampleRate() const { return sampleRate_.load(); }
    int channels() const { return channels_.load(); }
    int framesPerBurst() const { return framesPerBurst_.load(); }
    int bufferFrames() const { return bufferFrames_.load(); }
    int sharingMode() const { return sharingMode_.load(); }
    int performanceMode() const { return performanceMode_.load(); }
    int lastError() const { return lastError_.load(); }
    int generation() const { return generation_.load(); }
    float lastPeak() const { return lastPeak_.load(); }

private:
    using Clock = std::chrono::steady_clock;

    struct OpenAttempt {
        int32_t sharing;
        int32_t preset;
    };

    bool openAndStart();
    bool tryAttempt(const OpenAttempt& attempt);
    bool adopt(AAudioStreamStruct* stream);
    void prepareScratch(int32_t format, int channels);
    void publish(AAudioStreamStruct* stream, int rate, int channels, int burst);
    void closeStream();

    int readStream(float* dst, int frames, int64_t timeoutNanos);
    int readFloat(float* dst, int frames, int64_t timeoutNanos);
    int readI16(float* dst, int frames, int64_t timeoutNanos);
    int handleReadError(int result);
    bool isRecoverable(int result) const;
    int awaitReopen(int64_t timeoutNanos);

    const bool preferUnprocessed_;
    bool wanted_ = false;
    AAudioStreamStruct* stream_ = nullptr;
    int32_t format_ = 0;
    int streamChannels_ = 1;
    int reopenAttempts_ = 0;
    Clock::time_point nextReopenAt_{};
    std::vector<float> floatScratch_;
    std::vector<int16_t> i16Scratch_;

    std::atomic<int> sampleRate_{0};
    std::atomic<int> channels_{0};
    std::atomic<int> framesPerBurst_{0};
    std::atomic<int> bufferFrames_{0};
    std::atomic<int> sharingMode_{-1};
    std::atomic<int> performanceMode_{-1};
    std::atomic<int> lastError_{0};
    std::atomic<int> generation_{0};
    std::atomic<float> lastPeak_{0.0f};
};

}  // namespace geode::audio::capture
