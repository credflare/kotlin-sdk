package net.credflare.sdk

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable

class CameraFrame(val nv21: ByteArray, val width: Int, val height: Int, val rotationDegrees: Int) {
    val uprightWidth get() = if(rotationDegrees % 180 == 0) width else height
    val uprightHeight get() = if(rotationDegrees % 180 == 0) height else width
}

interface FaceSignalProvider: Closeable {
    val name: String
    fun detect(frame: CameraFrame, t: Long): FaceSample
}

class MlKitFaceSignalProvider: FaceSignalProvider {
    override val name = "mlkit"
    private val detector: FaceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .build())

    override fun detect(frame: CameraFrame, t: Long): FaceSample {
        val image = InputImage.fromByteArray(frame.nv21, frame.width, frame.height, frame.rotationDegrees, InputImage.IMAGE_FORMAT_NV21)
        return sample(Tasks.await(detector.process(image)), frame.uprightWidth, frame.uprightHeight, t)
    }

    fun detect(bitmap: Bitmap, rotationDegrees: Int, t: Long = 0): FaceSample {
        val upright = rotationDegrees % 180 == 0
        return sample(Tasks.await(detector.process(InputImage.fromBitmap(bitmap, rotationDegrees))), if(upright) bitmap.width else bitmap.height, if(upright) bitmap.height else bitmap.width, t)
    }

    override fun close() = detector.close()

    companion object {
        fun sample(faces: List<Face>, width: Int, height: Int, t: Long): FaceSample {
            if(faces.size != 1) return FaceSample(t, faces.size)
            val face = faces[0]
            val rect = face.boundingBox
            val left = rect.left.coerceIn(0, width).toDouble()
            val top = rect.top.coerceIn(0, height).toDouble()
            val right = rect.right.coerceIn(0, width).toDouble()
            val bottom = rect.bottom.coerceIn(0, height).toDouble()
            val eyes = listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability)
            return FaceSample(t, 1,
                FaceBox(left / width, top / height, (right - left) / width, (bottom - top) / height),
                eyes.takeIf { it.size == 2 }?.average(),
                face.smilingProbability?.toDouble(),
                // ML Kit's positive Y turns toward the image's right, which is the person's own left in an unmirrored camera frame.
                yaw = face.headEulerAngleY.toDouble(),
                pitch = face.headEulerAngleX.toDouble())
        }
    }
}
