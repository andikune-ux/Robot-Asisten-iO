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
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
        isRunning.value = true

        createNotificationChannel()
        val notification = buildNotification("Robot R A . i O aktif mengambang")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, 1001, notification, fgsType)
        } else {
            startForeground(1001, notification)
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlayViews()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        LogHelper.i("OverlayService", "OverlayService started with interactive floating bubble")
    }

    private fun setupOverlayViews() {
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
                            HapticHelper.vibrateClick(context)
                            isMenuOpen.value = !isMenuOpen.value
                            updatePanelVisibility()
                        },
                        onDoubleTap = {
                            triggerAnalyze()
                        },
                        onLongPress = {
                            HapticHelper.vibrateClick(context)
                            isMenuOpen.value = true
                            updatePanelVisibility()
                        },
                        onDrag = { dx, dy ->
                            bubbleParams.x += dx.toInt()
                            bubbleParams.y += dy.toInt()
                            try {
                                windowManager?.updateViewLayout(this@apply, bubbleParams)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

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
                            val qText = currentAnalysis.value?.questionText ?: "Soal"
                            AnswerPreviewCard(
                                result = answer,
                                questionSummary = qText,
                                onFillAnswer = {
                                    fillAnswerNow(answer.answer)
                                },
                                onEditAnswer = {
                                    ToastHelper.showShort(context, "Membuka editor jawaban")
                                    openApp()
                                },
                                onCancel = {
                                    currentAnswer.value = null
                                    bubbleStatus.value = BubbleStatus.IDLE
                                    updatePanelVisibility()
                                }
                            )
                        }
                        currentAnalysis.value != null -> {
                            val question = currentAnalysis.value!!
                            AnalysisPreviewCard(
                                detectedQuestion = question,
                                onAnswerNow = {
                                    triggerAnswerNow(question)
                                },
                                onReanalyze = {
                                    triggerAnalyze()
                                },
                                onCancel = {
                                    currentAnalysis.value = null
                                    bubbleStatus.value = BubbleStatus.IDLE
                                    updatePanelVisibility()
                                }
                            )
                        }
                        isMenuOpen.value -> {
                            OverlayMenuPanel(
                                activeMode = activeMode,
                                onAnalyze = {
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                    triggerAnalyze()
                                },
                                onAutoAnswer = {
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                    triggerAnalyze()
                                },
                                onOcrRead = {
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                    ToastHelper.showShort(context, "📸 Mode Visual Siap")
                                    triggerAnalyze()
                                },
                                onOpenHistory = {
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                    openApp()
                                },
                                onOpenSettings = {
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                    openApp()
                                },
                                onHideBubble = {
                                    isMenuOpen.value = false
                                    bubbleView?.visibility = android.view.View.GONE
                                    updatePanelVisibility()
                                    ToastHelper.showShort(context, "Bubble disembunyikan. Buka notifikasi untuk menampilkan.")
                                },
                                onTurnOffRobot = {
                                    stopSelf()
                                },
                                onClose = {
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                }
                            )
                        }
                    }
                }
            }
        }

        try {
            windowManager?.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            LogHelper.e("OverlayService", "Failed to add bubbleView", e)
        }
    }

    private fun updatePanelVisibility() {
        val shouldShow = isMenuOpen.value || currentAnalysis.value != null || currentAnswer.value != null
        if (shouldShow) {
            if (panelView?.parent == null) {
                try {
                    windowManager?.addView(panelView, panelParams)
                } catch (_: Exception) {}
            }
        } else {
            if (panelView?.parent != null) {
                try {
                    windowManager?.removeView(panelView)
                } catch (_: Exception) {}
            }
        }
    }

    private fun triggerAnalyze() {
        bubbleStatus.value = BubbleStatus.ANALYZING
        HapticHelper.vibrateClick(this)
        ToastHelper.showShort(this, "🔍 Menganalisa soal...")

        serviceScope.launch {
            // Sample test question detection or clipboard text
            val clipText = dev.andikune.robotasisten.io.util.ClipboardHelper.getClipboardText(this@OverlayService)
            val questionText: String = if (!clipText.isNullOrBlank()) {
                clipText
            } else {
                "Apa ibu kota negara Indonesia?\nA. Jakarta\nB. Nusantara\nC. Surabaya\nD. Bandung"
            }

            val question = QuestionDetector.detect(questionText)
            currentAnalysis.value = question
            bubbleStatus.value = BubbleStatus.IDLE
            updatePanelVisibility()
            SoundHelper.playBeep()
        }
    }

    private fun triggerAnswerNow(question: DetectedQuestion) {
        bubbleStatus.value = BubbleStatus.ANSWERING
        ToastHelper.showShort(this, "⚡ Menjawab soal...")

        serviceScope.launch {
            val settings = AppModule.provideSettingsRepository(this@OverlayService)
            val fallbackChain = AppModule.provideFallbackChain(this@OverlayService)
            val historyRepo = AppModule.provideHistoryRepository(this@OverlayService)

            val mode = settings.activeMode.value
            val result = fallbackChain.resolve(question.questionText, mode, question.type)

            currentAnalysis.value = null
            currentAnswer.value = result
            bubbleStatus.value = BubbleStatus.READY
            updatePanelVisibility()
            SoundHelper.playSuccess()

            historyRepo.recordAnswer(
                question = question.questionText,
                answer = result.answer,
                mode = mode,
                questionType = question.type.name,
                isCacheHit = result.isCacheHit
            )

            if (!settings.confirmBeforeFill.value) {
                fillAnswerNow(result.answer)
            }
        }
    }

    private fun fillAnswerNow(answerText: String) {
        AutoFillEngine.fillAnswer(this, answerText) { success ->
            ToastHelper.showShort(this, "📋 Jawaban disalin ke clipboard: $answerText")
            HapticHelper.vibrateSuccess(this)
            currentAnswer.value = null
            bubbleStatus.value = BubbleStatus.IDLE
            updatePanelVisibility()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "raio_overlay",
                "Robot Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status robot asisten belajar mengambang"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "raio_overlay")
            .setContentTitle("R A . i O")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_robot)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode")
        if (!mode.isNullOrBlank()) {
            AppModule.provideSettingsRepository(this).setActiveMode(mode)
        }
        if (bubbleView?.visibility == android.view.View.GONE) {
            bubbleView?.visibility = android.view.View.VISIBLE
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        isRunning.value = false
        serviceScope.cancel()

        try {
            if (bubbleView?.parent != null) windowManager?.removeView(bubbleView)
            if (panelView?.parent != null) windowManager?.removeView(panelView)
        } catch (_: Exception) {}
        LogHelper.i("OverlayService", "OverlayService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        val isRunning = MutableStateFlow(false)
    }
}
