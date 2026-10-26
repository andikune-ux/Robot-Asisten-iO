package dev.andikune.robotasisten.io

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.andikune.robotasisten.io.navigation.AppNavigation
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra(Constants.EXTRA_NAVIGATION_ROUTE)
        setContent {
            RAiOTheme {
                AppNavigation(
                    initialRoute = pendingRoute,
                    onInitialRouteHandled = { pendingRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(Constants.EXTRA_NAVIGATION_ROUTE)
    }
}
