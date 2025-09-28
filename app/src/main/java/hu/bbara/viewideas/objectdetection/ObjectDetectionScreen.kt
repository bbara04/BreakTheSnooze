package hu.bbara.viewideas.objectdetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.bbara.viewideas.objectdetection.camera.CustomCameraPreview
import android.util.Size

@Composable
fun ObjectDetectionScreen(
    modifier: Modifier = Modifier,
    targetLabel: String = "toothbrush",
    confidenceThreshold: Float = 0.6f,
    onDetectionSuccess: () -> Unit = {},
    onCancel: () -> Unit = {},
    autoRequestPermission: Boolean = true
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
        if (!granted && autoRequestPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Re-check on resume (e.g., after returning from Settings)
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

    BackHandler { onCancel() }

    when {
        hasCameraPermission -> {
            GrantedContent(
                modifier = modifier,
                targetLabel = targetLabel,
                confidenceThreshold = confidenceThreshold,
                onDetectionSuccess = onDetectionSuccess,
                onCancel = onCancel
            )
        }
        permanentlyDenied -> {
            PermanentlyDeniedContent(
                modifier = modifier,
                onOpenSettings = if (autoRequestPermission) {
                    {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } else null,
                onCancel = onCancel,
                autoRequestPermission = autoRequestPermission
            )
        }
        else -> {
            DeniedContent(
                modifier = modifier,
                onRequest = if (autoRequestPermission) {
                    { permissionLauncher.launch(Manifest.permission.CAMERA) }
                } else null,
                onCancel = onCancel,
                autoRequestPermission = autoRequestPermission
            )
        }
    }
}

@Composable
private fun GrantedContent(
    modifier: Modifier = Modifier,
    targetLabel: String,
    confidenceThreshold: Float,
    onDetectionSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    // Keep the screen on while this composable (camera preview) is visible
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hardcoded resolution "burnt in" for now
    val target = Size(720, 720)

    val detectionHandled = remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        CustomCameraPreview(
            modifier = Modifier.fillMaxSize(),
            targetResolution = target,
            onObjectsDetected = { detectedObjects ->
                if (!detectionHandled.value) {
                    val success = detectedObjects.any { detected ->
                        detected.labels.any { label ->
                            label.text.equals(targetLabel, ignoreCase = true) &&
                                label.confidence >= confidenceThreshold
                        }
                    }
                    if (success) {
                        detectionHandled.value = true
                        onDetectionSuccess()
                    }
                }
            }
        )
        ResolutionBadge(
            text = "Resolution: ${target.width}x${target.height}",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
        Button(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun DeniedContent(
    modifier: Modifier = Modifier,
    onRequest: (() -> Unit)? = null,
    onCancel: () -> Unit,
    autoRequestPermission: Boolean
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (autoRequestPermission) {
                "Camera permission is required to proceed."
            } else {
                "Camera permission is required. Unlock your phone, open the app, and grant camera access before scanning."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        onRequest?.let {
            Button(onClick = it) {
                Text("Grant permission")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PermanentlyDeniedContent(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    onCancel: () -> Unit,
    autoRequestPermission: Boolean
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (autoRequestPermission) {
                "Camera permission permanently denied."
            } else {
                "Camera permission permanently denied. Unlock your phone, open the app, and enable the camera permission in settings before scanning."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        if (autoRequestPermission) {
            Text("Please enable the Camera permission in App Settings.")
            Spacer(Modifier.height(12.dp))
        }
        onOpenSettings?.let {
            Button(onClick = it) {
                Text("Open Settings")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ResolutionBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
