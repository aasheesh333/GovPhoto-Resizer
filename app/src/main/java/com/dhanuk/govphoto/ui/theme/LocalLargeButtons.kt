package com.dhanuk.govphoto.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * When true, GovX buttons and tap targets are upsized to 56dp for accessibility.
 * Provided by MainActivity from SettingsViewModel.state.largeButtons.
 */
val LocalLargeButtons = staticCompositionLocalOf { false }

/**
 * When true, app renders with higher contrast color scheme for accessibility.
 * Provided by MainActivity from SettingsViewModel.state.highContrast.
 */
val LocalHighContrast = staticCompositionLocalOf { false }
