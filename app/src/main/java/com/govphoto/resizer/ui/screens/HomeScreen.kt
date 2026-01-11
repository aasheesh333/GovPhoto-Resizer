package com.govphoto.resizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.govphoto.resizer.R
import com.govphoto.resizer.ui.theme.*

/**
 * Home Screen - Main entry point with Quick Upload and Document Type selection.
 */
@Composable
fun HomeScreen(
    onNavigateToAllForms: () -> Unit,
    onNavigateToUpload: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var selectedNavItem by remember { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedItem = selectedNavItem,
                onItemSelected = { index ->
                    selectedNavItem = index
                    when (index) {
                        0 -> { /* Already on Home */ }
                        1 -> onNavigateToHistory()
                        2 -> onNavigateToSettings()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Section
            HomeHeader()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Upload Button
            QuickUploadButton(
                onClick = { onNavigateToUpload("quick_upload") }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Document Type Section
            DocumentTypeSection(
                onViewAllClick = onNavigateToAllForms,
                onPresetClick = onNavigateToUpload
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HomeHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Row with Logo and Language Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // App Logo
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "GovPhoto",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Language Toggle
                LanguageToggle()
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title and Subtitle
            Text(
                text = stringResource(R.string.app_tagline).substringBefore(" for"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Compliant with all government standards",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight
            )
        }
    }
}

@Composable
private fun LanguageToggle() {
    OutlinedButton(
        onClick = { /* Toggle language */ },
        shape = RoundedCornerShape(24.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 2.dp
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Translate,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "EN / HI",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Primary
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Primary
        )
    }
}

@Composable
private fun QuickUploadButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(72.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = IndiaGreen.copy(alpha = 0.3f),
                spotColor = IndiaGreen.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IndiaGreen
        ),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.quick_upload),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.auto_detect_requirements),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun DocumentTypeSection(
    onViewAllClick: () -> Unit,
    onPresetClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_document_type),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onViewAllClick) {
                Text(
                    text = stringResource(R.string.view_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Document Type Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DocumentTypeCard(
                icon = Icons.Default.Public,
                title = stringResource(R.string.passport),
                subtitle = "3.5 x 4.5 cm",
                onClick = { onPresetClick("passport") }
            )
            DocumentTypeCard(
                icon = Icons.Default.Fingerprint,
                title = stringResource(R.string.aadhaar),
                subtitle = "Official Update",
                onClick = { onPresetClick("aadhaar") }
            )
            DocumentTypeCard(
                icon = Icons.Default.Badge,
                title = stringResource(R.string.pan_card),
                subtitle = "Standard Size",
                onClick = { onPresetClick("pan_card") }
            )

            // Custom Size Card
            DocumentTypeCard(
                icon = Icons.Default.Edit,
                title = "Custom Size",
                subtitle = "Manual Width & Height",
                onClick = { onPresetClick(com.govphoto.resizer.data.model.PhotoPreset.MANUAL_PRESET_ID) }
            )
            
            // Browse All Forms Button
            BrowseAllFormsButton(onClick = onViewAllClick)
        }
    }
}

@Composable
private fun DocumentTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CategoryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                }
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Select",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun BrowseAllFormsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.browse_all_forms),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) },
            icon = {
                Icon(
                    imageVector = if (selectedItem == 0) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.nav_home)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_home),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selectedItem == 0) FontWeight.Bold else FontWeight.Medium
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Primary,
                selectedTextColor = Primary,
                indicatorColor = PrimaryContainer
            )
        )
        NavigationBarItem(
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) },
            icon = {
                Icon(
                    imageVector = if (selectedItem == 1) Icons.Filled.History else Icons.Outlined.History,
                    contentDescription = stringResource(R.string.nav_history)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_history),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selectedItem == 1) FontWeight.Bold else FontWeight.Medium
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Primary,
                selectedTextColor = Primary,
                indicatorColor = PrimaryContainer
            )
        )
        NavigationBarItem(
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) },
            icon = {
                Icon(
                    imageVector = if (selectedItem == 2) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                    contentDescription = stringResource(R.string.nav_profile)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_profile),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selectedItem == 2) FontWeight.Bold else FontWeight.Medium
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Primary,
                selectedTextColor = Primary,
                indicatorColor = PrimaryContainer
            )
        )
    }
}
