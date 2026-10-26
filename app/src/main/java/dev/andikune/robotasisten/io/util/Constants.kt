package dev.andikune.robotasisten.io.util

/**
 * Global application constants.
 */
object Constants {
    const val APP_NAME = "R A . i O"
    const val NOTIFICATION_CHANNEL_ID = "raio_overlay_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "R A . i O Overlay Service"
    const val NOTIFICATION_ID = 1001

    // Modes
    const val MODE_LIBRARY = "LIBRARY"
    const val MODE_AI_OFFLINE = "AI_OFFLINE"
    const val MODE_AI_OPEN_KEY = "AI_OPEN_KEY"

    // Actions
    const val ACTION_START_OVERLAY = "dev.andikune.robotasisten.io.action.START_OVERLAY"
    const val ACTION_STOP_OVERLAY = "dev.andikune.robotasisten.io.action.STOP_OVERLAY"
    const val ACTION_TRIGGER_ANALYZE = "dev.andikune.robotasisten.io.action.TRIGGER_ANALYZE"
    const val ACTION_TRIGGER_AUTO_ANSWER = "dev.andikune.robotasisten.io.action.TRIGGER_AUTO_ANSWER"

    // System Prompt for Essay & Study QA
    const val SYSTEM_PROMPT_ESSAY =
        "Anda adalah asisten belajar. Jawab soal essay berikut secara singkat, jelas, langsung pada inti. Maksimal 3 kalimat. Bahasa Indonesia yang baik dan benar. Tanpa kalimat pembuka atau penutup. Soal: "

    // Sensitive Exam Keywords
    val EXAM_KEYWORDS = listOf("ujian", "uts", "uas", "ulangan", "mid semester", "final exam", "tryout")
}
