package com.mediaeditor.editor

import android.graphics.*
import com.mediaeditor.model.VideoTransition
import com.mediaeditor.model.TransitionType

object EffectProcessor {

    fun applyTransition(frame: Bitmap, prevFrame: Bitmap?, progress: Float, type: TransitionType): Bitmap {
        if (prevFrame == null) return frame
        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = frame.width.toFloat()
        val h = frame.height.toFloat()

        when (type) {
            TransitionType.FADE -> {
                val alpha = (progress * 255).toInt()
                val paint = Paint().apply { this.alpha = alpha }
                canvas.drawBitmap(prevFrame, 0f, 0f, paint)
            }
            TransitionType.CROSS_FADE -> {
                canvas.drawBitmap(prevFrame, 0f, 0f, null)
                val alpha = (progress * 255).toInt()
                val paint = Paint().apply { this.alpha = alpha }
                canvas.drawBitmap(frame, 0f, 0f, paint)
            }
            TransitionType.SLIDE_LEFT -> {
                val offset = w * (1f - progress)
                canvas.drawBitmap(prevFrame, -offset, 0f, null)
                canvas.drawBitmap(frame, w - offset, 0f, null)
            }
            TransitionType.SLIDE_RIGHT -> {
                val offset = w * (1f - progress)
                canvas.drawBitmap(prevFrame, offset, 0f, null)
                canvas.drawBitmap(frame, -(w - offset), 0f, null)
            }
            TransitionType.SLIDE_UP -> {
                val offset = h * (1f - progress)
                canvas.drawBitmap(prevFrame, 0f, -offset, null)
                canvas.drawBitmap(frame, 0f, h - offset, null)
            }
            TransitionType.SLIDE_DOWN -> {
                val offset = h * (1f - progress)
                canvas.drawBitmap(prevFrame, 0f, offset, null)
                canvas.drawBitmap(frame, 0f, -(h - offset), null)
            }
            TransitionType.WIPE -> {
                val splitX = w * progress
                canvas.drawBitmap(prevFrame, 0f, 0f, null)
                canvas.save()
                canvas.clipRect(splitX, 0f, w, h)
                canvas.drawBitmap(frame, 0f, 0f, null)
                canvas.restore()
            }
            TransitionType.ZOOM -> {
                val scale = 1f + (1f - progress) * 0.3f
                canvas.drawBitmap(frame, 0f, 0f, null)
                canvas.save()
                canvas.scale(scale, scale, w / 2f, h / 2f)
                val alpha = ((1f - progress) * 255).toInt()
                canvas.drawBitmap(prevFrame, 0f, 0f, Paint().apply { this.alpha = alpha })
                canvas.restore()
            }
            TransitionType.BLUR -> {
                val alpha = ((1f - progress) * 255).toInt()
                canvas.drawBitmap(frame, 0f, 0f, null)
                val paint = Paint().apply { this.alpha = alpha }
                canvas.drawBitmap(prevFrame, 0f, 0f, paint)
            }
            else -> {
                canvas.drawBitmap(frame, 0f, 0f, null)
            }
        }
        return result
    }

    fun applyBlur(bitmap: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    // Box-blur approximation via color matrix
                    val blur = radius.coerceIn(0f, 25f) / 25f
                    setScale(1f - blur * 0.5f, 1f - blur * 0.5f, 1f - blur * 0.5f, 1f)
                }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun applyGlitchEffect(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = bitmap.width
        val h = bitmap.height
        val shift = (intensity * 20).toInt()

        // Channel splitting effect
        val paintR = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, shift.toFloat(),
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val paintB = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -shift.toFloat(),
                0f, 0f, 0f, 1f, 0f
            )))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawBitmap(bitmap, 0f, 0f, paintR)
        canvas.drawBitmap(bitmap, 0f, 0f, paintB)
        return result
    }

    fun applyPixelate(bitmap: Bitmap, blockSize: Int): Bitmap {
        if (blockSize <= 1) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 0 until bitmap.height step blockSize) {
            for (x in 0 until bitmap.width step blockSize) {
                val px = x.coerceAtMost(bitmap.width - blockSize)
                val py = y.coerceAtMost(bitmap.height - blockSize)
                val pixel = pixels[py * bitmap.width + px]
                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val ix = (x + dx).coerceAtMost(bitmap.width - 1)
                        val iy = (y + dy).coerceAtMost(bitmap.height - 1)
                        result.setPixel(ix, iy, pixel)
                    }
                }
            }
        }
        return result
    }

    fun applyVignette(bitmap: Bitmap, strength: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius * 0.7f,
                intArrayOf(Color.TRANSPARENT, Color.argb((strength * 180).toInt(), 0, 0, 0)),
                floatArrayOf(0.7f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        return result
    }
}
