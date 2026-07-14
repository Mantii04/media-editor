#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "ImageEffects"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct Pixel { uint8_t b, g, r, a; };

inline Pixel* px(void* pixels, int stride, int x, int y) {
    return reinterpret_cast<Pixel*>(
        reinterpret_cast<uint8_t*>(pixels) + y * stride + x * 4
    );
}

// Apply thermal/infrared color mapping
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeHueRotate(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height, jint degrees) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    float angle = degrees * 3.141592653589793f / 180.0f;
    float cosA = cosf(angle);
    float sinA = sinf(angle);

    // Approximate hue rotation matrix for RGB
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = px(pixels, info.stride, x, y);
            float r = p->r, g = p->g, b = p->b;
            // Weighted hue rotation
            p->r = static_cast<uint8_t>(
                std::min(255.0f, r + (g - b) * sinA * 0.5f)
            );
            p->g = static_cast<uint8_t>(
                std::min(255.0f, g + (b - r) * sinA * 0.5f)
            );
            p->b = static_cast<uint8_t>(
                std::min(255.0f, b + (r - g) * sinA * 0.5f)
            );
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}
