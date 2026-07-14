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
-keep class com.dhanuk.govphoto_resizer.data.model.** { *; }

# Keep Room entities
-keep class com.dhanuk.govphoto_resizer.data.local.entity.** { *; }

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
-keep class com.dhanuk.govphoto_resizer.data.local.dao.** { *; }

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
}

# Compose
-dontwarn androidx.compose.**
