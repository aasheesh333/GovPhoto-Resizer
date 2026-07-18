package com.dhanuk.govphoto.ui.components

import androidx.compose.foundation.layout.Column
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
 * Surfaces the banner state, consent status, variant, unit IDs and the last
 * AdMob load error so a "banner not loading" problem can be diagnosed
 * directly on the phone.
 */
@Composable
fun AdDiagnosticsDialog(
    info: AdsManager.DiagnosticInfo,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
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
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.refresh))
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
