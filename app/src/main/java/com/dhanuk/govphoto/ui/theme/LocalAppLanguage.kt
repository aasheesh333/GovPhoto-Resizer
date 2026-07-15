package com.dhanuk.govphoto.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.dhanuk.govphoto.data.datastore.AppLanguage

/**
 * CompositionLocal exposing the currently-selected app language.
 * Defaults to English when no provider is set. Consumers may read this
 * to resolve localized strings independent of the OS Locale. Currently
 * R.string.* resolution still uses the Activity-level locale applied via
 * attachBaseContext; this Local is provided for future direct consumers.
 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
