package com.govphoto.resizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.govphoto.resizer.R
import com.govphoto.resizer.ui.theme.*

/**
 * Settings Screen - Language, accessibility, and app preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("en") }
    var largeButtonsEnabled by remember { mutableStateOf(false) }
    var highContrastEnabled by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_settings),
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
                .verticalScroll(rememberScrollState())
        ) {
            // Language Section
            SettingsSection(title = stringResource(R.string.language)) {
                LanguageOption(
                    label = stringResource(R.string.english),
                    isSelected = selectedLanguage == "en",
                    onClick = { selectedLanguage = "en" }
                )
                LanguageOption(
                    label = stringResource(R.string.hindi),
                    isSelected = selectedLanguage == "hi",
                    onClick = { selectedLanguage = "hi" }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Accessibility Section
            SettingsSection(title = stringResource(R.string.accessibility)) {
                SettingsToggle(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.large_buttons),
                    subtitle = "Increase button sizes for easier tapping",
                    isChecked = largeButtonsEnabled,
                    onCheckedChange = { largeButtonsEnabled = it }
                )
                SettingsToggle(
                    icon = Icons.Default.Contrast,
                    title = stringResource(R.string.high_contrast),
                    subtitle = "Improve visibility with higher contrast",
                    isChecked = highContrastEnabled,
                    onCheckedChange = { highContrastEnabled = it }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // About Section
            SettingsSection(title = stringResource(R.string.about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.version),
                    subtitle = "1.0.0"
                )
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = stringResource(R.string.privacy_policy),
                    subtitle = "View privacy policy",
                    onClick = { /* Open privacy policy */ }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        content()
    }
}

@Composable
private fun LanguageOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Primary
                )
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary
                )
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondaryLight
                )
            }
        }
    }
}
