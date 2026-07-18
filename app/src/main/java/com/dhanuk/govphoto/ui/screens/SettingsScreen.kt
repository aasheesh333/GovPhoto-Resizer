package com.dhanuk.govphoto.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.datastore.AppLanguage
import com.dhanuk.govphoto.data.datastore.DarkModePref
import com.dhanuk.govphoto.ui.ads.BannerAd
import com.dhanuk.govphoto.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.state.collectAsState()
    val adInfo by viewModel.adDiagnosticInfo.collectAsState()
    var showAdDiagnostics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("govphoto_settings", android.content.Context.MODE_PRIVATE) }
    var preventScreenshots by remember { mutableStateOf(sharedPreferences.getBoolean("prevent_screenshots", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
            // Appearance Section
            SettingsSection(title = stringResource(R.string.appearance)) {
                // Theme selector
                ThemeSelector(
                    selected = settings.darkMode,
                    onSelected = { viewModel.setDarkMode(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Dynamic Color toggle
                val dynamicColorSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                SettingsToggle(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.dynamic_color),
                    subtitle = stringResource(R.string.dynamic_color_desc),
                    isChecked = settings.dynamicColor && dynamicColorSupported,
                    enabled = dynamicColorSupported,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Support us Section
            SettingsSection(title = stringResource(R.string.support_us_section)) {

                // Share app
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.share_app),
                    subtitle = stringResource(R.string.share_app_subtitle),
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "GovPhoto Resizer")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out GovPhoto Resizer — resize photos & signatures for any Indian exam form. " +
                                "Play Store link coming soon; in the meantime: https://play.google.com/store/apps/details?id=${context.packageName.removeSuffix(".debug")}"
                            )
                        }
                        try {
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_app_chooser_title)))
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.share_app_subtitle), Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Feedback (email to support)
                SettingsItem(
                    icon = Icons.Default.Email,
                    title = stringResource(R.string.feedback),
                    subtitle = stringResource(R.string.feedback_subtitle),
                    onClick = {
                        val emailIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@dhanuksoftwares.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "GovPhoto Resizer feedback")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "\n\n--- Device info ---\n" +
                                "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                                "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n" +
                                "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n"
                            )
                        }
                        try {
                            context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.feedback)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Privacy choices (UMP form)
                SettingsItem(
                    icon = Icons.Default.Cookie,
                    title = stringResource(R.string.privacy_choices),
                    subtitle = stringResource(R.string.privacy_choices_subtitle),
                    onClick = {
                        val activity = context as? android.app.Activity
                        val consentInfo = com.google.android.ump.UserMessagingPlatform.getConsentInformation(context)
                        // UMP: the privacy-options form only renders when the requirement
                        // status is REQUIRED; otherwise showPrivacyOptionsForm silently
                        // no-ops, which is why the entry point appeared "not working".
                        val privacyOptionsRequired = consentInfo.privacyOptionsRequirementStatus ==
                            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                        when {
                            activity == null ->
                                android.widget.Toast.makeText(context, context.getString(R.string.privacy_choices_not_available), android.widget.Toast.LENGTH_SHORT).show()
                            !privacyOptionsRequired ->
                                android.widget.Toast.makeText(context, context.getString(R.string.privacy_choices_not_required), android.widget.Toast.LENGTH_SHORT).show()
                            else -> com.google.android.ump.UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                                if (error != null) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.privacy_choices_not_available), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )

                // Open Privacy Policy in browser
                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = stringResource(R.string.privacy_policy_web),
                    subtitle = stringResource(R.string.privacy_policy_web_subtitle),
                    onClick = {
                        val url = BuildConfig.PRIVACY_URL
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Open Terms of Service in browser
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.terms_of_service),
                    subtitle = stringResource(R.string.terms_subtitle),
                    onClick = {
                        val url = BuildConfig.TERMS_URL
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Open Contact page in browser
                SettingsItem(
                    icon = Icons.Default.ContactMail,
                    title = stringResource(R.string.contact_us),
                    subtitle = stringResource(R.string.contact_subtitle),
                    onClick = {
                        val url = BuildConfig.CONTACT_URL
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Subscription Section
            SettingsSection(title = stringResource(R.string.subscription_section)) {
                SettingsItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = stringResource(R.string.remove_ads),
                    subtitle = stringResource(R.string.remove_ads_subtitle),
                    onClick = onNavigateToPaywall
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Language Section
            SettingsSection(title = stringResource(R.string.language)) {
                LanguageOption(
                    label = stringResource(R.string.english),
                    isSelected = settings.language == AppLanguage.ENGLISH,
                    onClick = {
                        viewModel.applyLanguage(AppLanguage.ENGLISH)
                        (context as? Activity)?.recreate()
                    }
                )
                LanguageOption(
                    label = stringResource(R.string.hindi),
                    isSelected = settings.language == AppLanguage.HINDI,
                    onClick = {
                        viewModel.applyLanguage(AppLanguage.HINDI)
                        (context as? Activity)?.recreate()
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Accessibility Section
            SettingsSection(title = stringResource(R.string.accessibility)) {
                SettingsToggle(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.large_buttons),
                    subtitle = stringResource(R.string.large_buttons_desc),
                    isChecked = settings.largeButtons,
                    onCheckedChange = { viewModel.setLargeButtons(it) }
                )
                SettingsToggle(
                    icon = Icons.Default.Contrast,
                    title = stringResource(R.string.high_contrast),
                    subtitle = stringResource(R.string.high_contrast_desc),
                    isChecked = settings.highContrast,
                    onCheckedChange = { viewModel.setHighContrast(it) }
                )
                SettingsToggle(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.prevent_screenshots),
                    subtitle = stringResource(R.string.prevent_screenshots_desc),
                    isChecked = preventScreenshots,
                    onCheckedChange = {
                        preventScreenshots = it
                        sharedPreferences.edit().putBoolean("prevent_screenshots", it).apply()
                        val activity = context as? android.app.Activity
                        if (it) {
                            activity?.window?.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.version),
                    subtitle = BuildConfig.VERSION_NAME
                )
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.share_crash_log),
                    subtitle = stringResource(R.string.share_crash_log_desc),
                    onClick = {
                        try {
                            val crashFile = java.io.File(context.filesDir, "last_crash.txt")
                            if (crashFile.exists()) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    crashFile
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Crash Log"))
                            } else {
                                android.widget.Toast.makeText(context, "No crash logs found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not share crash log", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.ad_diagnostics),
                    subtitle = "Inspect why banner / interstitial / rewarded ads may not load",
                    onClick = { showAdDiagnostics = true }
                )
            }

            if (showAdDiagnostics) {
                com.dhanuk.govphoto.ui.components.AdDiagnosticsDialog(
                    info = adInfo,
                    onDismiss = { showAdDiagnostics = false },
                    onRefresh = { viewModel.refreshAdDiagnostics() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
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
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { heading() }
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    selected: DarkModePref,
    onSelected: (DarkModePref) -> Unit
) {
    val options = listOf(
        DarkModePref.LIGHT to stringResource(R.string.theme_light),
        DarkModePref.DARK to stringResource(R.string.theme_dark),
        DarkModePref.SYSTEM to stringResource(R.string.theme_system),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (pref, label) ->
            FilterChip(
                selected = selected == pref,
                onClick = { onSelected(pref) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
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
                .heightIn(min = 48.dp)
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
                    selectedColor = MaterialTheme.colorScheme.primary
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
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
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
            contentDescription = stringResource(R.string.cd_settings_toggle),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.cd_settings_item),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_navigate_forward),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
