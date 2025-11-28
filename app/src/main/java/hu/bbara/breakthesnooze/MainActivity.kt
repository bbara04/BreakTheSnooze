package hu.bbara.breakthesnooze

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.AlarmScreen

class MainActivity : ComponentActivity() {

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsRequested = true
            maybePromptForFullScreenIntent()
        }
    private var permissionsRequested = false
    private var fullScreenDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissionsIfPossible()
        maybePromptForFullScreenIntent()
        setContent {
            BreakTheSnoozeTheme {
                val colorScheme = MaterialTheme.colorScheme
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val useDarkIcons = colorScheme.background.luminance() > 0.5f
                        window.decorView.setBackgroundColor(colorScheme.background.toArgb())
                        window.navigationBarColor = colorScheme.background.toArgb()
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightNavigationBars = useDarkIcons
                        controller.isAppearanceLightStatusBars = useDarkIcons
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    AlarmScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestPermissionsIfPossible()
        maybePromptForFullScreenIntent()
    }

    private fun requestPermissionsIfPossible() {
        if (permissionsRequested) return
        val isLocked = getSystemService<KeyguardManager>()?.isKeyguardLocked == true
        if (isLocked) return

        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissionsToRequest.isEmpty()) {
            permissionsRequested = true
            return
        }

        permissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun maybePromptForFullScreenIntent() {
        if (fullScreenDialogShown) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        if (notificationManager.canUseFullScreenIntent()) {
            fullScreenDialogShown = true
            return
        }
        fullScreenDialogShown = true
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(fallback)
            }
    }
}
