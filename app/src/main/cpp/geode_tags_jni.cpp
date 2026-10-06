#include <jni.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <memory>
#include <string_view>

#include "api/geode_api.h"

namespace {

using TagsHandle = std::unique_ptr<geode_tags, decltype(&geode_tags_destroy)>;

jbyteArray utf8Bytes(JNIEnv* env, const char* text) {
    const std::string_view view(text);
    jbyteArray out = env->NewByteArray(static_cast<jsize>(view.size()));
    if (out && !view.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(view.size()), reinterpret_cast<const jbyte*>(view.data()));
    }
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_dev_geode_engine_bridge_GeodeNative_tagsRead(JNIEnv* env, jobject, jint fd, jobjectArray texts, jintArray ints,
                                                  jfloatArray gains) {
    TagsHandle tags(geode_tags_read(fd), &geode_tags_destroy);
    if (!tags || !texts || !ints || !gains) return -1;
    if (env->GetArrayLength(texts) < GEODE_TAG_TEXT_COUNT || env->GetArrayLength(ints) < 4 ||
        env->GetArrayLength(gains) < 4) {
        return -1;
    }
    for (int i = 0; i < GEODE_TAG_TEXT_COUNT; ++i) {
        jbyteArray bytes = utf8Bytes(env, geode_tags_text(tags.get(), static_cast<GeodeTagText>(i)));
        env->SetObjectArrayElement(texts, i, bytes);
        env->DeleteLocalRef(bytes);
    }
    const size_t art = std::min<size_t>(geode_tags_art_bytes(tags.get()), static_cast<size_t>(INT32_MAX));
    const std::array<jint, 4> numbers{geode_tags_year(tags.get()), geode_tags_track(tags.get()),
                                      geode_tags_duration_ms(tags.get()), static_cast<jint>(art)};
    env->SetIntArrayRegion(ints, 0, 4, numbers.data());
    std::array<jfloat, 4> replayGain{};
    const int mask = geode_tags_replaygain(tags.get(), &replayGain[0], &replayGain[1], &replayGain[2], &replayGain[3]);
    env->SetFloatArrayRegion(gains, 0, 4, replayGain.data());
    return mask;
}

}  // extern "C"
