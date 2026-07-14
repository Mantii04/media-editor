package com.mediaeditor.editor

import android.graphics.ColorMatrix
import com.mediaeditor.model.FilterPreset

object FilterProcessor {

    val presets: List<FilterPreset> = listOf(
        // Standard
        FilterPreset.fromFloatArray("Normal", "Original", ColorMatrix().getArray()),
        FilterPreset.fromFloatArray("Grayscale", "B&W Timeless",
            ColorMatrix().apply { setSaturation(0f) }.getArray()),
        FilterPreset.fromFloatArray("Invert", "Negative",
            floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),

        // Vintage / Retro
        FilterPreset.fromFloatArray("Sepia", "Warm vintage",
            floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Vintage", "Retro film",
            floatArrayOf(0.9f, 0.1f, 0.0f, 0f, 10f, 0.1f, 0.8f, 0.1f, 0f, 5f, 0.0f, 0.2f, 0.7f, 0f, 15f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Retro", "70s vibe",
            floatArrayOf(0.85f, 0.2f, 0.0f, 0f, 8f, 0.1f, 0.75f, 0.15f, 0f, 12f, 0.0f, 0.1f, 0.8f, 0f, 5f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Fade", "Faded film",
            floatArrayOf(0.8f, 0f, 0f, 0f, 30f, 0f, 0.75f, 0f, 0f, 25f, 0f, 0f, 0.7f, 0f, 35f, 0f, 0f, 0f, 0.85f, 0f)),

        // Color
        FilterPreset.fromFloatArray("Cool", "Blue tone",
            floatArrayOf(0.8f, 0f, 0.2f, 0f, 0f, 0f, 0.9f, 0.1f, 0f, 0f, 0f, 0f, 1.1f, 0f, 5f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Warm", "Golden hour",
            floatArrayOf(1.1f, 0f, 0f, 0f, 10f, 0f, 1.0f, 0f, 0f, 5f, 0f, 0f, 0.8f, 0f, -5f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Pastel", "Soft dreamy",
            floatArrayOf(0.85f, 0.15f, 0f, 0f, 20f, 0.1f, 0.8f, 0.1f, 0f, 15f, 0.05f, 0.1f, 0.85f, 0f, 25f, 0f, 0f, 0f, 0.9f, 0f)),

        // Dramatic
        FilterPreset.fromFloatArray("Dramatic", "High contrast",
            floatArrayOf(1.4f, 0f, 0f, 0f, -30f, 0f, 1.4f, 0f, 0f, -30f, 0f, 0f, 1.4f, 0f, -30f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("HDR", "Vibrant pop",
            floatArrayOf(1.3f, 0f, 0f, 0f, -15f, 0f, 1.3f, 0f, 0f, -15f, 0f, 0f, 1.3f, 0f, -15f, 0f, 0f, 0f, 1.1f, 0f)),
        FilterPreset.fromFloatArray("Noir", "Film noir",
            ColorMatrix().apply {
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1.2f, 0f, 0f, 0f, -20f, 0f, 1.2f, 0f, 0f, -20f, 0f, 0f, 1.2f, 0f, -20f, 0f, 0f, 0f, 1f, 0f)))
            }.getArray()),

        // Creative
        FilterPreset.fromFloatArray("Neon", "Cyberpunk",
            floatArrayOf(0.5f, 0f, 1.0f, 0f, -20f, 0f, 0.3f, 0.8f, 0f, -10f, 0.8f, 0f, 1.2f, 0f, -15f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Cinematic", "Movie grade",
            floatArrayOf(0.9f, 0.05f, 0.05f, 0f, -8f, 0.05f, 0.9f, 0.05f, 0f, -8f, 0.05f, 0.05f, 0.9f, 0f, -5f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Dream", "Soft glow",
            floatArrayOf(0.9f, 0.1f, 0.1f, 0f, 15f, 0.05f, 0.9f, 0.05f, 0f, 15f, 0.1f, 0.1f, 0.9f, 0f, 20f, 0f, 0f, 0f, 0.9f, 0f)),
        FilterPreset.fromFloatArray("Sunset", "Evening glow",
            floatArrayOf(1.1f, 0.1f, 0f, 0f, 5f, 0f, 0.8f, 0f, 0f, 8f, 0f, 0f, 0.7f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)),
        FilterPreset.fromFloatArray("Forest", "Nature green",
            floatArrayOf(0.8f, 0.1f, 0.1f, 0f, -5f, 0f, 1.1f, 0f, 0f, 5f, 0.1f, 0.1f, 0.8f, 0f, -5f, 0f, 0f, 0f, 1f, 0f)),
    )

    fun applyFilter(pixels: IntArray, width: Int, height: Int, matrix: FloatArray): IntArray {
        if (matrix.size < 20) return pixels
        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val nr = (matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4]).coerceIn(0f, 255f).toInt()
            val ng = (matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9]).coerceIn(0f, 255f).toInt()
            val nb = (matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14]).coerceIn(0f, 255f).toInt()
            val na = (matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19]).coerceIn(0f, 255f).toInt()

            result[i] = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return result
    }
}
