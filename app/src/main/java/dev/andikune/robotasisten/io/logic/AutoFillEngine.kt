package dev.andikune.robotasisten.io.logic

import android.content.Context
import dev.andikune.robotasisten.io.service.MyAccessibilityService
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.LogHelper

enum class AutoFillResult { FILLED, COPIED_TO_CLIPBOARD, FAILED }

sealed class InteractiveActionResult {
    data class ClickedOption(val label: String) : InteractiveActionResult()
    data class FilledField(val text: String) : InteractiveActionResult()
    data object ClickedNext : InteractiveActionResult()
    data object CopiedToClipboard : InteractiveActionResult()
    data object Failed : InteractiveActionResult()
}

/** Attempts real screen interaction (clicking options / filling editable fields). */
object AutoFillEngine {

    fun executeInteractiveAction(
        context: Context,
        answer: String,
        question: DetectedQuestion,
        onComplete: (InteractiveActionResult) -> Unit
    ) {
        val cleanAnswer = answer.trim()
        if (cleanAnswer.isBlank()) {
            onComplete(InteractiveActionResult.Failed)
            return
        }

        val (optionLetter, optionText, optionIdx) = parseOptionCandidate(cleanAnswer, question)

        if (question.type == QuestionType.MULTIPLE_CHOICE || optionLetter.isNotEmpty()) {
            val requested = MyAccessibilityService.clickOptionNode(optionLetter, optionText, optionIdx) { success ->
                if (success) {
                    LogHelper.i("AutoFillEngine", "Interactive click success on option: $optionLetter $optionText")
                    onComplete(InteractiveActionResult.ClickedOption("$optionLetter $optionText".trim()))
                } else {
                    // Try field fill or fallback
                    tryFillOrCopy(context, cleanAnswer, onComplete)
                }
            }
            if (!requested) {
                tryFillOrCopy(context, cleanAnswer, onComplete)
            }
        } else {
            tryFillOrCopy(context, cleanAnswer, onComplete)
        }
    }

    private fun tryFillOrCopy(
        context: Context,
        text: String,
        onComplete: (InteractiveActionResult) -> Unit
    ) {
        val requested = MyAccessibilityService.fillFocusedField(text) { filled ->
            if (filled) {
                LogHelper.i("AutoFillEngine", "Interactive text filled into field")
                onComplete(InteractiveActionResult.FilledField(text))
            } else {
                copyFallback(context, text)
                onComplete(InteractiveActionResult.CopiedToClipboard)
            }
        }
        if (!requested) {
            copyFallback(context, text)
            onComplete(InteractiveActionResult.CopiedToClipboard)
        }
    }

    fun triggerNextButton(onComplete: (Boolean) -> Unit) {
        MyAccessibilityService.clickNextButton { clicked ->
            LogHelper.i("AutoFillEngine", "Trigger next button clicked: $clicked")
            onComplete(clicked)
        }
    }

    private fun parseOptionCandidate(
        answer: String,
        question: DetectedQuestion
    ): Triple<String, String, Int?> {
        val targetMatch = "(?:TARGET|Jawaban|Pilihan)?\\s*[:=]?\\s*([A-Ea-e])[.)\\s]".toRegex(RegexOption.IGNORE_CASE)
            .find(answer)

        val letter = targetMatch?.groupValues?.get(1)?.uppercase() ?: ""
        var optionText = ""
        var optionIdx: Int? = null

        if (letter.isNotEmpty()) {
            val charCode = letter[0] - 'A'
            if (charCode in question.options.indices) {
                optionIdx = charCode
                optionText = question.options[charCode]
            }
        }

        if (optionText.isEmpty() && question.options.isNotEmpty()) {
            val matchedIndex = question.options.indexOfFirst { opt ->
                answer.contains(opt, ignoreCase = true) || opt.contains(answer.take(30), ignoreCase = true)
            }
            if (matchedIndex != -1) {
                val inferredLetter = ('A' + matchedIndex).toString()
                return Triple(inferredLetter, question.options[matchedIndex], matchedIndex)
            }
        }

        return Triple(letter, optionText, optionIdx)
    }

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
