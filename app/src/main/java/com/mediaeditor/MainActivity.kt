package com.mediaeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mediaeditor.model.EditorMode
import com.mediaeditor.model.MediaItem
import com.mediaeditor.ui.MainScreen
import com.mediaeditor.ui.PhotoEditorScreen
import com.mediaeditor.ui.VideoEditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUri = intent?.data

        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigator(sharedUri)
                }
            }
        }
    }
}

@Composable
private fun AppNavigator(sharedUri: Uri?) {
    var currentMode by remember { mutableStateOf(EditorMode.HOME) }
    var currentMediaUri by remember { mutableStateOf<Uri?>(sharedUri) }

    if (sharedUri != null && currentMode == EditorMode.HOME) {
        val mimeType = contentResolver?.getType(sharedUri) ?: ""
        currentMode = if (mimeType.startsWith("video/")) EditorMode.VIDEO_EDITOR
                      else EditorMode.PHOTO_EDITOR
    }

    when (currentMode) {
        EditorMode.HOME -> {
            MainScreen(
                onNavigate = { mode, item ->
                    currentMode = mode
                    currentMediaUri = item?.uri
                }
            )
        }
        EditorMode.PHOTO_EDITOR -> {
            currentMediaUri?.let { uri ->
                PhotoEditorScreen(
                    mediaUri = uri,
                    onBack = { currentMode = EditorMode.HOME }
                )
            }
        }
        EditorMode.VIDEO_EDITOR -> {
            currentMediaUri?.let { uri ->
                VideoEditorScreen(
                    mediaUri = uri,
                    onBack = { currentMode = EditorMode.HOME }
                )
            }
        }
        else -> {
            MainScreen(
                onNavigate = { mode, item ->
                    currentMode = mode
                    currentMediaUri = item?.uri
                }
            )
        }
    }
}

// Needed for contentResolver access in composable
@Composable
private val contentResolver: android.content.ContentResolver?
    get() {
        val context = androidx.compose.ui.platform.LocalContext.current
        return context.contentResolver
    }
