#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <cmath>

#define LOG_TAG "NativeProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Fast pixel access without bounds checking
struct PixelARGB {
    uint8_t b, g, r, a;
};

inline PixelARGB* getPixels(AndroidBitmapInfo& info, void* pixels, int x, int y) {
    return reinterpret_cast<PixelARGB*>(
        reinterpret_cast<uint8_t*>(pixels) + y * info.stride + x * 4
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeGrayscale(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = getPixels(info, pixels, x, y);
            uint8_t gray = static_cast<uint8_t>(
                0.299f * p->r + 0.587f * p->g + 0.114f * p->b
            );
            p->r = gray; p->g = gray; p->b = gray;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeSepia(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = getPixels(info, pixels, x, y);
            float r = p->r, g = p->g, b = p->b;
            p->r = static_cast<uint8_t>(std::min(255.0f, r * 0.393f + g * 0.769f + b * 0.189f));
            p->g = static_cast<uint8_t>(std::min(255.0f, r * 0.349f + g * 0.686f + b * 0.168f));
            p->b = static_cast<uint8_t>(std::min(255.0f, r * 0.272f + g * 0.534f + b * 0.131f));
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeBrightness(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height, jint delta) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    auto clamp = [](int v) -> uint8_t {
        return static_cast<uint8_t>(v < 0 ? 0 : (v > 255 ? 255 : v));
    };

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = getPixels(info, pixels, x, y);
            p->r = clamp(p->r + delta);
            p->g = clamp(p->g + delta);
            p->b = clamp(p->b + delta);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeContrast(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height, jint factor) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    float f = factor / 100.0f;
    auto adjust = [f](int c) -> uint8_t {
        float v = (c - 128) * f + 128;
        return static_cast<uint8_t>(v < 0 ? 0 : (v > 255 ? 255 : v));
    };

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = getPixels(info, pixels, x, y);
            p->r = adjust(p->r);
            p->g = adjust(p->g);
            p->b = adjust(p->b);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeBlur(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height, jint radius) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    if (radius < 1) { AndroidBitmap_unlockPixels(env, bitmap); return JNI_TRUE; }

    // Simple box blur (single pass for speed)
    int r = std::min(radius, std::min(width, height) / 2);

    // Horizontal pass
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            for (int dx = -r; dx <= r; dx++) {
                int sx = x + dx;
                if (sx < 0 || sx >= width) continue;
                auto* sp = getPixels(info, pixels, sx, y);
                if (dx == 0) { sumR += sp->r; sumG += sp->g; sumB += sp->b; count++; }
                else { sumR += sp->r; sumG += sp->g; sumB += sp->b; count++; }
            }
            auto* p = getPixels(info, pixels, x, y);
            p->r = sumR / count; p->g = sumG / count; p->b = sumB / count;
        }
    }

    // Vertical pass
    for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
            int sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            for (int dy = -r; dy <= r; dy++) {
                int sy = y + dy;
                if (sy < 0 || sy >= height) continue;
                auto* sp = getPixels(info, pixels, x, sy);
                sumR += sp->r; sumG += sp->g; sumB += sp->b; count++;
            }
            auto* p = getPixels(info, pixels, x, y);
            p->r = sumR / count; p->g = sumG / count; p->b = sumB / count;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeInvert(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = getPixels(info, pixels, x, y);
            p->r = 255 - p->r; p->g = 255 - p->g; p->b = 255 - p->b;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeConvolve3x3(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height,
    jfloat k0, jfloat k1, jfloat k2, jfloat k3, jfloat k4,
    jfloat k5, jfloat k6, jfloat k7, jfloat k8) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    float kernel[9] = {k0, k1, k2, k3, k4, k5, k6, k7, k8};

    // Create a copy for reading
    std::vector<uint32_t> copy(width * height);
    auto* src = reinterpret_cast<uint32_t*>(pixels);
    std::copy(src, src + width * height, copy.begin());

    auto clamp = [](float v) -> uint8_t {
        return static_cast<uint8_t>(v < 0 ? 0 : (v > 255 ? 255 : v));
    };

    for (int y = 1; y < height - 1; y++) {
        for (int x = 1; x < width - 1; x++) {
            float sumR = 0, sumG = 0, sumB = 0;
            for (int ky = -1; ky <= 1; ky++) {
                for (int kx = -1; kx <= 1; kx++) {
                    auto pixel = copy[(y + ky) * width + (x + kx)];
                    float k = kernel[(ky + 1) * 3 + (kx + 1)];
                    sumR += ((pixel >> 16) & 0xFF) * k;
                    sumG += ((pixel >> 8) & 0xFF) * k;
                    sumB += (pixel & 0xFF) * k;
                }
            }
            auto* p = getPixels(info, pixels, x, y);
            p->r = clamp(sumR); p->g = clamp(sumG); p->b = clamp(sumB);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeSharpen(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height, jint intensity) {

    float f = intensity / 100.0f;
    // Sharpen kernel: center = 1 + 4*f, edges = -f
    float center = 1.0f + 4.0f * f;
    return Java_com_mediaeditor_ndk_NativeProcessor_nativeConvolve3x3(
        env, thiz, bitmap, width, height,
        0, -f, 0,
        -f, center, -f,
        0, -f, 0
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mediaeditor_ndk_NativeProcessor_nativeGamma(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height, jint gamma) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return JNI_FALSE;

    float g = 255.0f / std::pow(255.0f, gamma / 100.0f);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            auto* p = getPixels(info, pixels, x, y);
            p->r = static_cast<uint8_t>(g * std::pow(p->r, gamma / 100.0f));
            p->g = static_cast<uint8_t>(g * std::pow(p->g, gamma / 100.0f));
            p->b = static_cast<uint8_t>(g * std::pow(p->b, gamma / 100.0f));
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}
