package com.govphoto.resizer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = IndiaWhite,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = PrimaryDark,
    secondary = IndiaGreen,
    onSecondary = IndiaWhite,
    secondaryContainer = SuccessLight,
    onSecondaryContainer = IndiaGreen,
    tertiary = Saffron,
    onTertiary = Primary,
    tertiaryContainer = WarningLight,
    onTertiaryContainer = Primary,
    background = BackgroundLight,
    onBackground = TextMainLight,
    surface = SurfaceLight,
    onSurface = TextMainLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondaryLight,
    error = Error,
    onError = IndiaWhite,
    errorContainer = ErrorLight,
    onErrorContainer = Error,
    outline = BorderLight,
    outlineVariant = DividerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = IndiaWhite,
    primaryContainer = Primary,
    onPrimaryContainer = IndiaWhite,
    secondary = IndiaGreen,
    onSecondary = IndiaWhite,
    secondaryContainer = IndiaGreen,
    onSecondaryContainer = IndiaWhite,
    tertiary = Saffron,
    onTertiary = Primary,
    tertiaryContainer = Primary,
    onTertiaryContainer = Saffron,
    background = BackgroundDark,
    onBackground = TextMainDark,
    surface = SurfaceDark,
    onSurface = TextMainDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark,
    error = Error,
    onError = IndiaWhite,
    errorContainer = Error,
    onErrorContainer = IndiaWhite,
    outline = BorderDark,
    outlineVariant = DividerDark
)

@Composable
fun GovPhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) BackgroundDark.toArgb() else Primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
