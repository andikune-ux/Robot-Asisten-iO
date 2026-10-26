package dev.andikune.robotasisten.io.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.andikune.robotasisten.io.logic.ScreenStateTracker
import dev.andikune.robotasisten.io.util.LogHelper

/**
 * Core Accessibility Service to read node hierarchies, inspect question text, and assist autofill.
 */
class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogHelper.i("MyAccessibilityService", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != packageName) {
            ScreenStateTracker.updateForegroundPackage(pkg)
        }
    }

    override fun onInterrupt() {
        LogHelper.w("MyAccessibilityService", "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun extractScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val stringBuilder = StringBuilder()
        traverseNodes(root, stringBuilder)
        val text = stringBuilder.toString().trim()
        ScreenStateTracker.updateCapturedText(text)
        return text
    }

    fun extractOptions(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val options = mutableListOf<String>()
        traverseForOptions(root, options)
        return options
    }

    private fun traverseNodes(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            sb.append(text).append("\n")
        } else if (!desc.isNullOrBlank()) {
            sb.append(desc).append("\n")
        }
        for (i in 0 until node.childCount) {
            traverseNodes(node.getChild(i), sb)
        }
    }

    private fun traverseForOptions(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val className = node.className?.toString() ?: ""
        if (className.contains("RadioButton", true) || className.contains("CheckBox", true)) {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (!text.isNullOrBlank()) {
                list.add(text)
            }
        }
        for (i in 0 until node.childCount) {
            traverseForOptions(node.getChild(i), list)
        }
    }

    companion object {
        var instance: MyAccessibilityService? = null
            private set
    }
}
