package dev.andikune.robotasisten.io.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dev.andikune.robotasisten.io.util.LogHelper

/** Accessibility integration is not implemented; no screen content is read or acted on. */
class MyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogHelper.e("MyAccessibilityService", "Accessibility content reading/actions are not implemented")
    }
}
