package dev.andikune.robotasisten.io.logic

/** Strategy pattern deciding how to capture question content from the screen. */
enum class CaptureMode { ACCESSIBILITY_NODES, SCREEN_OCR }

object CaptureStrategy {
    /** Caller must pass a positively detected live accessibility connection. */
    fun selectStrategy(accessibilityConnected: Boolean = false): CaptureMode =
        if (accessibilityConnected) CaptureMode.ACCESSIBILITY_NODES else CaptureMode.SCREEN_OCR
}
