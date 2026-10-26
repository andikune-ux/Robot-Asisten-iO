package dev.andikune.robotasisten.io.logic

import dev.andikune.robotasisten.io.service.MyAccessibilityService

/**
 * Strategy pattern deciding how to capture question content from the screen.
 */
enum class CaptureMode {
    ACCESSIBILITY_NODES,
    SCREEN_OCR
}

object CaptureStrategy {
    fun selectStrategy(): CaptureMode {
        return if (MyAccessibilityService.instance != null) {
            CaptureMode.ACCESSIBILITY_NODES
        } else {
            CaptureMode.SCREEN_OCR
        }
    }
}
