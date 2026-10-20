package io.github.marcocapile.garbaggio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.marcocapile.garbaggio.ui.LauncherScreen
import io.github.marcocapile.garbaggio.ui.theme.GarbaggioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GarbaggioTheme {
                LauncherScreen()
            }
        }
    }
}
