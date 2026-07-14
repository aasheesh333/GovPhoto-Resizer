package com.dhanuk.govphoto_resizer.ui.viewmodel

import com.dhanuk.govphoto_resizer.data.datastore.AppLanguage
import com.dhanuk.govphoto_resizer.data.datastore.DarkModePref
import com.dhanuk.govphoto_resizer.data.datastore.SettingsRepository
import com.dhanuk.govphoto_resizer.data.datastore.SettingsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-JUnit test for SettingsViewModel using a mockk'd SettingsRepository
 * (mockk mocks final classes via its inline mock maker). No Android Context
 * dependency; the mock repository's `state` is wired to an in-memory
 * MutableStateFlow so setters visibly propagate to the VM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var repo: SettingsRepository
    private lateinit var backing: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        // Dispatchers.Main is touched by viewModelScope; route it to a test dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        backing = MutableStateFlow(SettingsState())
        repo = mockk(relaxed = true)
        every { repo.state } returns backing
        coEvery { repo.setLanguage(any()) } answers {
            val lang = firstArg<AppLanguage>()
            backing.update { it.copy(language = lang) }
        }
        coEvery { repo.setDynamicColor(any()) } answers {
            backing.update { it.copy(dynamicColor = firstArg()) }
        }
        coEvery { repo.setDarkMode(any()) } answers {
            backing.update { it.copy(darkMode = firstArg()) }
        }
        coEvery { repo.setLargeButtons(any()) } answers {
            backing.update { it.copy(largeButtons = firstArg()) }
        }
        coEvery { repo.setHighContrast(any()) } answers {
            backing.update { it.copy(highContrast = firstArg()) }
        }
        coEvery { repo.setOnboardingComplete(any()) } answers {
            backing.update { it.copy(onboardingComplete = firstArg()) }
        }
        coEvery { repo.setLastPresetId(any()) } answers {
            val id = firstArg<String?>()
            backing.update { it.copy(lastPresetId = id) }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_value_is_default_SettingsState() {
        val vm = SettingsViewModel(repo)
        // stateIn initial value is the supplied SettingsState() regardless of upstream
        assertEquals(SettingsState(), vm.state.value)
    }

    @Test
    fun setLanguage_invokes_repo_setLanguage() = runTest {
        val vm = SettingsViewModel(repo)
        vm.setLanguage(AppLanguage.HINDI)
        advanceUntilIdle()

        assertEquals(AppLanguage.HINDI, backing.value.language)
        coVerify { repo.setLanguage(AppLanguage.HINDI) }
    }

    @Test
    fun state_reflects_upstream_changes() = runTest {
        val vm = SettingsViewModel(repo)

        vm.setLanguage(AppLanguage.HINDI)
        vm.setLargeButtons(true)
        vm.setDarkMode(DarkModePref.DARK)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(AppLanguage.HINDI, s.language)
        assertTrue(s.largeButtons)
        assertEquals(DarkModePref.DARK, s.darkMode)
    }

    @Test
    fun setLargeButtons_invokes_repo_setter() = runTest {
        val vm = SettingsViewModel(repo)
        vm.setLargeButtons(true)
        advanceUntilIdle()

        coVerify { repo.setLargeButtons(true) }
        assertTrue(backing.value.largeButtons)
    }

    @Test
    fun setHighContrast_invokes_repo_setter() = runTest {
        val vm = SettingsViewModel(repo)
        vm.setHighContrast(true)
        advanceUntilIdle()

        coVerify { repo.setHighContrast(true) }
    }

    @Test
    fun setLastPresetId_null_clears_value() = runTest {
        backing.value = backing.value.copy(lastPresetId = "passport")
        val vm = SettingsViewModel(repo)

        vm.setLastPresetId(null)
        advanceUntilIdle()

        assertEquals(null, vm.state.value.lastPresetId)
    }
}
