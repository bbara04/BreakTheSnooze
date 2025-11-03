package hu.bbara.breakthesnooze.ui.alarm.dismiss

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.ui.alarm.rememberCameraPermissionState
import java.util.concurrent.Executors

class QrBarcodeScanDismissTask(
    private val expectedValue: String? = null,
    private val requiredUniqueCount: Int = 0
) : AlarmDismissTask {
    override val id: String = "qr_barcode_scan"
    override val labelResId: Int = R.string.alarm_qr_barcode_scan

    @Composable
    override fun Content(
        modifier: Modifier,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    ) {
        val context = LocalContext.current
        val packageName = context.packageName
        val permissionState = rememberCameraPermissionState()
        val expectedNormalized = remember(expectedValue) { expectedValue?.trim() }
        val uniqueGoal = remember(requiredUniqueCount) { requiredUniqueCount.coerceAtLeast(0) }
        val uniqueMode = expectedNormalized.isNullOrBlank() && uniqueGoal > 0
        val title = stringResource(id = R.string.qr_barcode_title)
        val instructions = when {
            expectedNormalized != null -> stringResource(id = R.string.qr_barcode_specific_instructions)
            uniqueMode -> stringResource(id = R.string.qr_barcode_unique_instructions, uniqueGoal)
            else -> stringResource(id = R.string.qr_barcode_instructions)
        }
        val cancelLabel = stringResource(id = R.string.qr_barcode_cancel)
        val mismatchText = stringResource(id = R.string.qr_barcode_mismatch)
        val duplicateText = stringResource(id = R.string.qr_barcode_duplicate)

        var errorMessage by remember { mutableStateOf<String?>(null) }
        var scannedCodes by remember { mutableStateOf(setOf<String>()) }

        BackHandler { onCancelled() }

        when {
            permissionState.hasPermission -> {
                val progressText = if (uniqueMode) {
                    stringResource(
                        id = R.string.qr_barcode_unique_progress,
                        scannedCodes.size,
                        uniqueGoal
                    )
                } else {
                    null
                }
                BarcodeScannerContent(
                    modifier = modifier,
                    title = title,
                    instructions = instructions,
                    cancelLabel = cancelLabel,
                    errorMessage = errorMessage,
                    progressText = progressText,
                    onBarcodeDetected = { rawValue ->
                        val normalized = rawValue.trim()
                        if (expectedNormalized != null) {
                            if (normalized == expectedNormalized) {
                                errorMessage = null
                                onCompleted()
                                true
                            } else {
                                errorMessage = mismatchText
                                false
                            }
                        } else if (uniqueMode) {
                            if (scannedCodes.contains(normalized)) {
                                errorMessage = duplicateText
                                false
                            } else {
                                val updatedSet = scannedCodes + normalized
                                scannedCodes = updatedSet
                                errorMessage = null
                                if (updatedSet.size >= uniqueGoal) {
                                    onCompleted()
                                    true
                                } else {
                                    false
                                }
                            }
                        } else {
                            errorMessage = null
                            onCompleted()
                            true
                        }
                    },
                    onCancel = onCancelled
                )
            }
            permissionState.permanentlyDenied -> {
                PermissionDeniedContent(
                    modifier = modifier,
                    isPermanent = true,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    onCancel = onCancelled
                )
            }
            else -> {
                PermissionDeniedContent(
                    modifier = modifier,
                    isPermanent = false,
                    onRequest = { permissionState.requestPermission() },
                    onCancel = onCancelled
                )
            }
        }
    }
}

@Composable
internal fun BarcodeScannerContent(
    modifier: Modifier = Modifier,
    title: String,
    instructions: String,
    cancelLabel: String,
    errorMessage: String? = null,
    progressText: String? = null,
    onBarcodeDetected: (String) -> Boolean,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    
    var detectionHandled by remember { mutableStateOf(false) }

    // Keep the screen on
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            executor.shutdown()
            barcodeScanner.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor) { imageProxy ->
                                if (detectionHandled) {
                                    imageProxy.close()
                                } else {
                                    processImageProxy(
                                        barcodeScanner,
                                        imageProxy,
                                        onBarcodeDetected = onBarcodeDetected
                                    ) { accepted ->
                                        if (accepted) {
                                            detectionHandled = true
                                        }
                                    }
                                }
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        // Handle camera binding errors
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Viewfinder square
        val viewfinderSize = 260.dp
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(viewfinderSize)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!progressText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = cancelLabel)
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
internal fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Boolean,
    onResult: (Boolean) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                var accepted = false
                if (barcodes.isNotEmpty()) {
                    for (barcode in barcodes) {
                        val value = barcode.rawValue?.trim() ?: continue
                        if (onBarcodeDetected(value)) {
                            accepted = true
                            break
                        }
                    }
                }
                onResult(accepted)
            }
            .addOnFailureListener {
                onResult(false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
internal fun PermissionDeniedContent(
    modifier: Modifier = Modifier,
    isPermanent: Boolean,
    onRequest: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onCancel: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isPermanent) {
                    "Camera permission permanently denied."
                } else {
                    "Camera permission is required to scan QR codes and barcodes."
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            
            if (isPermanent) {
                Text(
                    text = "Please enable the Camera permission in App Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
            
            onRequest?.let {
                Button(onClick = it) {
                    Text("Grant permission")
                }
                Spacer(Modifier.height(8.dp))
            }
            
            onOpenSettings?.let {
                Button(onClick = it) {
                    Text("Open Settings")
                }
                Spacer(Modifier.height(8.dp))
            }
            
            Button(onClick = onCancel) {
                Text(text = stringResource(id = R.string.qr_barcode_cancel))
            }
        }
    }
}
