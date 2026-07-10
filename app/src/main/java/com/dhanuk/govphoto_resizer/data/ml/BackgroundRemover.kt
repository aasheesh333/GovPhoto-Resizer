package com.dhanuk.govphoto_resizer.data.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.dhanuk.govphoto_resizer.ui.viewmodel.BackgroundColor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class BackgroundRemover @Inject constructor(
    private val segmenter: SegmenterClient
) {
    /**
     * Run selfie segmentation on [source], then composite subject over [bgColor].
     * Returns a new ARGB_8888 bitmap the same size as [source].
     */
    suspend fun remove(source: Bitmap, bgColor: BackgroundColor): Bitmap {
        val mask = segmenter.process(source)
        return composeOver(source, mask, bgColor)
    }

    /**
     * Pure compositing function — unit-testable without ML Kit.
     * Threshold 0.5: confidence > 0.5 keeps subject pixel; else uses bg.
     * 3px box-blur feather at edges (radius capped at width/200, min 1).
     */
    fun composeOver(
        source: Bitmap,
        mask: SegmentationMask,
        bgColor: BackgroundColor,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val maskConf = scaleMask(mask, w, h)
        val featherRadius = max(1, min(3, w / 200))
        val feathered = boxBlur(maskConf, w, h, featherRadius)

        val bg = buildBackground(w, h, bgColor)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        val bgPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        bg.getPixels(bgPixels, 0, w, 0, 0, w, h)

        for (i in outPixels.indices) {
            val conf = feathered[i].coerceIn(0f, 1f)
            outPixels[i] = blend(srcPixels[i], bgPixels[i], conf)
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        bg.recycle()
        return result
    }

    internal fun scaleMask(mask: SegmentationMask, targetW: Int, targetH: Int): FloatArray {
        val out = FloatArray(targetW * targetH)
        val srcW = mask.width
        val srcH = mask.height
        val buf = mask.buffer
        buf.rewind()
        val src = FloatArray(srcW * srcH)
        buf.get(src)
        if (srcW == targetW && srcH == targetH) return src
        for (y in 0 until targetH) {
            val sy = (y * srcH) / targetH
            for (x in 0 until targetW) {
                val sx = (x * srcW) / targetW
                out[y * targetW + x] = src[sy * srcW + sx]
            }
        }
        return out
    }

    internal fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val diam = radius * 2 + 1
        for (y in 0 until h) {
            var sum = 0f
            for (k in -radius..radius) {
                val x = k.coerceIn(0, w - 1)
                sum += src[y * w + x]
            }
            for (x in 0 until w) {
                tmp[y * w + x] = sum / diam
                val leave = (x - radius).coerceIn(0, w - 1)
                val enter = (x + radius + 1).coerceIn(0, w - 1)
                sum += src[y * w + enter] - src[y * w + leave]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (k in -radius..radius) {
                val y = k.coerceIn(0, h - 1)
                sum += tmp[y * w + x]
            }
            for (y in 0 until h) {
                out[y * w + x] = sum / diam
                val leave = (y - radius).coerceIn(0, h - 1)
                val enter = (y + radius + 1).coerceIn(0, h - 1)
                sum += tmp[enter * w + x] - tmp[leave * w + x]
            }
        }
        return out
    }

    internal fun buildBackground(w: Int, h: Int, bgColor: BackgroundColor): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        when (bgColor) {
            BackgroundColor.WHITE -> canvas.drawColor(Color.WHITE)
            BackgroundColor.STUDIO_BLUE -> canvas.drawColor(Color.parseColor("#B8D4E8"))
            BackgroundColor.LIGHT_GREY -> canvas.drawColor(Color.parseColor("#E8E8E8"))
            BackgroundColor.GRADIENT -> {
                val paint = Paint()
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.parseColor("#E8F4FC"),
                    Color.parseColor("#B8D4E8"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            BackgroundColor.TRANSPARENT -> canvas.drawColor(Color.TRANSPARENT)
        }
        return bmp
    }

    /** Blend src over bg with weight alpha (0=bg, 1=src). Handles ARGB. */
    internal fun blend(src: Int, bg: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        val inv = 1f - a
        val sr = Color.red(src); val sg = Color.green(src); val sb = Color.blue(src); val sa = Color.alpha(src)
        val br = Color.red(bg);  val bg_ = Color.green(bg); val bb = Color.blue(bg);  val ba = Color.alpha(bg)
        val outA = (sa * a + ba * inv).roundToInt().coerceIn(0, 255)
        val outR = (sr * a + br * inv).roundToInt().coerceIn(0, 255)
        val outG = (sg * a + bg_ * inv).roundToInt().coerceIn(0, 255)
        val outB = (sb * a + bb * inv).roundToInt().coerceIn(0, 255)
        return Color.argb(outA, outR, outG, outB)
    }
}
