package com.dhanuk.govphoto.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.dhanuk.govphoto.ui.screens.AllFormsScreen
import com.dhanuk.govphoto.ui.screens.BatchScreen
import com.dhanuk.govphoto.ui.screens.EditPhotoScreen
import com.dhanuk.govphoto.ui.screens.HelpScreen
import com.dhanuk.govphoto.ui.screens.HistoryScreen
import com.dhanuk.govphoto.ui.screens.HomeScreen
import com.dhanuk.govphoto.ui.screens.OnboardingScreen
import com.dhanuk.govphoto.ui.screens.PhotoUploadScreen
import com.dhanuk.govphoto.ui.screens.PreviewValidationScreen
import com.dhanuk.govphoto.ui.screens.SaveSuccessScreen
import com.dhanuk.govphoto.ui.screens.PaywallScreen
import com.dhanuk.govphoto.ui.screens.SettingsScreen
import com.dhanuk.govphoto.ui.viewmodel.SettingsViewModel
import com.dhanuk.govphoto.ui.viewmodel.SharedPhotoViewModel

@SuppressLint("UnrememberedGetBackStackEntry")
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
                    viewModelStoreOwner = remember { navController.getBackStackEntry("upload_edit_preview") }
                )
                sharedPhotoViewModel.setSelectedPreset(presetId)
                PhotoUploadScreen(
                    presetId = presetId,
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPhotoSelected = { navController.navigate(Screen.EditPhoto.route) }
                )
            }

            composable(Screen.EditPhoto.route) {
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = remember { navController.getBackStackEntry("upload_edit_preview") }
                )
                EditPhotoScreen(
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Screen.PreviewValidation.route) }
                )
            }

            composable(Screen.PreviewValidation.route) {
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = remember { navController.getBackStackEntry("upload_edit_preview") }
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

            composable(Screen.SaveSuccess.route) {
                val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                    viewModelStoreOwner = remember { navController.getBackStackEntry("upload_edit_preview") }
                )
                SaveSuccessScreen(
                    sharedViewModel = sharedPhotoViewModel,
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
                )
            }
        }

        composable(Screen.History.route) {
            val context = LocalContext.current
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onPhotoSelected = { imagePath ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(android.net.Uri.parse(imagePath), "image/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Batch.route) {
            val sharedPhotoViewModel: SharedPhotoViewModel = hiltViewModel(
                viewModelStoreOwner = remember { navController.getBackStackEntry("upload_edit_preview") }
            )
            BatchScreen(
                sharedViewModel = sharedPhotoViewModel,
                presets = emptyList(),
                onNavigateBack = { navController.popBackStack() },
                onProcessComplete = { navController.popBackStack() }
            )
        }

        composable(Screen.Paywall.route) {
            PaywallScreen(
                onNavigateBack = { navController.popBackStack() },
                onSubscribeSuccess = {
                    navController.popBackStack()
                }
            )
        }
    }
}
