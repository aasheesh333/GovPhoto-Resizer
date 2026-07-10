package com.dhanuk.govphoto_resizer.data.ml

import android.graphics.Bitmap
import android.graphics.Color
import com.dhanuk.govphoto_resizer.ui.viewmodel.BackgroundColor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.FloatBuffer
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class BackgroundRemoverTest {

    private fun solidBitmap(w: Int, h: Int, color: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return b
    }

    private fun maskAllSubject(w: Int, h: Int) = SegmentationMask(
        buffer = FloatBuffer.wrap(FloatArray(w * h) { 1f }),
        width = w, height = h,
    )

    private fun maskAllBackground(w: Int, h: Int) = SegmentationMask(
        buffer = FloatBuffer.wrap(FloatArray(w * h) { 0f }),
        width = w, height = h,
    )

    private val fakeClient = object : SegmenterClient {
        var maskToReturn: SegmentationMask = maskAllSubject(1, 1)
        override suspend fun process(bitmap: Bitmap) = maskToReturn
    }
    private val remover = BackgroundRemover(fakeClient)

    @Test
    fun composeOver_allSubject_keepsSourcePixels() {
        val src = solidBitmap(4, 4, Color.RED)
        val out = remover.composeOver(src, maskAllSubject(4, 4), BackgroundColor.WHITE)
        assertEquals(Color.RED, out.getPixel(2, 2))
    }

    @Test
    fun composeOver_allBackground_usesWhiteBg() {
        val src = solidBitmap(4, 4, Color.RED)
        val out = remover.composeOver(src, maskAllBackground(4, 4), BackgroundColor.WHITE)
        assertEquals(Color.WHITE, out.getPixel(2, 2))
    }

    @Test
    fun composeOver_allBackground_studioBlue() {
        val src = solidBitmap(4, 4, Color.RED)
        val out = remover.composeOver(src, maskAllBackground(4, 4), BackgroundColor.STUDIO_BLUE)
        val p = out.getPixel(2, 2)
        assertTrue("R", abs(Color.red(p) - 0xB8) <= 2)
        assertTrue("G", abs(Color.green(p) - 0xD4) <= 2)
        assertTrue("B", abs(Color.blue(p) - 0xE8) <= 2)
    }

    @Test
    fun composeOver_transparentBg_subjectKeepsAlpha() {
        val src = solidBitmap(4, 4, Color.RED)
        val out = remover.composeOver(src, maskAllSubject(4, 4), BackgroundColor.TRANSPARENT)
        assertEquals(255, Color.alpha(out.getPixel(2, 2)))
        val out2 = remover.composeOver(src, maskAllBackground(4, 4), BackgroundColor.TRANSPARENT)
        assertEquals(0, Color.alpha(out2.getPixel(2, 2)))
    }

    @Test
    fun blend_halfAlpha_averagesChannels() {
        val mid = remover.blend(Color.RED, Color.BLUE, 0.5f)
        assertTrue("R", abs(Color.red(mid) - 127) <= 1)
        assertTrue("G", abs(Color.green(mid) - 0) <= 1)
        assertTrue("B", abs(Color.blue(mid) - 127) <= 1)
    }

    @Test
    fun remove_delegatesToSegmenter_andComposes() = runTest {
        val src = solidBitmap(4, 4, Color.GREEN)
        fakeClient.maskToReturn = maskAllBackground(4, 4)
        val out = remover.remove(src, BackgroundColor.WHITE)
        assertEquals(Color.WHITE, out.getPixel(2, 2))
    }

    @Test
    fun scaleMask_upsamplesNearest() {
        val mask = SegmentationMask(
            buffer = FloatBuffer.wrap(floatArrayOf(1f, 0f, 1f, 0f)),
            width = 2, height = 2,
        )
        val scaled = remover.scaleMask(mask, 4, 4)
        assertEquals(16, scaled.size)
        assertEquals(1f, scaled[0], 0.01f)
    }
}
