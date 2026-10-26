package dev.andikune.robotasisten.io.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Audio feedback helper for operations and completion beeps.
 */
object SoundHelper {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        } catch (_: Exception) {}
    }

    fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    fun playSuccess() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }
}
