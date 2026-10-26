package dev.andikune.robotasisten.io.data.repo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository to persist and provide application configuration and preferences.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("raio_settings_prefs", Context.MODE_PRIVATE)

    private val _activeMode = MutableStateFlow(prefs.getString(KEY_ACTIVE_MODE, "LIBRARY") ?: "LIBRARY")
    val activeMode: StateFlow<String> = _activeMode.asStateFlow()

    private val _overlaySizeDp = MutableStateFlow(prefs.getInt(KEY_OVERLAY_SIZE, 56))
    val overlaySizeDp: StateFlow<Int> = _overlaySizeDp.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_OPACITY, 0.85f))
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    private val _confirmBeforeFill = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_BEFORE_FILL, true))
    val confirmBeforeFill: StateFlow<Boolean> = _confirmBeforeFill.asStateFlow()

    private val _autoAnalyze = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ANALYZE, false))
    val autoAnalyze: StateFlow<Boolean> = _autoAnalyze.asStateFlow()

    private val _silentMode = MutableStateFlow(prefs.getBoolean(KEY_SILENT_MODE, false))
    val silentMode: StateFlow<Boolean> = _silentMode.asStateFlow()

    private val _selectedProvider = MutableStateFlow(prefs.getString(KEY_SELECTED_PROVIDER, "OpenAI") ?: "OpenAI")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString(KEY_SELECTED_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _selectedOfflineModel = MutableStateFlow(prefs.getString(KEY_OFFLINE_MODEL, "Qwen 2.5 0.5B") ?: "Qwen 2.5 0.5B")
    val selectedOfflineModel: StateFlow<String> = _selectedOfflineModel.asStateFlow()

    private val _isOnboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val isOnboarded: StateFlow<Boolean> = _isOnboarded.asStateFlow()

    fun setActiveMode(mode: String) {
        prefs.edit().putString(KEY_ACTIVE_MODE, mode).apply()
        _activeMode.value = mode
    }

    fun setOverlaySize(size: Int) {
        prefs.edit().putInt(KEY_OVERLAY_SIZE, size).apply()
        _overlaySizeDp.value = size
    }

    fun setOverlayOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_OVERLAY_OPACITY, opacity).apply()
        _overlayOpacity.value = opacity
    }

    fun setConfirmBeforeFill(confirm: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_BEFORE_FILL, confirm).apply()
        _confirmBeforeFill.value = confirm
    }

    fun setAutoAnalyze(auto: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ANALYZE, auto).apply()
        _autoAnalyze.value = auto
    }

    fun setSilentMode(silent: Boolean) {
        prefs.edit().putBoolean(KEY_SILENT_MODE, silent).apply()
        _silentMode.value = silent
    }

    fun setSelectedProvider(provider: String) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider).apply()
        _selectedProvider.value = provider
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _selectedModel.value = model
    }

    fun setSelectedOfflineModel(model: String) {
        prefs.edit().putString(KEY_OFFLINE_MODEL, model).apply()
        _selectedOfflineModel.value = model
    }

    fun setOnboarded(onboarded: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, onboarded).apply()
        _isOnboarded.value = onboarded
    }

    companion object {
        private const val KEY_ACTIVE_MODE = "active_mode"
        private const val KEY_OVERLAY_SIZE = "overlay_size"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_CONFIRM_BEFORE_FILL = "confirm_before_fill"
        private const val KEY_AUTO_ANALYZE = "auto_analyze"
        private const val KEY_SILENT_MODE = "silent_mode"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_OFFLINE_MODEL = "selected_offline_model"
        private const val KEY_ONBOARDED = "is_onboarded"
    }
}
