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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaeditor.editor.EffectProcessor
import com.mediaeditor.editor.ExportProcessor
import com.mediaeditor.editor.FilterProcessor
import com.mediaeditor.editor.PhotoEditEngine
import com.mediaeditor.model.FilterPreset
import com.mediaeditor.model.TextOverlay
import com.mediaeditor.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class PhotoTool { NONE, FILTER, ADJUST, ROTATE, TEXT, CROP, EFFECT, STICKER, DRAW }

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

    var selectedTool by remember { mutableStateOf(PhotoTool.NONE) }
    var selectedFilter by remember { mutableStateOf<FilterPreset?>(null) }

    // Adjustments
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var vignette by remember { mutableFloatStateOf(0f) }
    var blur by remember { mutableFloatStateOf(0f) }

    // Transform
    var rotation by remember { mutableFloatStateOf(0f) }
    var verticalFlip by remember { mutableStateOf(false) }
    var horizontalFlip by remember { mutableStateOf(false) }

    // Effects
    var glitchIntensity by remember { mutableFloatStateOf(0f) }
    var pixelBlock by remember { mutableIntStateOf(1) }

    // Text overlay
    var textOverlays by remember { mutableStateOf<List<TextOverlay>>(emptyList()) }
    var showTextDialog by remember { mutableStateOf(false) }

    // Zoom/pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Load image
    LaunchedEffect(mediaUri) {
        isLoading = true
        val bmp = withContext(Dispatchers.IO) { FileUtils.loadBitmapFromUri(context, mediaUri) }
        originalBitmap = bmp
        workingBitmap = bmp
        displayBitmap = bmp
        isLoading = false
    }

    // Apply all effects reactively
    LaunchedEffect(selectedFilter, brightness, contrast, saturation, temperature,
        vignette, blur, glitchIntensity, pixelBlock, rotation, horizontalFlip, verticalFlip) {
        val bmp = workingBitmap ?: return@LaunchedEffect
        var result = bmp

        // Filter
        if (selectedFilter != null && selectedFilter!!.name != "Normal") {
            result = PhotoEditEngine.applyFilter(result, selectedFilter!!)
        }
        // Adjustments
        if (brightness != 0f) result = PhotoEditEngine.adjustBrightness(result, brightness)
        if (contrast != 0f) result = PhotoEditEngine.adjustContrast(result, contrast)
        if (saturation != 1f) result = PhotoEditEngine.adjustSaturation(result, saturation)
        // Effects
        if (vignette > 0f) result = EffectProcessor.applyVignette(result, vignette)
        if (blur > 0f) result = EffectProcessor.applyBlur(result, blur)
        if (glitchIntensity > 0f) result = EffectProcessor.applyGlitchEffect(result, glitchIntensity)
        if (pixelBlock > 1) result = EffectProcessor.applyPixelate(result, pixelBlock)

        displayBitmap = result
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            displayBitmap?.let { bmp ->
                                isExporting = true
                                val result = ExportProcessor.exportPhoto(context, bmp)
                                isExporting = false
                                result.onSuccess { snackbarHostState.showSnackbar("Saved ✓") }
                                    .onFailure { snackbarHostState.showSnackbar("Save failed") }
                            }
                        }
                    }) {
                        if (isExporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.Save, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Export") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { PhotoToolbar(selectedTool) { selectedTool = it } }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Preview
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 4f)
                            offsetX += pan.x; offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                displayBitmap?.let { bmp ->
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale,
                            translationX = offsetX, translationY = offsetY))
                }
                // Text overlays
                textOverlays.forEach { ov ->
                    Text(ov.text, color = Color(ov.color), fontSize = ov.fontSize.sp,
                        modifier = Modifier.offset(x = (ov.positionX * 500).dp, y = (ov.positionY * 500).dp))
                }
            }

            // Tool panel
            Surface(Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    when (selectedTool) {
                        PhotoTool.FILTER -> FilterPanel(selectedFilter) { selectedFilter = it }
                        PhotoTool.ADJUST -> AdjustPanel(
                            brightness, contrast, saturation, temperature, vignette, blur,
                            { brightness = it }, { contrast = it }, { saturation = it },
                            { temperature = it }, { vignette = it }, { blur = it })
                        PhotoTool.ROTATE -> RotatePanel(rotation, horizontalFlip, verticalFlip,
                            { rotation = it }, { horizontalFlip = it }, { verticalFlip = it })
                        PhotoTool.TEXT -> TextOverlayPanel(textOverlays, { textOverlays = it })
                        PhotoTool.EFFECT -> EffectPanel(glitchIntensity, pixelBlock,
                            { glitchIntensity = it }, { pixelBlock = it })
                        else -> { if (selectedTool != PhotoTool.NONE) Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoToolbar(selected: PhotoTool, onSelect: (PhotoTool) -> Unit) {
    val tools = listOf(
        PhotoTool.FILTER to Icons.Default.Palette,
        PhotoTool.ADJUST to Icons.Default.Tune,
        PhotoTool.ROTATE to Icons.Default.RotateRight,
        PhotoTool.TEXT to Icons.Default.TextFields,
        PhotoTool.EFFECT to Icons.Default.AutoAwesome,
        PhotoTool.CROP to Icons.Default.Crop
    )
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            tools.forEach { (tool, icon) ->
                val isSel = selected == tool
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(if (isSel) PhotoTool.NONE else tool) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (isSel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    }
                    Text(tool.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 10.sp, color = if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(selected: FilterPreset?, onSelect: (FilterPreset) -> Unit) {
    Text("Filters", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FilterProcessor.presets) { preset ->
            val isSel = selected?.name == preset.name
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(preset) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(preset.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun AdjustPanel(
    brightness: Float, contrast: Float, saturation: Float, temperature: Float,
    vignette: Float, blur: Float,
    onBrightness: (Float) -> Unit, onContrast: (Float) -> Unit, onSaturation: (Float) -> Unit,
    onTemperature: (Float) -> Unit, onVignette: (Float) -> Unit, onBlur: (Float) -> Unit
) {
    Column {
        Text("Adjustments", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        SimpleSlider("Brightness", brightness, -.5f, .5f, onBrightness)
        SimpleSlider("Contrast", contrast, -.5f, .5f, onContrast)
        SimpleSlider("Saturation", saturation, 0f, 2f, onSaturation)
        SimpleSlider("Temperature", temperature, -1f, 1f, onTemperature)
        SimpleSlider("Vignette", vignette, 0f, 1f, onVignette)
        SimpleSlider("Blur", blur, 0f, 25f, onBlur)
    }
}

@Composable
private fun SimpleSlider(label: String, value: Float, rangeStart: Float, rangeEnd: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Slider(value = value, onValueChange = onChange, valueRange = rangeStart..rangeEnd,
            modifier = Modifier.weight(1f).height(20.dp))
        Text(if (value >= 0) "+%.0f".format(value * 100) else "%.0f".format(value * 100),
            fontSize = 10.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun RotatePanel(rotation: Float, hFlip: Boolean, vFlip: Boolean,
                        onRot: (Float) -> Unit, onHFlip: (Boolean) -> Unit, onVFlip: (Boolean) -> Unit) {
    Text("Rotate & Flip", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ChipWithIcon(Icons.Default.RotateLeft, "Rot L") { onRot(rotation - 90f) }
        ChipWithIcon(Icons.Default.RotateRight, "Rot R") { onRot(rotation + 90f) }
        ChipWithIcon(Icons.Default.FlipToBack, "H Flip", hFlip) { onHFlip(!hFlip) }
        ChipWithIcon(Icons.Default.FlipToFront, "V Flip", vFlip) { onVFlip(!vFlip) }
    }
}

@Composable
private fun ChipWithIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                         selected: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp)); Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TextOverlayPanel(overlays: List<TextOverlay>, onOverlays: (List<TextOverlay>) -> Unit) {
    var textInput by remember { mutableStateOf("") }
    Column {
        Text("Text Overlay", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = textInput, onValueChange = { textInput = it },
                placeholder = { Text("Enter text...") }, modifier = Modifier.weight(1f),
                singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (textInput.isNotBlank()) {
                    onOverlays(overlays + TextOverlay(text = textInput))
                    textInput = ""
                }
            }) { Text("Add") }
        }
    }
}

@Composable
private fun EffectPanel(glitch: Float, pixel: Int,
                        onGlitch: (Float) -> Unit, onPixel: (Int) -> Unit) {
    Column {
        Text("Effects", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        SimpleSlider("Glitch", glitch, 0f, 1f, onGlitch)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pixelate", fontSize = 11.sp, modifier = Modifier.width(72.dp))
            Slider(value = pixel.toFloat(), onValueChange = { onPixel(it.roundToInt()) },
                valueRange = 1f..20f, modifier = Modifier.weight(1f).height(20.dp))
            Text("${pixel}px", fontSize = 10.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
        }
    }
}
