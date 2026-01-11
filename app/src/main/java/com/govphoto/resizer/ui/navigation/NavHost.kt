package com.govphoto.resizer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.govphoto.resizer.ui.screens.AllFormsScreen
import com.govphoto.resizer.ui.screens.EditPhotoScreen
import com.govphoto.resizer.ui.screens.HistoryScreen
import com.govphoto.resizer.ui.screens.HomeScreen
import com.govphoto.resizer.ui.screens.PhotoUploadScreen
import com.govphoto.resizer.ui.screens.PreviewValidationScreen
import com.govphoto.resizer.ui.screens.SettingsScreen
import com.govphoto.resizer.ui.viewmodel.SharedPhotoViewModel

/**
 * Main navigation host for the app.
 * Uses a shared ViewModel for passing image state between screens.
 */
@Composable
fun GovPhotoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAllForms = {
                    navController.navigate(Screen.AllForms.route)
                },
                onNavigateToUpload = { presetId ->
                    sharedPhotoViewModel.setSelectedPreset(presetId)
                    navController.navigate(Screen.PhotoUpload.createRoute(presetId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.AllForms.route) {
            AllFormsScreen(
                onNavigateBack = { navController.popBackStack() },
                onPresetSelected = { presetId ->
                    sharedPhotoViewModel.setSelectedPreset(presetId)
                    navController.navigate(Screen.PhotoUpload.createRoute(presetId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.PhotoUpload.route,
            arguments = listOf(
                navArgument("presetId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val presetId = backStackEntry.arguments?.getString("presetId") ?: ""
            PhotoUploadScreen(
                presetId = presetId,
                sharedViewModel = sharedPhotoViewModel,
                onNavigateBack = { navController.popBackStack() },
                onPhotoSelected = {
                    navController.navigate(Screen.EditPhoto.route)
                }
            )
        }
        
        composable(Screen.EditPhoto.route) {
            EditPhotoScreen(
                sharedViewModel = sharedPhotoViewModel,
                onNavigateBack = { navController.popBackStack() },
                onContinue = {
                    navController.navigate(Screen.PreviewValidation.route)
                }
            )
        }
        
        composable(Screen.PreviewValidation.route) {
            PreviewValidationScreen(
                sharedViewModel = sharedPhotoViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaveComplete = {
                    sharedPhotoViewModel.clearState()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onRetakeEdit = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onPhotoSelected = { /* Handle photo selection */ }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
