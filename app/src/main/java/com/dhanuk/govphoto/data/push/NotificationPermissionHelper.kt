package com.dhanuk.govphoto.data.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime-notification-permission helpers for Android 13+ (TIRAMISU).
 *
 * These live outside [PushRepository] because the system dialog must be launched
 * from UI code that owns an [androidx.activity.result.ActivityResultLauncher],
 * while the repo stays responsible for OneSignal tags / subscription state.
 */

fun isPostNotificationsPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

fun shouldShowPostNotificationsRationale(activity: Activity): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )

fun openNotificationSettings(activity: Activity) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
    }
    runCatching { activity.startActivity(intent) }
}
