package com.dhanuk.govphoto_resizer.data.ml

import android.graphics.Bitmap

/**
 * Image filters for enhancing scanned documents, signatures, and thumb impressions.
 * Inspired by scan-PDF apps (CamScanner, TapScanner) for making written text crisper.
 *
 * Each filter is a pure function [Bitmap] -> [Bitmap] (new ARGB_8888 bitmap, same size
 * as source). Uses getPixels/setPixels — same pattern as [BackgroundRemover].
 */
enum class ImageFilter {
    ORIGINAL,
    GRAYSCALE,
    BINARIZE,
    ENHANCE,
    LIGHTEN,
    HIGH_CONTRAST
}

object ImageFilterProcessor {

    /**
     * Apply [filter] to [source] and return a new ARGB_8888 bitmap.
     * ORIGINAL returns the source unchanged (no copy).
     * All other filters return a new independent bitmap.
     */
    suspend fun apply(source: Bitmap, filter: ImageFilter): Bitmap {
        if (source.isRecycled) return source
        return when (filter) {
            ImageFilter.ORIGINAL -> source
            ImageFilter.GRAYSCALE -> applyGrayscale(source)
            ImageFilter.BINARIZE -> applyBinarize(source)
            ImageFilter.ENHANCE -> applyEnhance(source)
            ImageFilter.LIGHTEN -> applyLighten(source)
            ImageFilter.HIGH_CONTRAST -> applyHighContrast(source)
        }
    }

    /** Convert to grayscale using standard luminance weighting. */
    fun applyGrayscale(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            val a = (c ushr 24) and 0xFF
            out[i] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Binarize using Otsu's method for adaptive threshold.
     * Produces pure black/white document-scan look — text stays sharp.
     */
    fun applyBinarize(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        val hist = IntArray(256)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lv = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            gray[i] = lv
            hist[lv]++
        }
        val threshold = otsuThreshold(hist, w * h)
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            val v = if (gray[i] > threshold) 0xFFFFFF else 0
            out[i] = (a shl 24) or v
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Enhance: contrast boost + unsharp mask (3x3 blur, then add 0.5x difference).
     * Good for faint handwritten text.
     */
    fun applyEnhance(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        val blurred = boxBlurGray(gray, w, h, 1)
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            val sharp = gray[i] + 0.5f * (gray[i] - blurred[i])
            val contrasted = applySContrast(sharp, 1.4f)
            val v = (contrasted * 255f).toInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** Lighten dark photos: +40 brightness then gamma 0.8 correction. */
    fun applyLighten(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val gammaLut = FloatArray(256) { i ->
            val normalized = (i.coerceIn(0, 255) / 255f + 40f / 255f).coerceIn(0f, 1f)
            Math.pow(normalized.toDouble(), 0.8).toFloat()
        }
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            val r = gammaLut[(c shr 16) and 0xFF]
            val g = gammaLut[(c shr 8) and 0xFF]
            val b = gammaLut[c and 0xFF]
            val ri = (r * 255f).toInt().coerceIn(0, 255)
            val gi = (g * 255f).toInt().coerceIn(0, 255)
            val bi = (b * 255f).toInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** High contrast: strong S-curve for washed-out scans. */
    fun applyHighContrast(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val ri = (applySContrast(r, 2.2f) * 255f).toInt().coerceIn(0, 255)
            val gi = (applySContrast(g, 2.2f) * 255f).toInt().coerceIn(0, 255)
            val bi = (applySContrast(b, 2.2f) * 255f).toInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    // ------------------------- Helpers -------------------------

    /** Otsu's threshold for binarization. */
    private fun otsuThreshold(hist: IntArray, total: Int): Int {
        var sum = 0
        for (i in 0..255) sum += i * hist[i]
        var sumB = 0
        var wB = 0
        var maxVariance = 0f
        var threshold = 127
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * hist[i]
            val mB = sumB.toFloat() / wB
            val mF = (sum - sumB).toFloat() / wF
            val variance = wB.toFloat() * wF * (mB - mF) * (mB - mF)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }
        return threshold
    }

    /** 3x3 box blur on grayscale float array. */
    private fun boxBlurGray(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val diam = (radius * 2 + 1).toFloat()
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

    /** S-curve contrast: 0.5 + (v - 0.5) * [amount]. Clamps to [0,1]. */
    private fun applySContrast(v: Float, amount: Float): Float {
        return (0.5f + (v - 0.5f) * amount).coerceIn(0f, 1f)
    }
}
