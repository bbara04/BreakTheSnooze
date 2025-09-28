package hu.bbara.viewideas.objectdetection.vision

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions

class ObjectDetectorProcessor(
    context: Context,
    private val detector: ObjectDetector,
    private val onDetectedObjects: (List<DetectedObject>) -> Unit
) : VisionProcessorBase<List<DetectedObject>>(context) {

    constructor(
        context: Context,
        options: CustomObjectDetectorOptions,
        onDetectedObjects: (List<DetectedObject>) -> Unit = {}
    ) : this(context, ObjectDetection.getClient(options), onDetectedObjects)

    override fun stop() {
        detector.close()
    }

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return detector.process(image)
    }

    override fun onSuccess(results: List<DetectedObject>, graphicOverlay: GraphicOverlay) {
        graphicOverlay.clear()
        for (obj in results) {
            graphicOverlay.add(ObjectGraphic(graphicOverlay, obj))
        }
        graphicOverlay.postInvalidate()
        onDetectedObjects(results)
    }

    override fun onFailure(e: Exception) {
        if (e.message?.contains("Failed to load model") == true) {
            Log.e(TAG, "Object detection failed: Failed to load model. Check if the model file exists and is valid.", e)
        } else {
            Log.e(TAG, "Object detection failed", e)
        }
    }

    companion object {
        private const val TAG = "ObjectDetectorProcessor"
    }
}
