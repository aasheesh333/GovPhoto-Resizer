package com.dhanuk.govphoto.ui.screens

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.model.PhotoPreset
import com.dhanuk.govphoto.data.model.PresetCategory
import com.dhanuk.govphoto.ui.ads.BannerAd
import com.dhanuk.govphoto.ui.theme.*
import com.dhanuk.govphoto.ui.viewmodel.AllFormsViewModel

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
    var selectedNavItem by rememberSaveable { mutableIntStateOf(0) }
    
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
                    // Edge-to-edge: without statusBarsPadding the title/icons
                    // sit under the system status bar and look half-cut.
                    .statusBarsPadding()
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
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
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
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedNavItem == 1,
                    onClick = {
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
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
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
            BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search_icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
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
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
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
                        contentDescription = stringResource(R.string.cd_category_icon),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_navigate_forward),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Divider(
            modifier = Modifier.padding(start = 80.dp),
            color = MaterialTheme.colorScheme.outlineVariant
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

@Composable
private fun getCategoryBgColor(category: PresetCategory): Color {
    return when (category) {
        PresetCategory.IDENTITY_CARDS -> MaterialTheme.colorScheme.primary
        PresetCategory.TRAVEL_VISAS -> MaterialTheme.colorScheme.secondary
        PresetCategory.CENTRAL_EXAMS -> MaterialTheme.colorScheme.tertiary
        PresetCategory.STATE_EXAMS -> MaterialTheme.colorScheme.tertiary
        PresetCategory.BANKING -> Color(0xFFDCFCE7)
        PresetCategory.DEFENCE -> Color(0xFFFEF3C7)
        PresetCategory.RAILWAYS -> MaterialTheme.colorScheme.secondaryContainer
        PresetCategory.TEACHING -> MaterialTheme.colorScheme.tertiary
        PresetCategory.EDUCATION -> MaterialTheme.colorScheme.primary
        PresetCategory.JOB_EXAMS -> MaterialTheme.colorScheme.tertiary
        PresetCategory.CUSTOM -> Color(0xFFF3F4F6)
    }
}

@Composable
private fun getCategoryTint(category: PresetCategory): Color {
    return when (category) {
        PresetCategory.IDENTITY_CARDS -> MaterialTheme.colorScheme.onPrimary
        PresetCategory.TRAVEL_VISAS -> Color(0xFF0D9488)
        PresetCategory.CENTRAL_EXAMS -> Color(0xFFEA580C)
        PresetCategory.STATE_EXAMS -> MaterialTheme.colorScheme.onTertiary
        PresetCategory.BANKING -> Color(0xFF16A34A)
        PresetCategory.DEFENCE -> Color(0xFFD97706)
        PresetCategory.RAILWAYS -> Color(0xFF4F46E5)
        PresetCategory.TEACHING -> MaterialTheme.colorScheme.onTertiary
        PresetCategory.EDUCATION -> MaterialTheme.colorScheme.onPrimary
        PresetCategory.JOB_EXAMS -> Color(0xFFEA580C)
        PresetCategory.CUSTOM -> Color(0xFF6B7280)
    }
}
