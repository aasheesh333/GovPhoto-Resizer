package com.dhanuk.govphoto.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto.data.auth.AuthRepository
import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes Google Sign-In → Firebase Auth → RevenueCat `logIn` to the UI.
 *
 * UI calls [launchGoogleSignIn] with the activity used to launch the
 * `GoogleSignInClient.signInIntent` and the resulting launcher. The viewmodel
 * does not own the launcher itself (composables do, via `rememberLauncherForActivityResult`).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    val signedInUser: StateFlow<FirebaseUser?> = authRepository.signedInUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun getGoogleSignInIntent(): android.content.Intent {
        return authRepository.getGoogleSignInClient().signInIntent
    }

    /**
     * Handle the result of the Google Sign-In intent. Returns the signed-in
     * Firebase user on success. Refreshes RevenueCat customer info afterwards.
     */
    fun onGoogleSignInResult(task: Task<GoogleSignInAccount>) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val activity = lastActivity
                if (activity == null) {
                    _errorMessage.value = "Activity context not available"
                    return@launch
                }
                authRepository.signInWithGoogle(activity, account)
                // After sign-in, refresh the cached Pro entitlement so the
                // entitlement-aware UI updates immediately.
                subscriptionRepository.bind()
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Unknown error"
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _busy.value = true
            try {
                authRepository.signOut()
                // Re-bind as anonymous so the cached Pro state reflects the
                // new (anonymous) RevenueCat identity.
                subscriptionRepository.bind()
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Sign-out failed"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Manual "Restore Purchases" after a fresh sign-in. */
    fun restorePurchases() {
        viewModelScope.launch {
            _busy.value = true
            try {
                authRepository.refreshCustomerInfo()?.let { info ->
                    // Push updated entitlement state into the cached store so
                    // the UI updates.
                    subscriptionRepository.bind()
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    /** Activity context captured by the UI before launching the sign-in intent. */
    var lastActivity: Activity? = null
}