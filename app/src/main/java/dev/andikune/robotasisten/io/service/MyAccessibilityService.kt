package dev.andikune.robotasisten.io.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.util.Log
import dev.andikune.robotasisten.io.util.CrashHandler
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.OcrHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MyAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CrashHandler.createCoroutineExceptionHandler("MyAccessibilityService", this))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        CrashHandler.init(this)
        connectedService = this
        LogHelper.i(TAG, "Accessibility screen actions connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connectedService === this) connectedService = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // PERBAIKAN #1: Race Condition pada Callback Screenshot
    private fun captureScreenshot(callback: (Result<String>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(runCatching { readActiveWindowText() })
            return
        }

        val completed = AtomicBoolean(false)
        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) {
                callback(Result.failure(IllegalStateException("Tangkapan layar melewati batas waktu")))
            }
        }
        mainHandler.postDelayed(timeout, SCREENSHOT_TIMEOUT_MS)

        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainHandlerExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    if (!completed.compareAndSet(false, true)) {
                        result.hardwareBuffer.closeSafely()
                        return
                    }
                    mainHandler.removeCallbacks(timeout)
                    
                    try {
                        val buffer = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        runCatching { wrapped?.recycle() }
                        buffer.closeSafely()

                        if (bitmap == null) {
                            Log.w(CrashHandler.TAG_DEBUG, "Frame layar kosong dari HardwareBuffer")
                            callback(accessibilityTextOrFailure(IllegalStateException("Gagal membaca frame layar")))
                        } else {
                            serviceScope.launch {
                                val recognized = try {
                                    OcrHelper.recognizeText(bitmap)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (t: Throwable) {
                                    Log.e(CrashHandler.TAG_DEBUG, "Error di [OCR Pemindaian Layar]: ${t.message}", t)
                                    CrashHandler.recordCrash(this@MyAccessibilityService, "OCR Pemindaian Layar", t)
                                    Result.failure(Exception(t))
                                } finally {
                                    try {
                                        bitmap.recycle()
                                    } catch (e: Throwable) {
                                        Log.e(CrashHandler.TAG_DEBUG, "Error di [Recycle Bitmap]: ${e.message}", e)
                                    }
                                }

                                if (recognized.isFailure || recognized.getOrNull().isNullOrBlank()) {
                                    callback(accessibilityTextOrFailure(recognized.exceptionOrNull() ?: IllegalStateException("OCR kosong")))
                                } else {
                                    callback(recognized)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(CrashHandler.TAG_DEBUG, "Error di [Pemrosesan Bitmap Layar HardwareBuffer]: ${t.message}", t)
                        CrashHandler.recordCrash(this@MyAccessibilityService, "Pemrosesan Bitmap Layar HardwareBuffer", t)
                        result.hardwareBuffer.closeSafely()
                        callback(accessibilityTextOrFailure(Exception(t)))
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(CrashHandler.TAG_DEBUG, "Error di [Tangkapan Layar Accessibility onFailure]: kode $errorCode", null)
                    if (completed.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeout)
                        callback(accessibilityTextOrFailure(IllegalStateException("Tangkapan layar gagal (kode $errorCode)")))
                    }
                }
            })
        } catch (t: Throwable) {
            Log.e(CrashHandler.TAG_DEBUG, "Error di [Inisiasi Tangkapan Layar takeScreenshot]: ${t.message}", t)
            CrashHandler.recordCrash(this@MyAccessibilityService, "Inisiasi Tangkapan Layar takeScreenshot", t)
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                callback(accessibilityTextOrFailure(Exception(t)))
            }
        }
    }

    // PERBAIKAN #2: Memory Leak & Circular Reference pada Node Collection
    private fun collectNodeText(node: AccessibilityNodeInfo, output: MutableSet<String>, depth: Int, visited: MutableSet<Int> = mutableSetOf()) {
        if (depth > MAX_NODE_DEPTH || output.sumOf { it.length } >= MAX_CAPTURE_CHARS) return
        
        val nodeId = System.identityHashCode(node)
        if (visited.contains(nodeId)) return 
        visited.add(nodeId)

        try {
            val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            if (label.isNotEmpty()) output.add(label)
            
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    collectNodeText(child, output, depth + 1, visited)
                } finally {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) child.recycle()
                }
            }
        } finally {
            visited.remove(nodeId)
        }
    }

    private fun accessibilityTextOrFailure(error: Throwable): Result<String> {
        val text = runCatching { readActiveWindowText() }.getOrDefault("")
        return if (text.isNotBlank()) Result.success(text) else Result.failure(error)
    }

    private fun readActiveWindowText(): String {
        val root = targetApplicationRoot() ?: return ""
        val collected = LinkedHashSet<String>()
        collectNodeText(root, collected, depth = 0)
        return collected.joinToString("\n").take(MAX_CAPTURE_CHARS)
    }

    private fun fillFocusedText(text: String): Boolean {
        val root = targetApplicationRoot() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        val firstEditable = findFirstEditable(root, depth = 0) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return firstEditable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_NODE_DEPTH) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun clickOption(optionLetter: String, optionText: String, optionIndex: Int?): Boolean {
        val root = targetApplicationRoot() ?: return false
        val cleanLetter = optionLetter.trim().uppercase()
        val cleanText = optionText.trim()

        // 1. Search for clickable node with matching option text or letter prefix
        val found = findMatchingOptionNode(root, cleanLetter, cleanText, depth = 0)
        if (found != null) {
            val clicked = performClickOnNodeOrParent(found)
            if (clicked) return true
        }

        // 2. Fallback: match by index among radio buttons / check boxes
        if (optionIndex != null && optionIndex >= 0) {
            val selectionNodes = mutableListOf<AccessibilityNodeInfo>()
            collectSelectionNodes(root, selectionNodes, depth = 0)
            if (optionIndex < selectionNodes.size) {
                return performClickOnNodeOrParent(selectionNodes[optionIndex])
            }
        }
        return false
    }

    private fun findMatchingOptionNode(
        node: AccessibilityNodeInfo,
        letter: String,
        text: String,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > MAX_NODE_DEPTH) return null
        val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()

        if (label.isNotEmpty()) {
            val matchesLetter = letter.isNotEmpty() && (
                label.startsWith("$letter.", ignoreCase = true) ||
                label.startsWith("$letter)", ignoreCase = true) ||
                label.equals(letter, ignoreCase = true)
            )
            val matchesText = text.length > 3 && label.contains(text, ignoreCase = true)

            if (matchesLetter || matchesText) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val candidate = findMatchingOptionNode(child, letter, text, depth + 1)
            if (candidate != null) return candidate
        }
        return null
    }

    private fun collectSelectionNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (depth > MAX_NODE_DEPTH) return
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("RadioButton", ignoreCase = true) || cls.contains("CheckBox", ignoreCase = true)) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSelectionNodes(child, list, depth + 1)
        }
    }

    private fun clickNextButton(): Boolean {
        val root = targetApplicationRoot() ?: return false
        val keywords = listOf(
            "selanjutnya", "berikutnya", "lanjut", "next", "continue",
            "kirim", "submit", "selesai", "finish", "jawab", "simpan & lanjut"
        )
        return findAndClickButton(root, keywords, depth = 0)
    }

    private fun findAndClickButton(node: AccessibilityNodeInfo, keywords: List<String>, depth: Int): Boolean {
        if (depth > MAX_NODE_DEPTH) return false
        val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty().lowercase()
        val isButtonClass = node.className?.toString()?.contains("Button", ignoreCase = true) == true

        if (label.isNotEmpty() && keywords.any { label.contains(it) }) {
            if (performClickOnNodeOrParent(node)) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickButton(child, keywords, depth + 1)) return true
        }
        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var steps = 0
        while (current != null && steps < 5) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            steps++
        }
        // If not clickable directly, try action click anyway
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun scrollActiveWindowOnce(forward: Boolean): Boolean {
        val root = targetApplicationRoot() ?: return false
        val standardScrolled = runCatching { findScrollableAndScroll(root, forward, depth = 0) }.getOrDefault(false)
        if (standardScrolled) return true

        // Fallback: Dispatch Gesture Swipe
        return performGestureScroll(forward)
    }

    private fun performGestureScroll(forward: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat().coerceAtLeast(720f)
        val height = displayMetrics.heightPixels.toFloat().coerceAtLeast(1280f)

        val path = android.graphics.Path()
        val centerX = width / 2f
        if (forward) {
            // Swipe UP to scroll DOWN
            path.moveTo(centerX, height * 0.75f)
            path.lineTo(centerX, height * 0.25f)
        } else {
            // Swipe DOWN to scroll UP
            path.moveTo(centerX, height * 0.25f)
            path.lineTo(centerX, height * 0.75f)
        }

        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 350)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun targetApplicationRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() != packageName) return active
        return runCatching {
            windows.asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .firstOrNull { it.packageName?.toString() != packageName }
        }.getOrNull() ?: active
    }

    private fun findScrollableAndScroll(node: AccessibilityNodeInfo, forward: Boolean, depth: Int): Boolean {
        if (depth > MAX_NODE_DEPTH) return false
        if (node.isScrollable) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (node.performAction(action)) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (findScrollableAndScroll(child, forward, depth + 1)) return true
            } finally {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) child.recycle()
            }
        }
        return false
    }

    private val mainHandlerExecutor = java.util.concurrent.Executor { command -> mainHandler.post(command) }

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val SCREENSHOT_TIMEOUT_MS = 12_000L
        private const val MAX_CAPTURE_CHARS = 12_000
        private const val MAX_NODE_DEPTH = 40

        @Volatile
        private var connectedService: MyAccessibilityService? = null

        fun requestScreenText(callback: (Result<String>) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post { service.captureScreenshot(callback) }
            return true
        }

        fun isConnected(): Boolean = connectedService != null

        fun fillFocusedField(text: String, callback: (Boolean) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.fillFocusedText(text) }.getOrDefault(false))
            }
            return true
        }

        fun clickOptionNode(optionLetter: String, optionText: String, optionIndex: Int?, callback: (Boolean) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.clickOption(optionLetter, optionText, optionIndex) }.getOrDefault(false))
            }
            return true
        }

        fun clickNextButton(callback: (Boolean) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.clickNextButton() }.getOrDefault(false))
            }
            return true
        }

        fun scrollActiveWindowOnce(forward: Boolean, callback: (Boolean) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.scrollActiveWindowOnce(forward) }.getOrDefault(false))
            }
            return true
        }

        private fun HardwareBuffer.closeSafely() {
            runCatching { close() }
        }
    }
}
