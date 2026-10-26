package dev.andikune.robotasisten.io.service

import android.app.Notification
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.andikune.robotasisten.io.navigation.Routes
import dev.andikune.robotasisten.io.ui.overlay.AnalysisPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.AnswerPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.BubbleStatus
import dev.andikune.robotasisten.io.ui.overlay.OverlayBubble
import dev.andikune.robotasisten.io.ui.overlay.OverlayMenuPanel
import dev.andikune.robotasisten.io.ui.overlay.OcrPreviewCard
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.util.HapticHelper
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.PermissionHelper
import dev.andikune.robotasisten.io.util.SoundHelper
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val overlayViewModelStore = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operationJob: Job? = null
    private var operationId = 0L

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
    private val currentAnalysis = mutableStateOf<DetectedQuestion?>(null)
    private val currentAnswer = mutableStateOf<ResolutionResult?>(null)
    private val currentOcrText = mutableStateOf<ScreenReadResult?>(null)

    private data class ScreenReadResult(val text: String, val source: String)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()

        try {
            startForeground(Constants.NOTIFICATION_ID, buildNotification("Overlay siap; ketuk bubble untuk membuka menu"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                throw SecurityException("Izin tampil di atas aplikasi lain belum diberikan")
            }
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (!setupOverlayViews()) throw IllegalStateException("Bubble tidak dapat dipasang")
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            isRunning.value = true
            QuickSettingsTile.requestRefresh(this)
            LogHelper.i(TAG, "Overlay started")
        } catch (error: Exception) {
            isRunning.value = false
            LogHelper.e(TAG, "Overlay startup failed", error)
            ToastHelper.showShort(this, error.message ?: "Overlay tidak dapat dimulai")
            stopForeground(STOP_FOREGROUND_REMOVE)
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
                            performClickFeedback()
                            if (currentAnalysis.value != null || currentAnswer.value != null || currentOcrText.value != null) {
                                currentAnalysis.value = null
                                currentAnswer.value = null
                                currentOcrText.value = null
                            } else {
                                isMenuOpen.value = !isMenuOpen.value
                            }
                            updatePanelVisibility()
                        },
                        onDoubleTap = { triggerAnalyze(autoAnswer = false) },
                        onLongPress = {
                            performClickFeedback()
                            isMenuOpen.value = true
                            updatePanelVisibility()
                        },
                        onDrag = { dx, dy ->
                            bubbleParams.x += dx.toInt()
                            bubbleParams.y += dy.toInt()
                            try {
                                if (bubbleView?.parent != null) manager.updateViewLayout(bubbleView, bubbleParams)
                            } catch (error: Exception) {
                                LogHelper.e(TAG, "Bubble layout update failed", error)
                            }
                        }
                    )
                }
            }
        }

        // Keep the target app's text field focused while the user taps overlay controls.
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

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
                            onCancel = { clearResult() }
                        )
                        isMenuOpen.value -> OverlayMenuPanel(
                            activeMode = activeMode,
                            confirmBeforeFill = confirmBeforeFill,
                            onAnalyze = { triggerAnalyze(autoAnswer = false) },
                            onAutoAnswer = { triggerAnalyze(autoAnswer = true) },
                            onOcrRead = { triggerReadOcr() },
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
                            onClose = { isMenuOpen.value = false; updatePanelVisibility() }
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
        val showAnalysis = currentAnalysis.value != null && bubbleStatus.value != BubbleStatus.ANSWERING
        val shouldShow = isMenuOpen.value || showAnalysis || currentAnswer.value != null || currentOcrText.value != null
        try {
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

    private fun triggerAnalyze(autoAnswer: Boolean) {
        operationJob?.cancel()
        val thisOperation = ++operationId
        currentAnalysis.value = null
        currentAnswer.value = null
        currentOcrText.value = null
        isMenuOpen.value = false
        updatePanelVisibility()
        bubbleStatus.value = BubbleStatus.ANALYZING
        performClickFeedback()

        operationJob = serviceScope.launch {
            val previousVisibility = bubbleView?.visibility ?: View.VISIBLE
            try {
                ToastHelper.showShort(this@OverlayService, "Membaca layar; jika aksesibilitas mati, teks clipboard akan dicoba")
                val readResult = readScreenOrClipboard()
                if (thisOperation != operationId) return@launch
                if (readResult == null || readResult.text.isBlank()) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Tidak ada teks terbaca. Aktifkan Aksesibilitas RELX.iO atau salin teks soal terlebih dahulu.")
                    updatePanelVisibility()
                    return@launch
                }

                if (!QuestionDetector.isLikelyQuestion(readResult.text)) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Teks terbaca, tetapi pola soal tidak terdeteksi. Gunakan menu Baca Teks Layar (OCR) untuk melihat atau menyalin teks.")
                    updatePanelVisibility()
                    return@launch
                }

                val detected = QuestionDetector.detect(readResult.text)
                currentAnalysis.value = detected
                bubbleStatus.value = BubbleStatus.IDLE
                updatePanelVisibility()
                ToastHelper.showShort(this@OverlayService, "Teks dibaca dari ${readResult.source}")
                if (!isSilentModeEnabled()) SoundHelper.playBeep()
                if (autoAnswer) triggerAnswerNow(detected)
            } catch (error: Exception) {
                if (thisOperation == operationId) {
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
        val thisOperation = ++operationId
        currentAnalysis.value = null
        currentAnswer.value = null
        currentOcrText.value = null
        isMenuOpen.value = false
        updatePanelVisibility()
        bubbleStatus.value = BubbleStatus.ANALYZING
        operationJob = serviceScope.launch {
            try {
                val readResult = readScreenOrClipboard()
                if (thisOperation != operationId) return@launch
                if (readResult == null || readResult.text.isBlank()) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Tidak ada teks OCR atau clipboard yang tersedia")
                } else {
                    currentOcrText.value = readResult
                    bubbleStatus.value = BubbleStatus.IDLE
                    updatePanelVisibility()
                    ToastHelper.showShort(this@OverlayService, "Teks dibaca dari ${readResult.source}")
                }
            } catch (error: Exception) {
                if (thisOperation == operationId) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    LogHelper.e(TAG, "OCR read failed", error)
                    ToastHelper.showShort(this@OverlayService, "OCR gagal: ${error.message ?: "coba lagi"}")
                }
            }
        }
    }

    private suspend fun readScreenOrClipboard(): ScreenReadResult? {
        var accessibilityWasAvailable = false
        if (PermissionHelper.isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            accessibilityWasAvailable = true
            val previousVisibility = bubbleView?.visibility
            bubbleView?.visibility = View.GONE
            try {
                // Give WindowManager a frame to remove the overlay from the screenshot.
                delay(180)
                val result = CompletableDeferred<Result<String>>()
                val accepted = MyAccessibilityService.requestScreenText { screenResult ->
                    result.complete(screenResult)
                }
                if (accepted) {
                    val screenText = withTimeoutOrNull(SCREEN_READ_TIMEOUT_MS) { result.await() }
                        ?.getOrNull()
                        ?.trim()
                    if (!screenText.isNullOrBlank()) return ScreenReadResult(screenText, "Layar (OCR/Aksesibilitas)")
                    LogHelper.w(TAG, "Screen OCR returned no text; trying clipboard")
                } else {
                    LogHelper.w(TAG, "Accessibility service is not connected; trying clipboard")
                }
            } finally {
                if (previousVisibility != null && bubbleView?.parent != null) bubbleView?.visibility = previousVisibility
            }
        }
        val clipboardText = runCatching { ClipboardHelper.getClipboardText(this) }
            .onFailure { LogHelper.e(TAG, "Clipboard read failed", it) }
            .getOrNull()
            ?.trim()
        return clipboardText?.takeIf { it.isNotBlank() }?.let {
            ScreenReadResult(it, if (accessibilityWasAvailable) "Clipboard fallback" else "Clipboard")
        }
    }

    private fun triggerAnswerNow(question: DetectedQuestion) {
        operationJob?.cancel()
        val thisOperation = ++operationId
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
                if (thisOperation != operationId) return@launch
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
            } catch (error: Exception) {
                if (thisOperation == operationId) {
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
                    clearResult()
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
        operationId++
        currentAnswer.value = null
        currentAnalysis.value = null
        currentOcrText.value = null
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

    private fun buildNotification(text: String): Notification {
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
        serviceScope.cancel()
        runCatching { if (bubbleView?.parent != null) windowManager?.removeViewImmediate(bubbleView) }
            .onFailure { LogHelper.e(TAG, "Bubble removal failed", it) }
        runCatching { if (panelView?.parent != null) windowManager?.removeViewImmediate(panelView) }
            .onFailure { LogHelper.e(TAG, "Panel removal failed", it) }
        runCatching { bubbleView?.disposeComposition() }
        runCatching { panelView?.disposeComposition() }

        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        overlayViewModelStore.clear()
        bubbleView = null
        panelView = null
        windowManager = null
        SoundHelper.release()
        super.onDestroy()
        LogHelper.i(TAG, "Overlay stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OverlayService"
        private const val SCREEN_READ_TIMEOUT_MS = 15_000L
        val isRunning = MutableStateFlow(false)
    }
}
