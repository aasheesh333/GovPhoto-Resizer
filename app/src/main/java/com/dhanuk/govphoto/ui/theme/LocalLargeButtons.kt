package com.dhanuk.govphoto.ui.theme

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

/**
 * Minimum-height Modifier that honours the Large Buttons accessibility setting:
 * 56dp when enabled, 48dp otherwise. Apply to bespoke (non-GovButton) call-to-
 * action buttons so the accessibility toggle scales them app-wide instead of only
 * affecting GovButton/GovOutlinedButton instances.
 */
@Composable
fun Modifier.minGovButtonHeight(): Modifier {
    val min = if (LocalLargeButtons.current) 56.dp else 48.dp
    return this.heightIn(min = min)
}
