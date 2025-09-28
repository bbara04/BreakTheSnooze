package hu.bbara.viewideas

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.getSystemService
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import hu.bbara.viewideas.ui.alarm.AlarmScreen

class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }
    private var permissionRequestedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViewIdeasTheme {
                val colorScheme = MaterialTheme.colorScheme
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        window.navigationBarColor = colorScheme.background.toArgb()
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightNavigationBars = colorScheme.background.luminance() > 0.5f
                    }
                }
                AlarmScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestCameraPermissionIfNeeded()
    }

    private fun requestCameraPermissionIfNeeded() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val keyguardManager = getSystemService<KeyguardManager>()
        val isLocked = keyguardManager?.isKeyguardLocked == true
        if (!hasPermission && !isLocked && !permissionRequestedThisSession) {
            permissionRequestedThisSession = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
