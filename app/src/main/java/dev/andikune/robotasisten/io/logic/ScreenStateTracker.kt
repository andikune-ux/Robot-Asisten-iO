package dev.andikune.robotasisten.io.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks on-screen state, foreground package, and last recognized question text.
 */
object ScreenStateTracker {
    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    private val _lastCapturedText = MutableStateFlow<String?>(null)
    val lastCapturedText: StateFlow<String?> = _lastCapturedText.asStateFlow()

    fun updateForegroundPackage(pkg: String?) {
        _currentPackage.value = pkg
    }

    fun updateCapturedText(text: String?) {
        _lastCapturedText.value = text
    }
}
