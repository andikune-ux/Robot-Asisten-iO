package dev.andikune.robotasisten.io.logic

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.andikune.robotasisten.io.service.MyAccessibilityService
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
        val service = MyAccessibilityService.instance
        if (service == null) {
            LogHelper.w("AutoFillEngine", "AccessibilityService is not connected, copying to clipboard as fallback")
            ClipboardHelper.copyToClipboard(context, "R A . i O Answer", answer)
            onComplete(false)
            return
        }

        val root = service.rootInActiveWindow
        if (root == null) {
            LogHelper.w("AutoFillEngine", "No active window root found, copying to clipboard")
            ClipboardHelper.copyToClipboard(context, "R A . i O Answer", answer)
            onComplete(false)
            return
        }

        var filled = false

        // 1. Try filling editable text field if present
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        findEditableNodes(root, editTexts)
        if (editTexts.isNotEmpty()) {
            val target = editTexts.first()
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, answer)
            }
            filled = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            LogHelper.i("AutoFillEngine", "Filled text field result: $filled")
        }

        // 2. If multiple choice option index was matched
        if (!filled && targetOptionIndex != null) {
            val clickableOptions = mutableListOf<AccessibilityNodeInfo>()
            findClickableOptions(root, clickableOptions)
            if (targetOptionIndex in clickableOptions.indices) {
                filled = clickableOptions[targetOptionIndex].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                LogHelper.i("AutoFillEngine", "Clicked option index $targetOptionIndex result: $filled")
            }
        }

        // 3. Fallback: Copy to clipboard
        if (!filled) {
            ClipboardHelper.copyToClipboard(context, "R A . i O Answer", answer)
        }

        onComplete(filled)
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodes(child, results)
        }
    }

    private fun findClickableOptions(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && (node.className?.contains("RadioButton", true) == true ||
            node.className?.contains("CheckBox", true) == true ||
            node.className?.contains("Button", true) == true)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableOptions(child, results)
        }
    }
}
