package com.dhanuk.govphoto.ui.diagnostics

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
import com.dhanuk.govphoto.data.push.PushRepository

/**
 * In-app push diagnostic dialog. Mirrors [com.dhanuk.govphoto.ui.components.AdDiagnosticsDialog]
 * so users without adb can see why the OneSignal dashboard shows zero installs.
 */
@Composable
fun PushDiagnosticsDialog(
    info: PushRepository.DiagnosticInfo,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val instruction = if (!info.isValidUuid) {
        stringResource(R.string.push_diag_invalid_app_id)
    } else if (info.tokenPreview == "<none>") {
        stringResource(R.string.push_diag_no_token)
    } else {
        stringResource(R.string.push_diag_looks_ok)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_diag_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                DiagnosticLine("OneSignal App ID", info.appId)
                DiagnosticLine("App ID length", info.appIdLength.toString())
                DiagnosticLine("Looks like UUID?", if (info.isValidUuid) "Yes" else "No")
                DiagnosticLine("Permission state", info.permissionState)
                DiagnosticLine("Push opted-in", info.optedIn.toString())
                DiagnosticLine("Push token preview", info.tokenPreview)
                if (info.warning != null) {
                    Text(
                        text = "Warning: ${info.warning}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
