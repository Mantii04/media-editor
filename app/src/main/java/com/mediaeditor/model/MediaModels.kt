package com.mediaeditor.model

import android.net.Uri

data class MediaProject(
    val name: String = "Untitled",
    val mediaItems: MutableList<MediaItem> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)

data class MediaItem(
    val id: Long = System.nanoTime(),
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val durationMs: Long = 0L,       // video only
    val width: Int = 0,
    val height: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

data class EditAction(
    val type: ActionType,
    val params: Map<String, Any> = emptyMap()
)

enum class ActionType {
    CROP, ROTATE, FLIP, RESIZE,
    FILTER, ADJUST_BRIGHTNESS, ADJUST_CONTRAST,
    ADJUST_SATURATION, TEXT_OVERLAY, DRAWING,
    TRIM, SPEED, VOLUME, ADD_AUDIO,
    MERGE, CUT_SPLIT
}

data class FilterPreset(
    val name: String,
    val description: String = "",
    val matrix: FloatArray // 4x5 ColorMatrix
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilterPreset) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()
}

data class VideoSegment(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1.0f
)

data class TextOverlay(
    val id: Long = System.nanoTime(),
    var text: String = "",
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var color: Long = 0xFFFFFFFF,
    var size: Float = 48f,
    var rotation: Float = 0f
)

enum class EditorMode {
    HOME, PHOTO_EDITOR, VIDEO_EDITOR, VIDEO_TIMELINE
}
