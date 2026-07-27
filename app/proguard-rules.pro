# Add project specific ProGuard rules here.

# Keep Gson annotations
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes for Gson
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep preset model classes
-keep class com.dhanuk.govphoto.data.model.** { *; }

# Keep Room entities
-keep class com.dhanuk.govphoto.data.local.entity.** { *; }

# Keep Hilt
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Room Database
-keep class * extends androidx.room.RoomDatabase { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# ML Kit common
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Room DAO interfaces
-keep class com.dhanuk.govphoto.data.local.dao.** { *; }

# Kotlin metadata
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
    <methods>;
}

# Compose
-dontwarn androidx.compose.**

# Compose animation: R8 strips KeyframesSpecConfig.at() on BOM 2024.01.00,
# causing NoSuchMethodError KeyframesSpec$KeyframeEntity.at(...) at runtime
# in release builds. Affects M3 Switch, Slider, CircularProgressIndicator,
# and any keyframes{}-based spec. Keep the keyframes class graph + members.
-keep class androidx.compose.animation.core.KeyframesSpec { *; }
-keep class androidx.compose.animation.core.KeyframesSpec$* { *; }
-keep class androidx.compose.animation.core.KeyframesSpecConfig { *; }
-keepclassmembers class androidx.compose.animation.core.** {
    public <methods>;
}

# DataStore Preferences — accessed via Kotlin reflection under minify
-keep class androidx.datastore.preferences.** { *; }
-keep class com.dhanuk.govphoto.data.datastore.** { *; }

# Firebase Crashlytics — auto-shipped by SDK, no explicit rules needed
# (verified by Firebase docs; Crashlytics Gradle plugin also bundles rules)

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# UMP
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# OneSignal
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
