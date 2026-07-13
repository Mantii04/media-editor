package com.mediaeditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExportProcessor {

    data class ExportConfig(
        val quality: Int = 95,
        val format: ExportFormat = ExportFormat.JPEG,
        val maxDimension: Int = 4096,
        val videoBitrate: Int = 15_000_000
    )

    enum class ExportFormat { JPEG, PNG, MP4 }

    suspend fun exportPhoto(
        context: Context,
        bitmap: Bitmap,
        config: ExportConfig = ExportConfig()
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val scaled = if (maxOf(bitmap.width, bitmap.height) > config.maxDimension) {
                resizeBitmap(bitmap, config.maxDimension)
            } else bitmap

            val compressFormat = when (config.format) {
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            val ext = when (config.format) {
                ExportFormat.JPEG -> ".jpg"
                ExportFormat.PNG -> ".png"
                else -> ".jpg"
            }

            val fileName = "IMG_${System.currentTimeMillis()}$ext"
            val uri = FileUtils.saveBitmapToGallery(context, scaled, fileName)
            if (uri != null) Result.success(uri)
            else Result.failure(Exception("Failed to save image"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val scale = if (maxOf(bitmap.width, bitmap.height) > maxDimension) {
            maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        } else 1f
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
    }
}
