plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

apply(from = "secrets.gradle.kts")

android {
    namespace = "com.dhanuk.govphoto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dhanuk.govphoto"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "PRIVACY_URL", "\"${project.findProperty("PRIVACY_URL") ?: "https://dhanuk.page.gd/govphoto-resizer/privacy.html"}\"")
        buildConfigField("String", "TERMS_URL", "\"${project.findProperty("TERMS_URL") ?: "https://example.in/terms.html"}\"")
        buildConfigField("String", "CONTACT_URL", "\"${project.findProperty("CONTACT_URL") ?: "https://example.in/contact.html"}\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"${project.findProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"}\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT", "\"${project.findProperty("ADMOB_BANNER_UNIT") ?: "ca-app-pub-3940256099942544/6300978111"}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT", "\"${project.findProperty("ADMOB_INTERSTITIAL_UNIT") ?: "ca-app-pub-3940256099942544/1033173712"}\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT", "\"${project.findProperty("ADMOB_REWARDED_UNIT") ?: "ca-app-pub-3940256099942544/5224354917"}\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${project.findProperty("REVENUECAT_API_KEY") ?: "test_reBLWKQuYoCcNnfaDmwjuQiGGCu"}\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${project.findProperty("ONESIGNAL_APP_ID") ?: "test-onesignal-id"}\"")

        manifestPlaceholders["onesignal_app_id"] = project.findProperty("ONESIGNAL_APP_ID") ?: "test-onesignal-id"
        // Route the real AdMob App ID (from secrets.properties / CI secrets) into the
        // manifest meta-data via a placeholder, so production builds use the live ID
        // while local dev falls back to Google's official test App ID.
        manifestPlaceholders["ADMOB_APP_ID"] = project.findProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export location
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            // secrets.gradle.kts (applied above on the app project) sets these
            // extra properties when CI decoded KEYSTORE_BASE64 into
            // release-keystore.jks. Read them via project.findProperty (the
            // app project scope) — not rootProject — so the lookup matches.
            val ksFile = project.findProperty("KEYSTORE_FILE") as String?
            if (ksFile != null && rootProject.file(ksFile).exists()) {
                storeFile = rootProject.file(ksFile)
                storePassword = project.findProperty("KEYSTORE_PASSWORD") as String
                keyAlias = project.findProperty("KEY_ALIAS") as String
                keyPassword = project.findProperty("KEY_PASSWORD") as String
                logger.lifecycle("release signingConfig: keystore=$ksFile alias=$keyAlias")
            } else {
                logger.warn("release signingConfig: KEYSTORE_FILE missing — release will sign with debug keystore")
            }
        }
    }

  buildTypes {
    release {
      // Use the configured release signingConfig whenever the keystore could
      // be loaded (detected by storeFile != null); otherwise fall back to
      // debug. The previous version read KEYSTORE_FILE via rootProject which
      // never matched (app-project extra properties), so release has been
      // signing with the debug keystore since day one.
      signingConfig = if (signingConfigs.findByName("release")?.storeFile != null) {
          signingConfigs.getByName("release")
      } else {
          signingConfigs.getByName("debug")
      }
      logger.lifecycle(
          "release buildType: signingConfig=" +
              (if (signingConfigs.findByName("release")?.storeFile != null) "release (CI keystore)" else "debug (fallback — no KEYSTORE_FILE)")
      )
      isMinifyEnabled = true
      isShrinkResources = true
      buildConfigField("boolean", "FORCE_NO_ADS", "false")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "FORCE_NO_ADS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.6")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta4")

    // Image Loading - Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Firebase BoM + Crashlytics + Analytics
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // AdMob via Google Play Services Ads + UMP
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")

    // RevenueCat
    implementation("com.revenuecat.purchases:purchases:8.24.0")

    // OneSignal
    implementation("com.onesignal:OneSignal:5.1.5")

    // Splash Screen API
    implementation("androidx.core:core-splashscreen:1.0.1")

    // EXIF orientation support for camera-captured photos
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
