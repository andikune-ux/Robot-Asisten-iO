package dev.andikune.robotasisten.io.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.andikune.robotasisten.io.MainActivity

/**
 * Splash entry activity forwarding immediately into MainActivity navigation graph.
 */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
