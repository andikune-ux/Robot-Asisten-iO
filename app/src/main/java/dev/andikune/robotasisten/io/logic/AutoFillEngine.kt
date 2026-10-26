package dev.andikune.robotasisten.io.logic

import android.content.Context
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.LogHelper

/** Automates placing the confirmed answer into the on-screen target application. */
object AutoFillEngine {
    fun fillAnswer(context: Context, answer: String, targetOptionIndex: Int? = null, onComplete: (Boolean) -> Unit) {
        // Clipboard copy is only a fallback aid; it does not mean the target was filled.
        LogHelper.i("AutoFillEngine", "Copying answer to clipboard; target fill unavailable")
        ClipboardHelper.copyToClipboard(context, "R A . i O Answer", answer)
        onComplete(false)
    }
}
