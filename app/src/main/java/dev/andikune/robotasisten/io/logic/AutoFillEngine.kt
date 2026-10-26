package dev.andikune.robotasisten.io.logic

import android.content.Context
import dev.andikune.robotasisten.io.service.MyAccessibilityService
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.LogHelper

enum class AutoFillResult { FILLED, COPIED_TO_CLIPBOARD, FAILED }

/** Attempts a real focused-field fill; clipboard is an explicit fallback, never reported as a fill. */
object AutoFillEngine {
    @Suppress("UNUSED_PARAMETER")
    fun fillAnswer(
        context: Context,
        answer: String,
        targetOptionIndex: Int? = null,
        onComplete: (AutoFillResult) -> Unit
    ) {
        if (answer.isBlank()) {
            onComplete(AutoFillResult.FAILED)
            return
        }

        val requestAccepted = MyAccessibilityService.fillFocusedField(answer) { filled ->
            if (filled) {
                LogHelper.i("AutoFillEngine", "Answer inserted into focused editable field")
                onComplete(AutoFillResult.FILLED)
            } else {
                onComplete(copyFallback(context, answer))
            }
        }
        if (!requestAccepted) onComplete(copyFallback(context, answer))
    }

    private fun copyFallback(context: Context, answer: String): AutoFillResult = runCatching {
        ClipboardHelper.copyToClipboard(context, "RELX.iO Answer", answer)
        AutoFillResult.COPIED_TO_CLIPBOARD
    }.onFailure { error ->
        LogHelper.e("AutoFillEngine", "Failed to copy answer to clipboard", error)
    }.getOrDefault(AutoFillResult.FAILED)
}
