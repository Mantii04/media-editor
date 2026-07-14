package com.mediaeditor.editor

import android.graphics.ColorMatrix
import com.mediaeditor.model.FilterPreset

object FilterProcessor {

    val presets: List<FilterPreset> = listOf(
        FilterPreset("Normal", "Original", ColorMatrix().getArray()),
        FilterPreset("Grayscale", "B&W", ColorMatrix().apply { setSaturation(0f) }.getArray()),
        FilterPreset("Sepia", "Warm vintage", floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        FilterPreset("Invert", "Negative", floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )),
        FilterPreset("Vintage", "Retro", floatArrayOf(
            0.9f, 0.1f, 0.0f, 0f, 10f,
            0.1f, 0.8f, 0.1f, 0f, 5f,
            0.0f, 0.2f, 0.7f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )),
        FilterPreset("Cool", "Blue", floatArrayOf(
            0.8f, 0f, 0.2f, 0f, 0f,
            0f, 0.9f, 0.1f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        )),
        FilterPreset("Warm", "Golden", floatArrayOf(
            1.1f, 0f, 0f, 0f, 10f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 0.8f, 0f, -5f,
            0f, 0f, 0f, 1f, 0f
        )),
        FilterPreset("Dramatic", "High contrast", floatArrayOf(
            1.4f, 0f, 0f, 0f, -30f,
            0f, 1.4f, 0f, 0f, -30f,
            0f, 0f, 1.4f, 0f, -30f,
            0f, 0f, 0f, 1f, 0f
        ))),
        FilterPreset("Noir", "Film noir", ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, -20f,
                0f, 1.2f, 0f, 0f, -20f,
                0f, 0f, 1.2f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )))
        }.getArray()),
        FilterPreset("Pastel", "Soft", ColorMatrix(floatArrayOf(
            0.85f, 0.15f, 0f, 0f, 20f,
            0.1f, 0.8f, 0.1f, 0f, 15f,
            0.05f, 0.1f, 0.85f, 0f, 25f,
            0f, 0f, 0f, 0.9f, 0f
        )).getArray()),
        FilterPreset("HDR", "Vibrant", ColorMatrix(floatArrayOf(
            1.3f, 0f, 0f, 0f, -15f,
            0f, 1.3f, 0f, 0f, -15f,
            0f, 0f, 1.3f, 0f, -15f,
            0f, 0f, 0f, 1.1f, 0f
        )).getArray()),
        FilterPreset("Fade", "Faded film", ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, 30f,
            0f, 0.75f, 0f, 0f, 25f,
            0f, 0f, 0.7f, 0f, 35f,
            0f, 0f, 0f, 0.85f, 0f
        )).getArray())
    )
}
