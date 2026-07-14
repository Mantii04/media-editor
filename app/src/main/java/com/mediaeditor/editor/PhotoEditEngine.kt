package com.mediaeditor.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object PhotoEditEngine {

    data class EditParams(
        val brightness: Float = 0f,
        val contrast: Float = 1.0f,
        val saturation: Float = 1.0f,
        val exposure: Float = 0f,
        val highlights: Float = 1.0f,
        val shadows: Float = 1.0f,
        val temperature: Float = 0f,
        val tint: Float = 0f,
        val vignette: Float = 0f,
        val grain: Float = 0f,
        val sharpen: Float = 0f
    )

    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(inputStream, null, opts)
        } catch (e: Exception) { null }
    }

    fun loadBitmapSampled(context: Context, uri: Uri, maxSize: Int = 4096): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, opts)
            val (origW, origH) = opts.outWidth to opts.outHeight
            var sampleSize = 1
            while (origW / sampleSize > maxSize || origH / sampleSize > maxSize) sampleSize *= 2

            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(inputStream2, null, opts2)
        } catch (e: Exception) { null }
    }

    fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun applyAdjustments(bitmap: Bitmap, params: EditParams): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Build combined ColorMatrix
        val matrix = ColorMatrix()

        // Contrast
        val contrastMatrix = ColorMatrix(floatArrayOf(
            params.contrast, 0f, 0f, 0f, 0f,
            0f, params.contrast, 0f, 0f, 0f,
            0f, 0f, params.contrast, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        // Brightness via translation
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, params.brightness,
            0f, 1f, 0f, 0f, params.brightness,
            0f, 0f, 1f, 0f, params.brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(brightnessMatrix)

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(params.saturation)
        matrix.postConcat(satMatrix)

        // Temperature (warmth) - simple approximation
        if (params.temperature != 0f) {
            val tempMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, params.temperature * 8f,
                0f, 1f, 0f, 0f, params.temperature * 4f,
                0f, 0f, 1f, 0f, params.temperature * -4f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(tempMatrix)
        }

        // Tint
        if (params.tint != 0f) {
            val tintMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, params.tint * -4f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, params.tint * 8f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(tintMatrix)
        }

        canvas.drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })

        // Vignette effect
        if (params.vignette > 0f) {
            val w = result.width.toFloat()
            val h = result.height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat() * 0.7f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb((params.vignette * 180).toInt(), 0, 0, 0)),
                    floatArrayOf(0.6f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                xfermode = PorterDuff.Mode.DST_IN
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        return result
    }

    fun cropBitmap(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val x = (left * bitmap.width).coerceIn(0f, bitmap.width - 1f).toInt()
        val y = (top * bitmap.height).coerceIn(0f, bitmap.height - 1f).toInt()
        val w = ((right - left) * bitmap.width).coerceIn(1f, bitmap.width.toFloat()).toInt()
        val h = ((bottom - top) * bitmap.height).coerceIn(1f, bitmap.height.toFloat()).toInt()
        return Bitmap.createBitmap(bitmap, x, y, w.coerceAtMost(bitmap.width - x), h.coerceAtMost(bitmap.height - y))
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) preScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            else preScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun addText(bitmap: Bitmap, text: String, x: Float, y: Float,
                color: Int, size: Float, rotation: Float = 0f): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result).apply { rotate(rotation, x, y) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = size * bitmap.width / 1080f
            isFakeBoldText = true
        }
        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawRoundRect(
            x - 12f, y + bounds.top - 8f,
            x + bounds.width() + 12f, y + bounds.bottom + 8f,
            12f, 12f, bgPaint
        )
        canvas.drawText(text, x, y, paint)
        return result
    }

    fun addSticker(bitmap: Bitmap, sticker: Bitmap, x: Float, y: Float, scale: Float = 1.0f): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val s = scale * bitmap.width / 1080f
        val sw = (sticker.width * s).toInt()
        val sh = (sticker.height * s).toInt()
        val scaled = Bitmap.createScaledBitmap(sticker, sw, sh, true)
        canvas.drawBitmap(scaled, x * bitmap.width - sw / 2f, y * bitmap.height - sh / 2f, null)
        return result
    }

    fun drawOnBitmap(bitmap: Bitmap, points: List<Pair<Float, Float>>, color: Int, strokeWidth: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.strokeWidth = strokeWidth * bitmap.width / 1080f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (points.size >= 2) {
            val path = Path()
            path.moveTo(points[0].first * bitmap.width, points[0].second * bitmap.height)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first * bitmap.width, points[i].second * bitmap.height)
            }
            canvas.drawPath(path, paint)
        }
        return result
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
                   quality: Int = 95): Uri? {
        return try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "photo_${System.currentTimeMillis()}.${if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"}")
            FileOutputStream(file).use { out -> bitmap.compress(format, quality, out) }
            Uri.fromFile(file)
        } catch (e: Exception) { null }
    }

    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val (w, h) = bitmap.width to bitmap.height
        val ratio = minOf(maxWidth.toFloat() / w, maxHeight.toFloat() / h, 1f)
        if (ratio >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    fun adjustColorTemperature(bitmap: Bitmap, temperature: Float): Bitmap {
        val matrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, temperature * 10f,
            0f, 1f, 0f, 0f, temperature * 5f,
            0f, 0f, 1f, 0f, temperature * -5f,
            0f, 0f, 0f, 1f, 0f
        ))
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return result
    }

    private const val TAG = "PhotoEditEngine"
}
