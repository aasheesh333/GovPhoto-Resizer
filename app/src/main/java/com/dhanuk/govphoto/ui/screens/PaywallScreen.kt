package com.dhanuk.govphoto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.ui.components.GovButton
import com.dhanuk.govphoto.ui.components.GovOutlinedButton
import com.dhanuk.govphoto.ui.viewmodel.PaywallViewModel
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Package

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
    var selectedPackage by remember { mutableStateOf<Package?>(null) }

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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero
            Text(stringResource(R.string.paywall_hero_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.paywall_hero_subtitle), style = MaterialTheme.typography.bodyMedium)

            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }
            if (state.subscribed) {
                Text(stringResource(R.string.paywall_already_pro), fontWeight = FontWeight.Bold)
                GovButton(text = stringResource(R.string.paywall_done), onClick = onSubscribeSuccess)
                return@Column
            }

            val packages = state.offering?.availablePackages.orEmpty()
                .filter { it.packageType == PackageType.WEEKLY || it.packageType == PackageType.MONTHLY || it.packageType == PackageType.ANNUAL }

            packages.forEach { pkg ->
                val isBest = pkg.packageType == PackageType.ANNUAL
                val selected = selectedPackage == pkg
                PlanCard(
                    pkg = pkg,
                    isBest = isBest,
                    isSelected = selected,
                    onSelect = { selectedPackage = pkg },
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            GovButton(
                text = stringResource(R.string.paywall_subscribe),
                enabled = selectedPackage != null && activity != null,
                onClick = {
                    val pkg = selectedPackage ?: return@GovButton
                    val act = activity ?: return@GovButton
                    viewModel.purchase(act, pkg) { onSubscribeSuccess() }
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
            Text(stringResource(R.string.paywall_legal), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlanCard(
    pkg: Package,
    isBest: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val priceStr = pkg.storeProduct.priceFormatted
    val period = when (pkg.packageType) {
        PackageType.WEEKLY -> stringResource(R.string.paywall_period_weekly)
        PackageType.MONTHLY -> stringResource(R.string.paywall_period_monthly)
        PackageType.ANNUAL -> stringResource(R.string.paywall_period_yearly)
        else -> ""
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(intrinsicSize = IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isBest) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.cd_best_value), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.paywall_best_value), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("$priceStr / $period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            RadioButton(selected = isSelected, onClick = onSelect)
        }
    }
}
