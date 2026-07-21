package com.dhanuk.govphoto.data.subscription

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.engagementDataStore by preferencesDataStore(name = "govphoto_engagement")

/**
 * Tracks pro-engagement nudges so they don't become annoying:
 *  - First install timestamp so we delay nudges for new users
 *  - Banner-dismissed timestamp (HomeScreen Pro banner)
 *  - Sheet-shown timestamp + last-shown-day (post-reward upgrade sheet)
 *  - Save-count trigger timestamp (e.g. "Loved GovPhoto?" after N saves)
 *
 * Keeps all upgrade nudges in one DataStore so the rules stay consistent.
 */
@Singleton
class EngagementStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val INSTALL_MS              = longPreferencesKey("install_ms")
        val BANNER_DISMISSED_MS     = longPreferencesKey("banner_dismissed_ms")
        val SHEET_LAST_SHOWN_MS     = longPreferencesKey("sheet_last_shown_ms")
        val SHEET_LAST_SHOWN_DAY_MS = longPreferencesKey("sheet_last_shown_day_ms")
        val FIRST_LAUNCH_DONE       = booleanPreferencesKey("first_launch_done")
    }

    data class State(
        val installMs: Long = 0L,
        val bannerDismissedMs: Long = 0L,
        val sheetLastShownMs: Long = 0L,
        val sheetLastShownDayMs: Long = 0L,
        val firstLaunchDone: Boolean = false,
    )

    val state: Flow<State> = context.engagementDataStore.data.map { prefs ->
        State(
            installMs = prefs[Keys.INSTALL_MS] ?: 0L,
            bannerDismissedMs = prefs[Keys.BANNER_DISMISSED_MS] ?: 0L,
            sheetLastShownMs = prefs[Keys.SHEET_LAST_SHOWN_MS] ?: 0L,
            sheetLastShownDayMs = prefs[Keys.SHEET_LAST_SHOWN_DAY_MS] ?: 0L,
            firstLaunchDone = prefs[Keys.FIRST_LAUNCH_DONE] ?: false,
        )
    }

    suspend fun firstRead(): State = state.first()

    /** Stamp the install timestamp (idempotent — does nothing on subsequent calls). */
    suspend fun stampInstallIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        val current = firstRead()
        if (current.installMs == 0L) {
            context.engagementDataStore.edit { it[Keys.INSTALL_MS] = nowMs }
        }
    }

    suspend fun markBannerDismissed(nowMs: Long = System.currentTimeMillis()) {
        context.engagementDataStore.edit { it[Keys.BANNER_DISMISSED_MS] = nowMs }
    }

    suspend fun markSheetShown(nowMs: Long = System.currentTimeMillis()) {
        val dayStart = startOfDay(nowMs)
        context.engagementDataStore.edit {
            it[Keys.SHEET_LAST_SHOWN_MS] = nowMs
            it[Keys.SHEET_LAST_SHOWN_DAY_MS] = dayStart
        }
    }

    suspend fun markFirstLaunchDone() {
        context.engagementDataStore.edit { it[Keys.FIRST_LAUNCH_DONE] = true }
    }

    private fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = ms
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}