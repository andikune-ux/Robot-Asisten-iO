package dev.andikune.robotasisten.io.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.andikune.robotasisten.io.MainActivity
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.logic.AutoFillResult
import dev.andikune.robotasisten.io.logic.AutoFillEngine
import dev.andikune.robotasisten.io.logic.DetectedQuestion
import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.logic.ResolutionResult
import dev.andikune.robotasisten.io.data.ai.RelxChatStore
import dev.andikune.robotasisten.io.navigation.Routes
import dev.andikune.robotasisten.io.ui.overlay.AnalysisPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.AnswerPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.BubbleStatus
import dev.andikune.robotasisten.io.ui.overlay.OverlayBubble
import dev.andikune.robotasisten.io.ui.overlay.OverlayMenuPanel
import dev.andikune.robotasisten.io.ui.overlay.OcrPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.PowerTaskPanel
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.util.HapticHelper
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.PermissionHelper
import dev.andikune.robotasisten.io.util.SoundHelper
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import android.view.inputmethod.InputMethodManager
import android.util.DisplayMetrics
import kotlin.math.min

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val overlayViewModelStore = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // PERBAIKAN #6: Gunakan AtomicLong untuk mencegah race condition operation ID
    private val operationId = java.util.concurrent.atomic.AtomicLong(0)
    private var operationJob: Job? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = overlayViewModelStore

    private var windowManager: WindowManager? = null
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    
    private val bubbleStatus = mutableStateOf(BubbleStatus.IDLE)
    private val isMenuOpen = mutableStateOf(false)
    private val isPowerOpen = mutableStateOf(false)
    private val isPanelDismissed = mutableStateOf(false)
    private val powerPrompt = mutableStateOf("")
    private val powerResult = mutableStateOf("")
    private val powerStatus = mutableStateOf("")
    private val isPowerBusy = mutableStateOf(false)
    private val autoReadContinuations = mutableStateOf(true)
    private val currentAnalysis = mutableStateOf<DetectedQuestion?>(null)
    private val currentAnswer = mutableStateOf<ResolutionResult?>(null)
    private val currentOcrText = mutableStateOf<ScreenReadResult?>(null)
    
    private data class ScreenReadResult(val text: String, val source: String)

    // PERBAIKAN #9: Lifecycle Management yang Aman di onCreate
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        createNotificationChannel()
        
        try {
            startForeground(Constants.NOTIFICATION_ID, buildNotification("Overlay siap"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                throw SecurityException("Izin tampil di atas aplikasi lain belum diberikan")
            }
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            if (!setupOverlayViews()) {
                throw IllegalStateException("Bubble tidak dapat dipasang")
            }
            
            // Lifecycle HANYA dilanjutkan jika setup berhasil
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            
            isRunning.value = true
            QuickSettingsTile.requestRefresh(this)
            LogHelper.i(TAG, "Overlay started successfully")
        } catch (error: Exception) {
            isRunning.value = false
            LogHelper.e(TAG, "Overlay startup failed", error)
            ToastHelper.showShort(this, error.message ?: "Overlay tidak dapat dimulai")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    private fun setupOverlayViews(): Boolean {
        val manager = windowManager ?: return false
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 250
        }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val size by settings.overlaySizeDp.collectAsState()
                val opacity by settings.overlayOpacity.collectAsState()
                RAiOTheme {
                    OverlayBubble(
                        status = bubbleStatus.value,
                        sizeDp = size,
                        opacity = opacity,
                        onTap = {
                            runCatching {
                                performClickFeedback()
                                val hasResult = currentAnalysis.value != null || currentAnswer.value != null || currentOcrText.value != null || isPowerOpen.value || powerResult.value.isNotBlank()
                                if (isMenuOpen.value) {
                                    isMenuOpen.value = false
                                    isPanelDismissed.value = true
                                } else if (hasResult) {
                                    isPanelDismissed.value = !isPanelDismissed.value
                                    if (isPowerOpen.value || powerResult.value.isNotBlank()) {
                                        isPowerOpen.value = !isPanelDismissed.value
                                        setPowerPanelFocusable(!isPanelDismissed.value)
                                    }
                                } else {
                                    isMenuOpen.value = true
                                    isPanelDismissed.value = false
                                    updatePanelPosition()
                                }
                                updatePanelVisibility()
                            }.onFailure { error ->
                                LogHelper.e(TAG, "Bubble tap action failed", error)
                                ToastHelper.showShort(this@OverlayService, "Bubble gagal dibuka: ${error.message ?: "lihat laporan crash"}")
                            }
                        },
                        onDoubleTap = { triggerAnalyze(autoAnswer = false) },
                        onLongPress = {
                            runCatching {
                                performClickFeedback()
                                isMenuOpen.value = true
                                isPanelDismissed.value = false
                                updatePanelPosition()
                                updatePanelVisibility()
                            }.onFailure { error ->
                                LogHelper.e(TAG, "Bubble long-press action failed", error)
                                ToastHelper.showShort(this@OverlayService, "Menu overlay gagal dibuka")
                            }
                        },
                        onDrag = { dx, dy ->
                            bubbleParams.x += dx.toInt()
                            bubbleParams.y += dy.toInt()
                            try {
                                if (bubbleView?.parent != null) manager.updateViewLayout(bubbleView, bubbleParams)
                                if (panelView?.parent != null && !isPanelDismissed.value) updatePanelPosition()
                            } catch (error: Exception) {
                                LogHelper.e(TAG, "Bubble layout update failed", error)
                            }
                        }
                    )
                }
            }
        }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12
            y = 12
        }

        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val activeMode by settings.activeMode.collectAsState()
                val confirmBeforeFill by settings.confirmBeforeFill.collectAsState()
                val answer = currentAnswer.value
                val question = currentAnalysis.value
                val ocrText = currentOcrText.value
                RAiOTheme {
                    when {
                        answer != null -> AnswerPreviewCard(
                            result = answer,
                            questionSummary = question?.questionText ?: "Soal",
                            allowFill = answer.source != dev.andikune.robotasisten.io.logic.FallbackChain.SOURCE_UNAVAILABLE,
                            onFillAnswer = { fillAnswerNow(answer.answer) },
                            onEditAnswer = { edited ->
                                currentAnswer.value = answer.copy(answer = edited, source = "Diedit pengguna")
                                ToastHelper.showShort(this@OverlayService, "Perubahan jawaban disimpan")
                            },
                            onCancel = { clearResult() }
                        )
                        ocrText != null -> OcrPreviewCard(
                            text = ocrText.text,
                            source = ocrText.source,
                            onCopy = {
                                runCatching { ClipboardHelper.copyToClipboard(this@OverlayService, "RELX.iO OCR", ocrText.text) }
                                .onSuccess { ToastHelper.showShort(this@OverlayService, "Teks OCR disalin ke clipboard") }
                                .onFailure { ToastHelper.showShort(this@OverlayService, "Gagal menyalin teks OCR") }
                            },
                            onClose = { clearResult() }
                        )
                        question != null -> AnalysisPreviewCard(
                            detectedQuestion = question,
                            onAnswerNow = { triggerAnswerNow(question) },
                            onReanalyze = { triggerAnalyze(autoAnswer = false) },
                            onCancel = { clearResult() },
                            isAnswering = bubbleStatus.value == BubbleStatus.ANSWERING
                        )
                        isPowerOpen.value || powerResult.value.isNotBlank() -> PowerTaskPanel(
                            prompt = powerPrompt.value,
                            result = powerResult.value,
                            isBusy = isPowerBusy.value,
                            status = powerStatus.value,
                            autoReadContinuations = autoReadContinuations.value,
                            onAutoReadContinuationsChange = { autoReadContinuations.value = it },
                            onPromptChange = { powerPrompt.value = it },
                            onConfirmTask = { triggerPowerAnalyze() },
                            onAnalyzeAndAnswer = {
                                isPowerOpen.value = false
                                setPowerPanelFocusable(false)
                                triggerAnalyze(autoAnswer = true)
                            },
                            onClose = {
                                isPowerOpen.value = false
                                setPowerPanelFocusable(false)
                                isPanelDismissed.value = true
                                updatePanelVisibility()
                            }
                        )
                        isMenuOpen.value -> OverlayMenuPanel(
                            activeMode = activeMode,
                            confirmBeforeFill = confirmBeforeFill,
                            onAnalyze = { triggerAnalyze(autoAnswer = true) },
                            onAutoAnswer = { triggerAnalyze(autoAnswer = true) },
                            onOcrRead = { triggerReadOcr() },
                            onOpenPower = {
                                isMenuOpen.value = false
                                isPowerOpen.value = true
                                setPowerPanelFocusable(true)
                                isPanelDismissed.value = false
                                powerPrompt.value = ""
                                powerResult.value = ""
                                powerStatus.value = ""
                                autoReadContinuations.value = true
                                updatePanelVisibility()
                            },
                            onOpenHistory = { isMenuOpen.value = false; updatePanelVisibility(); openApp(Routes.HISTORY) },
                            onOpenSettings = { isMenuOpen.value = false; updatePanelVisibility(); openApp(Routes.SETTINGS) },
                            onHideBubble = {
                                if (!PermissionHelper.hasNotificationPermission(this@OverlayService)) {
                                    ToastHelper.showShort(this@OverlayService, "Izinkan notifikasi sebelum menyembunyikan bubble agar kontrol pemulihan tersedia.")
                                } else {
                                    isMenuOpen.value = false
                                    bubbleView?.visibility = View.GONE
                                    updatePanelVisibility()
                                    ToastHelper.showShort(this@OverlayService, "Bubble disembunyikan. Tampilkan kembali dari notifikasi RELX.iO.")
                                }
                            },
                            onTurnOffRobot = { stopSelf() },
                            onClose = {
                                isMenuOpen.value = false
                                isPanelDismissed.value = true
                                updatePanelVisibility()
                            }
                        )
                    }
                }
            }
        }

        return try {
            manager.addView(bubbleView, bubbleParams)
            bubbleView?.parent != null
        } catch (error: Exception) {
            LogHelper.e(TAG, "Failed to add bubble view", error)
            runCatching { bubbleView?.disposeComposition() }
            bubbleView = null
            false
        }
    }

    private fun updatePanelVisibility() {
        val manager = windowManager ?: return
        val panel = panelView ?: return
        val hasResult = currentAnalysis.value != null || currentAnswer.value != null || currentOcrText.value != null || powerResult.value.isNotBlank()
        val shouldShow = !isPanelDismissed.value && (isMenuOpen.value || isPowerOpen.value || hasResult)
        try {
            if (shouldShow) updatePanelPosition()
            if (shouldShow && panel.parent == null) {
                manager.addView(panel, panelParams)
            } else if (!shouldShow && panel.parent != null) {
                manager.removeView(panel)
            }
        } catch (error: Exception) {
            LogHelper.e(TAG, "Panel visibility update failed", error)
            if (shouldShow) ToastHelper.showShort(this, "Panel overlay gagal dibuka; periksa izin overlay")
        }
    }

    private fun updatePanelPosition() {
        val manager = windowManager ?: return
        if (!::panelParams.isInitialized) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        runCatching { manager.defaultDisplay.getRealMetrics(metrics) }
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val screenWidth = (metrics.widthPixels.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).coerceAtLeast(1)
        val screenHeight = (metrics.heightPixels.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels).coerceAtLeast(1)
        val margin = (12f * density).toInt()
        val gap = (8f * density).toInt()
        val panelWidth = minOf((320f * density).toInt(), (screenWidth - margin * 2).coerceAtLeast(1))
        val panelHeight = minOf((600f * density).toInt(), (screenHeight - margin * 2).coerceAtLeast(1))
        val bubbleDp = runCatching { AppModule.provideSettingsRepository(this).overlaySizeDp.value }.getOrDefault(56)
        val bubbleSizePx = (bubbleDp * density).toInt()
        val rightX = bubbleParams.x + bubbleSizePx + gap
        val leftX = bubbleParams.x - panelWidth - gap
        val wantedX = if (rightX + panelWidth <= screenWidth - margin) rightX else leftX
        val wantedY = if (bubbleParams.y + panelHeight <= screenHeight - margin) bubbleParams.y else screenHeight - panelHeight - margin
        panelParams.apply {
            gravity = Gravity.TOP or Gravity.START
            width = panelWidth
            height = panelHeight
            x = wantedX.coerceIn(margin, (screenWidth - panelWidth - margin).coerceAtLeast(margin))
            y = wantedY.coerceIn(margin, (screenHeight - panelHeight - margin).coerceAtLeast(margin))
        }
        if (panelView?.parent != null) runCatching { manager.updateViewLayout(panelView, panelParams) }
        .onFailure { LogHelper.e(TAG, "Panel reposition failed", it) }
    }

    private fun setPowerPanelFocusable(focusable: Boolean) {
        val panel = panelView ?: return
        if (!::panelParams.isInitialized) return
        val notFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        panelParams.flags = if (focusable) panelParams.flags and notFocusable.inv() else panelParams.flags or notFocusable
        runCatching {
            if (panel.parent != null) windowManager?.updateViewLayout(panel, panelParams)
        }.onFailure { LogHelper.e(TAG, "Could not update Power input focus", it) }
    }

    // PERBAIKAN #5: State Restoration yang Robust di readScreenOrClipboard
    private suspend fun readScreenOrClipboard(): ScreenReadResult? {
        var accessibilityWasAvailable = false
        if (PermissionHelper.isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            accessibilityWasAvailable = true
            val previousBubbleAlpha = bubbleView?.alpha
            val previousPanelVisibility = panelView?.visibility
            
            bubbleView?.alpha = 0.28f
            panelView?.visibility = View.GONE
            
            try {
                runCatching {
                    val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    panelView?.windowToken?.let { inputManager.hideSoftInputFromWindow(it, 0) }
                }
                delay(100)
                
                val result = CompletableDeferred<Result<String>>()
                val accepted = MyAccessibilityService.requestScreenText { screenResult ->
                    result.complete(screenResult)
                }
                
                if (accepted) {
                    val screenText = withTimeoutOrNull(SCREEN_READ_TIMEOUT_MS) { result.await() }
                        ?.getOrNull()?.trim()
                    if (!screenText.isNullOrBlank()) return ScreenReadResult(screenText, "Layar (OCR/Aksesibilitas)")
                    LogHelper.w(TAG, "Screen OCR returned no text; trying clipboard")
                } else {
                    LogHelper.w(TAG, "Accessibility service is not connected; trying clipboard")
                }
            } finally {
                // PERBAIKAN #5: Null safety check sebelum restore state
                runCatching {
                    if (previousBubbleAlpha != null && bubbleView?.parent != null) {
                        bubbleView?.alpha = previousBubbleAlpha
                    }
                    if (previousPanelVisibility != null && panelView?.parent != null) {
                        panelView?.visibility = previousPanelVisibility
                    }
                }.onFailure { LogHelper.e(TAG, "Gagal memulihkan state UI setelah OCR", it) }
            }
        }

        val clipboardText = runCatching { ClipboardHelper.getClipboardText(this) }
            .onFailure { LogHelper.e(TAG, "Clipboard read failed", it) }
            .getOrNull()?.trim()
        return clipboardText?.takeIf { it.isNotBlank() }?.let {
            ScreenReadResult(it, if (accessibilityWasAvailable) "Clipboard fallback" else "Clipboard")
        }
    }

    // ... LANJUTKAN KE BAGIAN 2 DI BAWAH INI ...
}
