package dev.andikune.robotasisten.io.logic

/**
 * Strategy pattern deciding how to capture question content from the screen.
 */
enum class CaptureMode {
    ACCESSIBILITY_NODES,
    SCREEN_OCR
}

object CaptureStrategy {
    fun selectStrategy(): CaptureMode {
        /* TODO: Detect whether accessibility service is actively connected */
        return CaptureMode.ACCESSIBILITY_NODES
    }
}
