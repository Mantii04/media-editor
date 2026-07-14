package com.mediaeditor.model

import android.net.Uri
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/** Main project that contains all media and edits */
@Serializable
data class Project(
    val id: String = System.nanoTime().toString(),
    val name: String = "Untitled",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val mediaItems: List<MediaItem> = emptyList(),
    val timelineTracks: List<TimelineTrack> = emptyList(),
    val aspectRatio: AspectRatio = AspectRatio.FREE,
    val resolution: Resolution = Resolution.RES_1080P,
    val durationMs: Long = 0L
)

@Serializable
data class MediaItem(
    val id: Long = System.nanoTime(),
    @Contextual val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}

@Serializable
data class TimelineTrack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val clips: MutableList<TimelineClip> = mutableListOf(),
    val type: TrackType = TrackType.VIDEO,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f
)

@Serializable
data class TimelineClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val mediaItemId: Long = 0L,
    val startOffsetMs: Long = 0L,
    val endOffsetMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val transition: VideoTransition? = null,
    val effects: List<EffectConfig> = emptyList(),
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val blendMode: String = "normal"
)

enum class TrackType { VIDEO, AUDIO, TEXT, STICKER, OVERLAY }

@Serializable
data class VideoTransition(
    val type: TransitionType = TransitionType.FADE,
    val durationMs: Long = 500L,
    val easing: EasingType = EasingType.LINEAR
)

enum class TransitionType {
    NONE, FADE, CROSS_FADE, SLIDE_LEFT, SLIDE_RIGHT,
    SLIDE_UP, SLIDE_DOWN, WIPE, ZOOM, BLUR
}

enum class EasingType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

@Serializable
data class EffectConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: EffectType = EffectType.NONE,
    val intensity: Float = 1.0f,
    val enabled: Boolean = true
)

enum class EffectType {
    NONE, GLITCH, VIGNETTE, PIXELATE, BLUR, SHARPEN,
    CHROMA_KEY, MOTION_BLUR, DREAM, NEON, BLOOM, DUST
}

@Serializable
data class FilterPreset(
    val name: String,
    val description: String = "",
    val matrix: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilterPreset) return false
        return name == other.name
    }
    override fun hashCode(): Int = name.hashCode()
}

@Serializable
data class TextOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    var text: String = "Text",
    var fontName: String = "Default",
    var fontSize: Float = 48f,
    var color: Long = 0xFFFFFFFF,
    var backgroundColor: Long = 0x00000000,
    var positionX: Float = 0.5f,
    var positionY: Float = 0.5f,
    var rotation: Float = 0f,
    var scale: Float = 1.0f,
    var opacity: Float = 1.0f,
    var alignment: TextAlignment = TextAlignment.CENTER,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var startTimeMs: Long = 0L,
    var endTimeMs: Long = 5000L
)

enum class TextAlignment { LEFT, CENTER, RIGHT }

@Serializable
data class StickerOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    @Contextual val uri: Uri,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 5000L
)

@Serializable
data class Adjustment(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f
)

enum class AspectRatio(val width: Int, val height: Int) {
    FREE(0, 0),
    SQUARE(1, 1),
    PORTRAIT(9, 16),
    LANDSCAPE(16, 9),
    STORY(9, 16),
    CINEMATIC(21, 9),
    TIKTOK(9, 16)
}

enum class Resolution(val width: Int, val height: Int, val label: String) {
    RES_480P(854, 480, "480p SD"),
    RES_720P(1280, 720, "720p HD"),
    RES_1080P(1920, 1080, "1080p Full HD"),
    RES_1440P(2560, 1440, "1440p 2K"),
    RES_2160P(3840, 2160, "2160p 4K")
}

enum class EditorMode {
    HOME, PHOTO_EDITOR, VIDEO_EDITOR, VIDEO_TIMELINE, SETTINGS
}

data class EditAction(
    val type: ActionType,
    val params: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActionType {
    CROP, ROTATE, FLIP, RESIZE,
    FILTER, ADJUST_BRIGHTNESS, ADJUST_CONTRAST,
    ADJUST_SATURATION, ADJUST_TEMPERATURE,
    TEXT_OVERLAY, DRAWING, STICKER,
    TRIM, SPEED, VOLUME, ADD_AUDIO,
    MERGE, CUT, SPLIT, TRANSITION,
    EFFECT, CHROMA_KEY, REVERSE,
    STABILIZE, AUTO_CAPTION, VOICE_CHANGE
}

data class VideoSegment(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1.0f,
    val reverse: Boolean = false
)
