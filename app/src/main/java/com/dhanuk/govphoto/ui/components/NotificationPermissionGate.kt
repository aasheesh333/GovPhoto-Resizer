package com.dhanuk.govphoto.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.push.PushRepository
import com.dhanuk.govphoto.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors

/**
 * One-time app-level gate that asks the user for notification permission after
 * ~1 minute of foreground use. Required on Android 13+ so OneSignal can deliver
 * push notifications and register a push token on the dashboard.
 *
 * Foreground time is measured via [LifecycleEventObserver] so the 1-minute clock
 * only runs while the app is visible. The prompt is shown AT MOST ONCE per app
 * install (persisted via [com.dhanuk.govphoto.data.datastore.SettingsRepository.notificationPermissionAsked]).
 *
 * Wrap your top-level content with this:
 *   NotificationPermissionGate(settingsViewModel) { NavHost(...) }
 */
@Composable
fun NotificationPermissionGate(
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

    // Skip the prompt on devices that don't need it (pre-Android 13) or when
    // the user has already been asked once.
    val needsPrompt = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val alreadyAsked = settings.notificationPermissionAsked
    val shouldGate = needsPrompt && !alreadyAsked

    // Foreground-time accumulator + active-session start timestamp.
    var accumulatedMs by remember { mutableLongStateOf(0L) }
    var resumeMs by remember { mutableLongStateOf(0L) }
    var showPrompt by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    resumeMs = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_STOP -> {
                    val start = resumeMs
                    if (start > 0L) {
                        accumulatedMs += System.currentTimeMillis() - start
                    }
                    resumeMs = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Fire the dialog once accumulated foreground time crosses the threshold.
    LaunchedEffect(accumulatedMs, shouldGate) {
        if (shouldGate && accumulatedMs >= FOREGROUND_MS_BEFORE_PROMPT && !showPrompt) {
            showPrompt = true
        }
    }

    if (showPrompt) {
        NotificationPermissionDialog(
            onAllow = {
                pushRepository?.promptForPermission(fallbackToSettings = true)
                settingsViewModel.setNotificationPermissionAsked(true)
                showPrompt = false
            },
            onDismiss = {
                settingsViewModel.setNotificationPermissionAsked(true)
                showPrompt = false
            },
        )
    }

    content()
}

@Composable
private fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_perm_title)) },
        text = { Text(stringResource(R.string.notif_perm_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.notif_perm_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notif_perm_later)) }
        },
    )
}

private const val FOREGROUND_MS_BEFORE_PROMPT = 60_000L

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
private interface PushEntryPoint {
    fun pushRepository(): PushRepository
}