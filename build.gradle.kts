// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Note: as of AGP 9.0, the Kotlin Gradle plugin (KGP) is no longer applied
// as a separate `org.jetbrains.kotlin.android` plugin — AGP 9 ships with
// "built-in Kotlin" support (KGP 2.2.10+ embedded, auto-upgraded to whatever
// version a sibling Kotlin plugin such as `org.jetbrains.kotlin.plugin.compose`
// requires). The Kotlin Compose plugin below pins Kotlin 2.4.10 for both
// the compose compiler and the built-in Kotlin toolchain.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}
