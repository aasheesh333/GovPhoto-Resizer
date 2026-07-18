package com.dhanuk.govphoto.data.push

import android.content.Context
import android.util.Log
import com.dhanuk.govphoto.BuildConfig
import com.onesignal.OneSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneSignal push notification coordinator.
 *
 * Common reasons notifications don't arrive on the OneSignal dashboard:
 *  - Wrong / placeholder ONESIGNAL_APP_ID (init silently fails).
 *  - POST_NOTIFICATIONS runtime permission not granted on Android 13+
 *    (the OS never delivers push, no token is registered).
 *  - OneSignal.init never called, or threw before completing.
 *
 * This repository:
 *  - Catches and logs init exceptions with the app id redacted so
 *    "notifications not arriving" is diagnosable from logcat / Crashlytics.
 *  - Exposes [promptForPermission] so the UI can call OneSignal's official
 *    system-permission prompt at a well-timed moment (e.g. after 1 min of
 *    foreground use, once per app lifetime).
 */
@Singleton
class PushRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PushCategoryStore,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** OnInit: init with the configured App ID, then push the current per-
     *  category opt-in state as OneSignal tags. Failures are logged. */
    suspend fun init() {
        val appId = BuildConfig.ONESIGNAL_APP_ID
        Log.i(TAG, "init: appIdLen=${appId.length} (test=${appId == TEST_ID})")
        try {
            OneSignal.initWithContext(context, appId)
            Log.i(TAG, "OneSignal.initWithContext returned")
            // Post-init subscription log so users debugging "no devices on the
            // dashboard" can see exactly what the OneSignal SDK thinks about
            // this install. opt-in is needed for the SDK to register a
            // push token; a non-empty token 1.5s after init usually means
            // the registration succeeded.
            scope.launch {
                kotlinx.coroutines.delay(1_500)
                runCatching {
                    val sub = OneSignal.User.pushSubscription
                    Log.i(
                        TAG,
                        "subscription: optedIn=${sub.optedIn} " +
                            "token=${if (sub.token.isNullOrEmpty()) "<none>" else sub.token.take(8) + "..."}"
                    )
                }.onFailure { Log.w(TAG, "pushSubscription read failed (SDK API drift?)", it) }
            }
        } catch (t: Throwable) {
            // The SDK throws if appId is malformed / blank / non-UUID — surface
            // this in logcat so "notifications not arriving" is diagnosable.
            Log.e(TAG, "OneSignal.initWithContext threw — notifications will NOT work", t)
        }
        refreshTags()
    }

    /** Apply enabled-states as OneSignal tags, so server-segmented sends honour
     *  user prefs. Tag key = PushCategory.storageKey; value = "1"/"0". */
    suspend fun refreshTags() {
        for (cat in PushCategory.entries) {
            val enabled = store.isEnabled(cat)
            // Guard: if OneSignal init failed / was never called, addTag throws.
            // Best-effort, swallow SDK errors so a misbehaving SDK can't crash
            // the notification-settings toggles.
            runCatching { OneSignal.User.addTag(cat.storageKey, if (enabled) "1" else "0") }
                .onFailure { Log.w(TAG, "addTag($cat) failed", it) }
        }
    }

    fun setCategoryEnabled(category: PushCategory, enabled: Boolean) = scope.launch {
        store.setEnabled(category, enabled)
        runCatching { OneSignal.User.addTag(category.storageKey, if (enabled) "1" else "0") }
            .onFailure { Log.w(TAG, "addTag($category) failed", it) }
    }

    /**
     * Trigger the system notification-permission prompt via OneSignal.
     *
     * On Android 13+ this is required for any push notification to be delivered
     * and for a push token to be registered on the OneSignal dashboard.
     * [fallbackToSettings] = true routes the user to system notification settings
     * after the OS prompt has been permanently denied.
     *
     * Fire-and-forget: launches on the repo's IO scope so callers (e.g. the
     * NotificationPermissionGate) don't need to be in a coroutine themselves.
     */
    fun promptForPermission(fallbackToSettings: Boolean = true) {
        scope.launch {
            runCatching { OneSignal.Notifications.requestPermission(fallbackToSettings) }
                .onFailure { Log.w(TAG, "requestPermission failed", it) }
        }
    }

    private companion object {
        const val TAG = "PushRepository"
        const val TEST_ID = "test-onesignal-id"
    }
}