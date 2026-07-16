package com.dhanuk.govphoto.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class M3ExpressiveColorTest {
    @Test fun seed_color_defined() {
        assertEquals(Color::class, GovSeedColor::class)
        assertNotNull(GovSeedColor)
    }

    @Test fun light_scheme_uses_seed_primary() {
        val scheme = govLightColorScheme
        assertEquals(GovPrimary, scheme.primary)
        assertEquals(GovOnPrimary, scheme.onPrimary)
    }

    @Test fun dark_scheme_has_dark_background() {
        val dark = govDarkColorScheme
        val lum = computeLum(dark.background)
        assert(lum < 0.3f) { "Dark background too bright: $lum" }
    }

    private fun computeLum(c: Color): Float {
        val r = c.red; val g = c.green; val b = c.blue
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
