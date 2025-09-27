package hu.bbara.viewideas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import hu.bbara.viewideas.ui.alarm.AlarmScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViewIdeasTheme {
                AlarmScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
