package dev.andikune.robotasisten.io.ui.overlay

import androidx.compose.ui.graphics.Color
import dev.andikune.robotasisten.io.ui.theme.ErrorRed
import dev.andikune.robotasisten.io.ui.theme.SuccessGreen
import dev.andikune.robotasisten.io.ui.theme.TechBluePrimary
import dev.andikune.robotasisten.io.ui.theme.WarningAmber

/**
 * Bubble state representing robot lifecycle.
 */
enum class BubbleStatus(val color: Color, val label: String) {
    IDLE(TechBluePrimary, "Siap"),
    ANALYZING(WarningAmber, "Menganalisa..."),
    ANSWERING(TechBluePrimary, "Menjawab..."),
    READY(SuccessGreen, "Jawaban siap"),
    ERROR(ErrorRed, "Gagal")
}
