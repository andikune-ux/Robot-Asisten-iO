package dev.andikune.robotasisten.io.util

import android.media.AudioManager
import android.media.ToneGenerator

/** Audio feedback helper with lazy resource allocation and explicit cleanup. */
object SoundHelper {
    private var toneGenerator: ToneGenerator? = null

    @Synchronized
    private fun generator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
        }
        return toneGenerator
    }

    fun playBeep() {
        runCatching { generator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 100) }
    }

    fun playSuccess() {
        runCatching { generator()?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    }

    @Synchronized
    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }
}
