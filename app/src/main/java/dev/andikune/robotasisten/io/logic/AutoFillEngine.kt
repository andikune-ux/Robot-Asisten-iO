package dev.andikune.robotasisten.io.logic

import android.content.Context
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Automates placing the confirmed answer into the on-screen target application.
 */
object AutoFillEngine {

    fun fillAnswer(
        context: Context,
        answer: String,
        targetOptionIndex: Int? = null,
        onComplete: (Boolean) -> Unit
    ) {
        /* TODO: Implement AccessibilityNodeInfo inspection and action execution */
        LogHelper.i("AutoFillEngine", "Copying answer to clipboard as fallback")
        ClipboardHelper.copyToClipboard(context, "R A . i O Answer", answer)
        onComplete(true)
    }
}
