package com.govphoto.resizer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.govphoto.resizer.R
import com.govphoto.resizer.ui.theme.*
import com.govphoto.resizer.ui.viewmodel.SharedPhotoViewModel

/**
 * Photo Upload Screen - Camera and Gallery options for selecting a photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUploadScreen(
    presetId: String,
    sharedViewModel: SharedPhotoViewModel,
    onNavigateBack: () -> Unit,
    onPhotoSelected: () -> Unit
) {
    val context = LocalContext.current
    val selectedImageUri by sharedViewModel.selectedImageUri.collectAsState()
    val capturedBitmap by sharedViewModel.capturedBitmap.collectAsState()
    
    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            sharedViewModel.setSelectedImageUri(it)
            onPhotoSelected()
        }
    }
    
    // Camera launcher - uses TakePicture for full resolution
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            sharedViewModel.setCapturedBitmap(it)
            onPhotoSelected()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.upload_photo),
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
                        text = "1/3",
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Upload Options Title
            Text(
                text = stringResource(R.string.choose_upload_method),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Select a clear, front-facing photo",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryLight,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Camera Option
            UploadOptionCard(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.upload_from_camera),
                subtitle = stringResource(R.string.take_new_photo),
                backgroundColor = IndiaGreen.copy(alpha = 0.1f),
                iconTint = IndiaGreen,
                onClick = { cameraLauncher.launch(null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Gallery Option
            UploadOptionCard(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.upload_from_gallery),
                subtitle = stringResource(R.string.select_from_photos),
                backgroundColor = Primary.copy(alpha = 0.1f),
                iconTint = Primary,
                onClick = { galleryLauncher.launch("image/*") }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Photo Guidelines Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.photo_guidelines),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• ${stringResource(R.string.guideline_clear_photo)}\n• ${stringResource(R.string.guideline_good_lighting)}\n• ${stringResource(R.string.guideline_plain_background)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary.copy(alpha = 0.8f),
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryLight
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
