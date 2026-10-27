// ... (Import statements tetap sama seperti file asli) ...

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
    
    // ... (State flows tetap sama seperti file asli) ...

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

    // ... (Fungsi setupOverlayViews, updatePanelVisibility, updatePanelPosition, setPowerPanelFocusable TETAP SAMA) ...

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

    // ... (Fungsi readPowerScreen, scrollTargetWindow, triggerPowerAnalyze, triggerAnswerNow, fillAnswerNow, performClickFeedback, isSilentModeEnabled, clearResult, openApp, createNotificationChannel, buildNotification TETAP SAMA) ...

    // PERBAIKAN #8: Lifecycle Cleanup yang Lengkap di onDestroy
    override fun onDestroy() {
        isRunning.value = false
        QuickSettingsTile.requestRefresh(this)
        
        // Batalkan semua job dan tunggu selesai
        operationJob?.cancel()
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
