package dev.andikune.robotasisten.io.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Thread-safe toast message dispatcher.
 */
object ToastHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun showShort(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showLong(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
