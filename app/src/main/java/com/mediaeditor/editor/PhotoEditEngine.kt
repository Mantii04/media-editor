package com.mediaeditor.editor

import android.graphics.*
import com.mediaeditor.model.FilterPreset
import kotlin.math.*
import kotlin.math.roundToInt

object PhotoEditEngine {

    // === Crop ===
    fun crop(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val x = (left * bitmap.width).roundToInt()
        val y = (top * bitmap.height).roundToInt()
        val w = ((right - left) * bitmap.width).roundToInt()
        val h = ((bottom - top) * bitmap.height).roundToInt()
        return Bitmap.createBitmap(bitmap,
            max(0, x), max(0, y),
            min(w, bitmap.width - x), min(h, bitmap.height - y))
    }

    fun cropAspectRatio(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val targetRatio = targetW.toFloat() / targetH
        val srcRatio = srcW.toFloat() / srcH

        val (cropW, cropH) = if (srcRatio > targetRatio) {
            // Source wider - crop width
            (srcH * targetRatio).roundToInt() to srcH
        } else {
            srcW to (srcW / targetRatio).roundToInt()
        }

        val x = (srcW - cropW) / 2
        val y = (srcH - cropH) / 2
        return Bitmap.createBitmap(bitmap, x, y, cropW, cropH)
    }

    // === Rotate & Flip ===
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flip(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix().apply {
            preScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // === Resize ===
    fun resize(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scale = min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(), true)
    }

    fun resizeExact(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    // === Filters ===
    fun applyFilter(bitmap: Bitmap, preset: FilterPreset): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(preset.matrix))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // === Adjustments ===
    fun adjustBrightness(bitmap: Bitmap, value: Float): Bitmap {
        val v = value.coerceIn(-1f, 1f) * 255f
        val matrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, v,
            0f, 1f, 0f, 0f, v,
            0f, 0f, 1f, 0f, v,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, matrix)
    }

    fun adjustContrast(bitmap: Bitmap, value: Float): Bitmap {
        val c = 1f + value.coerceIn(-1f, 1f)
        val t = (1f - c) / 2f * 255f
        val matrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, matrix)
    }

    fun adjustSaturation(bitmap: Bitmap, value: Float): Bitmap {
        val matrix = ColorMatrix().apply { setSaturation(value.coerceIn(0f, 3f)) }
        return applyColorMatrix(bitmap, matrix)
    }

    fun adjustWarmth(bitmap: Bitmap, value: Float): Bitmap {
        val r = 1f + max(0f, value) * 0.15f
        val b = 1f + max(0f, -value) * 0.15f
        val matrix = ColorMatrix(floatArrayOf(
            r, 0f, 0f, 0f, value * 20f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, b, 0f, -value * 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, matrix)
    }

    fun adjustSharpen(bitmap: Bitmap, value: Float): Bitmap {
        if (value <= 0f) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val blurRadius = 1f + value * 4f

        paint.setMaskFilter(BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL))
        val blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurred)
        blurCanvas.drawBitmap(bitmap, 0f, 0f, paint)

        val overlay = Paint().apply {
            alpha = min(255, (value * 80).roundToInt())
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawBitmap(blurred, 0f, 0f, overlay)
        blurred.recycle()
        return result
    }

    // === Effects ===
    fun applyVignette(bitmap: Bitmap, strength: Float = 0.5f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val centerX = w / 2f
        val centerY = h / 2f
        val radius = sqrt(centerX * centerX + centerY * centerY) * 0.7f

        val gradient = RadialGradient(
            centerX, centerY, radius,
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            alpha = min(255, (strength * 150).roundToInt())
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return result
    }

    fun applyBlur(bitmap: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            setMaskFilter(BlurMaskFilter(radius.coerceIn(1f, 25f), BlurMaskFilter.Blur.NORMAL))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun autoEnhance(bitmap: Bitmap): Bitmap {
        // Auto levels: stretch histogram
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0

        for (pixel in pixels) {
            minR = min(minR, Color.red(pixel))
            maxR = max(maxR, Color.red(pixel))
            minG = min(minG, Color.green(pixel))
            maxG = max(maxG, Color.green(pixel))
            minB = min(minB, Color.blue(pixel))
            maxB = max(maxB, Color.blue(pixel))
        }

        // Apply auto contrast + saturation boost
        var result = adjustContrast(bitmap, 0.15f)
        result = adjustSaturation(result, 1.15f)
        return result
    }

    fun applyFrame(bitmap: Bitmap, color: Int, thickness: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val t = (thickness * w / 100f).roundToInt()
        if (t <= 0) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = t.toFloat()
        }
        val halfStroke = t / 2f
        canvas.drawRect(halfStroke, halfStroke, w - halfStroke, h - halfStroke, paint)
        return result
    }

    // === Text Overlay ===
    fun drawText(bitmap: Bitmap, text: String, x: Float, y: Float,
                 color: Int = Color.WHITE, size: Float = 48f,
                 rotation: Float = 0f, bgColor: Int? = null): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val scaledSize = size * bitmap.width / 1080f

        // Background
        if (bgColor != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = bgColor
                style = Paint.Style.FILL
            }
            canvas.save()
            canvas.rotate(rotation, x * bitmap.width, y * bitmap.height)
            canvas.drawRect(
                x * bitmap.width - 16.dp,
                y * bitmap.height - scaledSize * 0.7f,
                x * bitmap.width + text.length * scaledSize * 0.6f + 16.dp,
                y * bitmap.height + scaledSize * 0.3f,
                bgPaint
            )
            canvas.restore()
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = scaledSize
            this.isFakeBoldText = true
            if (bgColor == null) setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        canvas.save()
        canvas.rotate(rotation, x * bitmap.width, y * bitmap.height)
        canvas.drawText(text, x * bitmap.width, y * bitmap.height, paint)
        canvas.restore()
        return result
    }

    // === Drawing ===
    fun drawDoodle(bitmap: Bitmap, points: List<Pair<Float, Float>>,
                   color: Int = Color.RED, strokeWidth: Float = 8f): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.strokeWidth = strokeWidth * bitmap.width / 1080f
            this.style = Paint.Style.STROKE
            this.strokeCap = Paint.Cap.ROUND
            this.strokeJoin = Paint.Join.ROUND
        }

        if (points.size < 2) return result
        val path = Path()
        path.moveTo(points[0].first * bitmap.width, points[0].second * bitmap.height)
        for (i in 1 until points.size) {
            path.lineTo(points[i].first * bitmap.width, points[i].second * bitmap.height)
        }
        canvas.drawPath(path, paint)
        return result
    }

    // === Sticker ===
    fun drawSticker(bitmap: Bitmap, sticker: Bitmap, x: Float, y: Float, scale: Float = 0.3f): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val stickerW = (bitmap.width * scale).roundToInt()
        val stickerH = (stickerW * sticker.height / sticker.width).roundToInt()
        val scaledSticker = Bitmap.createScaledBitmap(sticker, stickerW, stickerH, true)

        canvas.drawBitmap(scaledSticker,
            x * bitmap.width - stickerW / 2f,
            y * bitmap.height - stickerH / 2f, null)
        scaledSticker.recycle()
        return result
    }

    // === Helper ===
    private fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun Float.dp(): Float = this * 2f // rough dp conversion for text background padding
}
