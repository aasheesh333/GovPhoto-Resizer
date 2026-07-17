package com.dhanuk.govphoto.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.ui.components.GovButton
import com.dhanuk.govphoto.ui.components.GovOutlinedButton
import com.dhanuk.govphoto.ui.components.SafeCircularSpinner
import com.dhanuk.govphoto.ui.viewmodel.PaywallViewModel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType

/**
 * Paywall with three plans in INR (Weekly ₹79, Monthly ₹149, Annual ₹1099).
 *
 * Display prices are hardcoded INR marketing figures. If a RevenueCat offering
 * is available, the matching package's formatted price replaces the INR display
 * and purchase goes through RevenueCat. With the CI test key, no offering is
 * returned, so the INR prices are shown as a fallback and the Subscribe button
 * shows a "billing setup in progress" toast (real keys plug in transparently).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onNavigateBack: () -> Unit,
    onSubscribeSuccess: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var selectedTier by remember { mutableStateOf(PaywallTier.MONTHLY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero
            Text(
                stringResource(R.string.paywall_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.paywall_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (state.loading) {
                SafeCircularSpinner(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
                return@Column
            }
            if (state.subscribed) {
                Text(stringResource(R.string.paywall_already_pro), fontWeight = FontWeight.Bold)
                GovButton(text = stringResource(R.string.paywall_done), onClick = onSubscribeSuccess)
                return@Column
            }

            // Resolve packages from the RC offering (null when not configured / test key).
            val weeklyPkg = state.offering?.availablePackages?.firstOrNull { it.packageType == PackageType.WEEKLY }
            val monthlyPkg = state.offering?.availablePackages?.firstOrNull { it.packageType == PackageType.MONTHLY }
            val annualPkg = state.offering?.availablePackages?.firstOrNull { it.packageType == PackageType.ANNUAL }

            // Three plan cards
            PaywallTierCard(
                tier = PaywallTier.WEEKLY,
                title = stringResource(R.string.paywall_tier_weekly),
                periodLabel = stringResource(R.string.paywall_period_weekly),
                priceInr = PaywallTier.WEEKLY.priceInr,
                rcPackage = weeklyPkg,
                isBestValue = false,
                isSelected = selectedTier == PaywallTier.WEEKLY,
                onClick = { selectedTier = PaywallTier.WEEKLY },
            )
            PaywallTierCard(
                tier = PaywallTier.MONTHLY,
                title = stringResource(R.string.paywall_tier_monthly),
                periodLabel = stringResource(R.string.paywall_period_monthly),
                priceInr = PaywallTier.MONTHLY.priceInr,
                rcPackage = monthlyPkg,
                isBestValue = false,
                isSelected = selectedTier == PaywallTier.MONTHLY,
                onClick = { selectedTier = PaywallTier.MONTHLY },
            )
            PaywallTierCard(
                tier = PaywallTier.ANNUAL,
                title = stringResource(R.string.paywall_tier_annual),
                periodLabel = stringResource(R.string.paywall_period_yearly),
                priceInr = PaywallTier.ANNUAL.priceInr,
                rcPackage = annualPkg,
                isBestValue = true,
                isSelected = selectedTier == PaywallTier.ANNUAL,
                onClick = { selectedTier = PaywallTier.ANNUAL },
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            // Subscribe CTA — picks the selected tier's RC package when present.
            val selectedPkg: Package? = when (selectedTier) {
                PaywallTier.WEEKLY -> weeklyPkg
                PaywallTier.MONTHLY -> monthlyPkg
                PaywallTier.ANNUAL -> annualPkg
            }
            GovButton(
                text = stringResource(R.string.paywall_subscribe),
                enabled = activity != null && !state.loading,
                onClick = {
                    val act = activity ?: return@GovButton
                    if (selectedPkg != null) {
                        viewModel.purchase(act, selectedPkg) { onSubscribeSuccess() }
                    } else {
                        // No RC offering yet (test key / billing not configured).
                        // Don't fake a purchase — tell the user clearly so they
                        // don't think the subscription went through.
                        Toast.makeText(
                            context,
                            context.getString(R.string.paywall_billing_not_ready),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GovOutlinedButton(
                text = stringResource(R.string.paywall_restore),
                enabled = activity != null && !state.restoring,
                onClick = {
                    val act = activity ?: return@GovOutlinedButton
                    viewModel.restore(act) { onSubscribeSuccess() }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.paywall_legal),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private enum class PaywallTier(val priceInr: Int) {
    WEEKLY(79),
    MONTHLY(149),
    ANNUAL(1099),
}

@Composable
private fun PaywallTierCard(
    tier: PaywallTier,
    title: String,
    periodLabel: String,
    priceInr: Int,
    rcPackage: Package?,
    isBestValue: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isBestValue) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                stringResource(R.string.paywall_best_value),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Prefer the RC package's formatted price when present, else show
                // the hardcoded INR marketing price.
                val priceText = rcPackage?.product?.price?.formatted ?: "₹$priceInr"
                Text(
                    "$priceText / $periodLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Selection indicator — check icon when selected.
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                // Empty circle to keep the row layout balanced.
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(50),
                        ),
                )
            }
        }
    }
}