package com.dhanuk.govphoto.ui.viewmodel

import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
import com.revenuecat.purchases.Package
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for [PaywallViewModel] state transitions.
 *
 * The paywall screen was rewritten to show 3 INR tiers and map RevenueCat
 * packages by type — the VM contract (loading -> offering/subscribed | error;
 * purchase success/failure; restore success/failure) is the contract the UI
 * relies on, so lock it down here.
 *
 * No Android / Hilt / RevenueCat SDK needed: the VM only touches a mocked
 * [SubscriptionRepository]. CustomerInfo / Offerings are stubbed with relaxed
 * mockk values where the success path matters; we avoid constructing mockk
 * instances of Android framework classes (Activity) which need Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private lateinit var repo: SubscriptionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = mockk(relaxed = true)
        every { repo.isPro } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_starts_in_loading_state_and_calls_loadOfferings() = runTest {
        // loadOfferings never completes within this test -> we can observe the
        // initial loading=true state and verify the repo was invoked.
        coEvery { repo.loadOfferings() } coAnswers { kotlinx.coroutines.awaitCancellation() }

        val vm = PaywallViewModel(repo)

        assertTrue("initial state should be loading", vm.state.value.loading)
        assertNull(vm.state.value.error)
        coVerify { repo.loadOfferings() }
    }

    @Test
    fun load_failure_sets_error_and_clears_loading() = runTest {
        coEvery { repo.loadOfferings() } throws RuntimeException("network down")

        val vm = PaywallViewModel(repo)

        val state = vm.state.value
        assertFalse("loading must clear after failure", state.loading)
        assertEquals("network down", state.error)
        assertNull(state.offering)
        assertFalse(state.subscribed)
    }

    @Test
    fun purchase_failure_sets_error_and_does_not_invoke_onSuccess() = runTest {
        coEvery { repo.purchase(any(), any()) } returns Result.failure(RuntimeException("card declined"))
        val pkg = mockk<Package>(relaxed = true)
        var successCalled = false
        val onSuccess: () -> Unit = { successCalled = true }

        val vm = PaywallViewModel(repo)
        // Pass the activity mock inline (inferred as Activity from the parameter
        // type) to match the restore tests' working pattern. Storing it in a
        // 'val activity = mockk(...)' first would fail to compile (mockk cannot
        // infer T without context) and instantiating mockk<Activity> explicitly
        // would need Robolectric.
        vm.purchase(mockk(relaxed = true), pkg, onSuccess)

        assertNotNull(vm.state.value.error)
        assertEquals("card declined", vm.state.value.error)
        assertFalse("onSuccess must NOT run on purchase failure", successCalled)
    }

    @Test
    fun restore_failure_sets_error() = runTest {
        coEvery { repo.restorePurchases() } returns Result.failure(RuntimeException("no purchases"))

        val vm = PaywallViewModel(repo)
        vm.restore(mockk(relaxed = true)) { /* not expected */ }

        assertEquals("no purchases", vm.state.value.error)
        assertFalse(vm.state.value.restoring)
    }

    @Test
    fun restore_success_when_subscribed_invokes_onSuccess() = runTest {
        val customerInfo = mockk<com.revenuecat.purchases.CustomerInfo>(relaxed = true)
        val subscribed = MutableStateFlow(true)
        every { repo.isPro } returns subscribed
        coEvery { repo.restorePurchases() } returns Result.success(customerInfo)
        var successCalled = false
        val onSuccess: () -> Unit = { successCalled = true }

        val vm = PaywallViewModel(repo)
        vm.restore(mockk(relaxed = true), onSuccess)

        assertTrue("onSuccess must run when restore yields a subscribed user", successCalled)
        assertTrue(vm.state.value.subscribed)
        assertFalse(vm.state.value.restoring)
    }
}