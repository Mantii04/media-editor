package com.mediaeditor.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.FileOutputStream

object FileUtils {

    fun loadBitmapFromUri(context: Context, uri: Uri, maxSize: Int = 2048): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Decode bounds first
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, opts)
            inputStream.close()

            // Calculate scale
            val scale = maxOf(
                opts.outWidth / maxSize,
                opts.outHeight / maxSize,
                1
            )

            // Decode scaled
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "IMG_${System.currentTimeMillis()}.jpg",
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 95
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mimeType = when (format) {
                Bitmap.CompressFormat.PNG -> "image/png"
                Bitmap.CompressFormat.WEBP -> "image/webp"
                else -> "image/jpeg"
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MediaEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(format, quality, out)
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES + "/MediaEditor"
            )
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
            }
            Uri.fromFile(file)
        }
    }

    fun saveVideoToGallery(
        context: Context,
        sourceUri: Uri,
        fileName: String = "VID_${System.currentTimeMillis()}.mp4"
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MediaEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null

            context.contentResolver.openInputStream(sourceUri)?.let { input ->
                context.contentResolver.openOutputStream(uri)?.let { output ->
                    input.copyTo(output)
                }
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES + "/MediaEditor"
            )
            dir.mkdirs()
            val file = File(dir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        }
    }

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, "mediaeditor").also { it.mkdirs() }
    }

    fun copyUriToCache(context: Context, uri: Uri): File {
        val file = File(getCacheDir(context), "input_${System.nanoTime()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    /** Recursively delete cache directory contents */
    fun clearCache(context: Context) {
        getCacheDir(context).listFiles()?.forEach { it.deleteRecursively() }
    }

    fun getVideoThumbnail(context: Context, uri: Uri, size: Int = 256): Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.frameAtTime
            retriever.release()

            if (bitmap != null) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    minOf(size, bitmap.width),
                    minOf(size * bitmap.height / bitmap.width, size),
                    true
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
