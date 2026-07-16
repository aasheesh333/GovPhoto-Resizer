package com.dhanuk.govphoto.data.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Abstraction over ML Kit Selfie Segmenter so unit tests can fake the SDK.
 * Real impl: [MlKitSegmenterClient]
 */
interface SegmenterClient {
    /**
     * Process [bitmap] and return a [SegmentationMask] with confidence values
     * in [0f, 1f] where 1 = subject (person) and 0 = background.
     * Suspends until the SDK callback fires.
     * @throws Exception on SDK failure
     */
    suspend fun process(bitmap: Bitmap): SegmentationMask
}

data class SegmentationMask(
    val buffer: FloatBuffer, // length = width * height; values in [0f, 1f]
    val width: Int,
    val height: Int,
)

@Singleton
class MlKitSegmenterClient @Inject constructor() : SegmenterClient {
    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    override suspend fun process(bitmap: Bitmap): SegmentationMask =
        suspendCancellableCoroutine { cont ->
            val input = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(input)
                .addOnSuccessListener { mask ->
                    val bb = mask.buffer
                    bb.rewind()
                    val floats = FloatArray(mask.width * mask.height)
                    bb.asFloatBuffer().get(floats)
                    cont.resume(
                        SegmentationMask(
                            buffer = FloatBuffer.wrap(floats),
                            width = mask.width,
                            height = mask.height,
                        )
                    )
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
