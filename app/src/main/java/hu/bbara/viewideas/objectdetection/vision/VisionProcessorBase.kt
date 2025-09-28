package hu.bbara.viewideas.objectdetection.vision

import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

abstract class VisionProcessorBase<T>(context: Context) : Closeable {

    abstract fun stop()

    protected abstract fun detectInImage(image: InputImage): Task<T>

    protected abstract fun onSuccess(results: T, graphicOverlay: GraphicOverlay)

    protected abstract fun onFailure(e: Exception)

    @ExperimentalGetImage
    fun processImageProxy(imageProxy: ImageProxy, graphicOverlay: GraphicOverlay) {
        val image = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        detectInImage(image)
            .addOnSuccessListener { results ->
                onSuccess(results, graphicOverlay)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    override fun close() {
        stop()
    }
}
