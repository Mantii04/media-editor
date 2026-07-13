package com.mediaeditor.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaeditor.editor.ExportProcessor
import com.mediaeditor.editor.FilterProcessor
import com.mediaeditor.editor.PhotoEditEngine
import com.mediaeditor.model.EditorMode
import com.mediaeditor.model.FilterPreset
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    mediaUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var workingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    // Tool state
    var selectedTool by remember { mutableStateOf("none") }
    var selectedFilter by remember { mutableStateOf<FilterPreset?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var verticalFlip by remember { mutableStateOf(false) }
    var horizontalFlip by remember { mutableStateOf(false) }

    // Text overlay
    var showTextDialog by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf("") }

    // Zoom/pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Load image
    LaunchedEffect(mediaUri) {
        isLoading = true
        val bmp = FileUtils.loadBitmapFromUri(context, mediaUri)
        originalBitmap = bmp
        workingBitmap = bmp
        displayBitmap = bmp
        isLoading = false
    }

    // Apply filter + adjustments
    LaunchedEffect(selectedFilter, brightness, contrast, saturation, rotation, horizontalFlip, verticalFlip) {
        val bmp = workingBitmap ?: return@LaunchedEffect
        var result = bmp

        if (selectedFilter != null && selectedFilter!!.name != "Normal") {
            result = PhotoEditEngine.applyFilter(result, selectedFilter!!)
        }
        if (brightness != 0f) result = PhotoEditEngine.adjustBrightness(result, brightness)
        if (contrast != 0f) result = PhotoEditEngine.adjustContrast(result, contrast)
        if (saturation != 1f) result = PhotoEditEngine.adjustSaturation(result, saturation)

        displayBitmap = result
    }

    // Restore states per tool
    fun resetTool() {
        selectedFilter = null
        brightness = 0f
        contrast = 0f
        saturation = 1f
    }

    fun applyChanges() {
        displayBitmap?.let { workingBitmap = it }
        resetTool()
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            displayBitmap?.let { snapshot ->
                                isExporting = true
                                val result = ExportProcessor.exportPhoto(
                                    context, snapshot,
                                    ExportProcessor.ExportConfig(quality = 95)
                                )
                                isExporting = false
                                result.onSuccess { uri ->
                                    snackbarHostState.showSnackbar("Saved to gallery ✓")
                                }.onFailure { e ->
                                    snackbarHostState.showSnackbar("Save failed: ${e.message}")
                                }
                            }
                        }
                    }) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { EditorToolbar(selectedTool, onToolSelected = { selectedTool = it }) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 4f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                displayBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }

                // Status overlay
                if (selectedTool == "crop") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "Crop: Pinch & drag to adjust",
                            color = Color.White,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Tool panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (selectedTool) {
                        "filter" -> FilterPanel(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { selectedFilter = it }
                        )
                        "adjust" -> AdjustPanel(
                            brightness = brightness,
                            contrast = contrast,
                            saturation = saturation,
                            onBrightnessChange = { brightness = it },
                            onContrastChange = { contrast = it },
                            onSaturationChange = { saturation = it }
                        )
                        "rotate" -> RotatePanel(
                            rotation = rotation,
                            horizontalFlip = horizontalFlip,
                            verticalFlip = verticalFlip,
                            onRotationChange = { rotation = it },
                            onHorizontalFlipChange = { horizontalFlip = it },
                            onVerticalFlipChange = { verticalFlip = it }
                        )
                        "text" -> TextPanel(
                            text = overlayText,
                            onTextChange = { overlayText = it },
                            onApply = {
                                if (overlayText.isNotBlank()) {
                                    displayBitmap = displayBitmap?.let { bmp ->
                                        PhotoEditEngine.drawText(bmp, overlayText, 0.5f, 0.5f)
                                    }
                                    workingBitmap = displayBitmap
                                }
                            }
                        )
                        else -> {
                            if (selectedTool != "none" && selectedTool != "crop") {
                                Text("", modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    selectedTool: String,
    onToolSelected: (String) -> Unit
) {
    val tools = listOf(
        "filter" to Icons.Default.Filter,   // Actually ColorLens, but close enough
        "adjust" to Icons.Default.Tune,
        "rotate" to Icons.Default.RotateRight,
        "text" to Icons.Default.TextFields,
        "crop" to Icons.Default.Crop,
        "none" to Icons.Default.Check
    )

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { (name, icon) ->
                val isSelected = selectedTool == name
                val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // "none" = check = apply changes
                            if (name == "none") {
                                onToolSelected("none")
                            } else {
                                onToolSelected(if (isSelected) "none" else name)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (name == "filter") Icons.Default.Palette else icon,
                            contentDescription = name,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        if (name == "none") "Done" else name.replaceFirstChar { it.uppercase() },
                        fontSize = 10.sp,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    selectedFilter: FilterPreset?,
    onFilterSelected: (FilterPreset) -> Unit
) {
    Column {
        Text("Filters", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FilterProcessor.presets) { preset ->
                val isSelected = selectedFilter?.name == preset.name
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFilterSelected(preset) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Text(
                        preset.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustPanel(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit
) {
    Column {
        Text("Adjustments", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp))

        SliderRow("Brightness", brightness, -0.5f, 0.5f, onBrightnessChange)
        SliderRow("Contrast", contrast, -0.5f, 0.5f, onContrastChange)
        SliderRow("Saturation", saturation, 0f, 2f, onSaturationChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = rangeStart..rangeEnd,
            modifier = Modifier.weight(1f).height(24.dp)
        )
        Text(
            if (label == "Saturation") "%.1f".format(value)
            else "%+.0f".format(value * 100),
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun RotatePanel(
    rotation: Float,
    horizontalFlip: Boolean,
    verticalFlip: Boolean,
    onRotationChange: (Float) -> Unit,
    onHorizontalFlipChange: (Boolean) -> Unit,
    onVerticalFlipChange: (Boolean) -> Unit
) {
    Column {
        Text("Rotate & Flip", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionChip(
                icon = Icons.Default.RotateLeft,
                label = "Rotate L",
                onClick = { onRotationChange(rotation - 90f) }
            )
            ActionChip(
                icon = Icons.Default.RotateRight,
                label = "Rotate R",
                onClick = { onRotationChange(rotation + 90f) }
            )
            ActionChip(
                icon = Icons.Default.FlipToBack,
                label = "H Flip",
                selected = horizontalFlip,
                onClick = { onHorizontalFlipChange(!horizontalFlip) }
            )
            ActionChip(
                icon = Icons.Default.FlipToFront,
                label = "V Flip",
                selected = verticalFlip,
                onClick = { onVerticalFlipChange(!verticalFlip) }
            )
        }
    }
}

@Composable
private fun TextPanel(
    text: String,
    onTextChange: (String) -> Unit,
    onApply: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Enter text...") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onApply) {
            Text("Add")
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}
