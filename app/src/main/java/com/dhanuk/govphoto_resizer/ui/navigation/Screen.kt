package com.dhanuk.govphoto_resizer.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object AllForms : Screen("all_forms")
    data object PhotoUpload : Screen("photo_upload/{presetId}") {
        fun createRoute(presetId: String) = "photo_upload/$presetId"
    }
    data object EditPhoto : Screen("edit_photo")
    data object PreviewValidation : Screen("preview_validation")
    data object SaveSuccess : Screen("save_success")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Help : Screen("help")
    data object HelpArticle : Screen("help_article/{articleId}") {
        fun createRoute(articleId: String) = "help_article/$articleId"
    }
    data object Batch : Screen("batch")
    data object BatchResults : Screen("batch_results")
}
