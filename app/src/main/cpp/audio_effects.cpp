#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "AudioEffects"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeMixAudio(
    JNIEnv* env, jobject thiz,
    jshortArray buf1, jshortArray buf2,
    jint len, jint volume1, jint volume2) {

    jshort* b1 = env->GetShortArrayElements(buf1, nullptr);
    jshort* b2 = env->GetShortArrayElements(buf2, nullptr);

    float v1 = volume1 / 100.0f;
    float v2 = volume2 / 100.0f;

    for (int i = 0; i < len; i++) {
        int mixed = static_cast<int>(b1[i] * v1 + b2[i] * v2);
        b1[i] = static_cast<jshort>(std::max(-32768, std::min(32767, mixed)));
    }

    env->ReleaseShortArrayElements(buf1, b1, 0);
    env->ReleaseShortArrayElements(buf2, b2, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeFadeAudio(
    JNIEnv* env, jobject thiz,
    jshortArray buffer, jint len, jint fadeLen, jboolean isFadeIn) {

    jshort* buf = env->GetShortArrayElements(buffer, nullptr);

    for (int i = 0; i < fadeLen && i < len; i++) {
        float gain = isFadeIn
            ? static_cast<float>(i) / fadeLen
            : static_cast<float>(fadeLen - i) / fadeLen;
        buf[i] = static_cast<jshort>(buf[i] * gain);
    }

    env->ReleaseShortArrayElements(buffer, buf, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeEqualizer(
    JNIEnv* env, jobject thiz,
    jshortArray buffer, jint len,
    jint lowGain, jint midGain, jint highGain) {

    jshort* buf = env->GetShortArrayElements(buffer, nullptr);

    float lG = lowGain / 100.0f;
    float mG = midGain / 100.0f;
    float hG = highGain / 100.0f;

    // Simple 3-band EQ via moving average filters
    for (int i = 2; i < len - 2; i++) {
        float low = (buf[i-2] + buf[i-1] + buf[i] + buf[i+1] + buf[i+2]) / 5.0f;
        float high = buf[i] - low;
        float mid = buf[i] - low - high;

        int out = static_cast<int>(low * lG + mid * mG + high * hG);
        buf[i] = static_cast<jshort>(std::max(-32768, std::min(32767, out)));
    }

    env->ReleaseShortArrayElements(buffer, buf, 0);
    return JNI_TRUE;
}
