package com.govphoto.resizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.govphoto.resizer.R
import com.govphoto.resizer.data.model.PhotoPreset
import com.govphoto.resizer.data.model.PresetCategory
import com.govphoto.resizer.ui.theme.*
import com.govphoto.resizer.ui.viewmodel.AllFormsViewModel

/**
 * All Forms Screen - Shows all government form types organized by category.
 * Now loads presets dynamically from the repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFormsScreen(
    onNavigateBack: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: AllFormsViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNavItem by remember { mutableIntStateOf(0) }
    
    val allPresets by viewModel.presets.collectAsState()
    
    // Group presets by category
    val groupedPresets = remember(allPresets, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            allPresets
        } else {
            allPresets.filter {
                it.examName.contains(searchQuery, ignoreCase = true) ||
                it.authority.contains(searchQuery, ignoreCase = true) ||
                (it.examNameHi?.contains(searchQuery) == true)
            }
        }
        filtered.groupBy { it.category }
            .toSortedMap(compareBy { it.sortOrder })
    }
    
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Title Row with Back Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = stringResource(R.string.all_government_form_types),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { /* Notifications */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = Primary
                        )
                    }
                }
                
                // Search Bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                )
                
                // Preset count indicator
                Text(
                    text = "${allPresets.size} presets available",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondaryLight,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Divider(color = DividerLight)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedNavItem == 0,
                    onClick = { selectedNavItem = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = stringResource(R.string.nav_forms)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_forms)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = PrimaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedNavItem == 1,
                    onClick = { 
                        selectedNavItem = 1
                        onNavigateToHistory()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) }
                )
                NavigationBarItem(
                    selected = selectedNavItem == 2,
                    onClick = { 
                        selectedNavItem = 2
                        onNavigateToSettings()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            groupedPresets.forEach { (category, presets) ->
                // Category Header
                item(key = "header_${category.name}") {
                    CategoryHeader(
                        title = category.displayName,
                        count = presets.size
                    )
                }
                
                // Presets in this category
                items(
                    items = presets,
                    key = { it.id }
                ) { preset ->
                    FormListItem(
                        icon = getCategoryIcon(category),
                        iconBgColor = getCategoryBgColor(category),
                        iconTint = getCategoryTint(category),
                        title = preset.examName,
                        subtitle = preset.getFormattedDimensions(),
                        onClick = { onPresetSelected(preset.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryLight
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondaryLight
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondaryLight
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = BorderLight,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        singleLine = true
    )
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Primary.copy(alpha = 0.1f)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun FormListItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
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
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondaryLight
            )
        }
        Divider(
            modifier = Modifier.padding(start = 80.dp),
            color = DividerLight
        )
    }
}

// Helper functions to get category-specific styling
private fun getCategoryIcon(category: PresetCategory): ImageVector {
    return when (category) {
        PresetCategory.IDENTITY_CARDS -> Icons.Default.Fingerprint
        PresetCategory.TRAVEL_VISAS -> Icons.Default.Public
        PresetCategory.CENTRAL_EXAMS -> Icons.Default.AccountBalance
        PresetCategory.STATE_EXAMS -> Icons.Default.LocationCity
        PresetCategory.BANKING -> Icons.Default.AccountBalanceWallet
        PresetCategory.DEFENCE -> Icons.Default.Shield
        PresetCategory.RAILWAYS -> Icons.Default.Train
        PresetCategory.TEACHING -> Icons.Default.School
        PresetCategory.EDUCATION -> Icons.Default.School
        PresetCategory.JOB_EXAMS -> Icons.Default.Work
        PresetCategory.CUSTOM -> Icons.Default.Tune
    }
}

private fun getCategoryBgColor(category: PresetCategory): Color {
    return when (category) {
        PresetCategory.IDENTITY_CARDS -> CategoryBlue
        PresetCategory.TRAVEL_VISAS -> CategoryTeal
        PresetCategory.CENTRAL_EXAMS -> CategoryOrange
        PresetCategory.STATE_EXAMS -> CategoryPurple
        PresetCategory.BANKING -> Color(0xFFDCFCE7) // Green tint
        PresetCategory.DEFENCE -> Color(0xFFFEF3C7) // Amber tint
        PresetCategory.RAILWAYS -> Color(0xFFE0E7FF) // Indigo tint
        PresetCategory.TEACHING -> CategoryPurple
        PresetCategory.EDUCATION -> CategoryBlue
        PresetCategory.JOB_EXAMS -> CategoryOrange
        PresetCategory.CUSTOM -> Color(0xFFF3F4F6) // Gray tint
    }
}

private fun getCategoryTint(category: PresetCategory): Color {
    return when (category) {
        PresetCategory.IDENTITY_CARDS -> Primary
        PresetCategory.TRAVEL_VISAS -> Color(0xFF0D9488)
        PresetCategory.CENTRAL_EXAMS -> Color(0xFFEA580C)
        PresetCategory.STATE_EXAMS -> Color(0xFF9333EA)
        PresetCategory.BANKING -> Color(0xFF16A34A)
        PresetCategory.DEFENCE -> Color(0xFFD97706)
        PresetCategory.RAILWAYS -> Color(0xFF4F46E5)
        PresetCategory.TEACHING -> Color(0xFF9333EA)
        PresetCategory.EDUCATION -> Primary
        PresetCategory.JOB_EXAMS -> Color(0xFFEA580C)
        PresetCategory.CUSTOM -> Color(0xFF6B7280)
    }
}
