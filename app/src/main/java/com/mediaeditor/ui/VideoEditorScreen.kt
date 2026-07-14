package com.mediaeditor.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mediaeditor.editor.VideoProcessor
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File

enum class VideoTool { NONE, TRIM, SPEED, EFFECT, FILTER, AUDIO, TEXT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    mediaUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Player
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = false
        }
    }

    // Video info
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var lastKnownPosition by remember { mutableLongStateOf(0L) }

    // Tools
    var selectedTool by remember { mutableStateOf(VideoTool.TRIM) }
    var isProcessing by remember { mutableStateOf(false) }
    var processedUri by remember { mutableStateOf<Uri?>(null) }

    // Trim
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimEnd by remember { mutableLongStateOf(0L) }

    // Speed
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Load info
    LaunchedEffect(mediaUri) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, mediaUri)
            val dur = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            durationMs = dur; videoWidth = w; videoHeight = h
            trimEnd = dur
        } finally { retriever.release() }
    }

    // Position tracker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            lastKnownPosition = currentPosition
            kotlinx.coroutines.delay(100)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0, durationMs)
        player.seekTo(clamped)
        currentPosition = clamped
        lastKnownPosition = clamped
    }

    DisposableEffect(Unit) { onDispose { player.release() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Video Editor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${videoWidth}x${videoHeight} | ${formatTime(durationMs)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    // More button
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Extract Audio") }, onClick = {
                                showMenu = false
                                scope.launch {
                                    isProcessing = true
                                    val out = File(FileUtils.getCacheDir(context), "audio_${System.nanoTime()}.m4a")
                                    val result = VideoProcessor.extractAudio(context, mediaUri, out)
                                    isProcessing = false
                                    result.onSuccess { snackbarHostState.showSnackbar("Audio extracted ✓") }
                                        .onFailure { snackbarHostState.showSnackbar("Failed") }
                                }
                            })
                            DropdownMenuItem(text = { Text("Export 4K") }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Export HEVC") }, onClick = { showMenu = false })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column {
                    // Tool tabs
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(
                            VideoTool.TRIM to "Trim",
                            VideoTool.SPEED to "Speed",
                            VideoTool.EFFECT to "Effects",
                            VideoTool.TEXT to "Text",
                            VideoTool.AUDIO to "Audio",
                            VideoTool.FILTER to "Filter"
                        ).forEach { (tool, label) ->
                            val isSel = selectedTool == tool
                            TextButton(onClick = { selectedTool = tool },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(label, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp, color = if (isSel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }

                    when (selectedTool) {
                        VideoTool.TRIM -> TrimControls(trimStart, trimEnd, durationMs, currentPosition,
                            { trimStart = it; seekTo(it) }, { trimEnd = it; seekTo(it) },
                            { scope.launch {
                                isProcessing = true
                                val out = File(FileUtils.getCacheDir(context), "trimmed_${System.nanoTime()}.mp4")
                                val result = VideoProcessor.trim(context, mediaUri, out, trimStart, trimEnd)
                                isProcessing = false
                                result.onSuccess { processedUri = it; snackbarHostState.showSnackbar("Trimmed ✓") }
                                    .onFailure { snackbarHostState.showSnackbar("Failed") }
                            }}, isProcessing)
                        VideoTool.SPEED -> SpeedControls(playbackSpeed,
                            { player.setPlaybackSpeed(it); playbackSpeed = it })
                        else -> Spacer(Modifier.height(60.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Player
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        val exoPlayer = player
                        PlayerView(ctx).also { view ->
                            view.player = exoPlayer
                            view.useController = true
                            view.setShowNextButton(false)
                            view.setShowPreviousButton(false)
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            view.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Timeline
            TimelineControls(
                durationMs = durationMs,
                currentPosition = currentPosition,
                trimStart = trimStart,
                trimEnd = trimEnd,
                onSeek = { seekTo(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Playback controls
            Row(Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { seekTo(currentPosition - 5000) }) {
                    Icon(Icons.Default.Replay10, "Back 5s", modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { seekTo(currentPosition + 5000) }) {
                    Icon(Icons.Default.Forward10, "Forward 5s", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineControls(durationMs: Long, currentPosition: Long,
                             trimStart: Long, trimEnd: Long,
                             onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (durationMs <= 0) return
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(trimStart), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(formatTime(trimEnd), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(2.dp))
        Slider(value = currentPosition.toFloat(), onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.toFloat(), modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            Text(formatTime(durationMs), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun TrimControls(trimStart: Long, trimEnd: Long, durationMs: Long,
                         currentPosition: Long,
                         onStartChange: (Long) -> Unit, onEndChange: (Long) -> Unit,
                         onApply: () -> Unit, isProcessing: Boolean) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text("Trim Video", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("In:", fontSize = 11.sp, modifier = Modifier.width(28.dp))
            Text(formatTime(trimStart), fontSize = 11.sp, modifier = Modifier.width(42.dp))
            Slider(trimStart.toFloat(), { onStartChange(it.toLong()) }, 0f..trimEnd.toFloat(),
                Modifier.weight(1f).height(20.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Out:", fontSize = 11.sp, modifier = Modifier.width(28.dp))
            Text(formatTime(trimEnd), fontSize = 11.sp, modifier = Modifier.width(42.dp))
            Slider(trimEnd.toFloat(), { onEndChange(it.toLong()) }, trimStart.toFloat()..durationMs.toFloat(),
                Modifier.weight(1f).height(20.dp))
        }
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth(),
            enabled = ! isProcessing && trimEnd > trimStart) {
            if (isProcessing) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
            Text("Apply Trim (${formatTime(trimEnd - trimStart)})", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SpeedControls(speed: Float, onSpeed: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text("Speed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                FilterChip(selected = speed == s, onClick = { onSpeed(s) },
                    label = { Text("${s}x", fontSize = 11.sp) }, modifier = Modifier.height(28.dp))
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
