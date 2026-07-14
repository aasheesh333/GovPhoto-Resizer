package com.dhanuk.govphoto_resizer.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dhanuk.govphoto_resizer.R
import com.dhanuk.govphoto_resizer.data.ml.FaceAnalysisResult
import com.dhanuk.govphoto_resizer.data.model.PhotoPreset
import com.dhanuk.govphoto_resizer.ui.theme.*
import com.dhanuk.govphoto_resizer.ui.viewmodel.BackgroundColor
import com.dhanuk.govphoto_resizer.ui.viewmodel.BackgroundOption
import com.dhanuk.govphoto_resizer.ui.viewmodel.EditState
import com.dhanuk.govphoto_resizer.ui.viewmodel.SharedPhotoViewModel
/**
 * Edit Photo Screen - Face alignment, background selection, and compression controls.
 * Displays the selected image with editing tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreen(
    sharedViewModel: SharedPhotoViewModel,
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val selectedImageUri by sharedViewModel.selectedImageUri.collectAsState()
    val originalBitmap by sharedViewModel.originalBitmap.collectAsState()
    val pristineOriginal by sharedViewModel.pristineOriginalBitmap.collectAsState()
    val displayedBitmap by sharedViewModel.displayedBitmap.collectAsState()
    val backgroundColor by sharedViewModel.backgroundColor.collectAsState()
    val compressionQuality by sharedViewModel.compressionQuality.collectAsState()
    val fileSizeKb by sharedViewModel.fileSizeKb.collectAsState()
    val selectedPreset by sharedViewModel.selectedPreset.collectAsState()
    val faceAnalysis by sharedViewModel.faceAnalysis.collectAsState()
    val rotationDegrees by sharedViewModel.rotationDegrees.collectAsState()
    val canUndo by sharedViewModel.canUndo.collectAsState()
    val canRedo by sharedViewModel.canRedo.collectAsState()
    val isRemovingBackground by sharedViewModel.isRemovingBackground.collectAsState()

    // Dynamic aspect ratio from preset
    val aspectRatio = sharedViewModel.aspectRatio
    val format = selectedPreset?.format?.uppercase() ?: "JPG"
    val maxSize = selectedPreset?.maxFileSizeKb ?: 500

    // Local UI state
    var selectedBackground by remember { mutableStateOf(BackgroundOption.NONE) }
    var compressionValue by remember { mutableFloatStateOf(compressionQuality) }

    // Image transformation — scale=1 + Crop = cover-fill. Do NOT key remember on
    // rotationDegrees (that wiped zoom/pan on every rotate and broke undo).
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var previewBoxSize by remember { mutableStateOf(IntSize.Zero) }
    // When true, UI restores from undo/redo — do NOT push history / re-trigger bg.
    var isRestoring by remember { mutableStateOf(false) }

    var showExitDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Discard edits?") },
            text = { Text("Your changes will be lost. Are you sure you want to go back?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showExitDialog = false
                        onNavigateBack()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showExitDialog = false }
                ) { Text("Keep editing") }
            }
        )
    }

    val renderBitmap = displayedBitmap ?: pristineOriginal ?: originalBitmap

    fun currentEditState(
        s: Float = scale,
        ox: Float = offsetX,
        oy: Float = offsetY,
        rot: Int = rotationDegrees,
        bg: BackgroundOption = selectedBackground,
        comp: Float = compressionValue
    ) = EditState(
        scale = s, offX = ox, offY = oy,
        rotationDegrees = rot,
        bgOption = bg,
        compression = comp
    )

    fun commitHistory(
        s: Float = scale,
        ox: Float = offsetX,
        oy: Float = offsetY,
        rot: Int = rotationDegrees,
        bg: BackgroundOption = selectedBackground,
        comp: Float = compressionValue
    ) {
        if (isRestoring) return
        sharedViewModel.pushHistory(currentEditState(s, ox, oy, rot, bg, comp))
    }

    // Seed baseline once image is ready so Undo has a return point.
    LaunchedEffect(originalBitmap) {
        val bmp = originalBitmap
        if (bmp != null && !bmp.isRecycled) {
            sharedViewModel.ensureHistoryBaseline(
                EditState(
                    scale = 1f, offX = 0f, offY = 0f,
                    rotationDegrees = 0,
                    bgOption = BackgroundOption.NONE,
                    compression = compressionQuality
                )
            )
        }
    }

    LaunchedEffect(selectedImageUri, originalBitmap) {
        if (selectedImageUri != null || originalBitmap != null) {
            sharedViewModel.analyzeFace()
        }
    }

    // Reset framing only when image or preset changes — NOT on rotate.
    LaunchedEffect(selectedPreset?.id, originalBitmap) {
        if (!isRestoring) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    // Debounce pan/pinch into one undo step after user stops moving fingers.
    // Skip while restoring undo/redo so we don't re-push the restored state.
    LaunchedEffect(scale, offsetX, offsetY, isRestoring) {
        if (isRestoring) return@LaunchedEffect
        delay(400)
        if (!isRestoring) commitHistory()
    }

    fun applyBackgroundOption(option: BackgroundOption) {
        selectedBackground = option
        if (option == BackgroundOption.NONE) {
            sharedViewModel.setBackgroundColor(BackgroundColor.WHITE)
            sharedViewModel.skipBackgroundRemoval()
        } else {
            sharedViewModel.setBackgroundColor(option.toBackgroundColor())
            sharedViewModel.removeBackground()
        }
    }

    // Apply undo/redo — sync local UI; keep isRestoring true briefly so
    // pan debounce does not immediately re-commit the restored values.
    fun applyEditState(state: EditState?) {
        if (state == null) return
        isRestoring = true
        scale = state.scale
        offsetX = state.offX
        offsetY = state.offY
        selectedBackground = state.bgOption
        compressionValue = state.compression
        sharedViewModel.setCompressionQuality(state.compression)
        // restoreFromState already ran inside undo/redo (rotation + bg)
    }

    // Clear restoring flag shortly after undo/redo UI sync
    LaunchedEffect(isRestoring) {
        if (isRestoring) {
            delay(450)
            isRestoring = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_photo),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // Undo / Redo sit LEFT of the "2/3" indicator. Disabled when
                    // history stack is empty / has no backward / forward entry.
                    IconButton(
                        onClick = { applyEditState(sharedViewModel.undoEdit()) },
                        enabled = canUndo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = stringResource(R.string.undo)
                        )
                    }
                    IconButton(
                        onClick = { applyEditState(sharedViewModel.redoEdit()) },
                        enabled = canRedo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = stringResource(R.string.redo)
                        )
                    }
                    Text(
                        text = "2/3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        // Continue = bake visible cover-window region into _bakedBitmap.
                        val bw = previewBoxSize.width.toFloat().coerceAtLeast(1f)
                        val bh = previewBoxSize.height.toFloat().coerceAtLeast(1f)
                        if (sharedViewModel.bakeTransform(scale, offsetX, offsetY, bw, bh)) {
                            onContinue()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.continue_to_save),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = stringResource(R.string.cd_navigate_forward),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Custom Preset Inputs (only for MANUAL preset) — shown at top so the
            // user can type width/height and immediately see the preview box ratio
            // update; the live preview below reflects whatever they entered.
            if (selectedPreset?.id == PhotoPreset.MANUAL_PRESET_ID) {
                CustomPresetInputs(sharedViewModel)
                Spacer(modifier = Modifier.height(16.dp))
                // Re-trigger auto-fit when the user changes the custom dimensions —
                // the displayed bitmap recovers to pristine+rotated baseline so the
                // user can re-pan to the new aspect-ratio box. No physical crop here.
                LaunchedEffect(aspectRatio) {
                    val ob = originalBitmap
                    if (ob != null && !ob.isRecycled) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        sharedViewModel.autoFitToPreset()
                    }
                }
            }

            // Photo Preview with actual image and dynamic aspect ratio.
            // Visual-only zoom/pan inside the preset-aspect-ratio box; no
            // physical crop until the user taps Continue (bakeTransform).
            PhotoPreviewWithImage(
                imageUri = selectedImageUri,
                bitmap = renderBitmap,
                backgroundColor = selectedBackground,
                aspectRatio = aspectRatio,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                faceAnalysis = faceAnalysis,
                isRemovingBackground = isRemovingBackground,
                onBoxSize = { previewBoxSize = it },
                onTransform = { newScale, newOffsetX, newOffsetY ->
                    val nextScale = (scale * newScale).coerceIn(0.25f, 4f)
                    scale = nextScale
                    val maxPan = maxOf(previewBoxSize.width, previewBoxSize.height)
                        .toFloat().coerceAtLeast(1f) * nextScale
                    offsetX = (offsetX + newOffsetX).coerceIn(-maxPan, maxPan)
                    offsetY = (offsetY + newOffsetY).coerceIn(-maxPan, maxPan)
                },
                onReset = {
                    sharedViewModel.resetAllEditsAndRefit()
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    selectedBackground = BackgroundOption.NONE
                    compressionValue = 0.7f
                },
                onRotate = {
                    // Full-image rotate; reset framing so box re-covers (no crop feel).
                    sharedViewModel.rotate90()
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    commitHistory(
                        s = 1f, ox = 0f, oy = 0f,
                        rot = (rotationDegrees + 90) % 360
                    )
                },
                onZoom = { factor ->
                    scale = (scale * factor).coerceIn(0.25f, 4f)
                    commitHistory()
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.align_face),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            BackgroundSelector(
                selectedOption = selectedBackground,
                onOptionSelected = { option ->
                    if (option == selectedBackground) return@BackgroundSelector
                    applyBackgroundOption(option)
                    commitHistory(bg = option)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            CompressionControl(
                value = compressionValue,
                onValueChange = {
                    compressionValue = it
                    sharedViewModel.setCompressionQuality(it)
                },
                onValueChangeFinished = {
                    commitHistory(comp = compressionValue)
                },
                estimatedSize = fileSizeKb,
                format = format,
                maxSize = maxSize
            )
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun PhotoPreviewWithImage(
    imageUri: Uri?,
    bitmap: Bitmap?,
    backgroundColor: BackgroundOption,
    aspectRatio: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    faceAnalysis: FaceAnalysisResult?,
    isRemovingBackground: Boolean,
    onBoxSize: (IntSize) -> Unit,
    onTransform: (Float, Float, Float) -> Unit,
    onReset: () -> Unit,
    onRotate: () -> Unit,
    onZoom: (Float) -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio) // Preset window size
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .onSizeChanged { onBoxSize(it) },
        contentAlignment = Alignment.Center
    ) {
        // Background color layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when (backgroundColor) {
                        BackgroundOption.NONE -> Color.Black.copy(alpha = 0.05f)
                        BackgroundOption.WHITE -> Color.White
                        BackgroundOption.STUDIO_BLUE -> Color(0xFFB8D4E8)
                        BackgroundOption.LIGHT_GREY -> Color(0xFFE8E8E8)
                        BackgroundOption.GRADIENT -> Color(0xFFB8D4E8)
                        BackgroundOption.TRANSPARENT -> Color.LightGray.copy(alpha = 0.3f)
                    }
                )
        )
        
        // Fit: full image visible inside preset box. User zooms/pans to
        // frame the shot. Physical crop happens only on Continue (bakeTransform).
        if (bitmap != null && !bitmap.isRecycled) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected photo",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onTransform(zoom, pan.x, pan.y)
                        }
                    },
                contentScale = ContentScale.Fit
            )
        } else if (imageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Selected photo",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onTransform(zoom, pan.x, pan.y)
                        }
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            // Placeholder when no image
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = stringResource(R.string.cd_no_image),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                    Text(
                    text = "No image selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isRemovingBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                com.dhanuk.govphoto_resizer.ui.components.SafeCircularSpinner(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
        
        // Grid overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val strokeWidth = 1f
            val gridColor = Color.White.copy(alpha = 0.3f)
            
            // Vertical lines (rule of thirds)
            drawLine(
                color = gridColor,
                start = Offset(size.width / 3, 0f),
                end = Offset(size.width / 3, size.height),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = gridColor,
                start = Offset(2 * size.width / 3, 0f),
                end = Offset(2 * size.width / 3, size.height),
                strokeWidth = strokeWidth
            )
            
            // Horizontal lines
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height / 3),
                end = Offset(size.width, size.height / 3),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, 2 * size.height / 3),
                end = Offset(size.width, 2 * size.height / 3),
                strokeWidth = strokeWidth
            )
        }
        
        // Face oval guide — proportions match FaceAnalyzer.defaultOvalGuide
        // so the visual guide matches the actual detection oval.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ovalColor = when {
                faceAnalysis == null -> Color.White.copy(alpha = 0.6f)
                faceAnalysis.faceCount == 0 -> Color(0xFFE53935) // red — no face
                faceAnalysis.isWithinMargin -> Color(0xFF2E7D32) // green
                else -> Color(0xFFFFA000) // amber
            }
            // Match FaceAnalyzer.defaultOvalGuide: 60% of min(w,h), height = width * 1.25
            val ovalW = minOf(size.width, size.height) * 0.60f
            val ovalH = ovalW * 1.25f
            val left = (size.width - ovalW) / 2f
            val top = (size.height - ovalH) / 2f
            drawOval(
                color = ovalColor,
                topLeft = Offset(left, top),
                size = Size(ovalW, ovalH),
                style = Stroke(width = 3f),
            )
            // Map face bounds with ContentScale.Fit + graphicsLayer transform.
            val bounds = faceAnalysis?.bounds
            val srcW = (bitmap?.width ?: 0).toFloat()
            val srcH = (bitmap?.height ?: 0).toFloat()
            if (bounds != null && srcW > 0f && srcH > 0f) {
                val fit = minOf(size.width / srcW, size.height / srcH)
                val ts = fit * scale
                val drawnW = srcW * ts
                val drawnH = srcH * ts
                val originX = (size.width - drawnW) / 2f + offsetX
                val originY = (size.height - drawnH) / 2f + offsetY
                drawRect(
                    color = Color.Cyan.copy(alpha = 0.7f),
                    topLeft = Offset(originX + bounds.left * ts, originY + bounds.top * ts),
                    size = Size(bounds.width() * ts, bounds.height() * ts),
                    style = Stroke(width = 2f),
                )
            }
        }
        
        // Control buttons row: [Reset] [Rotate] [Zoom-] [Zoom %] [Zoom+]
        // Crop / Undo-Crop removed — physical crop happens only on Continue
        // (bakeTransform). Undo/Redo now live in the TopAppBar, not here.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Reset button — restores pristine + clears history
            FloatingActionButton(
            onClick = onReset,
            modifier = Modifier.size(48.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset edits",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Rotate button — rotates the bitmap 90° clockwise each tap
            FloatingActionButton(
            onClick = onRotate,
            modifier = Modifier.size(48.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate 90 degrees",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Zoom out button
            FloatingActionButton(
            onClick = { onZoom(0.9f) },
            modifier = Modifier.size(48.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Zoom out",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Zoom indicator
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Zoom in button
            FloatingActionButton(
            onClick = { onZoom(1.1f) },
            modifier = Modifier.size(48.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Zoom in",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BackgroundSelector(
    selectedOption: BackgroundOption,
    onOptionSelected: (BackgroundOption) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.background),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Two rows of 3 items each — keeps labels on a single line
        BackgroundOption.entries.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    BackgroundOptionItem(
                        option = option,
                        isSelected = selectedOption == option,
                        onClick = { onOptionSelected(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundOptionItem(
    option: BackgroundOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (option) {
        BackgroundOption.NONE -> MaterialTheme.colorScheme.surfaceVariant
        BackgroundOption.WHITE -> Color.White
        BackgroundOption.STUDIO_BLUE -> Color(0xFFB8D4E8)
        BackgroundOption.LIGHT_GREY -> Color(0xFFE8E8E8)
        BackgroundOption.GRADIENT -> Color(0xFFB8D4E8)
        BackgroundOption.TRANSPARENT -> Color.Transparent
    }
    val label = when (option) {
        BackgroundOption.NONE -> "None"
        BackgroundOption.WHITE -> stringResource(R.string.white)
        BackgroundOption.STUDIO_BLUE -> "Studio Blue"
        BackgroundOption.LIGHT_GREY -> "Light Grey"
        BackgroundOption.GRADIENT -> "Gradient"
        BackgroundOption.TRANSPARENT -> "Transparent"
    }
    val icon = when (option) {
        BackgroundOption.NONE -> Icons.Default.Block
        BackgroundOption.WHITE -> Icons.Default.Circle
        BackgroundOption.STUDIO_BLUE -> Icons.Default.Circle
        BackgroundOption.LIGHT_GREY -> Icons.Default.Circle
        BackgroundOption.GRADIENT -> Icons.Default.Gradient
        BackgroundOption.TRANSPARENT -> Icons.Default.GridOff
    }

    Surface(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (option == BackgroundOption.NONE) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CompressionControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    estimatedSize: Int,
    format: String,
    maxSize: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.compression),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "~ $estimatedSize KB",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = stringResource(R.string.cd_compression_quality),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.quality).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.max_size).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = stringResource(R.string.cd_compression_size),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${maxSize}KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPresetInputs(viewModel: SharedPhotoViewModel) {
    val width by viewModel.customWidth.collectAsState()
    val height by viewModel.customHeight.collectAsState()
    val format by viewModel.customFormat.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Custom Dimensions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Width Input
            OutlinedTextField(
                value = width,
                onValueChange = { 
                    viewModel.updateCustomWidth(it)
                    viewModel.applyCustomPreset()
                },
                label = { Text("Width (px)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )

            // Height Input
            OutlinedTextField(
                value = height,
                onValueChange = { 
                    viewModel.updateCustomHeight(it)
                    viewModel.applyCustomPreset()
                },
                label = { Text("Height (px)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Format Selection
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Format:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            
            // JPG Chip
            FilterChip(
                selected = format.equals("jpg", ignoreCase = true),
                onClick = { 
                    viewModel.updateCustomFormat("jpg")
                    viewModel.applyCustomPreset()
                },
                label = { Text("JPG") }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // PNG Chip
            FilterChip(
                selected = format.equals("png", ignoreCase = true),
                onClick = { 
                    viewModel.updateCustomFormat("png")
                    viewModel.applyCustomPreset()
                },
                label = { Text("PNG") }
            )
        }
    }
}
