package com.dhanuk.govphoto_resizer.data.ml

import android.graphics.Bitmap
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

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
     */
    suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val ovalGuide = defaultOvalGuide(w, h)

        val faces = try {
            detector.detect(bitmap)
        } catch (e: Exception) {
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

        val within = faces.size == 1 && withinOval(primary.bounds, ovalGuide, margin = 0.05f)
        if (!within && faces.size == 1) issues += "Face not within guide oval"

        return FaceAnalysisResult(
            faceCount = faces.size,
            bounds = primary.bounds,
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
         * True if [faceBounds] is fully contained within [ovalGuide] shrunk by [margin]
         * (fraction of oval width/height on each side).
         * Pure function — unit-testable without ML Kit.
         */
        fun withinOval(faceBounds: RectF, ovalGuide: RectF, margin: Float = 0.05f): Boolean {
            val mx = ovalGuide.width() * margin
            val my = ovalGuide.height() * margin
            val inner = RectF(
                ovalGuide.left + mx,
                ovalGuide.top + my,
                ovalGuide.right - mx,
                ovalGuide.bottom - my,
            )
            return faceBounds.left >= inner.left &&
                faceBounds.top >= inner.top &&
                faceBounds.right <= inner.right &&
                faceBounds.bottom <= inner.bottom
        }
    }
}
