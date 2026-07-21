package com.dhanuk.govphoto.data.ml

import android.graphics.Bitmap
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

private const val MAX_FACE_DETECT_PX = 1024

data class FaceAnalysisResult(
    val faceCount: Int,
    val bounds: RectF?,
    val ovalGuide: RectF,
    val eyesLevel: Float,
    val sizeRatio: Float,
    val issues: List<String>,
    val isWithinMargin: Boolean,
)

@Singleton
class FaceAnalyzer @Inject constructor(
    private val detector: FaceDetectorClient,
) {
    /**
     * Analyze [bitmap] for passport-style face compliance.
     * Oval guide is centered, 60% of min(w,h) wide, 1.25 aspect (taller).
     * Downscales bitmap to max 1024px before ML Kit detection to avoid OOM.
     */
    suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        val detectionBitmap = if (maxDim > MAX_FACE_DETECT_PX) {
            val scale = MAX_FACE_DETECT_PX.toFloat() / maxDim.toFloat()
            val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        } else {
            bitmap
        }
        val ownsDownscaled = detectionBitmap !== bitmap

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val ovalGuide = defaultOvalGuide(w, h)

        val ovalScale = if (ownsDownscaled) {
            detectionBitmap.width.toFloat() / w
        } else 1f

        val faces = try {
            detector.detect(detectionBitmap)
        } catch (e: Exception) {
            if (ownsDownscaled) detectionBitmap.recycle()
            return FaceAnalysisResult(
                faceCount = 0,
                bounds = null,
                ovalGuide = ovalGuide,
                eyesLevel = 0f,
                sizeRatio = 0f,
                issues = listOf("Face detection failed: ${e.message ?: "unknown"}"),
                isWithinMargin = false,
            )
        }

        if (faces.isEmpty()) {
            if (ownsDownscaled) detectionBitmap.recycle()
            return FaceAnalysisResult(
                faceCount = 0,
                bounds = null,
                ovalGuide = ovalGuide,
                eyesLevel = 0f,
                sizeRatio = 0f,
                issues = listOf("No face detected"),
                isWithinMargin = false,
            )
        }

        val primary = faces.maxBy { f -> f.bounds.width() * f.bounds.height() }
        val faceH = primary.bounds.height().coerceAtLeast(1f)
        val sizeRatio = faceH / h
        val eyesLevel = if (primary.leftEyeY != null && primary.rightEyeY != null) {
            abs(primary.leftEyeY - primary.rightEyeY) / faceH
        } else 0f

        val issues = mutableListOf<String>()
        if (faces.size > 1) issues += "Multiple faces detected (${faces.size})"
        if (sizeRatio < 0.20f) issues += "Face too small"
        if (sizeRatio > 0.75f) issues += "Face too large"
        if (eyesLevel > 0.08f) issues += "Eyes not level"
        if (abs(primary.headEulerAngleZ) > 15f) issues += "Head tilted"

        val scaledBounds = if (ownsDownscaled) {
            RectF(
                primary.bounds.left / ovalScale,
                primary.bounds.top / ovalScale,
                primary.bounds.right / ovalScale,
                primary.bounds.bottom / ovalScale,
            )
        } else primary.bounds

        val within = faces.size == 1 && withinOval(scaledBounds, ovalGuide, minOverlap = 0.70f)
        if (!within && faces.size == 1) issues += "Face not within guide oval"

        if (ownsDownscaled) detectionBitmap.recycle()

        return FaceAnalysisResult(
            faceCount = faces.size,
            bounds = scaledBounds,
            ovalGuide = ovalGuide,
            eyesLevel = eyesLevel,
            sizeRatio = sizeRatio,
            issues = issues,
            isWithinMargin = within && issues.none {
                it.startsWith("Face too") || it == "Eyes not level" || it == "Head tilted"
            },
        )
    }

    companion object {
        /** Centered oval: width = 0.60 * min(w,h), height = width * 1.25, centered. */
        fun defaultOvalGuide(bitmapW: Float, bitmapH: Float): RectF {
            val ovalW = min(bitmapW, bitmapH) * 0.60f
            val ovalH = ovalW * 1.25f
            val left = (bitmapW - ovalW) / 2f
            val top = (bitmapH - ovalH) / 2f
            return RectF(left, top, left + ovalW, top + ovalH)
        }

        /**
         * Lenient oval check: a face counts as "inside" the guide when
         *   1) the face bounding-box centre is inside the oval, and
         *   2) at least [minOverlap] fraction of the face box overlaps the oval.
         * This prevents false negatives from ears/hair extending slightly past
         * the guide while a real human shooting a passport-style photo would
         * still say "the face is inside the oval".
         *
         * Pure function — unit-testable without ML Kit.
         */
        fun withinOval(faceBounds: RectF, ovalGuide: RectF, minOverlap: Float = 0.70f): Boolean {
            val centerInside = ovalGuide.contains(faceBounds.centerX(), faceBounds.centerY())
            if (!centerInside) return false
            val intersection = RectF(faceBounds)
            if (!intersection.intersect(ovalGuide)) return false
            val intersectArea = intersection.width().coerceAtLeast(0f) *
                intersection.height().coerceAtLeast(0f)
            val faceArea = faceBounds.width().coerceAtLeast(0f) *
                faceBounds.height().coerceAtLeast(0f)
            if (faceArea <= 0f) return false
            return intersectArea / faceArea >= minOverlap
        }
    }
}
