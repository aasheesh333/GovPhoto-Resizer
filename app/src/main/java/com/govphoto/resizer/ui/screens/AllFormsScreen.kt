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
import com.govphoto.resizer.R
import com.govphoto.resizer.ui.theme.*

/**
 * All Forms Screen - Shows all government form types organized by category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFormsScreen(
    onNavigateBack: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNavItem by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Title Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.all_government_form_types),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
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
            // Identity Cards Section
            item {
                CategoryHeader(title = stringResource(R.string.identity_cards))
            }
            items(identityCardsPresets) { preset ->
                FormListItem(
                    icon = preset.icon,
                    iconBgColor = CategoryBlue,
                    iconTint = Primary,
                    title = preset.title,
                    subtitle = preset.dimensions,
                    onClick = { onPresetSelected(preset.id) }
                )
            }
            
            // Travel & Visas Section
            item {
                CategoryHeader(title = stringResource(R.string.travel_visas))
            }
            items(travelVisasPresets) { preset ->
                FormListItem(
                    icon = preset.icon,
                    iconBgColor = CategoryTeal,
                    iconTint = Color(0xFF0D9488),
                    title = preset.title,
                    subtitle = preset.dimensions,
                    onClick = { onPresetSelected(preset.id) }
                )
            }
            
            // Job & Exams Section
            item {
                CategoryHeader(title = stringResource(R.string.job_exams))
            }
            items(jobExamsPresets) { preset ->
                FormListItem(
                    icon = preset.icon,
                    iconBgColor = CategoryOrange,
                    iconTint = Color(0xFFEA580C),
                    title = preset.title,
                    subtitle = preset.dimensions,
                    onClick = { onPresetSelected(preset.id) }
                )
            }
            
            // Education Section
            item {
                CategoryHeader(title = stringResource(R.string.education))
            }
            items(educationPresets) { preset ->
                FormListItem(
                    icon = preset.icon,
                    iconBgColor = CategoryPurple,
                    iconTint = Color(0xFF9333EA),
                    title = preset.title,
                    subtitle = preset.dimensions,
                    onClick = { onPresetSelected(preset.id) }
                )
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
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
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

// Sample preset data
private data class PresetItem(
    val id: String,
    val title: String,
    val dimensions: String,
    val icon: ImageVector
)

private val identityCardsPresets = listOf(
    PresetItem("aadhaar", "Aadhaar Card", "3.5cm x 4.5cm", Icons.Default.Fingerprint),
    PresetItem("pan", "PAN Card", "2.5cm x 3.5cm", Icons.Default.CreditCard),
    PresetItem("voter_id", "Voter ID", "3.5cm x 4.5cm", Icons.Default.HowToVote)
)

private val travelVisasPresets = listOf(
    PresetItem("passport", "Indian Passport", "2in x 2in (51mm x 51mm)", Icons.Default.Book),
    PresetItem("schengen", "Schengen Visa", "35mm x 45mm", Icons.Default.Public),
    PresetItem("us_visa", "US Visa", "2in x 2in (Digital)", Icons.Default.FlightTakeoff)
)

private val jobExamsPresets = listOf(
    PresetItem("upsc", "UPSC Civil Services", "3.5cm x 4.5cm", Icons.Default.AccountBalance),
    PresetItem("ssc_cgl", "SSC CGL", "3.5cm x 4.5cm", Icons.Default.Work),
    PresetItem("ibps", "IBPS Banking", "4.5cm x 3.5cm", Icons.Default.AccountBalanceWallet),
    PresetItem("railway", "Railway RRB", "3.5cm x 4.5cm", Icons.Default.Train)
)

private val educationPresets = listOf(
    PresetItem("cbse", "CBSE Admit Card", "Passport Size", Icons.Default.School),
    PresetItem("jee", "JEE Main", "10KB - 200KB (JPG)", Icons.Default.EditNote)
)
