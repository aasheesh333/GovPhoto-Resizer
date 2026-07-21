package com.dhanuk.govphoto.data.ml

import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaceAnalyzerTest {

    private fun solidBitmap(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(android.graphics.Color.GRAY)
        return b
    }

    private class FakeDetector : FaceDetectorClient {
        var faces: List<DetectedFace> = emptyList()
        var throwOnDetect: Exception? = null
        override suspend fun detect(bitmap: Bitmap): List<DetectedFace> {
            throwOnDetect?.let { throw it }
            return faces
        }
    }

    private val fake = FakeDetector()
    private val analyzer = FaceAnalyzer(fake)

    @Test
    fun withinOval_fullyInside_true() {
        val oval = RectF(100f, 100f, 300f, 350f)
        val face = RectF(140f, 140f, 260f, 300f)
        assertTrue(FaceAnalyzer.withinOval(face, oval))
    }

    @Test
    fun withinOval_smallProtrusion_centerInside_true() {
        val oval = RectF(100f, 100f, 300f, 350f)
        val face = RectF(140f, 140f, 310f, 300f) // slightly wider on the right
        assertTrue(FaceAnalyzer.withinOval(face, oval))
    }

    @Test
    fun withinOval_centerInsideButOverlapTooLow_false() {
        val oval = RectF(0f, 0f, 200f, 200f)
        val face = RectF(50f, 150f, 150f, 280f) // center inside; only top half overlaps
        assertFalse(FaceAnalyzer.withinOval(face, oval))
    }

    @Test
    fun withinOval_centerOutside_false() {
        val oval = RectF(0f, 0f, 200f, 200f)
        val face = RectF(150f, 150f, 250f, 250f) // center outside the oval
        assertFalse(FaceAnalyzer.withinOval(face, oval))
    }

    @Test
    fun withinOval_halfFaceOutside_false() {
        val oval = RectF(100f, 100f, 300f, 350f)
        val face = RectF(250f, 100f, 380f, 350f) // mostly outside the oval
        assertFalse(FaceAnalyzer.withinOval(face, oval))
    }

    @Test
    fun defaultOvalGuide_isCentered() {
        val oval = FaceAnalyzer.defaultOvalGuide(400f, 600f)
        val cx = (oval.left + oval.right) / 2f
        val cy = (oval.top + oval.bottom) / 2f
        assertEquals(200f, cx, 0.5f)
        assertEquals(300f, cy, 0.5f)
        assertEquals(240f, oval.width(), 0.5f)
        assertEquals(300f, oval.height(), 0.5f)
    }

    @Test
    fun analyze_noFace_issuesAndNotWithin() = runTest {
        fake.faces = emptyList()
        val r = analyzer.analyze(solidBitmap(400, 600))
        assertEquals(0, r.faceCount)
        assertNull(r.bounds)
        assertFalse(r.isWithinMargin)
        assertTrue(r.issues.any { it.contains("No face", ignoreCase = true) })
    }

    @Test
    fun analyze_singleFaceInside_within() = runTest {
        fake.faces = listOf(
            DetectedFace(
                bounds = RectF(120f, 200f, 280f, 400f),
                leftEyeY = 250f,
                rightEyeY = 250f,
                headEulerAngleZ = 0f,
            )
        )
        val r = analyzer.analyze(solidBitmap(400, 600))
        assertEquals(1, r.faceCount)
        assertTrue(r.isWithinMargin)
        assertTrue(r.issues.isEmpty())
    }

    @Test
    fun analyze_multipleFaces_flagged() = runTest {
        fake.faces = listOf(
            DetectedFace(bounds = RectF(120f, 200f, 280f, 400f)),
            DetectedFace(bounds = RectF(50f, 50f, 100f, 120f)),
        )
        val r = analyzer.analyze(solidBitmap(400, 600))
        assertEquals(2, r.faceCount)
        assertFalse(r.isWithinMargin)
        assertTrue(r.issues.any { it.contains("Multiple", ignoreCase = true) })
    }

    @Test
    fun analyze_eyesNotLevel_flagged() = runTest {
        fake.faces = listOf(
            DetectedFace(
                bounds = RectF(120f, 200f, 280f, 400f),
                leftEyeY = 250f,
                rightEyeY = 280f,
                headEulerAngleZ = 0f,
            )
        )
        val r = analyzer.analyze(solidBitmap(400, 600))
        assertTrue(r.issues.any { it.contains("Eyes", ignoreCase = true) })
        assertFalse(r.isWithinMargin)
    }

    @Test
    fun analyze_detectorThrows_returnsErrorIssue() = runTest {
        fake.throwOnDetect = RuntimeException("sdk down")
        val r = analyzer.analyze(solidBitmap(100, 100))
        assertEquals(0, r.faceCount)
        assertTrue(r.issues.any { it.contains("failed", ignoreCase = true) })
    }
}
