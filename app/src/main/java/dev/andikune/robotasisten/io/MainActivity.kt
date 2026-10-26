package dev.andikune.robotasisten.io

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.andikune.robotasisten.io.navigation.AppNavigation
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme

/**
 * Main Activity for R A . i O.
 * Hosts the Jetpack Compose navigation graph with edge-to-edge styling.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RAiOTheme {
                AppNavigation()
            }
        }
    }
}
