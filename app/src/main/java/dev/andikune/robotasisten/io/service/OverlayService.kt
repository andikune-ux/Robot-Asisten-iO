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
import kotlinx.coroutines.job // PERBAIKAN: Import ini wajib agar .job dikenali
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

    private suspend fun readPowerScreen(): ScreenReadResult? {
        val first = readScreenOrClipboard() ?: return null
        if (!autoReadContinuations.value || !PermissionHelper.isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) return first
        val pages = mutableListOf(first.text.trim())
        var successfulScrolls = 0
        try {
            val seenPages = mutableSetOf<String>()
            seenPages += pages.first().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("")
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!scrollTargetWindow(forward = true)) break
                successfulScrolls++
                delay(220)
                val next = readScreenOrClipboard()?.text?.trim().orEmpty()
                if (next.isBlank()) break
                val normalized = next.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("")
                val previous = pages.last().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("")
                if (normalized.isBlank() || normalized == previous || !seenPages.add(normalized)) break
                pages += next
            }
        } finally {
            repeat(successfulScrolls) {
                if (scrollTargetWindow(forward = false)) delay(160)
            }
        }
        val combined = pages.distinct().joinToString("\n\n[LANJUTAN LAYAR]\n\n").take(MAX_POWER_CAPTURE_CHARS)
        return ScreenReadResult(combined, if (pages.size > 1) "Layar (hingga ${pages.size} tampilan; posisi dipulihkan)" else first.source)
    }

    private suspend fun scrollTargetWindow(forward: Boolean): Boolean {
        val result = CompletableDeferred<Boolean>()
        val accepted = MyAccessibilityService.scrollActiveWindowOnce(forward) { didScroll ->
            if (!result.isCompleted) result.complete(didScroll)
        }
        if (!accepted) return false
        return withTimeoutOrNull(2_000L) { result.await() } ?: false
    }

    private fun triggerPowerAnalyze() {
        if (isPowerBusy.value) return
        operationJob?.cancel()
        val thisOperation = operationId.incrementAndGet()
        isPowerBusy.value = true
        bubbleStatus.value = BubbleStatus.ANALYZING
        powerResult.value = ""
        powerStatus.value = if (autoReadContinuations.value) "Membaca layar dan seluruh sambungannya sampai tidak ada konten baru…" else "Membaca layar dan menyiapkan konteks…"
        isPanelDismissed.value = false
        updatePanelVisibility()
        operationJob = serviceScope.launch {
            try {
                val readResult = readPowerScreen()
                if (thisOperation != operationId.get()) return@launch
                if (readResult == null || readResult.text.isBlank()) {
                    powerStatus.value = "Tidak ada teks layar. Aktifkan Aksesibilitas RELX.iO atau salin teks terlebih dahulu."
                    return@launch
                }
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val mode = settings.activeMode.value
                if (thisOperation != operationId.get()) return@launch
                if (mode.uppercase() !in setOf("AI_OFFLINE", "AI_OPEN_KEY")) {
                    powerStatus.value = "Pilih AI Offline atau AI API Key sebagai mode aktif terlebih dahulu."
                    return@launch
                }
                val profile = RelxChatStore(this@OverlayService).profile()
                val task = powerPrompt.value.trim().ifBlank { "AUTO_LANGUAGE: Analyze the actual question/task visible on screen and suggest the next safe step in the language of that screen content." }
                val prompt = buildString {
                    appendLine("Tugas pengguna: $task")
                    appendLine("Sumber konteks: ${readResult.source}")
                    appendLine("Teks layar:")
                    append(readResult.text.take(12_000))
                    appendLine("\nGabungkan bagian layar yang berlanjut, fokus pada tugas pengguna, dan jawab dalam bahasanya. Berikan analisa serta rencana yang bisa diperiksa. Jangan mengklaim telah melakukan aksi. Jangan menyuruh aplikasi menekan tombol Kirim/Submit, menghapus data, membayar, atau mengubah pengaturan tanpa konfirmasi manusia.")
                }
                val reply = AppModule.provideAiRepository(this@OverlayService).chatReply(
                    mode = mode,
                    history = emptyList(),
                    userMessage = prompt,
                    persona = profile.persona,
                    memory = profile.memory,
                    instructions = profile.instructions,
                    reasoningDepth = profile.reasoningDepth
                )
                if (thisOperation != operationId.get()) return@launch
                if (reply.isNullOrBlank()) {
                    powerResult.value = "AI tidak memberi hasil. Periksa model, provider, API key, jaringan, dan mode yang aktif."
                    powerStatus.value = "Analisa gagal; belum ada saran tindakan."
                } else {
                    powerResult.value = reply
                    powerStatus.value = "Analisa selesai. Teks tambahan dibaca otomatis jika dapat digulir; posisi layar dipulihkan."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LogHelper.e(TAG, "RELX Power analysis failed", error)
                powerStatus.value = "Analisa gagal: ${error.message ?: "coba lagi"}"
            } finally {
                if (thisOperation == operationId.get()) {
                    isPowerBusy.value = false
                    bubbleStatus.value = BubbleStatus.IDLE
                    updatePanelVisibility()
                }
            }
        }
    }

    private fun triggerAnalyze(autoAnswer: Boolean) {
        operationJob?.cancel()
        val thisOperation = operationId.incrementAndGet()
        setPowerPanelFocusable(false)
        currentAnalysis.value = null
        currentAnswer.value = null
        currentOcrText.value = null
        powerResult.value = ""
        isMenuOpen.value = false
        isPowerOpen.value = false
        isPanelDismissed.value = false
        updatePanelVisibility()
        bubbleStatus.value = BubbleStatus.ANALYZING
        performClickFeedback()
        operationJob = serviceScope.launch {
            val previousVisibility = bubbleView?.visibility ?: View.VISIBLE
            try {
                ToastHelper.showShort(this@OverlayService, "Membaca layar; jika aksesibilitas mati, teks clipboard akan dicoba")
                val readResult = readScreenOrClipboard()
                if (thisOperation != operationId.get()) return@launch
                if (readResult == null || readResult.text.isBlank()) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Tidak ada teks terbaca. Aktifkan Aksesibilitas RELX.iO atau salin teks soal terlebih dahulu.")
                    updatePanelVisibility()
                    return@launch
                }
                val likelyQuestion = QuestionDetector.isLikelyQuestion(readResult.text)
                val detected = QuestionDetector.detect(readResult.text).copy(confidence = if (likelyQuestion) 0.9f else 0.45f)
                currentAnalysis.value = detected
                bubbleStatus.value = BubbleStatus.IDLE
                updatePanelVisibility()
                ToastHelper.showShort(this@OverlayService, "Teks dibaca dari ${readResult.source}")
                if (!isSilentModeEnabled()) SoundHelper.playBeep()
                if (autoAnswer) triggerAnswerNow(detected)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (thisOperation == operationId.get()) {
                    LogHelper.e(TAG, "Question analysis failed", error)
                    currentAnalysis.value = null
                    currentAnswer.value = null
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Analisis gagal: ${error.message ?: "coba lagi"}")
                    updatePanelVisibility()
                }
            } finally {
                if (bubbleView?.parent != null && previousVisibility != View.GONE && bubbleView?.visibility == View.GONE) {
                    bubbleView?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun triggerReadOcr() {
        operationJob?.cancel()
        val thisOperation = operationId.incrementAndGet()
        setPowerPanelFocusable(false)
        currentAnalysis.value = null
        currentAnswer.value = null
        currentOcrText.value = null
        powerResult.value = ""
        isMenuOpen.value = false
        isPowerOpen.value = false
        isPanelDismissed.value = false
        updatePanelVisibility()
        bubbleStatus.value = BubbleStatus.ANALYZING
        operationJob = serviceScope.launch {
            try {
                val readResult = readScreenOrClipboard()
                if (thisOperation != operationId.get()) return@launch
                if (readResult == null || readResult.text.isBlank()) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Tidak ada teks OCR atau clipboard yang tersedia")
                } else {
                    currentOcrText.value = readResult
                    bubbleStatus.value = BubbleStatus.IDLE
                    updatePanelVisibility()
                    ToastHelper.showShort(this@OverlayService, "Teks dibaca dari ${readResult.source}")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (thisOperation == operationId.get()) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    LogHelper.e(TAG, "OCR read failed", error)
                    ToastHelper.showShort(this@OverlayService, "OCR gagal: ${error.message ?: "coba lagi"}")
                }
            }
        }
    }

    private fun triggerAnswerNow(question: DetectedQuestion) {
        operationJob?.cancel()
        val thisOperation = operationId.incrementAndGet()
        bubbleStatus.value = BubbleStatus.ANSWERING
        currentAnswer.value = null
        currentOcrText.value = null
        updatePanelVisibility()
        ToastHelper.showShort(this, "Memproses jawaban dengan mode ${AppModule.provideSettingsRepository(this).activeMode.value}")
        operationJob = serviceScope.launch {
            try {
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val fallbackChain = AppModule.provideFallbackChain(this@OverlayService)
                val historyRepo = AppModule.provideHistoryRepository(this@OverlayService)
                val mode = settings.activeMode.value
                val result = fallbackChain.resolve(question.questionText, mode, question.type)
                if (thisOperation != operationId.get()) return@launch
                currentAnswer.value = result
                bubbleStatus.value = BubbleStatus.READY
                updatePanelVisibility()
                if (!isSilentModeEnabled()) SoundHelper.playSuccess()
                if (result.source != dev.andikune.robotasisten.io.logic.FallbackChain.SOURCE_UNAVAILABLE) {
                    historyRepo.recordAnswer(
                        question = question.questionText,
                        answer = result.answer,
                        mode = mode,
                        questionType = question.type.name,
                        isCacheHit = result.isCacheHit
                    )
                }
                if (!settings.confirmBeforeFill.value && result.source != dev.andikune.robotasisten.io.logic.FallbackChain.SOURCE_UNAVAILABLE) {
                    fillAnswerNow(result.answer)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (thisOperation == operationId.get()) {
                    LogHelper.e(TAG, "Answer resolution failed", error)
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Jawaban gagal diproses: ${error.message ?: "coba lagi"}")
                }
            }
        }
    }

    private fun fillAnswerNow(answerText: String) {
        AutoFillEngine.fillAnswer(this, answerText) { result ->
            when (result) {
                AutoFillResult.FILLED -> {
                    ToastHelper.showShort(this, "Jawaban dimasukkan ke kolom yang sedang fokus")
                    if (!isSilentModeEnabled()) HapticHelper.vibrateSuccess(this)
                    currentAnswer.value = currentAnswer.value?.copy(source = "Sudah diisi")
                    updatePanelVisibility()
                }
                AutoFillResult.COPIED_TO_CLIPBOARD -> {
                    ToastHelper.showShort(this, "Jawaban disalin ke clipboard; tempel secara manual.")
                }
                AutoFillResult.FAILED -> ToastHelper.showShort(this, "Pengisian dan penyalinan jawaban gagal")
            }
        }
    }

    private fun performClickFeedback() {
        if (!isSilentModeEnabled()) HapticHelper.vibrateClick(this)
    }

    private fun isSilentModeEnabled(): Boolean =
        runCatching { AppModule.provideSettingsRepository(this).silentMode.value }.getOrDefault(false)

    private fun clearResult() {
        operationJob?.cancel()
        operationId.incrementAndGet()
        currentAnswer.value = null
        currentAnalysis.value = null
        currentOcrText.value = null
        powerResult.value = ""
        isPowerOpen.value = false
        setPowerPanelFocusable(false)
        isPanelDismissed.value = false
        isMenuOpen.value = false
        bubbleStatus.value = BubbleStatus.IDLE
        updatePanelVisibility()
    }

    private fun openApp(route: String = Routes.HOME) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(Constants.EXTRA_NAVIGATION_ROUTE, route)
                }
            )
        }.onFailure { LogHelper.e(TAG, "Could not open app route $route", it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Status dan kontrol bubble RELX.iO" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val restoreIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(Constants.ACTION_RESTORE_OVERLAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).setAction(Constants.ACTION_STOP_OVERLAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_robot)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_robot, "Tampilkan bubble", restoreIntent)
            .addAction(R.drawable.ic_robot, "Matikan", stopIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP_OVERLAY -> {
                stopSelf()
                return START_NOT_STICKY
            }
            Constants.ACTION_RESTORE_OVERLAY -> {
                bubbleView?.visibility = View.VISIBLE
                isMenuOpen.value = false
                updatePanelVisibility()
                return START_STICKY
            }
            Constants.ACTION_TRIGGER_ANALYZE -> {
                triggerAnalyze(autoAnswer = false)
                return START_NOT_STICKY
            }
            Constants.ACTION_TRIGGER_AUTO_ANSWER -> {
                triggerAnalyze(autoAnswer = true)
                return START_NOT_STICKY
            }
            else -> Unit
        }
        intent?.getStringExtra("mode")?.takeIf { it.isNotBlank() }?.let {
            AppModule.provideSettingsRepository(this).setActiveMode(it)
        }
        if (bubbleView?.visibility == View.GONE) bubbleView?.visibility = View.VISIBLE
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        QuickSettingsTile.requestRefresh(this)
        
        operationJob?.cancel()
        // PERBAIKAN: Menggunakan .job dari import kotlinx.coroutines.job
        runBlocking { serviceScope.coroutineContext.job.cancelAndJoin() }
        
        runCatching { if (bubbleView?.parent != null) windowManager?.removeViewImmediate(bubbleView) }
        runCatching { if (panelView?.parent != null) windowManager?.removeViewImmediate(panelView) }
        runCatching { bubbleView?.disposeComposition() }
        runCatching { panelView?.disposeComposition() }
        
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        overlayViewModelStore.clear()
        bubbleView = null
        panelView = null
        windowManager = null
        SoundHelper.release()
        super.onDestroy()
        LogHelper.i(TAG, "Overlay stopped cleanly")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val SCREEN_READ_TIMEOUT_MS = 15_000L
        private const val MAX_POWER_CAPTURE_CHARS = 20_000
        val isRunning = MutableStateFlow(false)
    }
}
