package com.govphoto.resizer.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AllForms : Screen("all_forms")
    data object PhotoUpload : Screen("photo_upload/{presetId}") {
        fun createRoute(presetId: String) = "photo_upload/$presetId"
    }
    data object EditPhoto : Screen("edit_photo")
    data object PreviewValidation : Screen("preview_validation")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
