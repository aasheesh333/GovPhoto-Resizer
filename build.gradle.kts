// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Note: as of AGP 9.0, the Kotlin Gradle plugin (KGP) is no longer applied
// as a separate `org.jetbrains.kotlin.android` plugin — AGP 9 ships with
// "built-in Kotlin" support (KGP 2.2.10+ embedded, auto-upgraded to whatever
// version a sibling Kotlin plugin such as `org.jetbrains.kotlin.plugin.compose`
// requires). The Kotlin Compose plugin below pins Kotlin 2.3.21 for both
// the compose compiler and the built-in Kotlin toolchain.
//
// We pin Kotlin 2.3.21 (not 2.4.10) because AGP 9.x's bundled lint crashes
// under Kotlin 2.4 when scanning build scripts:
//   Message: \`findFirCompiledSymbol only works on compiled declarations, but
//            the given declaration is not compiled.\`
//   Stack:   KaFirScriptSymbol → SymbolLightClassForScript.getOwnFields
//            → UastGradleVisitor.visitBuildScript → LintDriver.checkBuildScripts
// Re-upgrade to Kotlin 2.4 once a fixed AGP (>9.3.1) ships; the changelog
// above keeps a paper trail.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}
