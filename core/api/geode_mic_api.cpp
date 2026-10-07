#include <memory>

#include "api/geode_api.h"
#include "audio/capture/MicStream.hpp"

struct geode_mic {
    explicit geode_mic(bool preferUnprocessed) : stream(preferUnprocessed) {}

    geode::audio::capture::MicStream stream;
};

extern "C" {

geode_mic* geode_mic_create(int prefer_unprocessed) {
    return std::make_unique<geode_mic>(prefer_unprocessed != 0).release();
}

int geode_mic_start(geode_mic* m) {
    return m && m->stream.start() ? 1 : 0;
}

int geode_mic_read(geode_mic* m, float* mono, int max_frames, int64_t timeout_nanos) {
    return m ? m->stream.read(mono, max_frames, timeout_nanos) : 0;
}

void geode_mic_stop(geode_mic* m) {
    if (m) m->stream.stop();
}

void geode_mic_destroy(geode_mic* m) {
    std::unique_ptr<geode_mic> owned(m);
}

int geode_mic_sample_rate(const geode_mic* m) {
    return m ? m->stream.sampleRate() : 0;
}

int geode_mic_channels(const geode_mic* m) {
    return m ? m->stream.channels() : 0;
}

int geode_mic_frames_per_burst(const geode_mic* m) {
    return m ? m->stream.framesPerBurst() : 0;
}

int geode_mic_buffer_frames(const geode_mic* m) {
    return m ? m->stream.bufferFrames() : 0;
}

int geode_mic_sharing_mode(const geode_mic* m) {
    return m ? m->stream.sharingMode() : -1;
}

int geode_mic_performance_mode(const geode_mic* m) {
    return m ? m->stream.performanceMode() : -1;
}

int geode_mic_last_error(const geode_mic* m) {
    return m ? m->stream.lastError() : 0;
}

int geode_mic_generation(const geode_mic* m) {
    return m ? m->stream.generation() : 0;
}

float geode_mic_last_peak(const geode_mic* m) {
    return m ? m->stream.lastPeak() : 0.0f;
}

}  // extern "C"
