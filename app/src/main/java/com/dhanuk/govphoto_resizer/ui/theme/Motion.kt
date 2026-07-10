package com.dhanuk.govphoto_resizer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object MotionEasing {
    val Standard: Easing = FastOutSlowInEasing
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

object MotionDurations {
    const val Short: Int = 150
    const val Medium: Int = 250
    const val Long: Int = 400
}

object GovSpringSpecs {
    fun <T> button() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
