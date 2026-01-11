package com.govphoto.resizer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for GovPhoto Resizer.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class GovPhotoApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Application-level initialization can be done here
    }
}
