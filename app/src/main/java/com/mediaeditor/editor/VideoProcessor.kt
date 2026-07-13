package com.mediaeditor.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    /** Trim video without re-encoding */
    suspend fun trim(context: Context, input: Uri, output: File, startMs: Long, endMs: Long): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%",
            "-ss", formatTime(startMs),
            "-to", formatTime(endMs),
            "-c", "copy",
            "-avoid_negative_ts", "1",
            "-y", "%OUTPUT%"
        )
    }

    /** Merge multiple videos */
    suspend fun merge(context: Context, inputs: List<Uri>, output: File): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = FileUtils.getCacheDir(context)
                val concatFile = File(cacheDir, "concat_${System.nanoTime()}.txt")
                val paths = inputs.map { copyToCache(context, it) }
                concatFile.writeText(paths.joinToString("\n") { "file '$it'" })

                val cmd = "-f concat -safe 0 -i ${concatFile.absolutePath} -c copy -y ${output.absolutePath}"
                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode)) Result.success(Uri.fromFile(output))
                else Result.failure(Exception("Merge: ${session.allLogs.take(200)}"))
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    /** Change playback speed */
    suspend fun changeSpeed(context: Context, input: Uri, speed: Float, output: File): Result<Uri> {
        val s = speed.coerceIn(0.125f, 8f)
        val aFilter = when {
            s <= 0.5f -> "atempo=${s * 2},atempo=0.5"
            s >= 2f -> "atempo=2,atempo=${s / 2}"
            else -> "atempo=$s"
        }
        return runCmd(context, input, output,
            "-i", "%INPUT%",
            "-filter_complex", "[0:v]setpts=${1.0 / s}*PTS[v];[0:a]$aFilter[a]",
            "-map", "[v]", "-map", "[a]",
            "-preset", "fast",
            "-y", "%OUTPUT%"
        )
    }

    /** Replace audio track or mute */
    suspend fun replaceAudio(context: Context, video: Uri, audio: Uri?,
                             videoVolume: Float = 0f, output: File): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                val vPath = copyToCache(context, video)
                if (audio == null) {
                    val cmd = "-i $vPath -an -c:v copy -y ${output.absolutePath}"
                    val s = FFmpegKit.execute(cmd)
                    if (ReturnCode.isSuccess(s.returnCode)) Result.success(Uri.fromFile(output))
                    else Result.failure(Exception("Mute failed"))
                } else {
                    val aPath = copyToCache(context, audio)
                    val filter = if (videoVolume > 0f)
                        "[1:a]aformat=sample_rates=44100:cl=stereo[a1];[0:a]aformat=sample_rates=44100:cl=stereo,volume=$videoVolume[a2];[a1][a2]amix=inputs=2:duration=first"
                    else "[1:a]aformat=sample_rates=44100:cl=stereo[a]"

                    val cmd = "-i $vPath -i $aPath -c:v copy " +
                            "-filter_complex \"$filter\" -map 0:v:0 -map \"[a]\" " +
                            "-shortest -y ${output.absolutePath}"
                    val s = FFmpegKit.execute(cmd)
                    if (ReturnCode.isSuccess(s.returnCode)) Result.success(Uri.fromFile(output))
                    else Result.failure(Exception("Audio: ${s.allLogs.take(200)}"))
                }
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    /** Extract audio to MP3 */
    suspend fun extractAudio(context: Context, input: Uri, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-vn", "-acodec", "libmp3lame", "-y", "%OUTPUT%")
    }

    /** Apply video filter */
    suspend fun applyFilter(context: Context, input: Uri, filterName: String, output: File): Result<Uri> {
        val vf = when (filterName) {
            "grayscale" -> "hue=s=0"
            "sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
            "vintage" -> "curves=vintage"
            "bright" -> "eq=brightness=0.15"
            "dark" -> "eq=brightness=-0.15"
            "cool" -> "colortemperature=4500"
            "warm" -> "colortemperature=6500"
            "blur" -> "boxblur=2:1"
            "negative" -> "negate"
            else -> "null"
        }
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-vf", vf, "-c:a", "copy", "-preset", "fast", "-y", "%OUTPUT%")
    }

    /** Export short segment as GIF */
    suspend fun exportGif(context: Context, input: Uri, startMs: Long, durMs: Long, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-ss", formatTime(startMs), "-t", formatTime(durMs),
            "-vf", "fps=10,scale=480:-1:flags=lanczos", "-y", "%OUTPUT%")
    }

    /** Compress video by setting bitrate */
    suspend fun compress(context: Context, input: Uri, bitrateK: Int, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-b:v", "${bitrateK}k", "-c:a", "aac", "-b:a", "128k",
            "-preset", "medium", "-y", "%OUTPUT%")
    }

    /** Reverse video */
    suspend fun reverse(context: Context, input: Uri, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-vf", "reverse", "-af", "areverse",
            "-preset", "fast", "-y", "%OUTPUT%")
    }

    /** Adjust volume */
    suspend fun adjustVolume(context: Context, input: Uri, vol: Float, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-af", "volume=${vol.coerceIn(0f, 5f)}", "-c:v", "copy", "-y", "%OUTPUT%")
    }

    /** Cut video at specified point */
    suspend fun cutAt(context: Context, input: Uri, timeMs: Long, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-to", formatTime(timeMs), "-c", "copy",
            "-avoid_negative_ts", "1", "-y", "%OUTPUT%")
    }

    /** Extract frame as image */
    suspend fun extractFrame(context: Context, input: Uri, timeMs: Long, output: File): Result<Uri> {
        return runCmd(context, input, output,
            "-i", "%INPUT%", "-ss", formatTime(timeMs).removeSuffix(".000"),
            "-vframes", "1", "-q:v", "2", "-y", "%OUTPUT%")
    }

    // === Internal helper ===
    private suspend fun runCmd(
        context: Context, input: Uri, output: File,
        vararg parts: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val inputPath = copyToCache(context, input)
            val cmd = parts.joinToString(" ")
                .replace("%INPUT%", inputPath)
                .replace("%OUTPUT%", output.absolutePath)
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode))
                Result.success(Uri.fromFile(output))
            else
                Result.failure(Exception("FFmpeg: ${session.allLogs.take(200)}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000.0
        return "%02d:%06.3f".format((s % 3600 / 60).toInt(), s % 60)
    }

    private fun copyToCache(context: Context, uri: Uri): String {
        val cacheDir = FileUtils.getCacheDir(context)
        val file = File(cacheDir, "v_${System.nanoTime()}")
        if (uri.scheme == "file") return uri.path ?: uri.toString()
        context.contentResolver.openInputStream(uri)?.use { i ->
            file.outputStream().use { o -> i.copyTo(o) }
        }
        return file.absolutePath
    }
}
