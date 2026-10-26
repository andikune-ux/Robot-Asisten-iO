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
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.andikune.robotasisten.io.MainActivity
import dev.andikune.robotasisten.io.R
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.logic.AutoFillEngine
import dev.andikune.robotasisten.io.logic.DetectedQuestion
import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.logic.ResolutionResult
import dev.andikune.robotasisten.io.ui.overlay.AnalysisPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.AnswerPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.BubbleStatus
import dev.andikune.robotasisten.io.ui.overlay.OverlayBubble
import dev.andikune.robotasisten.io.ui.overlay.OverlayMenuPanel
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme
import dev.andikune.robotasisten.io.util.Constants
import dev.andikune.robotasisten.io.util.HapticHelper
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.SoundHelper
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    private val bubbleStatus = mutableStateOf(BubbleStatus.IDLE)
    private val isMenuOpen = mutableStateOf(false)
    private val currentAnalysis = mutableStateOf<DetectedQuestion?>(null)
    private val currentAnswer = mutableStateOf<ResolutionResult?>(null)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()
        try {
            startForeground(1001, buildNotification("Robot R A . i O aktif mengambang"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                throw SecurityException("Izin tampil di atas aplikasi lain belum diberikan")
            }
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (!setupOverlayViews()) {
                throw IllegalStateException("View overlay tidak dapat dipasang")
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            isRunning.value = true
            LogHelper.i("OverlayService", "OverlayService started with interactive floating bubble")
        } catch (e: Exception) {
            isRunning.value = false
            LogHelper.e("OverlayService", "Overlay startup failed", e)
            ToastHelper.showShort(this, e.message ?: "Overlay tidak dapat dimulai")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun setupOverlayViews(): Boolean {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 250 }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val size by settings.overlaySizeDp.collectAsState()
                val opacity by settings.overlayOpacity.collectAsState()
                RAiOTheme {
                    OverlayBubble(
                        status = bubbleStatus.value, sizeDp = size, opacity = opacity,
                        onTap = { HapticHelper.vibrateClick(context); isMenuOpen.value = !isMenuOpen.value; updatePanelVisibility() },
                        onDoubleTap = { triggerAnalyze() },
                        onLongPress = { HapticHelper.vibrateClick(context); isMenuOpen.value = true; updatePanelVisibility() },
                        onDrag = { dx, dy ->
                            bubbleParams.x += dx.toInt(); bubbleParams.y += dy.toInt()
                            try { windowManager?.updateViewLayout(this@apply, bubbleParams) }
                            catch (e: Exception) { LogHelper.e("OverlayService", "Bubble layout update failed", e) }
                        }
                    )
                }
            }
        }
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val activeMode by settings.activeMode.collectAsState()
                RAiOTheme {
                    when {
                        currentAnswer.value != null -> {
                            val answer = currentAnswer.value!!
                            AnswerPreviewCard(result = answer, questionSummary = currentAnalysis.value?.questionText ?: "Soal",
                                onFillAnswer = { fillAnswerNow(answer.answer) },
                                onEditAnswer = { ToastHelper.showShort(context, "Membuka editor jawaban"); openApp() },
                                onCancel = { currentAnswer.value = null; currentAnalysis.value = null; bubbleStatus.value = BubbleStatus.IDLE; updatePanelVisibility() })
                        }
                        currentAnalysis.value != null -> {
                            val question = currentAnalysis.value!!
                            AnalysisPreviewCard(detectedQuestion = question,
                                onAnswerNow = { triggerAnswerNow(question) }, onReanalyze = { triggerAnalyze() },
                                onCancel = { currentAnalysis.value = null; bubbleStatus.value = BubbleStatus.IDLE; updatePanelVisibility() })
                        }
                        isMenuOpen.value -> OverlayMenuPanel(activeMode = activeMode,
                            onAnalyze = { isMenuOpen.value = false; updatePanelVisibility(); triggerAnalyze() },
                            onAutoAnswer = { isMenuOpen.value = false; updatePanelVisibility(); triggerAnalyze() },
                            onOcrRead = {
                                isMenuOpen.value = false; updatePanelVisibility()
                                ToastHelper.showShort(context, "Pembacaan layar belum tersedia; tidak ada tangkapan layar yang dilakukan")
                            },
                            onOpenHistory = { isMenuOpen.value = false; updatePanelVisibility(); openApp() },
                            onOpenSettings = { isMenuOpen.value = false; updatePanelVisibility(); openApp() },
                            onHideBubble = { isMenuOpen.value = false; bubbleView?.visibility = android.view.View.GONE; updatePanelVisibility(); ToastHelper.showShort(context, "Bubble disembunyikan. Buka notifikasi untuk menampilkan.") },
                            onTurnOffRobot = { stopSelf() },
                            onClose = { isMenuOpen.value = false; updatePanelVisibility() })
                    }
                }
            }
        }
        try {
            windowManager?.addView(bubbleView, bubbleParams)
            return bubbleView?.parent != null
        } catch (e: Exception) {
            LogHelper.e("OverlayService", "Failed to add bubbleView", e)
            return false
        }
    }

    private fun updatePanelVisibility() {
        val shouldShow = isMenuOpen.value || currentAnalysis.value != null || currentAnswer.value != null
        if (shouldShow && panelView?.parent == null) {
            try { windowManager?.addView(panelView, panelParams) }
            catch (e: Exception) { LogHelper.e("OverlayService", "Failed to show overlay panel", e) }
        } else if (!shouldShow && panelView?.parent != null) {
            try { windowManager?.removeView(panelView) }
            catch (e: Exception) { LogHelper.e("OverlayService", "Failed to hide overlay panel", e) }
        }
    }

    private fun triggerAnalyze() {
        bubbleStatus.value = BubbleStatus.ANALYZING
        HapticHelper.vibrateClick(this)
        ToastHelper.showShort(this, "🔍 Menganalisa soal...")
        serviceScope.launch {
            try {
                val questionText = dev.andikune.robotasisten.io.util.ClipboardHelper.getClipboardText(this@OverlayService)
                if (questionText.isNullOrBlank()) {
                    bubbleStatus.value = BubbleStatus.IDLE
                    ToastHelper.showShort(this@OverlayService, "Tidak ada teks di clipboard. Pembacaan layar belum tersedia.")
                    updatePanelVisibility()
                    return@launch
                }
                currentAnalysis.value = QuestionDetector.detect(questionText)
                bubbleStatus.value = BubbleStatus.IDLE
                updatePanelVisibility()
                SoundHelper.playBeep()
            } catch (e: Exception) {
                LogHelper.e("OverlayService", "Question analysis failed", e)
                bubbleStatus.value = BubbleStatus.IDLE
                ToastHelper.showShort(this@OverlayService, "Analisis gagal; tidak ada data layar yang dibaca")
            }
        }
    }

    private fun triggerAnswerNow(question: DetectedQuestion) {
        bubbleStatus.value = BubbleStatus.ANSWERING
        ToastHelper.showShort(this, "⚡ Menjawab soal...")
        serviceScope.launch {
            try {
                val settings = AppModule.provideSettingsRepository(this@OverlayService)
                val fallbackChain = AppModule.provideFallbackChain(this@OverlayService)
                val historyRepo = AppModule.provideHistoryRepository(this@OverlayService)
                val mode = settings.activeMode.value
                val result = fallbackChain.resolve(question.questionText, mode, question.type)
                currentAnswer.value = result
                bubbleStatus.value = BubbleStatus.READY
                updatePanelVisibility()
                SoundHelper.playSuccess()
                historyRepo.recordAnswer(question = question.questionText, answer = result.answer, mode = mode,
                    questionType = question.type.name, isCacheHit = result.isCacheHit)
                if (!settings.confirmBeforeFill.value) fillAnswerNow(result.answer)
            } catch (e: Exception) {
                LogHelper.e("OverlayService", "Answer resolution failed", e)
                bubbleStatus.value = BubbleStatus.IDLE
                ToastHelper.showShort(this@OverlayService, "Jawaban gagal diproses")
            }
        }
    }

    private fun fillAnswerNow(answerText: String) {
        try {
            AutoFillEngine.fillAnswer(this, answerText) { success ->
                if (success) {
                    ToastHelper.showShort(this, "Jawaban disalin ke clipboard: $answerText")
                    HapticHelper.vibrateSuccess(this)
                    currentAnswer.value = null
                    currentAnalysis.value = null
                    bubbleStatus.value = BubbleStatus.IDLE
                    updatePanelVisibility()
                } else {
                    ToastHelper.showShort(this, "Jawaban disalin ke clipboard; pengisian otomatis belum tersedia")
                    currentAnswer.value = null
                    currentAnalysis.value = null
                    bubbleStatus.value = BubbleStatus.IDLE
                    updatePanelVisibility()
                }
            }
        } catch (e: Exception) {
            LogHelper.e("OverlayService", "Answer fill failed", e)
            ToastHelper.showShort(this, "Gagal mengisi jawaban")
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("raio_overlay", "Robot Overlay", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Status robot asisten belajar mengambang" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "raio_overlay").setContentTitle("R A . i O")
            .setContentText(text).setSmallIcon(R.drawable.ic_robot).setContentIntent(pendingIntent).setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP_OVERLAY -> { stopSelf(); return START_NOT_STICKY }
            Constants.ACTION_TRIGGER_ANALYZE -> if (isRunning.value) triggerAnalyze()
        }
        val mode = intent?.getStringExtra("mode")
        if (!mode.isNullOrBlank()) AppModule.provideSettingsRepository(this).setActiveMode(mode)
        if (bubbleView?.visibility == android.view.View.GONE) bubbleView?.visibility = android.view.View.VISIBLE
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        serviceScope.cancel()
        try { if (bubbleView?.parent != null) windowManager?.removeView(bubbleView) } catch (e: Exception) { LogHelper.e("OverlayService", "Bubble removal failed", e) }
        try { if (panelView?.parent != null) windowManager?.removeView(panelView) } catch (e: Exception) { LogHelper.e("OverlayService", "Panel removal failed", e) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        LogHelper.i("OverlayService", "OverlayService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { val isRunning = MutableStateFlow(false) }
}
