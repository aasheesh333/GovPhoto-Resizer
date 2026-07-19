package com.dhanuk.govphoto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.GovPhotoApp
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.datastore.AppLanguage
import com.dhanuk.govphoto.ui.ads.GlobalBannerAd
import com.dhanuk.govphoto.ui.theme.*
import com.dhanuk.govphoto.ui.viewmodel.HomeViewModel
import com.dhanuk.govphoto.ui.viewmodel.RecentPresetUiItem
import com.dhanuk.govphoto.ui.viewmodel.SettingsViewModel

/**
 * Home Screen - Main entry point with Quick Upload and Document Type selection.
 */
@Composable
fun HomeScreen(
    onNavigateToAllForms: () -> Unit,
    onNavigateToUpload: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    var selectedNavItem by rememberSaveable { mutableIntStateOf(0) }
    val recentPresets by homeViewModel.recentPresets.collectAsState()
    
    Scaffold(
        bottomBar = {
            Column {
                // Banner ad above the bottom navigation bar — fixed in the
                // Scaffold bottomBar so it never scrolls with content.
                GlobalBannerAd()
                BottomNavigationBar(
                    selectedItem = selectedNavItem,
                    onItemSelected = { index ->
                        when (index) {
                            0 -> selectedNavItem = 0
                            1 -> onNavigateToHistory()
                            2 -> onNavigateToSettings()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Section
                HomeHeader(settingsViewModel = settingsViewModel)

                // Pro engagement banner (suppressed for Pro users and after dismiss).
                ProBannerHost(
                    onOpenPaywall = onNavigateToPaywall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Upload Button
                QuickUploadButton(
                    onClick = { onNavigateToUpload("quick_upload") }
                )

                // Recent Presets Row (only shown when not empty)
                if (recentPresets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    RecentPresetsRow(
                        recentPresets = recentPresets,
                        onPresetClick = onNavigateToUpload
                    )
                }

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
}

@Composable
private fun HomeHeader(settingsViewModel: SettingsViewModel) {
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
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = stringResource(R.string.cd_app_logo),
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
                LanguageToggle(settingsViewModel = settingsViewModel)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title and Subtitle
            Text(
                text = stringResource(R.string.app_tagline_short),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.compliant_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageToggle(settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.state.collectAsState()
    val context = LocalContext.current
    val isHindi = settings.language == AppLanguage.HINDI
    OutlinedButton(
        onClick = {
            // Apply synchronously to the locale cache so the subsequent recreate()
            // picks up the new language in attachBaseContext(); without recreate the
            // toggle never visibly switched language from the home screen.
            val newLang = if (isHindi) AppLanguage.ENGLISH else AppLanguage.HINDI
            settingsViewModel.applyLanguage(newLang)
            (context as? android.app.Activity)?.recreate()
        },
        shape = RoundedCornerShape(24.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 2.dp
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Translate,
            contentDescription = stringResource(R.string.cd_language_selector),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isHindi) "HI" else "EN",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = stringResource(R.string.cd_language_dropdown),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
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
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
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
                        contentDescription = stringResource(R.string.cd_quick_upload),
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
                contentDescription = stringResource(R.string.cd_navigate_forward),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun RecentPresetsRow(
    recentPresets: List<RecentPresetUiItem>,
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
                text = stringResource(R.string.recent_presets),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(R.string.cd_recent_presets),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(recentPresets, key = { it.presetId }) { item ->
                RecentPresetChip(item = item, onClick = { onPresetClick(item.presetId) })
            }
        }
    }
}

@Composable
private fun RecentPresetChip(
    item: RecentPresetUiItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = stringResource(R.string.cd_recent_preset_item),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (item.useCount > 1) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "x${item.useCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.examName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.dimensions.isNotEmpty()) {
                Text(
                    text = item.dimensions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = item.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = stringResource(R.string.cd_navigate_forward),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
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
                subtitle = "5.1 x 5.1 cm",
                onClick = { onPresetClick("passport") }
            )
            DocumentTypeCard(
                icon = Icons.Default.Fingerprint,
                title = stringResource(R.string.aadhaar),
                subtitle = "3.5 x 4.5 cm",
                onClick = { onPresetClick("aadhaar") }
            )
            DocumentTypeCard(
                icon = Icons.Default.Badge,
                title = stringResource(R.string.pan_card),
                subtitle = "2.5 x 3.5 cm",
                onClick = { onPresetClick("pan_card") }
            )

            // Custom Size Card
            DocumentTypeCard(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.custom_size),
                subtitle = stringResource(R.string.custom_size_subtitle),
                onClick = { onPresetClick(com.dhanuk.govphoto.data.model.PhotoPreset.MANUAL_PRESET_ID) }
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
        .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = stringResource(R.string.cd_document_type),
          tint = MaterialTheme.colorScheme.onPrimary,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.minGovButtonHeight(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.select),
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
            modifier = Modifier
                .minGovButtonHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search_icon),
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

/**
 * Reads engagement state + Pro status via Hilt EntryPoint and shows the
 * compact HomeProBanner only when:
 *  - user is NOT Pro
 *  - at least 1 day has passed since first install (avoid jarring new users)
 *  - user hasn't dismissed the banner in the last 7 days
 */
@Composable
private fun ProBannerHost(
    onOpenPaywall: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entry = remember {
        runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.dhanuk.govphoto.GovPhotoAppEntryPoint::class.java,
            )
        }.getOrNull()
    } ?: return

    val subRepo = entry.subscriptionRepository()
    val engagement = entry.engagementStore()

    val isPro by subRepo.isPro.collectAsState()
    val engagementState by engagement.state.collectAsState(initial = com.dhanuk.govphoto.data.subscription.EngagementStore.State())

    // Once-only stamp of install timestamp.
    LaunchedEffect(Unit) {
        engagement.stampInstallIfNeeded()
    }

    val now = System.currentTimeMillis()
    val oneDayMs = 24L * 60L * 60L * 1000L
    val sevenDaysMs = 7L * oneDayMs

    val showBanner = !isPro &&
        engagementState.installMs > 0L &&
        now - engagementState.installMs >= oneDayMs &&
        (engagementState.bannerDismissedMs == 0L || now - engagementState.bannerDismissedMs >= sevenDaysMs)

    if (showBanner) {
        com.dhanuk.govphoto.ui.subscription.HomeProBanner(
            onOpenPaywall = onOpenPaywall,
            onDismiss = {
                kotlinx.coroutines.MainScope().launch {
                    engagement.markBannerDismissed()
                }
            },
        )
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
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
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
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        NavigationBarItem(
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) },
            icon = {
                Icon(
                    imageVector = if (selectedItem == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.nav_settings)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selectedItem == 2) FontWeight.Bold else FontWeight.Medium
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
