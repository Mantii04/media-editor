package com.mediaeditor.ndk

import android.graphics.Bitmap

/**
 * JNI bridge to native (C++) image and audio processing routines.
 * Provides hardware-accelerated effects that run on CPU via SIMD/NEON.
 */
object NativeProcessor {

    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("native_processor")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
        }
    }

    val isAvailable: Boolean get() = nativeLoaded

    // ==================== Image Processing ====================

    /** Apply grayscale using luminance weights (faster than ColorMatrix) */
    external fun nativeGrayscale(bitmap: Bitmap, width: Int, height: Int): Boolean

    /** Apply sepia tone effect */
    external fun nativeSepia(bitmap: Bitmap, width: Int, height: Int): Boolean

    /** Apply brightness adjustment (delta: -255 to 255) */
    external fun nativeBrightness(bitmap: Bitmap, width: Int, height: Int, delta: Int): Boolean

    /** Apply contrast adjustment (factor: 0.0 to 3.0 * 100 as int) */
    external fun nativeContrast(bitmap: Bitmap, width: Int, height: Int, factor: Int): Boolean

    /** Apply box blur with given radius */
    external fun nativeBlur(bitmap: Bitmap, width: Int, height: Int, radius: Int): Boolean

    /** Apply sharpening kernel */
    external fun nativeSharpen(bitmap: Bitmap, width: Int, height: Int, intensity: Int): Boolean

    /** Invert colors */
    external fun nativeInvert(bitmap: Bitmap, width: Int, height: Int): Boolean

    /** Apply gamma correction (gamma * 100 as int, range 50-300) */
    external fun nativeGamma(bitmap: Bitmap, width: Int, height: Int, gamma: Int): Boolean

    /** Apply hue rotation (degrees: 0-360) */
    external fun nativeHueRotate(bitmap: Bitmap, width: Int, height: Int, degrees: Int): Boolean

    /** Mix two bitmaps with given ratio (0-100) */
    external fun nativeBlend(
        dst: Bitmap, src: Bitmap, width: Int, height: Int, ratio: Int
    ): Boolean

    // ==================== Convolution ====================

    /** Apply 3x3 convolution kernel */
    external fun nativeConvolve3x3(
        bitmap: Bitmap, width: Int, height: Int,
        k0: Float, k1: Float, k2: Float,
        k3: Float, k4: Float, k5: Float,
        k6: Float, k7: Float, k8: Float
    ): Boolean

    // ==================== Audio Processing ====================

    /** Mix two PCM 16-bit audio buffers */
    external fun nativeMixAudio(
        buffer1: ShortArray, buffer2: ShortArray,
        len: Int, volume1: Int, volume2: Int
    ): Boolean

    /** Apply fade in/out on PCM 16-bit audio */
    external fun nativeFadeAudio(
        buffer: ShortArray, len: Int, fadeLen: Int, isFadeIn: Boolean
    ): Boolean

    /** Simple 3-band equalizer */
    external fun nativeEqualizer(
        buffer: ShortArray, len: Int,
        lowGain: Int, midGain: Int, highGain: Int
    ): Boolean

    // Fallback software implementations when native is not available
    object Fallback {
        fun grayscale(pixels: IntArray): IntArray {
            return IntArray(pixels.size) { i ->
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                (-0x1000000) or (gray shl 16) or (gray shl 8) or gray
            }
        }

        fun brightness(pixels: IntArray, delta: Int): IntArray {
            return IntArray(pixels.size) { i ->
                val p = pixels[i]
                val r = ((p shr 16) and 0xFF).coerceIn(0, 255)
                val g = ((p shr 8) and 0xFF).coerceIn(0, 255)
                val b = (p and 0xFF).coerceIn(0, 255)
                val nr = (r + delta).coerceIn(0, 255)
                val ng = (g + delta).coerceIn(0, 255)
                val nb = (b + delta).coerceIn(0, 255)
                (-0x1000000) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }
}
