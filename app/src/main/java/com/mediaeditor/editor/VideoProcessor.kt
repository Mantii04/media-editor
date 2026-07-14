package com.mediaeditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.net.Uri
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoProcessor {

    data class VideoInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotation: Int = 0
    )

    suspend fun getInfo(context: Context, uri: Uri): VideoInfo {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                VideoInfo(
                    durationMs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L,
                    width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 0,
                    height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 0,
                    rotation = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                    )?.toIntOrNull() ?: 0
                )
            } finally { retriever.release() }
        }
    }

    /** Trim video without re-encoding (fast, preserves quality) */
    suspend fun trim(context: Context, input: Uri, output: File,
                      startMs: Long, endMs: Long): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val inputPath = copyToCache(context, input)
            val extractor = createExtractor(inputPath)
            val trackConfigs = analyzeTracks(extractor)

            if (trackConfigs.isEmpty()) {
                return@withContext Result.failure(Exception("No media tracks found"))
            }

            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTracks = mutableMapOf<Int, Int>()
            var sampleCount = 0L

            for (track in trackConfigs) {
                extractor.selectTrack(track.index)
                val muxerIndex = muxer.addTrack(track.format)
                muxerTracks[track.index] = muxerIndex

                val startUs = startMs * 1000L
                val endUs = endMs * 1000L
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val buffer = ByteBuffer.allocate(512 * 1024)
                val info = MediaCodec.BufferInfo()

                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break

                    val time = extractor.sampleTime
                    if (time == -1L || time > endUs) break

                    if (time >= startUs) {
                        info.set(0, size, time - startUs, extractor.sampleFlags)
                        muxer.writeSampleData(muxerIndex, buffer, info)
                        sampleCount++
                    }
                    extractor.advance()
                }
                extractor.unselectTrack(track.index)
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            if (sampleCount == 0L) {
                Result.failure(Exception("No samples written. Check trim range."))
            } else {
                Result.success(Uri.fromFile(output))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cut video at specified point (keep first part) */
    suspend fun cutAt(context: Context, input: Uri, timeMs: Long, output: File): Result<Uri> {
        return trim(context, input, output, 0, timeMs)
    }

    /** Split video at specified point — returns two parts */
    suspend fun splitAt(context: Context, input: Uri, timeMs: Long, outputDir: File): Result<Pair<Uri, Uri>> = withContext(Dispatchers.IO) {
        try {
            val part1 = File(outputDir, "split1_${System.nanoTime()}.mp4")
            val part2 = File(outputDir, "split2_${System.nanoTime()}.mp4")
            val trimmed = trim(context, input, part1, 0, timeMs)
            if (trimmed.isFailure) return@withContext Result.failure(trimmed.exceptionOrNull()!!)
            val totalDuration = getInfo(context, input).durationMs
            val trimmed2 = trim(context, input, part2, timeMs, totalDuration)
            if (trimmed2.isFailure) return@withContext Result.failure(trimmed2.exceptionOrNull()!!)
            Result.success(Pair(trimmed.getOrThrow(), trimmed2.getOrThrow()))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Extract audio track to AAC/MP4 */
    suspend fun extractAudio(context: Context, input: Uri, output: File): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val inputPath = copyToCache(context, input)
            val extractor = createExtractor(inputPath)
            val tracks = analyzeTracks(extractor)
            val audioTrack = tracks.find { it.isAudio }
                ?: return@withContext Result.failure(Exception("No audio track found"))

            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerIndex = muxer.addTrack(audioTrack.format)
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            extractor.selectTrack(audioTrack.index)
            var wrote = false
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(muxerIndex, buffer, info)
                wrote = true
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            if (wrote) Result.success(Uri.fromFile(output))
            else Result.failure(Exception("Failed to extract audio"))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Extract a single frame as JPEG */
    suspend fun extractFrame(context: Context, input: Uri, timeMs: Long, output: File): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, input)
            val bitmap = retriever.frameAtTime
                ?: return@withContext Result.failure(Exception("Could not extract frame"))
            output.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            retriever.release()
            Result.success(Uri.fromFile(output))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Merge multiple videos sequentially */
    suspend fun merge(context: Context, sources: List<Uri>, output: File): Result<Uri> = withContext(Dispatchers.IO) {
        if (sources.size < 2) return@withContext Result.failure(Exception("Need at least 2 videos"))
        try {
            // Use first video's track format for the muxer
            val firstPath = copyToCache(context, sources[0])
            val firstExtractor = createExtractor(firstPath)
            val tracks = analyzeTracks(firstExtractor)
            firstExtractor.release()

            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTracks = mutableListOf<Int>()
            for (track in tracks) {
                muxerTracks.add(muxer.addTrack(track.format))
            }
            muxer.start()

            var offsetUs = 0L
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()

            for (uri in sources) {
                val path = copyToCache(context, uri)
                val extractor = createExtractor(path)
                val srcTracks = analyzeTracks(extractor)

                for (srcTrack in srcTracks) {
                    val muxerIdx = muxerTracks.getOrNull(srcTrack.index) ?: continue
                    extractor.selectTrack(srcTrack.index)
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.set(0, size, extractor.sampleTime + offsetUs, extractor.sampleFlags)
                        muxer.writeSampleData(muxerIdx, buffer, info)
                        extractor.advance()
                    }
                    extractor.unselectTrack(srcTrack.index)
                }

                offsetUs += extractor.sampleTime
                extractor.release()
            }

            muxer.stop()
            muxer.release()
            Result.success(Uri.fromFile(output))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Change video speed (timestamp adjustment - no re-encode) */
    suspend fun changeSpeed(context: Context, input: Uri, output: File, speed: Float): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val inputPath = copyToCache(context, input)
            val extractor = createExtractor(inputPath)
            val tracks = analyzeTracks(extractor)

            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTracks = mutableMapOf<Int, Int>()
            for (track in tracks) {
                muxerTracks[track.index] = muxer.addTrack(track.format)
            }
            muxer.start()

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            for (track in tracks) {
                val muxerIdx = muxerTracks[track.index] ?: continue
                extractor.selectTrack(track.index)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val adjustedTime = (extractor.sampleTime / speed).toLong()
                    info.set(0, size, adjustedTime, extractor.sampleFlags)
                    muxer.writeSampleData(muxerIdx, buffer, info)
                    extractor.advance()
                }
                extractor.unselectTrack(track.index)
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            Result.success(Uri.fromFile(output))
        } catch (e: Exception) { Result.failure(e) }
    }

    private data class TrackConfig(
        val index: Int,
        val mime: String,
        val format: MediaFormat,
        val isVideo: Boolean,
        val isAudio: Boolean
    )

    private fun createExtractor(path: String) = MediaExtractor().apply { setDataSource(path) }

    private fun analyzeTracks(extractor: MediaExtractor): List<TrackConfig> {
        val tracks = mutableListOf<TrackConfig>()
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            tracks.add(TrackConfig(i, mime, fmt,
                mime.startsWith("video/"), mime.startsWith("audio/")))
        }
        return tracks
    }

    private fun copyToCache(context: Context, uri: Uri): String {
        val cacheDir = FileUtils.getCacheDir(context)
        val file = File(cacheDir, "v_${System.nanoTime()}.mp4")
        if (uri.scheme == "file") return uri.path ?: uri.toString()
        context.contentResolver.openInputStream(uri)?.use { i ->
            file.outputStream().use { o -> i.copyTo(o) }
        }
        return file.absolutePath
    }
}
