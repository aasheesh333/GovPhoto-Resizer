package com.dhanuk.govphoto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    val loading: Boolean = true,
    val offering: Offering? = null,
    val subscribed: Boolean = false,
    val error: String? = null,
    val restoring: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { subscriptionRepository.loadOfferings() }
            .onSuccess { offerings ->
                val off = offerings.current ?: offerings.all.values.firstOrNull()
                _state.value = PaywallUiState(
                    loading = false,
                    offering = off,
                    subscribed = subscriptionRepository.isPro.value,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load plans")
            }
    }

    fun purchase(activity: android.app.Activity, pkg: Package, onSuccess: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(error = null)
        subscriptionRepository.purchase(activity, pkg)
            .onSuccess { onSuccess() }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Purchase failed") }
    }

    fun restore(activity: android.app.Activity, onSuccess: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(restoring = true)
        subscriptionRepository.restorePurchases()
            .onSuccess {
                _state.value = _state.value.copy(restoring = false, subscribed = subscriptionRepository.isPro.value)
                if (subscriptionRepository.isPro.value) onSuccess()
            }
            .onFailure { e ->
                _state.value = _state.value.copy(restoring = false, error = e.message ?: "Restore failed")
            }
    }

    /**
     * Forward the user-supplied contact details to RevenueCat as attributes so the
     * developer can identify the buyer in the RevenueCat dashboard without
     * implementing a full login system (Method-3 from the engagement plan).
     */
    fun saveContact(email: String?, phone: String?) = viewModelScope.launch {
        subscriptionRepository.setUserContact(email, phone)
    }
}
