#include <jni.h>
#include <stdint.h>

#include <string>
#include <vector>

#include "chromaprint.h"

namespace {

std::vector<int16_t> BytesToPcm16(JNIEnv *env, jbyteArray pcm_bytes) {
    const jsize byte_count = env->GetArrayLength(pcm_bytes);
    std::vector<jbyte> bytes(byte_count);
    env->GetByteArrayRegion(pcm_bytes, 0, byte_count, bytes.data());

    std::vector<int16_t> samples(byte_count / 2);
    for (size_t index = 0; index < samples.size(); ++index) {
        const auto lo = static_cast<uint8_t>(bytes[index * 2]);
        const auto hi = static_cast<int8_t>(bytes[index * 2 + 1]);
        samples[index] = static_cast<int16_t>((static_cast<int16_t>(hi) << 8) | lo);
    }
    return samples;
}

jstring ThrowIllegalState(JNIEnv *env, const char *message) {
    jclass exception_class = env->FindClass("java/lang/IllegalStateException");
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
    }
    return nullptr;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_orion_blaster_core_fingerprint_NativeChromaprintBridge_fingerprintPcm16Native(
    JNIEnv *env,
    jobject,
    jbyteArray pcm_bytes,
    jint sample_rate,
    jint channel_count) {
    if (pcm_bytes == nullptr) {
        return ThrowIllegalState(env, "pcm bytes are missing");
    }
    if (sample_rate <= 0 || channel_count <= 0) {
        return ThrowIllegalState(env, "invalid pcm format");
    }

    std::vector<int16_t> samples = BytesToPcm16(env, pcm_bytes);
    if (samples.empty()) {
        return ThrowIllegalState(env, "pcm samples are empty");
    }

    ChromaprintContext *context = chromaprint_new(CHROMAPRINT_ALGORITHM_DEFAULT);
    if (context == nullptr) {
        return ThrowIllegalState(env, "failed to create chromaprint context");
    }

    char *fingerprint = nullptr;
    const int started = chromaprint_start(context, sample_rate, channel_count);
    const int fed = started ? chromaprint_feed(context, samples.data(), static_cast<int>(samples.size())) : 0;
    const int finished = fed ? chromaprint_finish(context) : 0;
    const int got_fingerprint = finished ? chromaprint_get_fingerprint(context, &fingerprint) : 0;

    if (!got_fingerprint || fingerprint == nullptr) {
        chromaprint_free(context);
        return ThrowIllegalState(env, "failed to generate chromaprint fingerprint");
    }

    jstring result = env->NewStringUTF(fingerprint);
    chromaprint_dealloc(fingerprint);
    chromaprint_free(context);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_orion_blaster_core_fingerprint_NativeChromaprintBridge_versionNative(
    JNIEnv *env,
    jobject) {
    return env->NewStringUTF(chromaprint_get_version());
}
