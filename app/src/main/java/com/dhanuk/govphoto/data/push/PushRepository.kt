package com.dhanuk.govphoto.data.push

import android.content.Context
import com.dhanuk.govphoto.BuildConfig
import com.onesignal.OneSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PushCategoryStore,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** OnInit: enable OneSignal with verbose logging in debug. Tag user with category state. */
    suspend fun init() {
        OneSignal.initWithContext(context, BuildConfig.ONESIGNAL_APP_ID)
        OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) com.onesignal.Debug.LOG_LEVEL.DEBUG else com.onesignal.Debug.LOG_LEVEL.WARN
        refreshTags()
    }

    /** Apply enabled-states as OneSignal tags, so server-segmented sends honor user prefs.
     *  Tag key = PushCategory.storageKey; value = "1" if enabled else "0". */
    suspend fun refreshTags() {
        for (cat in PushCategory.entries) {
            val enabled = store.isEnabled(cat)
            // Send a tag-bracket map - OneSignal accepts key-value tags only at user-level.
            OneSignal.User.addTag(cat.storageKey, if (enabled) "1" else "0")
        }
    }

    fun setCategoryEnabled(category: PushCategory, enabled: Boolean) = scope.launch {
        store.setEnabled(category, enabled)
        OneSignal.User.addTag(category.storageKey, if (enabled) "1" else "0")
    }
}
