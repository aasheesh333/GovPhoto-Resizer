package com.dhanuk.govphoto.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.ml.FaceAnalysisResult
import com.dhanuk.govphoto.data.model.PhotoPreset
import com.dhanuk.govphoto.ui.theme.*
import com.dhanuk.govphoto.ui.viewmodel.BackgroundColor
import com.dhanuk.govphoto.ui.viewmodel.SharedPhotoViewModel
import kotlinx.coroutines.launch

/**
 * Preview & Validation Screen - Shows processed photo with validation checklist.
 * Handles Saving and Sharing of the final image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewValidationScreen(
    sharedViewModel: SharedPhotoViewModel,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit, // This now just navigates home
    onRetakeEdit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val selectedImageUri by sharedViewModel.selectedImageUri.collectAsState()
    val originalBitmap by sharedViewModel.originalBitmap.collectAsState()
    val pristineOriginal by sharedViewModel.pristineOriginalBitmap.collectAsState()
    val bakedBitmap by sharedViewModel.bakedBitmap.collectAsState()
    val displayedBitmap by sharedViewModel.displayedBitmap.collectAsState()
    val backgroundColor by sharedViewModel.backgroundColor.collectAsState()
    val fileSizeKb by sharedViewModel.fileSizeKb.collectAsState()
    val presetName by sharedViewModel.selectedPresetName.collectAsState()
    val selectedPreset by sharedViewModel.selectedPreset.collectAsState()
    val processedImageUri by sharedViewModel.processedImageUri.collectAsState()
    val faceAnalysis by sharedViewModel.faceAnalysis.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Original, 1 = Processed
    var isSaving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }

    // Runtime permission for saving on Android 9 and below (API <= 28).
    // WRITE_EXTERNAL_STORAGE is required for MediaStore writes on those versions.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingSave) {
            performSave(
                scope = scope,
                context = context,
                sharedViewModel = sharedViewModel,
                setSaving = { isSaving = it },
                onSaveComplete = onSaveComplete
            )
        } else if (pendingSave) {
            Toast.makeText(
                context,
                "Storage permission is required to save photos on this device",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingSave = false
    }

    // Ensure face analysis is available when Preview opens
    LaunchedEffect(selectedImageUri, originalBitmap) {
        if (faceAnalysis == null && (selectedImageUri != null || originalBitmap != null)) {
            sharedViewModel.analyzeFace()
        }
    }
    
    // Function to handle sharing
    fun shareImage(uri: Uri) {
        val format = selectedPreset?.format?.lowercase() ?: "jpg"
        val mimeType = if (format == "png") "image/png" else "image/jpeg"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.preview_validation),
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
                    Text(
                        text = "3/3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    if (processedImageUri != null) {
                        IconButton(onClick = { processedImageUri?.let { shareImage(it) } }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }
                    }
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
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save Button — wrapped with try/catch (Throwable) at the
                    // UI coroutine boundary as a last-resort safety net. This
                    // catches VirtualMachineError, native OOM, etc., that might
                    // escape the ViewModel's internal catches. Never app-crash
                    // from a Save tap.
                    Button(
                        onClick = {
                            if (isSaving) return@Button

                            // Android 9 and below need WRITE_EXTERNAL_STORAGE at runtime.
                            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P &&
                                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingSave = true
                                storagePermissionLauncher.launch(
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                                return@Button
                            }

                            performSave(
                                scope = scope,
                                context = context,
                                sharedViewModel = sharedViewModel,
                                setSaving = { isSaving = it },
                                onSaveComplete = onSaveComplete
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        if (isSaving) {
                            // Material3 CircularProgressIndicator crashes with
                            // NoSuchMethodError KeyframesSpecConfig.at on this BOM —
                            // use safe Canvas spinner instead.
                            com.dhanuk.govphoto.ui.components.SafeCircularSpinner(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 2.dp
                            )
                        } else {
Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = stringResource(R.string.cd_save_button),
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.save_photo),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                    
                    // Retake/Edit Button
                    OutlinedButton(
                        onClick = onRetakeEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_retake_photo),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.retake_edit),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tab Selector
            TabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
            
            // Preview Card. Processed = baked (output of Continue); Original =
            // pristine, never edited (full untouched image with EXIF applied).
            PreviewCard(
                imageUri = selectedImageUri,
                originalBitmap = pristineOriginal ?: originalBitmap,
                processedBitmap = bakedBitmap ?: displayedBitmap,
                backgroundColor = backgroundColor,
                fileSizeKb = fileSizeKb,
                preset = selectedPreset,
                selectedTab = selectedTab
            )
            
            // Validation Checklist
            ValidationChecklist(
                fileSizeKb = fileSizeKb,
                preset = selectedPreset,
                faceAnalysis = faceAnalysis,
            )
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun TabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            listOf(
                stringResource(R.string.original),
                stringResource(R.string.processed)
            ).forEachIndexed { index, label ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                    onClick = { onTabSelected(index) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selectedTab == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    imageUri: Uri?,
    originalBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    backgroundColor: BackgroundColor,
    fileSizeKb: Int,
    preset: PhotoPreset?,
    selectedTab: Int
) {
    val context = LocalContext.current
    val aspectRatio = preset?.getAspectRatio() ?: 0.8f

    val bgColor = when (backgroundColor) {
        BackgroundColor.WHITE -> Color.White
        BackgroundColor.STUDIO_BLUE -> Color(0xFFB8D4E8)
        BackgroundColor.LIGHT_GREY -> Color(0xFFE8E8E8)
        BackgroundColor.GRADIENT -> Color(0xFFB8D4E8)
        BackgroundColor.TRANSPARENT -> Color.LightGray.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Photo Preview container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 1) {
                    // Processed view — template-shaped box with 3x3 grid
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(240.dp)
                            .aspectRatio(aspectRatio)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        // Baked bitmap already has preset AR — Fit fills box without stretch.
                        if (processedBitmap != null && !processedBitmap.isRecycled) {
                            androidx.compose.foundation.Image(
                                bitmap = processedBitmap.asImageBitmap(),
                                contentDescription = "Processed photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (originalBitmap != null && !originalBitmap.isRecycled) {
                            androidx.compose.foundation.Image(
                                bitmap = originalBitmap.asImageBitmap(),
                                contentDescription = "Processable photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (imageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Processed photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.cd_person_placeholder),
                                tint = Color.Gray,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                        // 3x3 grid overlay on processed image
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridColor = Color.White.copy(alpha = 0.4f)
                            val strokeWidth = 1f
                            // Vertical lines
                            drawLine(gridColor, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), strokeWidth)
                            drawLine(gridColor, Offset(2 * size.width / 3, 0f), Offset(2 * size.width / 3, size.height), strokeWidth)
                            // Horizontal lines
                            drawLine(gridColor, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), strokeWidth)
                            drawLine(gridColor, Offset(0f, 2 * size.height / 3), Offset(size.width, 2 * size.height / 3), strokeWidth)
                        }
                    }
                } else {
                    // Original view — show full source image with 2x2 grid (not stretched)
                    val srcBmp = originalBitmap
                    val srcAR = if (srcBmp != null && srcBmp.width > 0 && srcBmp.height > 0) {
                        srcBmp.width.toFloat() / srcBmp.height.toFloat()
                    } else 1f
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(240.dp)
                            .aspectRatio(srcAR)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (srcBmp != null && !srcBmp.isRecycled) {
                            androidx.compose.foundation.Image(
                                bitmap = srcBmp.asImageBitmap(),
                                contentDescription = "Original photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (imageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Original photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.cd_person_placeholder),
                                tint = Color.Gray,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                        // 2x2 grid overlay on original image
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridColor = Color.White.copy(alpha = 0.4f)
                            val strokeWidth = 1f
                            // Vertical center line
                            drawLine(gridColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth)
                            // Horizontal center line
                            drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth)
                        }
                    }
                }

                // Valid badge only on Processed tab (not on Original)
                if (selectedTab == 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = stringResource(R.string.cd_valid_badge),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.valid).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Info Row
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedTab == 1) stringResource(R.string.processed_preview) else "Original Photo",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = preset?.getFormattedDimensions() ?: "Custom Size",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "$fileSizeKb KB",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun performSave(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    sharedViewModel: SharedPhotoViewModel,
    setSaving: (Boolean) -> Unit,
    onSaveComplete: () -> Unit
) {
    setSaving(true)
    scope.launch {
        try {
            val result = sharedViewModel.savePhotoToGallery()
            result.onSuccess {
                Toast.makeText(context, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
                onSaveComplete()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "Failed to save: ${error.message ?: "unknown"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (t: Throwable) {
            android.util.Log.e("PreviewValidation", "Save crashed", t)
            try {
                sharedViewModel.writeCrashFile("UI save catch", t)
            } catch (_: Throwable) {}
            val msg = if (t is OutOfMemoryError)
                "Out of memory — try a smaller photo"
            else "Error: ${t.message}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        } finally {
            setSaving(false)
        }
    }
}

@Composable
private fun ValidationChecklist(
    fileSizeKb: Int,
    preset: PhotoPreset?,
    faceAnalysis: FaceAnalysisResult?,
) {
    val isSizeValid = fileSizeKb <= (preset?.maxFileSizeKb ?: 500)
    val isPhotoPreset = preset?.presetType != com.dhanuk.govphoto.data.model.PresetType.SIGNATURE &&
        preset?.presetType != com.dhanuk.govphoto.data.model.PresetType.THUMB &&
        preset?.presetType != com.dhanuk.govphoto.data.model.PresetType.DOCUMENT
    val faceOk = faceAnalysis?.isWithinMargin == true && (faceAnalysis.faceCount == 1)
    val faceDesc = when {
        faceAnalysis == null -> "Checking face…"
        faceOk -> stringResource(R.string.face_detected_desc)
        faceAnalysis.faceCount == 0 -> "No face detected"
        faceAnalysis.issues.isNotEmpty() -> faceAnalysis.issues.joinToString(" · ")
        else -> "Face not compliant"
    }
    
    Column {
        Text(
            text = stringResource(R.string.validation_checklist),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isPhotoPreset) {
                ValidationItem(
                    icon = Icons.Default.Face,
                    title = stringResource(R.string.face_detected),
                    description = faceDesc,
                    isSuccess = faceOk
                )
            }
            ValidationItem(
                icon = Icons.Default.AspectRatio,
                title = stringResource(R.string.correct_dimensions),
                description = "Cropped to ${preset?.getFormattedDimensions() ?: "standard size"}",
                isSuccess = true
            )
            ValidationItem(
                icon = Icons.Default.CloudDownload,
                title = if (isSizeValid) stringResource(R.string.file_size_ok) else "File Size Warning",
                description = "Optimized for upload (< ${preset?.maxFileSizeKb ?: 500}KB)",
                isSuccess = isSizeValid
            )
        }
        
        // Success Info Note — only when face AND file size are valid.
        val allChecksPass = if (isPhotoPreset) faceOk && isSizeValid else isSizeValid
        if (allChecksPass) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_validation_status),
                    tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.photo_meets_requirements),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationItem(
    icon: ImageVector,
    title: String,
    description: String,
    isSuccess: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left border indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.cd_validation_status),
                        tint = if (isSuccess) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = stringResource(R.string.cd_validation_status),
                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
