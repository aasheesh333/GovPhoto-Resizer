package com.govphoto.resizer.ui.screens

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.govphoto.resizer.R
import com.govphoto.resizer.ui.theme.*
import com.govphoto.resizer.ui.viewmodel.BackgroundColor
import com.govphoto.resizer.ui.viewmodel.SharedPhotoViewModel

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
    val capturedBitmap by sharedViewModel.capturedBitmap.collectAsState()
    val backgroundColor by sharedViewModel.backgroundColor.collectAsState()
    val compressionQuality by sharedViewModel.compressionQuality.collectAsState()
    val fileSizeKb by sharedViewModel.fileSizeKb.collectAsState()
    val selectedPreset by sharedViewModel.selectedPreset.collectAsState()
    
    // Dynamic aspect ratio from preset
    val aspectRatio = sharedViewModel.aspectRatio
    val format = selectedPreset?.format?.uppercase() ?: "JPG"
    val maxSize = selectedPreset?.maxFileSizeKb ?: 500
    
    // Local UI state
    var selectedBackground by remember { mutableStateOf(BackgroundOption.WHITE) }
    var compressionValue by remember { mutableFloatStateOf(compressionQuality) }
    
    // Image transformation state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Sync background with ViewModel
    LaunchedEffect(selectedBackground) {
        sharedViewModel.setBackgroundColor(
            when (selectedBackground) {
                BackgroundOption.WHITE -> BackgroundColor.WHITE
                BackgroundOption.LIGHT_BLUE -> BackgroundColor.LIGHT_BLUE
                BackgroundOption.REMOVE -> BackgroundColor.TRANSPARENT
            }
        )
        if (selectedBackground == BackgroundOption.REMOVE) {
            sharedViewModel.removeBackground()
        }
    }
    
    // Sync compression with ViewModel
    LaunchedEffect(compressionValue) {
        sharedViewModel.setCompressionQuality(compressionValue)
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
                            imageVector = Icons.Default.ArrowBackIosNew,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    Text(
                        text = "2/3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IndiaGreen
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
                        contentDescription = null,
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
            // Photo Preview with actual image and dynamic aspect ratio
            PhotoPreviewWithImage(
                imageUri = selectedImageUri,
                bitmap = capturedBitmap,
                backgroundColor = selectedBackground,
                aspectRatio = aspectRatio,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                onTransform = { newScale, newOffsetX, newOffsetY ->
                    scale = (scale * newScale).coerceIn(0.5f, 3f)
                    offsetX += newOffsetX
                    offsetY += newOffsetY
                },
                onReset = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.align_face),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Background Section
            BackgroundSelector(
                selectedOption = selectedBackground,
                onOptionSelected = { selectedBackground = it }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Custom Preset Inputs (Only if Manual)
            if (selectedPreset?.id == com.govphoto.resizer.data.model.PhotoPreset.MANUAL_PRESET_ID) {
                CustomPresetInputs(sharedViewModel)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Compression Section
            CompressionControl(
                value = compressionValue,
                onValueChange = { compressionValue = it },
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
    onTransform: (Float, Float, Float) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio) // Use dynamic aspect ratio
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, BorderLight, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Background color layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when (backgroundColor) {
                        BackgroundOption.WHITE -> Color.White
                        BackgroundOption.LIGHT_BLUE -> PhotoBgLightBlue
                        BackgroundOption.REMOVE -> Color.LightGray.copy(alpha = 0.3f)
                    }
                )
        )
        
        // Actual image display
        if (imageUri != null) {
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
                contentScale = ContentScale.Crop
            )
        } else if (bitmap != null) {
            // Display bitmap from camera
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
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
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder when no image
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = TextSecondaryLight,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No image selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryLight
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
        
        // Face oval guide
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .aspectRatio(0.75f)
                .offset(y = (-10).dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50)
                )
        )
        
        // Control buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Reset button
            FloatingActionButton(
                onClick = onReset,
                modifier = Modifier.size(44.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset position",
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
            
            // Crop button placeholder
            FloatingActionButton(
                onClick = { /* Crop action */ },
                modifier = Modifier.size(44.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = "Crop",
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
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackgroundOption.entries.forEach { option ->
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

@Composable
private fun BackgroundOptionItem(
    option: BackgroundOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (option) {
        BackgroundOption.WHITE -> Color.White
        BackgroundOption.LIGHT_BLUE -> PhotoBgLightBlue
        BackgroundOption.REMOVE -> Color.Transparent
    }
    
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        },
        color = if (isSelected) PrimaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(1.dp, BorderLight, CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    if (option == BackgroundOption.REMOVE) {
                        Icon(
                            imageVector = Icons.Default.GridOff,
                            contentDescription = null,
                            tint = TextSecondaryLight,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IndiaGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = when (option) {
                    BackgroundOption.WHITE -> stringResource(R.string.white)
                    BackgroundOption.LIGHT_BLUE -> stringResource(R.string.light_blue)
                    BackgroundOption.REMOVE -> stringResource(R.string.remove)
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) Primary else TextSecondaryLight
            )
        }
    }
}

@Composable
private fun CompressionControl(
    value: Float,
    onValueChange: (Float) -> Unit,
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
                    color = Primary.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = IndiaGreen.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "~ $estimatedSize KB",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = IndiaGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
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
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.quality).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryLight
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.max_size).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = null,
                            tint = Saffron,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary,
                        inactiveTrackColor = DividerLight
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondaryLight
                    )
                    Text(
                        text = "${maxSize}KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondaryLight
                    )
                }
            }
        }
    }
}

enum class BackgroundOption {
    WHITE, LIGHT_BLUE, REMOVE
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
