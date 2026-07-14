package com.mediaeditor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mediaeditor.model.EditorMode
import com.mediaeditor.ui.MainScreen
import com.mediaeditor.ui.PhotoEditorScreen
import com.mediaeditor.ui.VideoEditorScreen
import com.mediaeditor.ui.theme.MediaEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for shared intent
        val sharedUri = handleSharedIntent(intent)

        setContent {
            MediaEditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(sharedUri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle re-share while app is already running
    }

    /** Extract URI from both SEND and VIEW intents, with persistable permission */
    private fun handleSharedIntent(intent: Intent?): Uri? {
        if (intent == null) return null

        val uri = when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        uri?.let { takePersistableUriPermission(it) }
        return uri
    }

    private fun takePersistableUriPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Permission not available to persist — non-fatal
        }
    }
}

@Composable
private fun AppContent(sharedUri: Uri?) {
    var currentMode by remember { mutableStateOf(EditorMode.HOME) }
    var currentMediaUri by remember { mutableStateOf<Uri?>(null) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Check permissions on first composition
    LaunchedEffect(Unit) {
        permissionsGranted = hasPermissions(context)
        if (sharedUri != null && permissionsGranted) {
            currentMediaUri = sharedUri
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        permissionRequested = true
        permissionsGranted = grantResults.values.all { it }
        if (permissionsGranted && sharedUri != null && currentMode == EditorMode.HOME) {
            currentMediaUri = sharedUri
        }
    }

    // Request permissions on first composition if not granted
    LaunchedEffect(Unit) {
        if (!permissionsGranted && !permissionRequested) {
            permissionLauncher.launch(getRequiredPermissions())
        }
    }

    // Main navigation
    AnimatedContent(
        targetState = currentMode,
        transitionSpec = {
            fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
            fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        },
        label = "screen_transition"
    ) { mode ->
        when (mode) {
            EditorMode.HOME -> {
                if (!permissionsGranted) {
                    PermissionGate(
                        onRetry = {
                            permissionLauncher.launch(getRequiredPermissions())
                        }
                    )
                } else {
                    MainScreen(
                        sharedUri = currentMediaUri,
                        onNavigate = { editorMode, item ->
                            currentMode = editorMode
                            currentMediaUri = item?.uri
                        },
                        onBackToHome = {
                            currentMode = EditorMode.HOME
                            currentMediaUri = null
                        }
                    )
                }
            }
            EditorMode.PHOTO_EDITOR -> {
                currentMediaUri?.let { uri ->
                    PhotoEditorScreen(
                        mediaUri = uri,
                        onBack = {
                            currentMode = EditorMode.HOME
                            currentMediaUri = null
                        }
                    )
                }
            }
            EditorMode.VIDEO_EDITOR -> {
                currentMediaUri?.let { uri ->
                    VideoEditorScreen(
                        mediaUri = uri,
                        onBack = {
                            currentMode = EditorMode.HOME
                            currentMediaUri = null
                        }
                    )
                }
            }
            else -> {
                MainScreen(
                    onNavigate = { editorMode, item ->
                        currentMode = editorMode
                        currentMediaUri = item?.uri
                    },
                    onBackToHome = {
                        currentMode = EditorMode.HOME
                        currentMediaUri = null
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome to MediaEditor Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "We need access to your media library\nto edit photos and videos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Grant Access", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text("Continue without access (limited)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun hasPermissions(context: android.content.Context): Boolean {
    return getRequiredPermissions().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}

private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        emptyArray()
    }
}
