package com.dhanuk.govphoto.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.ui.viewmodel.PaywallViewModel
import kotlinx.coroutines.launch

/**
 * Account / Restore section in Settings — RevenueCat-only email sign-in.
 *
 * Flow:
 *  - Anonymous user (fresh install / never signed in):
 *      Email field + "Restore" button → SubscriptionRepository.signIn(email)
 *      → RevenueCat transfers any anonymous entitlements to the email account,
 *      and on a future device the same email will return the same Pro plan.
 *  - Signed-in user:
 *      Shows the current appUserID with a "Sign out" button that resets the
 *      SDK to a fresh anonymous identity.
 *
 * No Firebase / Google Sign-In is involved — RevenueCat's `awaitLogIn(email)`
 * does the cross-device identity linking natively.
 */
@Composable
fun RestorePurchasesSection(
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val signedInEmail by viewModel.signedInEmail.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var emailInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    // Seed the input with the currently signed-in email when state flips.
    LaunchedEffect(signedInEmail) {
        if (!signedInEmail.isNullOrEmpty()) emailInput = signedInEmail!!
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.restore_section),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isAnonymous || signedInEmail.isNullOrEmpty()) {
                    // Anonymous — show email field + restore CTA.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.restore_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.restore_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it.trim() },
                        label = { Text(stringResource(R.string.restore_email_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        isError = localError != null,
                        supportingText = localError?.let { { Text(it) } },
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val email = emailInput.trim()
                            if (!email.contains("@") || !email.contains(".")) {
                                localError = context.getString(R.string.restore_invalid_email)
                                return@Button
                            }
                            localError = null
                            scope.launch {
                                viewModel.signIn(email) { ok, msg ->
                                    if (ok) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.restore_success),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.restore_failed, msg ?: ""),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                        },
                        enabled = !busy && emailInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.restore_button))
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.restore_button))
                        }
                    }
                } else {
                    // Signed in — show email + restore + sign out buttons.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.restore_signed_in_as),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = signedInEmail ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { scope.launch { viewModel.refreshCustomerInfo() } },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.restore_button))
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    viewModel.signOut { ok, _ ->
                                        if (ok) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.restore_signed_out),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.restore_sign_out))
                        }
                    }
                }
            }
        }
    }
}