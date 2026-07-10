package com.dhanuk.govphoto_resizer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// M3 Expressive seed derived from OD run #1. If OD failed, fallback #006495 used.
val GovSeedColor = Color(0xFF006495)

// Tonal palette (light scheme)
val GovPrimary        = Color(0xFF006495)
val GovOnPrimary      = Color(0xFFFFFFFF)
val GovPrimaryContainer = Color(0xFFD0E6FF)
val GovOnPrimaryContainer = Color(0xFF001F2A)

val GovSecondary       = Color(0xFF4F616E)
val GovOnSecondary     = Color(0xFFFFFFFF)
val GovSecondaryContainer = Color(0xFFD2E5F4)
val GovOnSecondaryContainer = Color(0xFF0B1D29)

val GovTertiary        = Color(0xFF5A5C7E)
val GovOnTertiary      = Color(0xFFFFFFFF)
val GovTertiaryContainer = Color(0xFFE3E0FF)
val GovOnTertiaryContainer = Color(0xFF161A37)

val GovError           = Color(0xFFBA1A1A)
val GovOnError         = Color(0xFFFFFFFF)
val GovErrorContainer  = Color(0xFFFFDAD6)
val GovOnErrorContainer = Color(0xFF410002)

val GovBackground      = Color(0xFFF8F9FB)
val GovOnBackground    = Color(0xFF191C1E)
val GovSurface         = Color(0xFFF8F9FB)
val GovOnSurface       = Color(0xFF191C1E)
val GovSurfaceVariant  = Color(0xFFDDE3EA)
val GovOnSurfaceVariant = Color(0xFF41474D)
val GovOutline         = Color(0xFF71787E)
val GovOutlineVariant  = Color(0xFFC1C7CE)

// Dark scheme
val GovPrimaryDark        = Color(0xFF9ECAFF)
val GovOnPrimaryDark      = Color(0xFF00344C)
val GovPrimaryContainerDark = Color(0xFF004B6D)
val GovOnPrimaryContainerDark = Color(0xFFD0E6FF)

val GovSecondaryDark     = Color(0xFFB6C9DA)
val GovOnSecondaryDark   = Color(0xFF20323F)
val GovSecondaryContainerDark = Color(0xFF374956)
val GovOnSecondaryContainerDark = Color(0xFFD2E5F4)

val GovTertiaryDark     = Color(0xFFC5C4FF)
val GovOnTertiaryDark   = Color(0xFF2A2E54)
val GovTertiaryContainerDark = Color(0xFF42457A)
val GovOnTertiaryContainerDark = Color(0xFFE3E0FF)

val GovErrorDark        = Color(0xFFFFB4AB)
val GovOnErrorDark      = Color(0xFF690005)
val GovErrorContainerDark = Color(0xFF93000A)
val GovOnErrorContainerDark = Color(0xFFFFDAD6)

val GovBackgroundDark   = Color(0xFF111417)
val GovOnBackgroundDark = Color(0xFFE2E2E5)
val GovSurfaceDark      = Color(0xFF111417)
val GovOnSurfaceDark     = Color(0xFFE2E2E5)
val GovSurfaceVariantDark = Color(0xFF41474D)
val GovOnSurfaceVariantDark = Color(0xFFC1C7CE)
val GovOutlineDark       = Color(0xFF8B9198)
val GovOutlineVariantDark = Color(0xFF41474D)

// Status — M3 Expressive expressive tones (NOT legacy tertiary green)
val GovSuccess         = Color(0xFF2E7D32)
val GovSuccessContainer= Color(0xFFB8F5C0)
val GovOnSuccess       = Color(0xFFFFFFFF)
val GovOnSuccessContainer = Color(0xFF002107)
val GovWarning         = Color(0xFFB86A00)
val GovWarningContainer= Color(0xFFFFDDBA)

// Photo background swatches (used by EditPhotoScreen / PreviewValidationScreen)
val GovPhotoBgWhite       = Color(0xFFFFFFFF)
val GovPhotoBgStudioBlue  = Color(0xFFD0E6F5)
val GovPhotoBgLightGrey   = Color(0xFFEEEEEE)
val GovPhotoBgGradientA    = Color(0xFFFFFFFF)
val GovPhotoBgGradientB   = Color(0xFFD0E6F5)
val GovPhotoBgTransparent = Color(0x00000000)

// Pre-built schemes consumed by Theme.kt
val govLightColorScheme = lightColorScheme(
    primary = GovPrimary, onPrimary = GovOnPrimary,
    primaryContainer = GovPrimaryContainer, onPrimaryContainer = GovOnPrimaryContainer,
    secondary = GovSecondary, onSecondary = GovOnSecondary,
    secondaryContainer = GovSecondaryContainer, onSecondaryContainer = GovOnSecondaryContainer,
    tertiary = GovTertiary, onTertiary = GovOnTertiary,
    tertiaryContainer = GovTertiaryContainer, onTertiaryContainer = GovOnTertiaryContainer,
    error = GovError, onError = GovOnError,
    errorContainer = GovErrorContainer, onErrorContainer = GovOnErrorContainer,
    background = GovBackground, onBackground = GovOnBackground,
    surface = GovSurface, onSurface = GovOnSurface,
    surfaceVariant = GovSurfaceVariant, onSurfaceVariant = GovOnSurfaceVariant,
    outline = GovOutline, outlineVariant = GovOutlineVariant,
)

val govDarkColorScheme = darkColorScheme(
    primary = GovPrimaryDark, onPrimary = GovOnPrimaryDark,
    primaryContainer = GovPrimaryContainerDark, onPrimaryContainer = GovOnPrimaryContainerDark,
    secondary = GovSecondaryDark, onSecondary = GovOnSecondaryDark,
    secondaryContainer = GovSecondaryContainerDark, onSecondaryContainer = GovOnSecondaryContainerDark,
    tertiary = GovTertiaryDark, onTertiary = GovOnTertiaryDark,
    tertiaryContainer = GovTertiaryContainerDark, onTertiaryContainer = GovOnTertiaryContainerDark,
    error = GovErrorDark, onError = GovOnErrorDark,
    errorContainer = GovErrorContainerDark, onErrorContainer = GovOnErrorContainerDark,
    background = GovBackgroundDark, onBackground = GovOnBackgroundDark,
    surface = GovSurfaceDark, onSurface = GovOnSurfaceDark,
    surfaceVariant = GovSurfaceVariantDark, onSurfaceVariant = GovOnSurfaceVariantDark,
    outline = GovOutlineDark, outlineVariant = GovOutlineVariantDark,
)


