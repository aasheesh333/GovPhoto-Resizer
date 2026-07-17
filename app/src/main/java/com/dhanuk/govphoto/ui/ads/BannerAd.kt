package com.dhanuk.govphoto.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.data.ads.AdsRepository
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.android.EntryPointAccessors

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    // Skip entirely in debug or when ad-free
    if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
    val context = LocalContext.current
    val adsRepository = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AdsEntryPoint::class.java,
            ).adsRepository()
        }.getOrNull()
    }
    val isAdFree by (adsRepository?.isAdFree?.collectAsState() ?: remember { mutableStateOf(true) })
    if (isAdFree) return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdsEntryPoint {
    fun adsRepository(): AdsRepository
}
