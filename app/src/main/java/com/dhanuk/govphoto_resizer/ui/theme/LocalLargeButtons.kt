package com.dhanuk.govphoto_resizer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * When true, GovX buttons and tap targets are upsized to 56dp for accessibility.
 * Provided by MainActivity from SettingsViewModel.state.largeButtons.
 */
val LocalLargeButtons = staticCompositionLocalOf { false }
