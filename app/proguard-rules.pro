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

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }
-keep class com.revenuecat.purchases.google.** { *; }
-keepclassmembers class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# OneSignal
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
