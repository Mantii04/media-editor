package com.mediaeditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.Uri
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object ExportProcessor {

    data class ExportConfig(
        val quality: Int = 95,
        val format: ExportFormat = ExportFormat.JPEG,
        val maxDimension: Int = 4096,
        val videoBitrate: Int = 15_000_000,
        val videoFrameRate: Int = 30,
        val useHEVC: Boolean = true,
        val exportAudio: Boolean = true
    )

    enum class ExportFormat { JPEG, PNG, WEBP, MP4, MOV, GIF }

    suspend fun exportPhoto(
        context: Context,
        bitmap: Bitmap,
        config: ExportConfig = ExportConfig()
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val scaled = if (maxOf(bitmap.width, bitmap.height) > config.maxDimension) {
                resizeBitmap(bitmap, config.maxDimension)
            } else bitmap

            val (compressFormat, ext) = when (config.format) {
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG to ".jpg"
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG to ".png"
                ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP to ".webp"
                else -> Bitmap.CompressFormat.JPEG to ".jpg"
            }

            val fileName = "IMG_${System.currentTimeMillis()}$ext"
            val uri = FileUtils.saveBitmapToGallery(context, scaled, fileName, compressFormat, config.quality)
            if (uri != null) Result.success(uri)
            else Result.failure(Exception("Failed to save image"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun exportVideo(
        context: Context,
        inputUri: Uri,
        outputPath: String,
        config: ExportConfig = ExportConfig()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputFile = FileUtils.copyUriToCache(context, inputUri)
            val extractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }

            val outputMime = if (config.useHEVC) MediaFormat.MIMETYPE_VIDEO_HEVC
                             else MediaFormat.MIMETYPE_VIDEO_H264
            val encoder = selectEncoder(outputMime) ?: return@withContext
                Result.failure(Exception("Encoder not available"))

            var videoTrackIdx = -1
            var audioTrackIdx = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                when {
                    mime.startsWith("video/") -> {
                        videoTrackIdx = i
                        videoFormat = fmt
                        extractor.selectTrack(i)
                    }
                    mime.startsWith("audio/") && config.exportAudio -> {
                        audioTrackIdx = i
                        audioFormat = fmt
                        extractor.selectTrack(i)
                    }
                }
            }

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoOutputTrack = -1
            var audioOutputTrack = -1

            if (videoFormat != null) {
                videoOutputTrack = muxer.addTrack(videoFormat!!)
            }
            if (audioFormat != null) {
                audioOutputTrack = muxer.addTrack(audioFormat!!)
            }
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()

            // Read all selected tracks in timestamp order
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                buffer.clear()
                val sz = extractor.readSampleData(buffer, 0)
                if (sz < 0) break
                val trackIndex = extractor.sampleTrackIndex
                val targetIdx = when (trackIndex) {
                    videoTrackIdx -> videoOutputTrack
                    audioTrackIdx -> audioOutputTrack
                    else -> -1
                }
                if (targetIdx >= 0) {
                    info.set(0, sz, extractor.sampleTime, extractor.sampleFlags)
                    muxer.writeSampleData(targetIdx, buffer, info)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            Result.success(outputPath)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val scale = if (maxOf(bitmap.width, bitmap.height) > maxDimension) {
            maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        } else 1f
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
        )
    }

    private fun selectEncoder(mimeType: String): MediaCodec? {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            for (mime in info.supportedTypes) {
                if (mime.equals(mimeType, ignoreCase = true))
                    return MediaCodec.createByCodecName(info.name)
            }
        }
        return null
    }
}
