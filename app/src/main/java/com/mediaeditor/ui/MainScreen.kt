package com.mediaeditor.ui

import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mediaeditor.model.EditorMode
import com.mediaeditor.model.MediaItem
import com.mediaeditor.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedUri: Uri? = null,
    onNavigate: (EditorMode, MediaItem?) -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Handle shared content immediately
    LaunchedEffect(sharedUri) {
        if (sharedUri != null) {
            val mimeType = context.contentResolver.getType(sharedUri) ?: ""
            val cursor = context.contentResolver.query(sharedUri, null, null, null, null)
            val fileName = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else "Shared"
                } else "Shared"
            } ?: "Shared"
            cursor?.close()
            val item = MediaItem(uri = sharedUri, fileName = fileName, mimeType = mimeType)
            if (mimeType.startsWith("video/")) onNavigate(EditorMode.VIDEO_EDITOR, item)
            else onNavigate(EditorMode.PHOTO_EDITOR, item)
        }
    }

    // Load media
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { loadMediaItems(context, 60) }.let {
            mediaItems = it; isLoading = false
        }
    }

    // Pickers
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val item = MediaItem(uri = it, fileName = "Photo", mimeType = "image/*")
            onNavigate(EditorMode.PHOTO_EDITOR, item)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val item = MediaItem(uri = it, fileName = "Video", mimeType = "video/*")
            onNavigate(EditorMode.VIDEO_EDITOR, item)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediaEditor Pro", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Quick action cards
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickCard("Edit Photo", Icons.Filled.Image,
                        listOf(EditorPrimary, EditorPrimaryLight)) { photoPicker.launch("image/*") }
                }
                item {
                    QuickCard("Edit Video", Icons.Filled.VideoFile,
                        listOf(GradientStart, GradientEnd)) { videoPicker.launch("video/*") }
                }
                item {
                    QuickCard("Trim Video", Icons.Filled.ContentCut,
                        listOf(EditorSecondary, EditorSecondaryDark)) { videoPicker.launch("video/*") }
                }
                item {
                    QuickCard("Merge", Icons.Filled.Layers,
                        listOf(SuccessGreen, EditorSecondary)) { videoPicker.launch("video/*") }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Media grid
            Text("Recent Media", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EditorPrimary)
                }
            } else if (mediaItems.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(48.dp), tint = TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("No media found", color = TextSecondary)
                        Text("Grant media access to see your files", color = TextTertiary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(400.dp)
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCard)
                                .clickable {
                                    val mode = if (item.isVideo) EditorMode.VIDEO_EDITOR else EditorMode.PHOTO_EDITOR
                                    onNavigate(mode, item)
                                }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (item.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(0.7f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun QuickCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      gradient: List<Color>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(130.dp).height(90.dp),
        shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(gradient), RoundedCornerShape(14.dp)).padding(12.dp)) {
            Column {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

private suspend fun loadMediaItems(context: android.content.Context, limit: Int): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val resolver = context.contentResolver

    // Images
    resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_ADDED),
        null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        var count = 0
        while (c.moveToNext() && count < limit) {
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getString(idCol))
            items.add(MediaItem(uri = uri, fileName = c.getString(nameCol),
                mimeType = c.getString(mimeCol) ?: "image/*"))
            count++
        }
    }

    // Videos
    resolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_ADDED),
        null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        var count = 0
        while (c.moveToNext() && count < limit / 2) {
            val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getString(idCol))
            items.add(MediaItem(uri = uri, fileName = c.getString(nameCol),
                mimeType = "video/*", durationMs = c.getLong(2)))
        }
    }

    return items.sortedByDescending { it.addedAt }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}
