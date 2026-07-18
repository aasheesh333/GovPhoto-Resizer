package com.dhanuk.govphoto.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button

/**
 * Modal bottom sheet shown at high-intent moments (after a successful rewarded ad,
 * as a Home/Settings CTA, etc.) to nudge the user toward the Pro plan without
 * being spammy. The host decides the trigger condition; this composable only
 * renders the content and the dismissal / open-paywall callbacks.
 *
 * Call sites are responsible for limiting frequency (e.g. once per session).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpgradePromptSheet(
    title: String,
    subtitle: String,
    primaryCta: String,
    onOpenPaywall: () -> Unit,
    onDismiss: () -> Unit,
    benefits: List<UpgradeBenefit> = defaultBenefits(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Hero header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Benefits list
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                benefits.forEach { benefit ->
                    BenefitRow(benefit.icon, benefit.title, benefit.subtitle)
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onOpenPaywall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = primaryCta)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Maybe later")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

data class UpgradeBenefit(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private fun defaultBenefits(): List<UpgradeBenefit> = listOf(
    UpgradeBenefit(
        icon = Icons.Filled.Close,
        title = "100% Ad-Free",
        subtitle = "No banner ads, no interstitial ads — ever",
    ),
    UpgradeBenefit(
        icon = Icons.Filled.Bolt,
        title = "Instant 30-min Support",
        subtitle = "Email replies within 30 minutes, priority queue",
    ),
    UpgradeBenefit(
        icon = Icons.Filled.Email,
        title = "Priority Access",
        subtitle = "First to try new presets and features",
    ),
)

@Composable
private fun BenefitRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(4.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}