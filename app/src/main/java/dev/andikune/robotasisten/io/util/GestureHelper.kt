package dev.andikune.robotasisten.io.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build

/**
 * Utility to dispatch simulated taps and swipes via AccessibilityService.
 */
object GestureHelper {
    fun dispatchClick(service: AccessibilityService, x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 80)
        val builder = GestureDescription.Builder().apply {
            addStroke(stroke)
        }
        service.dispatchGesture(
            builder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    callback?.invoke(false)
                }
            },
            null
        )
    }
}
