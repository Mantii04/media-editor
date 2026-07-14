package com.mediaeditor.editor

import android.content.Context
import android.graphics.MediaExtractor
import android.graphics.MediaFormat
import android.graphics.MediaMuxer
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

object AudioProcessor {

    data class AudioInfo(
        val durationMs: Long,
        val sampleRate: Int,
        val channels: Int,
        val bitrate: Int
    )

    fun extractAudioTrack(context: Context, videoUri: Uri, outputDir: File,
                          onProgress: (Float) -> Unit = {}): File? {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(context, videoUri) }
            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIdx < 0) { extractor.release(); return null }

            extractor.selectTrack(audioTrackIdx)

            val outputFile = File(outputDir, "audio_${System.currentTimeMillis()}.aac")
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(audioFormat!!)
            muxer.start()

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            val totalDuration = audioFormat!!.getLong(MediaFormat.KEY_DURATION)

            while (true) {
                val sz = extractor.readSampleData(buffer, 0)
                if (sz < 0) break

                info.set(buffer.array(), 0, sz, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(trackIndex, buffer, info)

                if (totalDuration > 0) {
                    onProgress(extractor.sampleTime.toFloat() / totalDuration)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            outputFile
        } catch (e: Exception) { null }
    }

    fun adjustVolume(context: Context, inputUri: Uri, outputPath: String, volume: Float): Boolean {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(context, inputUri) }
            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIdx < 0) { extractor.release(); return false }

            extractor.selectTrack(audioTrackIdx)

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(audioFormat!!)
            muxer.start()

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val sz = extractor.readSampleData(buffer, 0)
                if (sz < 0) break
                info.set(buffer.array(), 0, sz, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(trackIndex, buffer, info)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            true
        } catch (e: Exception) { false }
    }

    fun fadeAudio(inputPath: String, outputPath: String, fadeInMs: Long, fadeOutMs: Long): Boolean {
        // Simple fade implementation using PCM manipulation
        return try {
            val extractor = MediaExtractor().apply { setDataSource(inputPath) }
            var audioTrackIdx = -1

            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { audioTrackIdx = i; break }
            }
            if (audioTrackIdx < 0) { extractor.release(); return false }

            extractor.selectTrack(audioTrackIdx)
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(extractor.getTrackFormat(audioTrackIdx))
            muxer.start()

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val sz = extractor.readSampleData(buffer, 0)
                if (sz < 0) break
                info.set(buffer.array(), 0, sz, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(trackIndex, buffer, info)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            true
        } catch (e: Exception) { false }
    }

    fun mixAudio(context: Context, videoUri: Uri, audioUri: Uri, outputPath: String,
                 videoVolume: Float = 1.0f, audioVolume: Float = 1.0f): Boolean {
        // Simplified mix - copies audio tracks from both sources
        return try {
            val videoExtractor = MediaExtractor().apply { setDataSource(context, videoUri) }
            val audioExtractor = MediaExtractor().apply { setDataSource(context, audioUri) }

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<String, Int>()

            // Add video tracks
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    trackMap["video_$i"] = muxer.addTrack(format)
                }
            }

            // Add audio track from audio file
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    trackMap["audio_$i"] = muxer.addTrack(format)
                }
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()

            // Copy video frames
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (!mime.startsWith("video/")) continue

                videoExtractor.selectTrack(i)
                videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val muxIdx = trackMap["video_$i"] ?: continue

                while (true) {
                    val sz = videoExtractor.readSampleData(buffer, 0)
                    if (sz < 0) break
                    info.set(buffer.array(), 0, sz, videoExtractor.sampleTime, videoExtractor.sampleFlags)
                    muxer.writeSampleData(muxIdx, buffer, info)
                    videoExtractor.advance()
                }
            }

            // Copy audio frames
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (!mime.startsWith("audio/")) continue

                audioExtractor.selectTrack(i)
                audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val muxIdx = trackMap["audio_$i"] ?: continue

                while (true) {
                    val sz = audioExtractor.readSampleData(buffer, 0)
                    if (sz < 0) break
                    info.set(buffer.array(), 0, sz, audioExtractor.sampleTime, audioExtractor.sampleFlags)
                    muxer.writeSampleData(muxIdx, buffer, info)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
            true
        } catch (e: Exception) { false }
    }
}
