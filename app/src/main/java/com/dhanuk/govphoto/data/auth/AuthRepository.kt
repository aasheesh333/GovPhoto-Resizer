package com.dhanuk.govphoto.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.authStateListener
import com.google.firebase.auth.ktx.auth
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.logIn
import com.revenuecat.purchases.logOut
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates Google Sign-In → Firebase Auth → RevenueCat `logIn(userId)`.
 *
 * Flow:
 *  1. User taps "Sign in with Google" → Google Sign-In account picker.
 *  2. We exchange the Google ID token for a Firebase credential, sign into Firebase.
 *  3. We call `Purchases.logIn(firebaseUid)` so the existing RevenueCat
 *     customer info (and any active Pro entitlement) is attached to that
 *     Firebase UID. The same user signing in on a different device with the
 *     same Google account will get the same Firebase UID → RevenueCat will
 *     return the same customer info, automatically restoring Pro.
 *  4. We also call `OneSignal.User.addEmail` and `OneSignal.setExternalUserId`
 *     to align the push-notification identity with the auth identity.
 *
 * Anonymous users (no Google sign-in) still get a random RevenueCat App User
 * ID — they retain their Pro only as long as the device's local entitlement
 * cache. To migrate later, sign in and we call Purchases.logIn which
 * automatically transfers the anonymous entitlements to the verified UID.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val auth: FirebaseAuth = Firebase.auth

    private val _signedInUser = MutableStateFlow<FirebaseUser?>(null)
    val signedInUser: StateFlow<FirebaseUser?> = _signedInUser.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Track Firebase auth state so the UI updates reactively.
        scope.launch {
            auth.authStateListener { firebaseAuth ->
                _signedInUser.value = firebaseAuth.currentUser
            }
        }
    }

    /** True when the user has a verified Firebase UID linked to a Google account. */
    fun isSignedIn(): Boolean = auth.currentUser != null

    /**
     * Build the Google Sign-In client. Default web client id is the Firebase
     * default (configured via google-services.json).
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("default_web_client_id")
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Run the full sign-in + RevenueCat logIn flow. Returns the signed-in
     * FirebaseUser on success, or throws on failure.
     */
    suspend fun signInWithGoogle(activity: Activity, account: GoogleSignInAccount): FirebaseUser {
        val idToken = account.idToken ?: error("Google account missing idToken")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = withContext(Dispatchers.IO) { auth.signInWithCredential(credential).await() }
        val user = authResult.user ?: error("Firebase auth returned null user")
        _signedInUser.value = user

        // Link the Firebase UID to RevenueCat. Purchases.logIn transfers any
        // anonymous entitlements to the verified UID, and on a fresh device
        // the same Firebase UID will return the same Pro entitlements.
        linkRevenueCatToUser(user)

        // Sync push-notification identity with the auth identity.
        linkOneSignalToUser(user, account.email)

        return user
    }

    /**
     * Resolve `Purchases.logIn(firebaseUid)` so the current Pro entitlement
     * is attached to the verified user and can be restored on another device.
     */
    suspend fun linkRevenueCatToUser(user: FirebaseUser): CustomerInfo? {
        if (!Purchases.isConfigured) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val info = Purchases.sharedInstance.logIn(user.uid).customerInfo
                info
            }
        }.onFailure { android.util.Log.w(TAG, "Purchases.logIn failed: ${it.message}") }
            .getOrNull()
    }

    private fun linkOneSignalToUser(user: FirebaseUser, email: String?) {
        runCatching {
            val resolvedEmail = email ?: user.email ?: ""
            if (resolvedEmail.isNotEmpty()) {
                com.onesignal.OneSignal.User.addEmail(resolvedEmail)
            }
            com.onesignal.OneSignal.User.addAlias(user.uid, "firebase_uid")
        }.onFailure { android.util.Log.w(TAG, "OneSignal identity sync failed: ${it.message}") }
    }

    /**
     * Sign out: clear Firebase session + RevenueCat anonymous identity + OneSignal
     * external ID. Caller is responsible for routing the user back to the
     * Google Sign-In flow when they next want to restore.
     */
    suspend fun signOut(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            // 1. Sign out of Google (clears the locally cached Google account)
            getGoogleSignInClient().signOut().await()
            // 2. Sign out of Firebase
            auth.signOut()
            // 3. Reset RevenueCat to a fresh anonymous identity so the device's
            //    Pro entitlement is no longer attached to the previous user.
            if (Purchases.isConfigured) {
                runCatching { Purchases.sharedInstance.logOut() }
                    .onFailure { android.util.Log.w(TAG, "Purchases.logOut failed: ${it.message}") }
            }
            // 4. Clear OneSignal external ID
            runCatching {
                com.onesignal.OneSignal.User.removeAlias("firebase_uid")
                com.onesignal.OneSignal.User.addEmail("")
            }
        }
        _signedInUser.value = null
    }

    /**
     * Manual "Restore Purchases" trigger the user can tap in Settings if they
     * just signed in and want to refresh their entitlement state.
     */
    suspend fun refreshCustomerInfo(): CustomerInfo? {
        if (!Purchases.isConfigured) return null
        return runCatching {
            withContext(Dispatchers.IO) { Purchases.sharedInstance.awaitCustomerInfo() }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}