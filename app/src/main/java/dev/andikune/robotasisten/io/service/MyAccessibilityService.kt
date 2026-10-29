package dev.andikune.robotasisten.io.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.accessibilityservice.GestureDescription
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
        } catch (se: SecurityException) {
            Log.w(
                CrashHandler.TAG_DEBUG,
                "SecurityException: Layanan accessibility belum memiliki kemampuan takeScreenshot (Perlu restart toggle service di Pengaturan HP). Mengalihkan otomatis ke pembacaan hierarki teks aktif.",
                se
            )
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                callback(accessibilityTextOrFailure(se))
            }
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

    private data class NodeInfoCompat(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val text: String,
        val className: String,
        val isClickable: Boolean,
        val isCheckable: Boolean,
        val isEditable: Boolean,
        val isEnabled: Boolean
    )

    private fun collectVisibleNodes(
        node: AccessibilityNodeInfo,
        list: MutableList<NodeInfoCompat>,
        depth: Int = 0
    ) {
        if (depth > MAX_NODE_DEPTH) return
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val isVisible = (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN || node.isVisibleToUser) &&
                rect.width() > 0 && rect.height() > 0

        if (isVisible) {
            val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            val cls = node.className?.toString().orEmpty()
            list.add(
                NodeInfoCompat(
                    node = node,
                    bounds = rect,
                    text = label,
                    className = cls,
                    isClickable = node.isClickable,
                    isCheckable = node.isCheckable,
                    isEditable = node.isEditable,
                    isEnabled = node.isEnabled
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleNodes(child, list, depth + 1)
        }
    }

    private fun performTapGesture(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    private fun performClickWithPrecision(node: AccessibilityNodeInfo, bounds: Rect? = null): Boolean {
        val rect = bounds ?: Rect().also { node.getBoundsInScreen(it) }
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        // 1. Accessibility click on node or clickable parent
        var current: AccessibilityNodeInfo? = node
        var steps = 0
        var accessibilityClicked = false
        while (current != null && steps < 5) {
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    accessibilityClicked = true
                    break
                }
            }
            current = current.parent
            steps++
        }

        if (!accessibilityClicked) {
            accessibilityClicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // 2. Hardware-level physical touch coordinate simulation at exact center (X-center, Y-center)
        if (rect.width() > 0 && rect.height() > 0) {
            performTapGesture(centerX, centerY)
            return true
        }

        return accessibilityClicked
    }

    private fun fillFocusedText(text: String, questionText: String? = null): Boolean {
        val root = targetApplicationRoot() ?: return false
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        val visibleNodes = mutableListOf<NodeInfoCompat>()
        collectVisibleNodes(root, visibleNodes, depth = 0)

        // Identify question boundary Y if questionText is provided
        var questionMinY = (screenHeight * 0.10).toInt()
        if (!questionText.isNullOrBlank()) {
            val qSnippet = questionText.take(40).trim().lowercase()
            val qNode = visibleNodes.firstOrNull { it.text.lowercase().contains(qSnippet) }
            if (qNode != null) {
                questionMinY = (qNode.bounds.bottom - 20).coerceAtLeast(questionMinY)
            }
        }

        // Find editable candidates below the question
        val editableCandidates = visibleNodes.filter {
            (it.isEditable || it.className.contains("EditText", true) || it.className.contains("TextInput", true)) &&
            it.bounds.width() > 80 && it.bounds.height() > 30 &&
            it.bounds.centerY() >= questionMinY
        }

        val target = editableCandidates.firstOrNull() ?: visibleNodes.firstOrNull {
            (it.isEditable || it.className.contains("EditText", true)) &&
            it.bounds.centerY() > (screenHeight * 0.15)
        }

        if (target != null) {
            val rect = target.bounds
            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()

            // Focus and tap center to activate keyboard/input field
            target.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            performTapGesture(centerX, centerY)

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setSuccess = target.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            if (!setSuccess) {
                target.node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
            return true
        }

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return false
    }

    private fun clickOption(
        optionLetter: String,
        optionText: String,
        optionIndex: Int?,
        questionText: String? = null
    ): Boolean {
        val root = targetApplicationRoot() ?: return false
        val cleanLetter = optionLetter.trim().uppercase()
        val cleanText = optionText.trim()
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        val visibleNodes = mutableListOf<NodeInfoCompat>()
        collectVisibleNodes(root, visibleNodes, depth = 0)

        if (visibleNodes.isEmpty()) return false

        // Identify question boundary Y to avoid selecting headers or previous questions
        var questionMinY = 0
        if (!questionText.isNullOrBlank()) {
            val qSnippet = questionText.take(40).trim().lowercase()
            val qNode = visibleNodes.firstOrNull { it.text.lowercase().contains(qSnippet) }
            if (qNode != null) {
                questionMinY = (qNode.bounds.bottom - 40).coerceAtLeast(0)
            }
        }

        val candidates = visibleNodes.filter {
            it.bounds.bottom > (screenHeight * 0.08) &&
            it.bounds.centerY() >= questionMinY
        }

        // 1. Search for matching text node
        val matchingTextNodes = candidates.filter { candidate ->
            val label = candidate.text
            if (label.isBlank()) return@filter false

            val matchesLetter = cleanLetter.isNotEmpty() && (
                label.startsWith("$cleanLetter.", ignoreCase = true) ||
                label.startsWith("$cleanLetter)", ignoreCase = true) ||
                label.startsWith("$cleanLetter ", ignoreCase = true) ||
                label.equals(cleanLetter, ignoreCase = true) ||
                "^(?:Pilihan\\s*)?$cleanLetter[.):\\s]".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(label)
            )

            val matchesText = cleanText.length > 2 && label.contains(cleanText, ignoreCase = true)
            matchesLetter || matchesText
        }

        val bestTextCandidate = matchingTextNodes.maxByOrNull { candidate ->
            var score = 0
            if (cleanLetter.isNotEmpty() && candidate.text.startsWith(cleanLetter, ignoreCase = true)) score += 50
            if (cleanText.isNotEmpty() && candidate.text.contains(cleanText, ignoreCase = true)) score += 50
            score
        }

        if (bestTextCandidate != null) {
            // Check A: RadioButton / CheckBox on the same horizontal row (within 45px Y-axis)
            val rowTarget = candidates.firstOrNull { other ->
                other != bestTextCandidate &&
                Math.abs(other.bounds.centerY() - bestTextCandidate.bounds.centerY()) <= 45 &&
                (other.isCheckable || other.className.contains("Radio", true) || other.className.contains("Check", true) || other.isClickable)
            }

            if (rowTarget != null) {
                Log.i(CrashHandler.TAG_DEBUG, "Target opsi presisi (Row sibling): ${rowTarget.className} di (${rowTarget.bounds.centerX()}, ${rowTarget.bounds.centerY()})")
                return performClickWithPrecision(rowTarget.node, rowTarget.bounds)
            }

            // Check B: Look for parent container or parent's checkable children
            var parent = bestTextCandidate.node.parent
            var pSteps = 0
            while (parent != null && pSteps < 3) {
                val pRect = Rect()
                parent.getBoundsInScreen(pRect)
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    val cCls = child.className?.toString().orEmpty()
                    if (child != bestTextCandidate.node && (cCls.contains("Radio", true) || cCls.contains("Check", true) || child.isCheckable)) {
                        val cRect = Rect()
                        child.getBoundsInScreen(cRect)
                        Log.i(CrashHandler.TAG_DEBUG, "Target opsi presisi (Parent child): $cCls di (${cRect.centerX()}, ${cRect.centerY()})")
                        return performClickWithPrecision(child, cRect)
                    }
                }
                if (parent.isClickable) {
                    Log.i(CrashHandler.TAG_DEBUG, "Target opsi presisi (Clickable parent) di (${pRect.centerX()}, ${pRect.centerY()})")
                    return performClickWithPrecision(parent, pRect)
                }
                parent = parent.parent
                pSteps++
            }

            // Check C: Click the text candidate itself
            Log.i(CrashHandler.TAG_DEBUG, "Target opsi presisi (Text node) di (${bestTextCandidate.bounds.centerX()}, ${bestTextCandidate.bounds.centerY()})")
            return performClickWithPrecision(bestTextCandidate.node, bestTextCandidate.bounds)
        }

        // 2. Fallback: match by index among radio buttons / check boxes in content area
        if (optionIndex != null && optionIndex >= 0) {
            val selectionNodes = candidates.filter {
                it.className.contains("RadioButton", ignoreCase = true) ||
                it.className.contains("CheckBox", ignoreCase = true) ||
                it.isCheckable
            }
            if (optionIndex < selectionNodes.size) {
                val target = selectionNodes[optionIndex]
                Log.i(CrashHandler.TAG_DEBUG, "Target opsi presisi fallback index [$optionIndex] di (${target.bounds.centerX()}, ${target.bounds.centerY()})")
                return performClickWithPrecision(target.node, target.bounds)
            }
        }

        return false
    }

    private fun clickNextButton(): Boolean {
        val root = targetApplicationRoot() ?: return false
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        val visibleNodes = mutableListOf<NodeInfoCompat>()
        collectVisibleNodes(root, visibleNodes, depth = 0)

        val navKeywords = listOf(
            "selanjutnya", "berikutnya", "lanjut", "next", "continue",
            "kirim", "submit", "selesai", "finish", "jawab", "simpan & lanjut",
            "simpan dan lanjutkan", "soal berikutnya", "simpan", "save & next"
        )

        // Filter out top 20% (toolbar, search, back button)
        val candidates = visibleNodes.filter {
            it.bounds.width() > 0 && it.bounds.height() > 0 &&
            it.bounds.bottom > (screenHeight * 0.20)
        }

        val scored = candidates.mapNotNull { candidate ->
            val textLower = candidate.text.lowercase()
            val clsLower = candidate.className.lowercase()
            val nodeDesc = candidate.node.contentDescription?.toString().orEmpty().lowercase()
            val viewId = candidate.node.viewIdResourceName?.lowercase().orEmpty()

            var score = 0

            for (kw in navKeywords) {
                if (textLower == kw) {
                    score += 120
                    break
                } else if (textLower.contains(kw)) {
                    score += 80
                    break
                } else if (nodeDesc.contains(kw)) {
                    score += 70
                    break
                } else if (viewId.contains(kw) || viewId.contains("btn_next") || viewId.contains("next")) {
                    score += 60
                    break
                }
            }

            if (nodeDesc.contains("chevron_right") || nodeDesc.contains("arrow_forward") ||
                textLower.contains(">") || textLower.contains("→") || viewId.contains("forward")) {
                score += 50
            }

            // Prioritize buttons in lower half / bottom 30% of screen
            if (candidate.bounds.centerY() > screenHeight * 0.70) {
                score += 60
            } else if (candidate.bounds.centerY() > screenHeight * 0.50) {
                score += 35
            } else {
                score -= 30
            }

            if (candidate.isClickable) score += 25
            if (clsLower.contains("button") || clsLower.contains("fab")) score += 25

            if (score >= 60) candidate to score else null
        }

        val best = scored.maxByOrNull { it.second }?.first
        if (best != null) {
            Log.i(CrashHandler.TAG_DEBUG, "Tombol navigasi presisi: '${best.text}' [${best.className}] di (${best.bounds.centerX()}, ${best.bounds.centerY()})")
            return performClickWithPrecision(best.node, best.bounds)
        }

        return false
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

        fun fillFocusedField(text: String, questionText: String? = null, callback: (Boolean) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.fillFocusedText(text, questionText) }.getOrDefault(false))
            }
            return true
        }

        fun clickOptionNode(
            optionLetter: String,
            optionText: String,
            optionIndex: Int?,
            questionText: String? = null,
            callback: (Boolean) -> Unit
        ): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.clickOption(optionLetter, optionText, optionIndex, questionText) }.getOrDefault(false))
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
