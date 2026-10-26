package dev.andikune.robotasisten.io

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.andikune.robotasisten.io.navigation.AppNavigation
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme

@AndroidEntryPoint
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
