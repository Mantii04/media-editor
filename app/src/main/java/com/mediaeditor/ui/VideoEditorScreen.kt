package com.mediaeditor.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

    // Video info extracted via MediaMetadataRetriever
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Position tracking
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    // Editing state
    var selectedTool by remember { mutableStateOf("trim") }
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimEnd by remember { mutableLongStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var processedUri by remember { mutableStateOf<Uri?>(null) }

    // Speed
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Audio
    var showAudioPicker by remember { mutableStateOf(false) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }

    // Load video info
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
            durationMs = dur
            videoWidth = w
            videoHeight = h
            trimEnd = dur
        } finally {
            retriever.release()
        }
    }

    // Track position for UI
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            kotlinx.coroutines.delay(100)
        }
    }

    // Listen for playback state
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0, durationMs)
        player.seekTo(clamped)
        currentPosition = clamped
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Video Editor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${videoWidth}x${videoHeight} | ${formatTime(durationMs)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSpeedMenu = true }, enabled = !isProcessing) {
                            Icon(Icons.Default.Speed, "Speed")
                        }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            listOf(0.25f, 0.5f, 1f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        playbackSpeed = speed
                                        player.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    },
                                    leadingIcon = {
                                        if (speed == playbackSpeed) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("trim" to "Trim", "speed" to "Speed").forEach { (key, label) ->
                            val isSel = selectedTool == key
                            TextButton(
                                onClick = { selectedTool = key },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(label,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    when (selectedTool) {
                        "trim" -> TrimPanel(
                            trimStart = trimStart, trimEnd = trimEnd,
                            durationMs = durationMs, currentPosition = currentPosition,
                            onTrimStartChange = { trimStart = it; seekTo(it) },
                            onTrimEndChange = { trimEnd = it; seekTo(it) },
                            onApply = {
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        val cacheDir = FileUtils.getCacheDir(context)
                                        val outFile = File(cacheDir, "trimmed_${System.currentTimeMillis()}.mp4")
                                        val result = VideoProcessor.trim(
                                            context, mediaUri, outFile, trimStart, trimEnd
                                        )
                                        result.onSuccess { uri ->
                                            processedUri = uri
                                            snackbarHostState.showSnackbar("Trimmed ✓")
                                        }.onFailure { e ->
                                            snackbarHostState.showSnackbar("Failed: ${e.message}")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Error: ${e.message}")
                                    }
                                    isProcessing = false
                                }
                            },
                            isProcessing = isProcessing
                        )
                        "speed" -> SpeedPanel(
                            speed = playbackSpeed,
                            onSpeedChange = { player.setPlaybackSpeed(it); playbackSpeed = it }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Video preview using AndroidView wrapping PlayerView
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = this@apply.player
                            useController = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Timeline
            TimelineBar(
                durationMs = durationMs, currentPosition = currentPosition,
                trimStart = trimStart, trimEnd = trimEnd,
                onSeek = { seekTo(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { seekTo(currentPosition - 5000) }) {
                    Icon(Icons.Default.Replay10, "Back 5s", modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { seekTo(currentPosition + 5000) }) {
                    Icon(Icons.Default.Forward10, "Forward 5s", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineBar(
    durationMs: Long, currentPosition: Long, trimStart: Long, trimEnd: Long,
    onSeek: (Long) -> Unit, modifier: Modifier = Modifier
) {
    if (durationMs <= 0) return
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(trimStart), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(formatTime(trimEnd), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Text(formatTime(currentPosition), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TrimPanel(
    trimStart: Long, trimEnd: Long, durationMs: Long, currentPosition: Long,
    onTrimStartChange: (Long) -> Unit, onTrimEndChange: (Long) -> Unit,
    onApply: () -> Unit, isProcessing: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Trim Video", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start:", fontSize = 12.sp, modifier = Modifier.width(40.dp))
            Text(formatTime(trimStart), fontSize = 12.sp, modifier = Modifier.width(52.dp))
            Slider(
                value = trimStart.toFloat(),
                onValueChange = { onTrimStartChange(it.toLong()) },
                valueRange = 0f..trimEnd.toFloat(),
                modifier = Modifier.weight(1f).height(24.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("End:", fontSize = 12.sp, modifier = Modifier.width(40.dp))
            Text(formatTime(trimEnd), fontSize = 12.sp, modifier = Modifier.width(52.dp))
            Slider(
                value = trimEnd.toFloat(),
                onValueChange = { onTrimEndChange(it.toLong()) },
                valueRange = trimStart.toFloat()..durationMs.toFloat(),
                modifier = Modifier.weight(1f).height(24.dp)
            )
        }
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing && trimEnd > trimStart
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text("Apply Trim (${formatTime(trimEnd - trimStart)})")
        }
    }
}

@Composable
private fun SpeedPanel(
    speed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Playback Speed", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.25f, 0.5f, 1f, 1.5f, 2f).forEach { s ->
                FilterChip(
                    selected = speed == s,
                    onClick = { onSpeedChange(s) },
                    label = { Text("${s}x", fontSize = 13.sp) }
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
