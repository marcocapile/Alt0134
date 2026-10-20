package io.github.marcocapile.launchermanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.marcocapile.launchermanager.ui.LauncherScreen
import io.github.marcocapile.launchermanager.ui.theme.LauncherManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LauncherManagerTheme {
                LauncherScreen()
            }
        }
    }
}
