package com.dhanuk.govphoto_resizer.data.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests ordered NAME_ASCENDING so that the "defaults" test runs before any
 * writing test, ensuring it observes a pristine state. DataStore's per-Context
 * instance cache survives across JUnit test instances (Robolectric's
 * ApplicationProvider returns a singleton), so per-test file deletion alone
 * does not reset the in-memory store; running the read-only default first
 * sidesteps the issue. Writing tests below are self-contained round-trips
 * whose assertions only depend on the value they themselves wrote.
 */
@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear the synchronous locale SharedPreferences cache so cached-tag
        // round-trip assertions start from a known empty state.
        context.getSharedPreferences("govphoto_locale_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repo = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        // Delete the persisted DataStore file so subsequent test runs (other JVM
        // invocations) start fresh. The in-VM singleton cache isn't reset by
        // this, but each writing test only asserts the value it just wrote.
        File(context.filesDir, "datastore/govphoto_settings.preferences_pb").delete()
        context.getSharedPreferences("govphoto_locale_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun a_defaults_are_english_light_and_dynamic_false() = runTest {
        val state = repo.state.first()
        assertEquals(AppLanguage.ENGLISH, state.language)
        assertEquals(false, state.dynamicColor)
        assertEquals(DarkModePref.LIGHT, state.darkMode)
    }

    @Test
    fun b_setLanguage_persists_and_emits() = runTest {
        repo.setLanguage(AppLanguage.HINDI)
        val first = repo.state.first()
        assertEquals(AppLanguage.HINDI, first.language)
    }

    @Test
    fun c_setDarkMode_light_round_trips() = runTest {
        repo.setDarkMode(DarkModePref.LIGHT)
        assertEquals(DarkModePref.LIGHT, repo.state.first().darkMode)
    }

    @Test
    fun d_setOnboardingComplete_true_round_trips() = runTest {
        repo.setOnboardingComplete(true)
        assertTrue(repo.state.first().onboardingComplete)
    }

    @Test
    fun e_setLanguage_updates_cached_locale_tag() = runTest {
        repo.setLanguage(AppLanguage.HINDI)
        assertEquals("hi", repo.getCachedLanguageTag())
    }
}
