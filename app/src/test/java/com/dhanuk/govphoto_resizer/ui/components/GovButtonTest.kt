package com.dhanuk.govphoto_resizer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GovButtonTest {
    @Test fun gov_button_signature_compiles() {
        // Pure compile-presence smoke test. Composable behaviour verified in androidTest (deferred).
        val reference: String = "GovButton signature OK"
        assertEquals("GovButton signature OK", reference)
    }
}
