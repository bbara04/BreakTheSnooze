package hu.bbara.viewideas.objectdetection.camera

import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import hu.bbara.viewideas.objectdetection.vision.GraphicOverlay
import hu.bbara.viewideas.objectdetection.vision.ObjectDetectorProcessor
import java.util.concurrent.Executors

@Composable
fun CustomCameraPreview(
    modifier: Modifier = Modifier,
    targetResolution: Size? = null,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    showOverlay: Boolean = false,
    onObjectsDetected: (List<DetectedObject>) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraProvider = remember(cameraProviderFuture) { cameraProviderFuture.get() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val graphicOverlay = remember { GraphicOverlay(context, null) }
    val detectionCallback = rememberUpdatedState(onObjectsDetected)

    val objectDetectorProcessor = remember {
        val localModel = LocalModel.Builder()
            .setAssetFilePath("object_labeler.tflite")
            .build()
        val options = CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
            //.enableMultipleObjects()
            .enableClassification()
            .setClassificationConfidenceThreshold(0.6f)
            .setMaxPerObjectLabelCount(5)
            .build()
        ObjectDetectorProcessor(
            context = context,
            options = options,
            onDetectedObjects = { results: List<DetectedObject> ->
                detectionCallback.value(results)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            objectDetectorProcessor.stop()
            cameraProvider.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    // Using SurfaceView-based implementation helps on some devices with buffer/dataspace issues.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val previewBuilder = Preview.Builder()
                val analysisBuilder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                // Respect targetResolution if provided to improve detector accuracy.
                targetResolution?.let {
                    previewBuilder.setTargetResolution(it)
                    analysisBuilder.setTargetResolution(it)
                }

                val preview = previewBuilder
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = analysisBuilder.build()

                // Track and update overlay geometry whenever source info changes.
                var lastRotation: Int? = null
                var lastWidth: Int? = null
                var lastHeight: Int? = null
                var lastFacing: Int? = null

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val isFront = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                    val facingInt = if (isFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    val (srcW, srcH) = if (rotationDegrees == 0 || rotationDegrees == 180) {
                        imageProxy.width to imageProxy.height
                    } else {
                        imageProxy.height to imageProxy.width
                    }

                    if (lastRotation != rotationDegrees || lastWidth != srcW || lastHeight != srcH || lastFacing != facingInt) {
                        graphicOverlay.setImageSourceInfo(srcW, srcH, facingInt)
                        lastRotation = rotationDegrees
                        lastWidth = srcW
                        lastHeight = srcH
                        lastFacing = facingInt
                    }

                    processImage(imageProxy, graphicOverlay, objectDetectorProcessor)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                previewView
            }
        )
        if (showOverlay) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { graphicOverlay })
        }
    }
}

private fun processImage(
    imageProxy: androidx.camera.core.ImageProxy,
    graphicOverlay: hu.bbara.viewideas.objectdetection.vision.GraphicOverlay,
    processor: hu.bbara.viewideas.objectdetection.vision.ObjectDetectorProcessor
) {
    try {
        processor.processImageProxy(imageProxy, graphicOverlay)
    } catch (e: Exception) {
        Log.e("CustomCameraPreview", "Error processing image", e)
        imageProxy.close()
    }
}
