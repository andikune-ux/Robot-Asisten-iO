package dev.andikune.robotasisten.io.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* TODO: handle screen content events */ }
    override fun onInterrupt() { /* TODO: handle interruption */ }
}
