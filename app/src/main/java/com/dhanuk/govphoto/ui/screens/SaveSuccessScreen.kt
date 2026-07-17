package com.dhanuk.govphoto.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.data.ads.InterstitialController
import com.dhanuk.govphoto.ui.ads.BannerAd
import com.dhanuk.govphoto.ui.components.GovButton
import com.dhanuk.govphoto.ui.components.GovOutlinedButton
import com.dhanuk.govphoto.ui.viewmodel.SharedPhotoViewModel
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSuccessScreen(
    sharedViewModel: SharedPhotoViewModel,
    onNavigateHome: () -> Unit,
) {
    val context = LocalContext.current

    val processedImageUri by sharedViewModel.processedImageUri.collectAsState()
    val selectedPreset by sharedViewModel.selectedPreset.collectAsState()
    val selectedPresetName by sharedViewModel.selectedPresetName.collectAsState()
    val fileSizeKb by sharedViewModel.fileSizeKb.collectAsState()

    var animateCheck by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateCheck = true }
    val scale by animateFloatAsState(
        targetValue = if (animateCheck) 1f else 0.5f,
        animationSpec = tween(durationMillis = 600),
        label = "checkScale"
    )

    val presetNameText = selectedPresetName ?: stringResource(R.string.save_success_custom)
    val compliantTitle = stringResource(R.string.save_success_compliant, presetNameText)

    val widthPx = selectedPreset?.widthPx ?: 0
    val heightPx = selectedPreset?.heightPx ?: 0
    val preset = selectedPreset
    val widthCm: Int
    val heightCm: Int
    if (preset?.widthCm != null && preset?.heightCm != null) {
        widthCm = preset!!.widthCm!!.toInt()
        heightCm = preset!!.heightCm!!.toInt()
    } else {
        val dpi = preset?.dpi ?: 300
        widthCm = ((widthPx.toFloat() / dpi * 2.54f).toInt())
        heightCm = ((heightPx.toFloat() / dpi * 2.54f).toInt())
    }
    val formatName = selectedPreset?.format?.uppercase() ?: "JPG"

    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        // mark save count + trigger interstitial (AdMob rate-limit enforced inside controller)
        val controller = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SaveSuccessInterstitialEntryPoint::class.java,
            ).interstitialController()
        }.getOrNull() ?: return@LaunchedEffect
        controller.recordSaveReceived()
        // Slight delay so the success screen paints before the interstitial
        kotlinx.coroutines.delay(300)
        activity?.let { controller.tryShow(it) }
    }

    val detailsText = stringResource(
        R.string.save_success_details,
        widthCm,
        heightCm,
        fileSizeKb,
        formatName
    )

    fun openInGallery(uri: Uri) {
        val format = selectedPreset?.format?.lowercase() ?: "jpg"
        val mimeType = if (format == "png") "image/png" else "image/jpeg"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun shareImage(uri: Uri) {
        val format = selectedPreset?.format?.lowercase() ?: "jpg"
        val mimeType = if (format == "png") "image/png" else "image/jpeg"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.save_success_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    Spacer(modifier = Modifier.size(48.dp))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.cd_back_button),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_validation_status),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(84.dp)
                        .scale(scale)
                )
            }

            Text(
                text = compliantTitle,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    processedImageUri?.let { uri ->
                        Surface(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.cd_saved_thumbnail),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } ?: run {
                        Surface(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    }

                    Text(
                        text = detailsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GovButton(
                    text = stringResource(R.string.share),
                    onClick = {
                        processedImageUri?.let { shareImage(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = processedImageUri != null
                )

                GovOutlinedButton(
                    text = stringResource(R.string.done),
                    onClick = onNavigateHome,
                    modifier = Modifier.fillMaxWidth()
                )

                GovOutlinedButton(
                    text = stringResource(R.string.view_in_gallery),
                    onClick = {
                        processedImageUri?.let { openInGallery(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = processedImageUri != null
                )
            }
            BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
}
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SaveSuccessInterstitialEntryPoint {
    fun interstitialController(): InterstitialController
}
