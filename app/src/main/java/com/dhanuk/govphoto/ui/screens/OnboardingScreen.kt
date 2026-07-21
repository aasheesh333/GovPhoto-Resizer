package com.dhanuk.govphoto.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.ui.ads.GlobalBannerAd
import com.dhanuk.govphoto.ui.components.GovButton
import com.dhanuk.govphoto.ui.components.GovTextButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.Description,
            titleRes = R.string.onboarding_title_1,
            descRes = R.string.onboarding_desc_1,
        ),
        OnboardingPage(
            icon = Icons.Default.CameraAlt,
            titleRes = R.string.onboarding_title_2,
            descRes = R.string.onboarding_desc_2,
        ),
        OnboardingPage(
            icon = Icons.Default.AutoFixHigh,
            titleRes = R.string.onboarding_title_3,
            descRes = R.string.onboarding_desc_3,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    GovTextButton(
                        text = stringResource(R.string.onboarding_skip),
                        onClick = onComplete,
                    )
                }
            )
        },
        bottomBar = { GlobalBannerAd() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            PageIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GovButton(
                    text = if (isLastPage) {
                        stringResource(R.string.onboarding_get_started)
                    } else {
                        stringResource(R.string.onboarding_next)
                    },
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = stringResource(R.string.cd_onboarding_feature),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(page.descRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.4f else 1f,
                animationSpec = tween(durationMillis = 250),
                label = "dotScale$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
            )
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
)
