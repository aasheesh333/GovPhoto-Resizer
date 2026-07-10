package com.dhanuk.govphoto_resizer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.dhanuk.govphoto_resizer.ui.screens.AllFormsScreen
import com.dhanuk.govphoto_resizer.ui.screens.BatchScreen
import com.dhanuk.govphoto_resizer.ui.screens.EditPhotoScreen
import com.dhanuk.govphoto_resizer.ui.screens.HelpScreen
import com.dhanuk.govphoto_resizer.ui.screens.HistoryScreen
import com.dhanuk.govphoto_resizer.ui.screens.HomeScreen
import com.dhanuk.govphoto_resizer.ui.screens.OnboardingScreen
import com.dhanuk.govphoto_resizer.ui.screens.PhotoUploadScreen
import com.dhanuk.govphoto_resizer.ui.screens.PreviewValidationScreen
import com.dhanuk.govphoto_resizer.ui.screens.SaveSuccessScreen
import com.dhanuk.govphoto_resizer.ui.screens.SettingsScreen
import com.dhanuk.govphoto_resizer.ui.viewmodel.SettingsViewModel
import com.dhanuk.govphoto_resizer.ui.viewmodel.SharedPhotoViewModel

@Composable
fun GovPhotoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by settingsViewModel.state.collectAsState()
    val startDest = if (settings.onboardingComplete) Screen.Home.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    settingsViewModel.setOnboardingComplete(true)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAllForms = { navController.navigate(Screen.AllForms.route) },
                onNavigateToUpload = { presetId ->
                    navController.navigate(Screen.PhotoUpload.createRoute(presetId))
                },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.AllForms.route) {
            AllFormsScreen(
                onNavigateBack = { navController.popBackStack() },
                onPresetSelected = { presetId ->
                    navController.navigate(Screen.PhotoUpload.createRoute(presetId))
                },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        navigation(
            startDestination = Screen.PhotoUpload.route,
            route = "upload_edit_preview"
        ) {
            composable(
                route = Screen.PhotoUpload.route,
                arguments = listOf(navArgument("presetId") { type = NavType.StringType })
            ) { backStackEntry ->
                val presetId = backStackEntry.arguments?.getString("presetId") ?: ""
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = navController.getBackStackEntry("upload_edit_preview")
                )
                sharedPhotoViewModel.setSelectedPreset(presetId)
                PhotoUploadScreen(
                    presetId = presetId,
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPhotoSelected = { navController.navigate(Screen.EditPhoto.route) }
                )
            }

            composable(Screen.EditPhoto.route) { backStackEntry ->
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = navController.getBackStackEntry("upload_edit_preview")
                )
                EditPhotoScreen(
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Screen.PreviewValidation.route) }
                )
            }

            composable(Screen.PreviewValidation.route) { backStackEntry ->
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = navController.getBackStackEntry("upload_edit_preview")
                )
                PreviewValidationScreen(
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onSaveComplete = {
                        navController.navigate(Screen.SaveSuccess.route) {
                            popUpTo(Screen.PreviewValidation.route) { inclusive = true }
                        }
                    },
                    onRetakeEdit = { navController.popBackStack() }
                )
            }

            composable(Screen.SaveSuccess.route) { backStackEntry ->
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = navController.getBackStackEntry("upload_edit_preview")
                )
                SaveSuccessScreen(
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onPhotoSelected = { }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Batch.route) { backStackEntry ->
            val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                viewModelStoreOwner = navController.getBackStackEntry("upload_edit_preview")
            )
            BatchScreen(
                sharedViewModel = sharedPhotoViewModel,
                presets = emptyList(),
                onNavigateBack = { navController.popBackStack() },
                onProcessComplete = { navController.popBackStack() }
            )
        }
    }
}
