package com.dhanuk.govphoto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.ads.AdsManager

/**
 * In-app ad diagnostic dialog for devices where `adb logcat` is unavailable.
 * Surfaces the banner state, consent status, unit IDs and the last
 * AdMob load error so a "banner not loading" problem can be diagnosed
 * directly on the phone. Includes buttons to refresh the snapshot or
 * re-run the UMP consent flow.
 */
@Composable
fun AdDiagnosticsDialog(
    info: AdsManager.DiagnosticInfo,
    showActions: Boolean = true,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRequestConsent: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ad_status_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                DiagnosticLine("Variant", info.variant)
                DiagnosticLine("Debug / ForceNoAds", "${info.variant == "debug"} / ${info.forceNoAds}")
                DiagnosticLine("Ad-free", info.isAdFree.toString())
                DiagnosticLine("Consent status", info.consentStatus)
                DiagnosticLine("Privacy options required", info.privacyOptionsRequirementStatus)
                DiagnosticLine("Consent OK", info.canRequestAds.toString())
                DiagnosticLine("Banner state", info.bannerState.name)
                DiagnosticLine("Retries", info.retryCount.toString())
                DiagnosticLine("App ID", info.appId)
                DiagnosticLine("Banner unit", info.bannerUnitId)
                if (info.lastErrorCode != null) {
                    DiagnosticLine("Last error code", info.lastErrorCode.toString())
                    DiagnosticLine("Last error domain", info.lastErrorDomain.orEmpty())
                    DiagnosticLine("Last error message", info.lastErrorMessage.orEmpty())
                }
                if (info.warning != null) {
                    Text(
                        text = "Warning: ${info.warning}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (showActions) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (!info.canRequestAds) {
                            "Consent OK is false. Ads currently attempt to load without waiting " +
                                "(matching interstitial), but NO_FILL/limited ads can happen in " +
                                "regions requiring consent. Tap 'Request consent' if you prefer the " +
                                "UMP form, or check the unit ID/account status."
                        } else {
                            "Consent OK. If the banner still doesn't appear, the unit ID or account status is the most likely cause."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (showActions && !info.canRequestAds) {
                    TextButton(onClick = onRequestConsent) {
                        Text("Request consent")
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.refresh))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
