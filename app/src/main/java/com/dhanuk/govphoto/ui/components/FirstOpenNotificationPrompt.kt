package com.dhanuk.govphoto.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.push.PushRepository
import com.dhanuk.govphoto.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

/**
 * One-shot rationale popup that asks the user to allow notifications ~3
 * seconds after the app first opens (and after onboarding is complete).
 *
 * Behaviour contract:
 *  - Fires only on Android 13+ (TIRAMISU) where the OS-level POST_NOTIFICATIONS
 *    permission prompt must be explicitly granted.
 *  - Fires only ONCE per install: persisted via
 *    [com.dhanuk.govphoto.data.datastore.SettingsRepository.notifPromptShown]
 *    which is set to true on both the Allow and "Not now" buttons.
 *  - Respects onboarding: must wait until onboardingComplete == true so the
 *    popup does not race the onboarding pager on first launch.
 *  - Waiting 3 seconds avoids a jarring popup the moment the user lands on
 *    Home, giving them time to see app content first.
 *
 * Wrap your top-level content with this:
 *   FirstOpenNotificationPrompt(settingsViewModel) { NavHost(...) }
 *
 * Re-enabling notifications later (after the user denied here) is done via the
 * master Notifications Switch in SettingsScreen — tapping it ON calls
 * [PushRepository.promptForPermission] again.
 */
@Composable
fun FirstOpenNotificationPrompt(
    settingsViewModel: SettingsViewModel,
    content: @Composable () -> Unit,
) {
    val settings by settingsViewModel.state.collectAsState()
    val context = LocalContext.current

    val pushRepository = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                PushEntryPoint::class.java,
            ).pushRepository()
        }.getOrNull()
    }

    val needsPrompt = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val eligible = needsPrompt && settings.onboardingComplete && !settings.notifPromptShown

    var showPrompt by remember { mutableStateOf(false) }

    // Single delayed fire — 3s after the gate becomes eligible. If the user
    // is still mid-onboarding, the LaunchedEffect re-keys when eligibility
    // finally becomes true (post-onboarding) and waits 3s from there.
    LaunchedEffect(eligible) {
        if (eligible) {
            delay(DELAY_MS_BEFORE_PROMPT)
            showPrompt = true
        }
    }

    if (showPrompt) {
        NotificationRationaleDialog(
            onAllow = {
                pushRepository?.promptForPermission(fallbackToSettings = true)
                pushRepository?.setNotificationEnabled(true)
                settingsViewModel.setNotificationsEnabled(true)
                settingsViewModel.setNotifPromptShown(true)
                showPrompt = false
            },
            onDismiss = {
                pushRepository?.setNotificationEnabled(false)
                settingsViewModel.setNotificationsEnabled(false)
                settingsViewModel.setNotifPromptShown(true)
                showPrompt = false
            },
        )
    }

    content()
}

@Composable
private fun NotificationRationaleDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_rationale_title)) },
        text = { Text(stringResource(R.string.notif_rationale_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.notif_rationale_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notif_rationale_not_now)) }
        },
    )
}

private const val DELAY_MS_BEFORE_PROMPT = 3_000L

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
private interface PushEntryPoint {
    fun pushRepository(): PushRepository
}
