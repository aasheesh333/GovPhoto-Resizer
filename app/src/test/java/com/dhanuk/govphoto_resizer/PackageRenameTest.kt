package com.dhanuk.govphoto_resizer

import org.junit.Assert.assertTrue
import org.junit.Test

class PackageRenameTest {

    @Test
    fun package_is_dhanuk() {
        val pkg = javaClass.`package`?.name
        assertTrue(
            "Expected package to start with com.dhanuk.govphoto_resizer but was $pkg",
            pkg?.startsWith("com.dhanuk.govphoto_resizer") == true
        )
    }
}
