package hu.bbara.viewideas.ui.alarm.dismiss

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import hu.bbara.viewideas.R
import java.util.concurrent.Executors

class QrBarcodeScanDismissTask : AlarmDismissTask {
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
        val lifecycleOwner = LocalLifecycleOwner.current

        var hasCameraPermission by remember { mutableStateOf(false) }
        var permanentlyDenied by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                val activity = context as? android.app.Activity
                permanentlyDenied = activity?.let {
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } ?: false
            } else {
                permanentlyDenied = false
            }
        }

        // Initial check and request on first composition
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            hasCameraPermission = granted
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Re-check on resume
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    hasCameraPermission = granted
                    if (!granted) {
                        val activity = context as? android.app.Activity
                        permanentlyDenied = activity?.let {
                            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                                it,
                                Manifest.permission.CAMERA
                            )
                        } ?: false
                    } else {
                        permanentlyDenied = false
                    }
                }
            }
            val lifecycle = lifecycleOwner.lifecycle
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        BackHandler { onCancelled() }

        when {
            hasCameraPermission -> {
                BarcodeScannerContent(
                    modifier = modifier,
                    onBarcodeDetected = onCompleted,
                    onCancel = onCancelled
                )
            }
            permanentlyDenied -> {
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
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCancel = onCancelled
                )
            }
        }
    }
}

@Composable
private fun BarcodeScannerContent(
    modifier: Modifier = Modifier,
    onBarcodeDetected: () -> Unit,
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

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                                    processImageProxy(
                                        barcodeScanner,
                                        imageProxy,
                                        onBarcodeDetected = {
                                            if (!detectionHandled) {
                                                detectionHandled = true
                                                onBarcodeDetected()
                                            }
                                        }
                                    )
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
            
            // Overlay UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.qr_barcode_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.qr_barcode_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = onCancel,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    Text(text = stringResource(id = R.string.qr_barcode_cancel))
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: () -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    onBarcodeDetected()
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
private fun PermissionDeniedContent(
    modifier: Modifier = Modifier,
    isPermanent: Boolean,
    onRequest: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onCancel: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
