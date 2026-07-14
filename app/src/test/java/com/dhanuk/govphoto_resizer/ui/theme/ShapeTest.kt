package com.dhanuk.govphoto_resizer.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeTest {
    @Test fun shapes_have_expressive_scale() {
        val s: Shapes = govShapes
        assertTrue("extraSmall should be RoundedCornerShape", s.extraSmall is RoundedCornerShape)
        assertTrue("small should be RoundedCornerShape", s.small is RoundedCornerShape)
        assertTrue("medium should be RoundedCornerShape", s.medium is RoundedCornerShape)
        assertTrue("large should be RoundedCornerShape", s.large is RoundedCornerShape)
        assertTrue("extraLarge should be RoundedCornerShape", s.extraLarge is RoundedCornerShape)
    }

    @Test fun full_shape_is_circle() {
        assertEquals(CircleShape, govShapeFull)
    }
}
