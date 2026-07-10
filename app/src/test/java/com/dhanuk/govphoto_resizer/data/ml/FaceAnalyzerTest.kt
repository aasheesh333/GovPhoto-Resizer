package com.dhanuk.govphoto_resizer.data.ml

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
        assertTrue(FaceAnalyzer.withinOval(face, oval, margin = 0.05f))
    }

    @Test
    fun withinOval_protrudesRight_false() {
        val oval = RectF(100f, 100f, 300f, 350f)
        val face = RectF(140f, 140f, 310f, 300f)
        assertFalse(FaceAnalyzer.withinOval(face, oval, margin = 0.05f))
    }

    @Test
    fun withinOval_exactlyOnInnerEdge_true() {
        val oval = RectF(0f, 0f, 100f, 100f)
        val face = RectF(5f, 5f, 95f, 95f)
        assertTrue(FaceAnalyzer.withinOval(face, oval, 0.05f))
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
