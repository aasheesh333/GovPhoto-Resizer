package com.govphoto.resizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.govphoto.resizer.ui.navigation.GovPhotoNavHost
import com.govphoto.resizer.ui.theme.GovPhotoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity - Entry point of the application.
 * Uses Jetpack Compose for UI rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            GovPhotoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GovPhotoNavHost()
                }
            }
        }
    }
}
