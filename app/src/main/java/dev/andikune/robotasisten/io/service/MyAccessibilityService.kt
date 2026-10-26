package dev.andikune.robotasisten.io.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.OcrHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Screen access is performed only when the user explicitly asks the overlay to read a screen
 * or fill a focused field. No screen text is retained between requests.
 */
class MyAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedService = this
        LogHelper.i(TAG, "Accessibility screen actions connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connectedService === this) connectedService = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun requestScreenText(callback: (Result<String>) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureScreenshot(callback)
        } else {
            callback(runCatching { readActiveWindowText() })
        }
    }

    private fun captureScreenshot(callback: (Result<String>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(runCatching { readActiveWindowText() })
            return
        }

        val completed = AtomicBoolean(false)
        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) {
                callback(accessibilityTextOrFailure(IllegalStateException("Tangkapan layar melewati batas waktu")))
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
                            callback(Result.failure(IllegalStateException("Gagal membaca frame layar")))
                        } else {
                            serviceScope.launch {
                                val recognized = try {
                                    OcrHelper.recognizeText(bitmap)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Result.failure(error)
                                } finally {
                                    bitmap.recycle()
                                }
                                val finalResult = if (recognized.getOrNull().isNullOrBlank()) {
                                    accessibilityTextOrFailure(IllegalStateException("OCR tidak menemukan teks"))
                                } else recognized
                                callback(finalResult)
                            }
                        }
                    } catch (error: Exception) {
                        result.hardwareBuffer.closeSafely()
                        callback(Result.failure(error))
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (completed.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeout)
                        callback(accessibilityTextOrFailure(IllegalStateException("Tangkapan layar gagal (kode $errorCode)")))
                    }
                }
            })
        } catch (error: Exception) {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                callback(accessibilityTextOrFailure(error))
            }
        }
    }

    private fun accessibilityTextOrFailure(error: Throwable): Result<String> {
        val text = runCatching { readActiveWindowText() }.getOrDefault("")
        return if (text.isNotBlank()) Result.success(text) else Result.failure(error)
    }

    private fun readActiveWindowText(): String {
        val root = rootInActiveWindow ?: return ""
        val collected = LinkedHashSet<String>()
        collectNodeText(root, collected, depth = 0)
        return collected.joinToString("\n").take(MAX_CAPTURE_CHARS)
    }

    private fun collectNodeText(node: AccessibilityNodeInfo, output: MutableSet<String>, depth: Int) {
        if (depth > MAX_NODE_DEPTH || output.sumOf { it.length } >= MAX_CAPTURE_CHARS) return
        val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        if (label.isNotEmpty()) output.add(label)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectNodeText(child, output, depth + 1)
            } finally {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) child.recycle()
            }
        }
    }

    private fun fillFocusedText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!focused.isEditable) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private val mainHandlerExecutor = java.util.concurrent.Executor { command -> mainHandler.post(command) }

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val SCREENSHOT_TIMEOUT_MS = 12_000L
        private const val MAX_CAPTURE_CHARS = 12_000
        private const val MAX_NODE_DEPTH = 40

        @Volatile
        private var connectedService: MyAccessibilityService? = null

        /** Returns false if accessibility is not enabled; otherwise calls back exactly once. */
        fun requestScreenText(callback: (Result<String>) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post { service.requestScreenText(callback) }
            return true
        }

        /** Returns false when no enabled service or editable focused target is available. */
        fun fillFocusedField(text: String, callback: (Boolean) -> Unit): Boolean {
            val service = connectedService ?: return false
            service.mainHandler.post {
                callback(runCatching { service.fillFocusedText(text) }.getOrDefault(false))
            }
            return true
        }

        private fun HardwareBuffer.closeSafely() {
            runCatching { close() }
        }
    }
}
