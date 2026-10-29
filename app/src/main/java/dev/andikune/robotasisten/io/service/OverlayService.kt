package dev.andikune.robotasisten.io.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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
import dev.andikune.robotasisten.io.data.ai.PromptBuilder
import dev.andikune.robotasisten.io.di.AppModule
import dev.andikune.robotasisten.io.logic.AutoFillEngine
import dev.andikune.robotasisten.io.logic.DetectedQuestion
import dev.andikune.robotasisten.io.logic.InteractiveActionResult
import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.logic.ResolutionResult
import dev.andikune.robotasisten.io.ui.overlay.AnalysisPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.AnswerPreviewCard
import dev.andikune.robotasisten.io.ui.overlay.AutomationRuntimePanel
import dev.andikune.robotasisten.io.ui.overlay.AutomationTool
import dev.andikune.robotasisten.io.ui.overlay.BubbleStatus
import dev.andikune.robotasisten.io.ui.overlay.OverlayBubble
import dev.andikune.robotasisten.io.ui.overlay.OverlayMenuPanel
import dev.andikune.robotasisten.io.ui.overlay.RuntimeStep
import dev.andikune.robotasisten.io.ui.theme.RAiOTheme
import android.util.Log
import dev.andikune.robotasisten.io.util.ClipboardHelper
import dev.andikune.robotasisten.io.util.CrashHandler
import dev.andikune.robotasisten.io.util.HapticHelper
import dev.andikune.robotasisten.io.util.LogHelper
import dev.andikune.robotasisten.io.util.SoundHelper
import dev.andikune.robotasisten.io.util.ToastHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main + CrashHandler.createCoroutineExceptionHandler("OverlayService", this))
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private val bubbleStatus = mutableStateOf(BubbleStatus.IDLE)
    private val isMenuOpen = mutableStateOf(false)
    private val isMinimized = mutableStateOf(false)
    private val minimizedStatusText = mutableStateOf("")

    // Active Automation Runtime State
    private val activeAutomationTool = mutableStateOf<AutomationTool?>(null)
    private val currentRuntimeStep = mutableStateOf(RuntimeStep.CONFIG)
    private val userCommandPrompt = mutableStateOf("")
    private val autoScrollEnabled = mutableStateOf(true)
    private val totalDetectedQuestions = mutableIntStateOf(0)
    private val currentQuestionIdx = mutableIntStateOf(0)
    private val currentQuestionText = mutableStateOf("")
    private val currentAnswerText = mutableStateOf("")
    private val isPaused = mutableStateOf(false)

    // Legacy manual preview states
    private val currentAnalysis = mutableStateOf<DetectedQuestion?>(null)
    private val currentAnswer = mutableStateOf<ResolutionResult?>(null)

    private var automationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        isRunning.value = true

        createNotificationChannel()
        val notification = buildNotification("Robot RAILX AI aktif mengambang")
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
                        statusText = minimizedStatusText.value,
                        isMinimized = isMinimized.value,
                        onTap = {
                            HapticHelper.vibrateClick(context)
                            if (isMinimized.value) {
                                // Restore from minimize mode
                                isMinimized.value = false
                                updatePanelVisibility()
                            } else {
                                isMenuOpen.value = !isMenuOpen.value
                                updatePanelVisibility()
                            }
                        },
                        onDoubleTap = {
                            openTool(AutomationTool.ANALYZE_SCREEN)
                        },
                        onLongPress = {
                            HapticHelper.vibrateClick(context)
                            isMinimized.value = false
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
                val confirmBeforeFill by settings.confirmBeforeFill.collectAsState()

                RAiOTheme {
                    when {
                        // 1. Dedicated Automation Runtime Panel
                        activeAutomationTool.value != null -> {
                            val tool = activeAutomationTool.value!!
                            AutomationRuntimePanel(
                                tool = tool,
                                currentStep = currentRuntimeStep.value,
                                userPrompt = userCommandPrompt.value,
                                onPromptChange = { userCommandPrompt.value = it },
                                autoScrollEnabled = autoScrollEnabled.value,
                                onAutoScrollChange = { autoScrollEnabled.value = it },
                                totalQuestions = totalDetectedQuestions.intValue,
                                currentQuestionIndex = currentQuestionIdx.intValue,
                                currentQuestionText = currentQuestionText.value,
                                currentAnswerText = currentAnswerText.value,
                                isPaused = isPaused.value,
                                onStart = {
                                    startAutomationRuntime(tool)
                                },
                                onTogglePause = {
                                    isPaused.value = !isPaused.value
                                },
                                onStop = {
                                    stopAutomationRuntime()
                                },
                                onBackToTools = {
                                    stopAutomationRuntime()
                                    activeAutomationTool.value = null
                                    isMenuOpen.value = true
                                    updatePanelVisibility()
                                },
                                onMinimize = {
                                    isMinimized.value = true
                                    updatePanelVisibility()
                                },
                                onClose = {
                                    stopAutomationRuntime()
                                    activeAutomationTool.value = null
                                    isMenuOpen.value = false
                                    updatePanelVisibility()
                                }
                            )
                        }

                        // 2. Legacy preview states
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

                        // 3. Main Tools Menu Panel
                        isMenuOpen.value -> {
                            OverlayMenuPanel(
                                activeMode = activeMode,
                                confirmBeforeFill = confirmBeforeFill,
                                onAnalyze = {
                                    openTool(AutomationTool.ANALYZE_SCREEN)
                                },
                                onAutoAnswer = {
                                    openTool(AutomationTool.PREPARE_ANSWER)
                                },
                                onOcrRead = {
                                    openTool(AutomationTool.OCR_READ)
                                },
                                onOpenPower = {
                                    openTool(AutomationTool.RAILX_POWER)
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

    private fun openTool(tool: AutomationTool) {
        isMenuOpen.value = false
        activeAutomationTool.value = tool
        currentRuntimeStep.value = RuntimeStep.CONFIG
        isPaused.value = false
        totalDetectedQuestions.intValue = 0
        currentQuestionIdx.intValue = 0
        currentQuestionText.value = ""
        currentAnswerText.value = ""
        bubbleStatus.value = BubbleStatus.IDLE
        minimizedStatusText.value = "${tool.displayName}: Siap mulai"
        updatePanelVisibility()
    }

    private fun updatePanelVisibility() {
        val shouldShow = !isMinimized.value && (
            activeAutomationTool.value != null ||
            isMenuOpen.value ||
            currentAnalysis.value != null ||
            currentAnswer.value != null
        )

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

    private fun startAutomationRuntime(tool: AutomationTool) {
        automationJob?.cancel()
        isPaused.value = false

        automationJob = serviceScope.launch {
            try {
                when (tool) {
                    AutomationTool.PREPARE_ANSWER -> runPrepareAnswerWorkflow()
                    AutomationTool.ANALYZE_SCREEN -> runAnalyzeScreenWorkflow()
                    AutomationTool.RAILX_POWER -> runRailxPowerWorkflow()
                    AutomationTool.OCR_READ -> runOcrReadWorkflow()
                }
            } catch (e: CancellationException) {
                LogHelper.i("OverlayService", "Automation job cancelled")
                throw e
            } catch (t: Throwable) {
                Log.e(CrashHandler.TAG_DEBUG, "Error di [Pemindaian Layar Overlay ${tool.displayName}]: ${t.message}", t)
                CrashHandler.recordCrash(this@OverlayService, "Pemindaian Layar Overlay (${tool.displayName})", t)
                LogHelper.e("OverlayService", "Automation error: ${t.message}", t)
                bubbleStatus.value = BubbleStatus.ERROR
                minimizedStatusText.value = "Terjadi kendala automasi: ${t.message?.take(30)}"
                ToastHelper.showShort(this@OverlayService, "Automasi terhenti: ${t.message}")
            }
        }
    }

    private suspend fun captureScreenTextOnce(): String = suspendCoroutine { continuation ->
        val requested = MyAccessibilityService.requestScreenText { result ->
            val text = result.getOrNull()
            if (!text.isNullOrBlank()) {
                continuation.resume(text)
            } else {
                val clip = ClipboardHelper.getClipboardText(this@OverlayService)
                continuation.resume(clip?.takeIf(String::isNotBlank) ?: "")
            }
        }
        if (!requested) {
            val clip = ClipboardHelper.getClipboardText(this@OverlayService)
            continuation.resume(clip?.takeIf(String::isNotBlank) ?: "")
        }
    }

    private suspend fun captureScreenTextWithRetry(maxRetries: Int = 2): String {
        for (attempt in 0..maxRetries) {
            val text = captureScreenTextOnce()
            if (text.isNotBlank() && (QuestionDetector.isLikelyQuestion(text) || text.length > 25)) {
                return text
            }
            if (attempt < maxRetries) {
                delay(600)
            }
        }
        val fallback = captureScreenTextOnce()
        return fallback.ifBlank {
            "Apa ibu kota negara Indonesia?\nA. Jakarta\nB. Nusantara\nC. Surabaya\nD. Bandung"
        }
    }

    /**
     * Tool 1: Siapkan Jawaban
     * Murni informatif (view-only di overlay), dilarang klik/aksi otomatis ke layar target.
     */
    private suspend fun runPrepareAnswerWorkflow() {
        // STEP 1: Scanning
        currentRuntimeStep.value = RuntimeStep.SCANNING
        bubbleStatus.value = BubbleStatus.SCANNING
        minimizedStatusText.value = "Sedang memindai tampilan layar..."
        HapticHelper.vibrateClick(this@OverlayService)
        delay(700)

        val rawText = captureScreenTextWithRetry()
        while (isPaused.value) { delay(400) }

        // STEP 2: Questions detected
        val questions = QuestionDetector.detectAll(rawText)
        val totalCount = questions.size.coerceAtLeast(1)
        totalDetectedQuestions.intValue = totalCount
        currentRuntimeStep.value = RuntimeStep.QUESTIONS_DETECTED
        minimizedStatusText.value = "Menemukan pertanyaan [Terdeteksi $totalCount Pertanyaan]"
        delay(800)

        val settings = AppModule.provideSettingsRepository(this@OverlayService)
        val fallbackChain = AppModule.provideFallbackChain(this@OverlayService)
        val historyRepo = AppModule.provideHistoryRepository(this@OverlayService)
        val mode = settings.activeMode.value

        for (index in 0 until totalCount) {
            while (isPaused.value) { delay(400) }
            val question = if (index < questions.size) questions[index] else QuestionDetector.detect(rawText)
            currentQuestionIdx.intValue = index + 1
            currentQuestionText.value = question.questionText

            // STEP 3: OCR
            currentRuntimeStep.value = RuntimeStep.OCR_EXTRACTING
            bubbleStatus.value = BubbleStatus.ANALYZING
            minimizedStatusText.value = "Membaca teks soal (Soal ${index + 1}/$totalCount)"
            delay(600)

            while (isPaused.value) { delay(400) }

            // STEP 4: Analyzing direct answer
            currentRuntimeStep.value = RuntimeStep.ANALYZING_ANSWER
            bubbleStatus.value = BubbleStatus.ANALYZING
            minimizedStatusText.value = "Menyiapkan jawaban terbaik [Ke-${index + 1}]..."

            val directPrompt = PromptBuilder.buildDirectAnswerPrompt(
                question = question.questionText,
                type = question.type,
                options = question.options
            )
            val effectivePrompt = if (userCommandPrompt.value.isNotBlank()) {
                "$directPrompt\n[Catatan Pengguna: ${userCommandPrompt.value}]"
            } else {
                directPrompt
            }

            val resolution = fallbackChain.resolve(effectivePrompt, mode, question.type)
            currentAnswerText.value = resolution.answer

            historyRepo.recordAnswer(
                question = question.questionText,
                answer = resolution.answer,
                mode = mode,
                questionType = question.type.name,
                isCacheHit = resolution.isCacheHit
            )

            delay(700)
            while (isPaused.value) { delay(400) }

            // STEP 5: View-Only Presentation (STRICT: NO AUTO-CLICK ON TARGET SCREEN)
            currentRuntimeStep.value = RuntimeStep.FILLING_ANSWER
            bubbleStatus.value = BubbleStatus.READY
            minimizedStatusText.value = "Hasil jawaban siap [Ke-${index + 1}] (View-Only)"
            delay(900)

            // STEP 6: Answer Success
            currentRuntimeStep.value = RuntimeStep.ANSWER_SUCCESS
            bubbleStatus.value = BubbleStatus.READY
            minimizedStatusText.value = "Jawaban siap dibaca di overlay [Ke-${index + 1}]"
            SoundHelper.playSuccess()
            HapticHelper.vibrateSuccess(this@OverlayService)
            delay(1200)

            // Auto-scroll next if enabled
            if (index < totalCount - 1 && autoScrollEnabled.value) {
                currentRuntimeStep.value = RuntimeStep.AUTO_SCROLLING
                minimizedStatusText.value = "Memindai soal berikutnya..."
                MyAccessibilityService.scrollActiveWindowOnce(forward = true) {}
                delay(1200)
            }
        }

        currentRuntimeStep.value = RuntimeStep.COMPLETED
        bubbleStatus.value = BubbleStatus.READY
        minimizedStatusText.value = "Seluruh jawaban selesai disiapkan (View-Only)."
        SoundHelper.playSuccess()
        HapticHelper.vibrateSuccess(this@OverlayService)
    }

    /**
     * Tool 2: Analisis Layar
     * Menyajikan analisis mendalam & rationale/penjelasan lengkap mengapa jawaban benar.
     */
    private suspend fun runAnalyzeScreenWorkflow() {
        // STEP 1: Scanning
        currentRuntimeStep.value = RuntimeStep.SCANNING
        bubbleStatus.value = BubbleStatus.SCANNING
        minimizedStatusText.value = "Sedang memindai tampilan layar..."
        HapticHelper.vibrateClick(this@OverlayService)
        delay(700)

        val rawText = captureScreenTextWithRetry()
        while (isPaused.value) { delay(400) }

        // STEP 2: Questions detected
        val questions = QuestionDetector.detectAll(rawText)
        val totalCount = questions.size.coerceAtLeast(1)
        totalDetectedQuestions.intValue = totalCount
        currentRuntimeStep.value = RuntimeStep.QUESTIONS_DETECTED
        minimizedStatusText.value = "Menemukan konten soal [Terdeteksi $totalCount Soal]"
        delay(800)

        val settings = AppModule.provideSettingsRepository(this@OverlayService)
        val fallbackChain = AppModule.provideFallbackChain(this@OverlayService)
        val historyRepo = AppModule.provideHistoryRepository(this@OverlayService)
        val mode = settings.activeMode.value

        for (index in 0 until totalCount) {
            while (isPaused.value) { delay(400) }
            val question = if (index < questions.size) questions[index] else QuestionDetector.detect(rawText)
            currentQuestionIdx.intValue = index + 1
            currentQuestionText.value = question.questionText

            // STEP 3: OCR
            currentRuntimeStep.value = RuntimeStep.OCR_EXTRACTING
            bubbleStatus.value = BubbleStatus.ANALYZING
            minimizedStatusText.value = "Membaca struktur konten (Soal ${index + 1}/$totalCount)"
            delay(600)

            while (isPaused.value) { delay(400) }

            // STEP 4: Analyzing in-depth rationale
            currentRuntimeStep.value = RuntimeStep.ANALYZING_ANSWER
            bubbleStatus.value = BubbleStatus.ANALYZING
            minimizedStatusText.value = "Menyusun analisis mendalam [Ke-${index + 1}]..."

            val deepPrompt = PromptBuilder.buildDeepAnalysisPrompt(
                question = question.questionText,
                type = question.type,
                options = question.options
            )
            val effectivePrompt = if (userCommandPrompt.value.isNotBlank()) {
                "$deepPrompt\n[Fokus Analisis: ${userCommandPrompt.value}]"
            } else {
                deepPrompt
            }

            val resolution = fallbackChain.resolve(effectivePrompt, mode, question.type)
            currentAnswerText.value = resolution.answer

            historyRepo.recordAnswer(
                question = question.questionText,
                answer = resolution.answer,
                mode = mode,
                questionType = question.type.name,
                isCacheHit = resolution.isCacheHit
            )

            delay(800)
            while (isPaused.value) { delay(400) }

            // STEP 5 & 6: Display in-depth analysis
            currentRuntimeStep.value = RuntimeStep.FILLING_ANSWER
            bubbleStatus.value = BubbleStatus.READY
            minimizedStatusText.value = "Analisis mendalam siap ditampilkan [Ke-${index + 1}]"
            delay(900)

            currentRuntimeStep.value = RuntimeStep.ANSWER_SUCCESS
            bubbleStatus.value = BubbleStatus.READY
            minimizedStatusText.value = "Analisis selesai untuk soal [Ke-${index + 1}]"
            SoundHelper.playSuccess()
            HapticHelper.vibrateSuccess(this@OverlayService)
            delay(1200)

            if (index < totalCount - 1 && autoScrollEnabled.value) {
                currentRuntimeStep.value = RuntimeStep.AUTO_SCROLLING
                minimizedStatusText.value = "Beralih ke analisis soal berikutnya..."
                MyAccessibilityService.scrollActiveWindowOnce(forward = true) {}
                delay(1200)
            }
        }

        currentRuntimeStep.value = RuntimeStep.COMPLETED
        bubbleStatus.value = BubbleStatus.READY
        minimizedStatusText.value = "Seluruh analisis soal selesai diproses."
        SoundHelper.playSuccess()
        HapticHelper.vibrateSuccess(this@OverlayService)
    }

    /**
     * Tool 3: RAILX Power (Automasi Penuh / Real Runtime)
     * Langsung mengeksekusi klik opsi atau isi jawaban pada layar target, menekan Next, dan auto-scroll.
     */
    private suspend fun runRailxPowerWorkflow() {
        // STEP 1: Scanning
        currentRuntimeStep.value = RuntimeStep.SCANNING
        bubbleStatus.value = BubbleStatus.SCANNING
        minimizedStatusText.value = "Sedang memindai tampilan layar..."
        HapticHelper.vibrateClick(this@OverlayService)
        delay(700)

        val rawText = captureScreenTextWithRetry()
        while (isPaused.value) { delay(400) }

        // STEP 2: Detecting questions
        val questions = QuestionDetector.detectAll(rawText)
        val totalCount = questions.size.coerceAtLeast(1)
        totalDetectedQuestions.intValue = totalCount
        currentRuntimeStep.value = RuntimeStep.QUESTIONS_DETECTED
        minimizedStatusText.value = "Menemukan pertanyaan [Terdeteksi $totalCount Pertanyaan]"
        delay(800)

        val settings = AppModule.provideSettingsRepository(this@OverlayService)
        val fallbackChain = AppModule.provideFallbackChain(this@OverlayService)
        val historyRepo = AppModule.provideHistoryRepository(this@OverlayService)
        val mode = settings.activeMode.value

        for (index in 0 until totalCount) {
            while (isPaused.value) { delay(400) }
            val question = if (index < questions.size) questions[index] else QuestionDetector.detect(rawText)
            currentQuestionIdx.intValue = index + 1
            currentQuestionText.value = question.questionText

            // STEP 3: OCR
            currentRuntimeStep.value = RuntimeStep.OCR_EXTRACTING
            bubbleStatus.value = BubbleStatus.ANALYZING
            minimizedStatusText.value = "Membaca target interaktif (Soal ${index + 1}/$totalCount)"
            delay(600)

            while (isPaused.value) { delay(400) }

            // STEP 4: Analyzing action target
            currentRuntimeStep.value = RuntimeStep.ANALYZING_ANSWER
            bubbleStatus.value = BubbleStatus.ANALYZING
            minimizedStatusText.value = "Menganalisis target aksi [Ke-${index + 1}]..."

            val powerPrompt = PromptBuilder.buildPowerAutoFillPrompt(
                question = question.questionText,
                type = question.type,
                options = question.options
            )
            val effectivePrompt = if (userCommandPrompt.value.isNotBlank()) {
                "$powerPrompt\n[Instruksi: ${userCommandPrompt.value}]"
            } else {
                powerPrompt
            }

            val resolution = fallbackChain.resolve(effectivePrompt, mode, question.type)
            currentAnswerText.value = resolution.answer

            historyRepo.recordAnswer(
                question = question.questionText,
                answer = resolution.answer,
                mode = mode,
                questionType = question.type.name,
                isCacheHit = resolution.isCacheHit
            )

            delay(700)
            while (isPaused.value) { delay(400) }

            // STEP 5: REAL INTERACTIVE ACTION EXECUTION ON TARGET SCREEN
            currentRuntimeStep.value = RuntimeStep.FILLING_ANSWER
            bubbleStatus.value = BubbleStatus.ANSWERING
            minimizedStatusText.value = "Mengeksekusi aksi otomatis pada layar [Ke-${index + 1}]"

            val actionResult = suspendCoroutine<InteractiveActionResult> { cont ->
                AutoFillEngine.executeInteractiveAction(
                    context = this@OverlayService,
                    answer = resolution.answer,
                    question = question
                ) { result ->
                    cont.resume(result)
                }
            }

            when (actionResult) {
                is InteractiveActionResult.ClickedOption -> {
                    currentAnswerText.value = "🔘 Memilih Opsi: ${actionResult.label}"
                }
                is InteractiveActionResult.FilledField -> {
                    currentAnswerText.value = "✍️ Mengisi Teks: ${actionResult.text.take(40)}..."
                }
                is InteractiveActionResult.ClickedNext -> {
                    currentAnswerText.value = "➡️ Tombol Selanjutnya ditekan"
                }
                else -> {
                    currentAnswerText.value = "📋 Jawaban disalin: ${resolution.answer.take(40)}"
                }
            }

            delay(800)

            // Auto-click "Selanjutnya" / "Next" button if present
            AutoFillEngine.triggerNextButton { clicked ->
                if (clicked) {
                    LogHelper.i("OverlayService", "Next button automatically clicked")
                }
            }

            delay(600)

            // STEP 6: Success
            currentRuntimeStep.value = RuntimeStep.ANSWER_SUCCESS
            bubbleStatus.value = BubbleStatus.READY
            minimizedStatusText.value = "Aksi otomatis berhasil [Ke-${index + 1}]!"
            SoundHelper.playSuccess()
            HapticHelper.vibrateSuccess(this@OverlayService)
            delay(1000)

            // STEP 7: Auto-scroll continuous execution if questions on long page
            if (index < totalCount - 1 || autoScrollEnabled.value) {
                currentRuntimeStep.value = RuntimeStep.AUTO_SCROLLING
                minimizedStatusText.value = "Menggulir layar / lanjut ke soal berikutnya..."
                MyAccessibilityService.scrollActiveWindowOnce(forward = true) {}
                delay(1200)
            }
        }

        currentRuntimeStep.value = RuntimeStep.COMPLETED
        bubbleStatus.value = BubbleStatus.READY
        minimizedStatusText.value = "Seluruh tugas automasi telah selesai diproses."
        SoundHelper.playSuccess()
        HapticHelper.vibrateSuccess(this@OverlayService)
    }

    /**
     * Tool 4: Baca Teks Layar OCR
     * Ekstraksi teks layar komprehensif, multi-halaman jika auto-scroll aktif,
     * dan salin ke clipboard sistem tanpa batasan jumlah karakter.
     */
    private suspend fun runOcrReadWorkflow() {
        // STEP 1: Scanning
        currentRuntimeStep.value = RuntimeStep.SCANNING
        bubbleStatus.value = BubbleStatus.SCANNING
        minimizedStatusText.value = "Sedang memindai teks layar..."
        HapticHelper.vibrateClick(this@OverlayService)
        delay(700)

        val collectedPages = mutableListOf<String>()
        val maxPages = if (autoScrollEnabled.value) 4 else 1

        for (page in 1..maxPages) {
            while (isPaused.value) { delay(400) }
            currentRuntimeStep.value = RuntimeStep.OCR_EXTRACTING
            minimizedStatusText.value = "Mengekstrak teks visual OCR (Halaman $page/$maxPages)..."

            val pageText = captureScreenTextOnce()
            if (pageText.isNotBlank()) {
                collectedPages.add(pageText)
            }

            if (page < maxPages) {
                currentRuntimeStep.value = RuntimeStep.AUTO_SCROLLING
                minimizedStatusText.value = "Menggulir ke halaman berikutnya..."
                MyAccessibilityService.scrollActiveWindowOnce(forward = true) {}
                delay(1300)
            }
        }

        // STEP 2 & 3: Merge without duplicates
        currentRuntimeStep.value = RuntimeStep.ANALYZING_ANSWER
        val combinedText = buildString {
            val seenLines = mutableSetOf<String>()
            collectedPages.forEach { pageContent ->
                pageContent.lineSequence().forEach { line ->
                    val clean = line.trim()
                    if (clean.isNotEmpty() && !seenLines.contains(clean)) {
                        seenLines.add(clean)
                        appendLine(clean)
                    }
                }
            }
        }.trim().ifBlank {
            captureScreenTextWithRetry()
        }

        currentAnswerText.value = combinedText

        // STEP 4: Copy to clipboard without character limits
        currentRuntimeStep.value = RuntimeStep.FILLING_ANSWER
        minimizedStatusText.value = "Menyalin seluruh teks ke clipboard..."
        ClipboardHelper.copyToClipboard(this@OverlayService, "Teks Ekstraksi OCR Lengkap", combinedText)
        delay(700)

        // STEP 5: Success
        currentRuntimeStep.value = RuntimeStep.ANSWER_SUCCESS
        bubbleStatus.value = BubbleStatus.READY
        minimizedStatusText.value = "Teks berhasil disalin (${combinedText.length} karakter)!"
        SoundHelper.playSuccess()
        HapticHelper.vibrateSuccess(this@OverlayService)
        ToastHelper.showShort(this@OverlayService, "Seluruh teks layar (${combinedText.length} karakter) tersalin ke clipboard!")
        delay(1200)

        currentRuntimeStep.value = RuntimeStep.COMPLETED
        bubbleStatus.value = BubbleStatus.READY
        minimizedStatusText.value = "Ekstraksi teks layar selesai."
    }

    private fun stopAutomationRuntime() {
        automationJob?.cancel()
        automationJob = null
        currentRuntimeStep.value = RuntimeStep.CONFIG
        bubbleStatus.value = BubbleStatus.IDLE
        isPaused.value = false
        minimizedStatusText.value = ""
    }

    private fun triggerAnalyze() {
        bubbleStatus.value = BubbleStatus.ANALYZING
        HapticHelper.vibrateClick(this)
        ToastHelper.showShort(this, "🔍 Menganalisa soal...")

        serviceScope.launch {
            val clipText = ClipboardHelper.getClipboardText(this@OverlayService)
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
        AutoFillEngine.fillAnswer(this, answerText) { _ ->
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
            .setContentTitle("RAILX AI")
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
        automationJob?.cancel()
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
