package com.govphoto.resizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.govphoto.resizer.R
import com.govphoto.resizer.ui.theme.*

/**
 * Edit Photo Screen - Face alignment, background selection, and compression controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreen(
    onNavigateBack: () -> Unit,
    onContinue: () -> Unit
) {
    var selectedBackground by remember { mutableStateOf(BackgroundOption.WHITE) }
    var compressionValue by remember { mutableFloatStateOf(0.7f) }
    val estimatedSize = (20 + (180 * compressionValue)).toInt()
    
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
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.continue_to_save),
                        style = MaterialTheme.typography.labelLarge.copy(
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
            // Photo Preview with Grid
            PhotoPreviewWithGrid(
                backgroundColor = selectedBackground
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
            
            // Compression Section
            CompressionControl(
                value = compressionValue,
                onValueChange = { compressionValue = it },
                estimatedSize = estimatedSize
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PhotoPreviewWithGrid(
    backgroundColor: BackgroundOption
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Gray.copy(alpha = 0.2f)),
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
                        BackgroundOption.REMOVE -> Color.Transparent
                    }
                )
        )
        
        // Placeholder for actual image
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.5f),
            modifier = Modifier.size(120.dp)
        )
        
        // Grid overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Grid lines would be drawn here
        }
        
        // Face oval guide
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(0.8f)
                .offset(y = (-20).dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50)
                )
        )
        
        // Undo/Redo buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FloatingActionButton(
                onClick = { /* Undo */ },
                modifier = Modifier.size(40.dp),
                containerColor = Color.Black.copy(alpha = 0.4f),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = stringResource(R.string.undo),
                    modifier = Modifier.size(20.dp)
                )
            }
            FloatingActionButton(
                onClick = { /* Redo */ },
                modifier = Modifier.size(40.dp),
                containerColor = Color.Black.copy(alpha = 0.4f),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = stringResource(R.string.redo),
                    modifier = Modifier.size(20.dp)
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
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
        } else {
            ButtonDefaults.outlinedButtonBorder
        },
        color = if (isSelected) PrimaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when (option) {
                                BackgroundOption.WHITE -> Color.White
                                BackgroundOption.LIGHT_BLUE -> PhotoBgLightBlue
                                BackgroundOption.REMOVE -> Color.Transparent
                            }
                        )
                        .border(1.dp, BorderLight, CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    if (option == BackgroundOption.REMOVE) {
                        Icon(
                            imageVector = Icons.Default.GridOff,
                            contentDescription = null,
                            tint = TextSecondaryLight,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when (option) {
                    BackgroundOption.WHITE -> stringResource(R.string.white)
                    BackgroundOption.LIGHT_BLUE -> stringResource(R.string.light_blue)
                    BackgroundOption.REMOVE -> stringResource(R.string.remove)
                },
                style = MaterialTheme.typography.labelMedium.copy(
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
    estimatedSize: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.compression),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PrimaryContainer
            ) {
                Text(
                    text = "~ $estimatedSize KB",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = BackgroundLight,
            border = ButtonDefaults.outlinedButtonBorder
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.quality).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryLight
                    )
                    Text(
                        text = stringResource(R.string.max_size).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryLight
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
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
                        text = "20KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                    Text(
                        text = "200KB",
                        style = MaterialTheme.typography.bodySmall,
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
