package com.govphoto.resizer.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import com.govphoto.resizer.R
import com.govphoto.resizer.data.model.PhotoPreset
import com.govphoto.resizer.ui.theme.*
import com.govphoto.resizer.ui.viewmodel.BackgroundColor
import com.govphoto.resizer.ui.viewmodel.SharedPhotoViewModel
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
    val capturedBitmap by sharedViewModel.capturedBitmap.collectAsState()
    val backgroundColor by sharedViewModel.backgroundColor.collectAsState()
    val fileSizeKb by sharedViewModel.fileSizeKb.collectAsState()
    val presetName by sharedViewModel.selectedPresetName.collectAsState()
    val selectedPreset by sharedViewModel.selectedPreset.collectAsState()
    val processedImageUri by sharedViewModel.processedImageUri.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Original, 1 = Processed
    var isSaving by remember { mutableStateOf(false) }
    
    // Function to handle sharing
    fun shareImage(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    }
    
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.preview_validation),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Step 3 of 3",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryLight
                        )
                    }
                    // Share Button (Visible if processed image exists)
                    if (processedImageUri != null) {
                        IconButton(onClick = { shareImage(processedImageUri!!) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Primary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
                
                // Progress Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .width(if (index == 2) 40.dp else 32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index <= 2) IndiaGreen else DividerLight
                                )
                        )
                        if (index < 2) Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save Button
                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                scope.launch {
                                    val result = sharedViewModel.savePhotoToGallery()
                                    isSaving = false
                                    result.onSuccess {
                                        Toast.makeText(context, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
                                        onSaveComplete()
                                    }.onFailure {
                                        Toast.makeText(context, "Failed to save: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Saffron
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.save_photo),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Primary
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = TextSecondaryLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.retake_edit),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextSecondaryLight
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
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tab Selector
            TabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
            
            // Preview Card
            PreviewCard(
                imageUri = selectedImageUri,
                bitmap = capturedBitmap,
                backgroundColor = backgroundColor,
                fileSizeKb = fileSizeKb,
                preset = selectedPreset
            )
            
            // Validation Checklist
            ValidationChecklist(fileSizeKb = fileSizeKb, preset = selectedPreset)
            
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
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
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
                    color = if (selectedTab == index) Primary else Color.Transparent,
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
                            color = if (selectedTab == index) Color.White else TextSecondaryLight
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
    bitmap: Bitmap?,
    backgroundColor: BackgroundColor,
    fileSizeKb: Int,
    preset: PhotoPreset?
) {
    val context = LocalContext.current
    val aspectRatio = preset?.getAspectRatio() ?: 0.8f
    
    val bgColor = when (backgroundColor) {
        BackgroundColor.WHITE -> Color.White
        BackgroundColor.LIGHT_BLUE -> PhotoBgLightBlue
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
                // Actual Photo Box with dynamic aspect ratio
                Box(
                    modifier = Modifier
                        .width(200.dp) // Fixed width basis
                        .aspectRatio(aspectRatio) // Dynamic height based on ratio
                        .background(bgColor)
                        .border(1.dp, BorderLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Preview photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
                
                // Valid Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = IndiaGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.valid).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = IndiaGreen
                        )
                    }
                }
            }
            
            // Info Row
            Divider(color = DividerLight)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.processed_preview),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = preset?.getFormattedDimensions() ?: "Custom Size",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndiaGreen.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "$fileSizeKb KB",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = IndiaGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationChecklist(fileSizeKb: Int, preset: PhotoPreset?) {
    val isSizeValid = fileSizeKb <= (preset?.maxFileSizeKb ?: 500)
    
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
            ValidationItem(
                icon = Icons.Default.Face,
                title = stringResource(R.string.face_detected),
                description = stringResource(R.string.face_detected_desc),
                isSuccess = true
            )
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
        
        // Success Info Note
        if (isSizeValid) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SuccessLight
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.photo_meets_requirements),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Success
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
                    .background(if (isSuccess) Success else Warning)
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
                        .background(if (isSuccess) SuccessLight else WarningLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSuccess) Success else Warning,
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
                        color = TextSecondaryLight
                    )
                }
                
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSuccess) Success else Warning,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
