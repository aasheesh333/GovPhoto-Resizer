package com.dhanuk.govphoto.data.ml

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Abstraction over ML Kit Face Detection so unit tests can fake the SDK.
 * Real impl: [MlKitFaceDetectorClient]
 */
interface FaceDetectorClient {
    /**
     * Detect faces in [bitmap]. Returns empty list if none found.
     * @throws Exception on SDK failure
     */
    suspend fun detect(bitmap: Bitmap): List<DetectedFace>
}

data class DetectedFace(
    val bounds: RectF,
    val leftEyeY: Float? = null,
    val rightEyeY: Float? = null,
    val headEulerAngleZ: Float = 0f,
)

@Singleton
class MlKitFaceDetectorClient @Inject constructor() : FaceDetectorClient {
    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    override suspend fun detect(bitmap: Bitmap): List<DetectedFace> =
        suspendCancellableCoroutine { cont ->
            val input = InputImage.fromBitmap(bitmap, 0)
            detector.process(input)
                .addOnSuccessListener { faces ->
                    cont.resume(faces.map { face ->
                        val box = face.boundingBox
                        DetectedFace(
                            bounds = RectF(
                                box.left.toFloat(),
                                box.top.toFloat(),
                                box.right.toFloat(),
                                box.bottom.toFloat(),
                            ),
                            leftEyeY = face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.y,
                            rightEyeY = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.y,
                            headEulerAngleZ = face.headEulerAngleZ,
                        )
                    })
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
