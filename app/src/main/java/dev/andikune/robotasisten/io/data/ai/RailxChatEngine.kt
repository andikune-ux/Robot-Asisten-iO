package dev.andikune.robotasisten.io.data.ai

enum class RailxChatMode { FAST, SEDANG, PRO }

data class UserAiSettings(
    val customInstruction: String = "", // Diambil dari input UI "Instruction Response"
    val memoryKnowledge: String = "",   // Diambil dari input UI "Memory Knowledge"
    val chatMode: RailxChatMode = RailxChatMode.SEDANG
)

data class ChatConfig(
    val systemPrompt: String,
    val maxTokens: Int,
    val temperature: Float,
    val topP: Float
)

object RailxChatEngine {

    fun buildChatConfig(settings: UserAiSettings): ChatConfig {
        val promptBuilder = StringBuilder()

        // 1. BASE SYSTEM PROMPT & MULTI-LANGUAGE
        promptBuilder.append(
            """
            [SYSTEM PROMPT - RAILX AI CHAT ENGINE]
            Kamu adalah RAILX AI, asisten virtual interaktif khusus untuk fitur Chat Percakapan.
            
            IDENTITAS & GAYA BAHASA (PERSONA UTAMA):
            1. Bahasa Utama (Default): Gunakan Bahasa Indonesia modern, komunikatif, luwes, dan alami (semi-formal/santai tapi tetap sopan). DILARANG KERAS kaku seperti terjemahan mesin atau buku teks.
            2. Multi-Bahasa (Auto-Detect): Otomatis deteksi bahasa pengguna (Inggris, Jepang, Mandarin, dll) dan jawab dalam bahasa tersebut secara fluent dan alami.
            3. Bebas Basa-Basi: Langsung masuk ke inti obrolan tanpa mukadimah panjang di awal kalimat.
            """.trimIndent()
        )

        // 2. INJEKSI MEMORY KNOWLEDGE (JIKA DIISI)
        if (settings.memoryKnowledge.isNotBlank()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(
                """
                [MEMORY KNOWLEDGE & CONTEXT]
                Berikut adalah basis pengetahuan dan memori permanen pengguna yang WAJIB kamu ingat dan gunakan dalam setiap analisis:
                ${settings.memoryKnowledge.trim()}
                """.trimIndent()
            )
        }

        // 3. INJEKSI CUSTOM PERSONA / INSTRUCTION (JIKA DIISI)
        if (settings.customInstruction.isNotBlank()) {
            promptBuilder.append("\n\n")
            promptBuilder.append(
                """
                [CUSTOM PERSONA & INSTRUCTION RESPONSE]
                Pengguna telah menetapkan instruksi/persona khusus. WAJIB ikuti instruksi berikut dalam merespon:
                ${settings.customInstruction.trim()}
                """.trimIndent()
            )
        }

        // 4. ATURAN MODE DENSITY & HYPERPARAMETERS (FAST / SEDANG / PRO)
        val maxTokens: Int
        val temperature: Float
        val topP: Float

        when (settings.chatMode) {
            RailxChatMode.FAST -> {
                promptBuilder.append("\n\n[MODE AKTIF: FAST]\n- Jawab ultra-cepat, to the point, maksimal 2-3 kalimat atau poin ringkas.")
                maxTokens = 250
                temperature = 0.3f
                topP = 0.85f
            }
            RailxChatMode.SEDANG -> {
                promptBuilder.append("\n\n[MODE AKTIF: SEDANG]\n- Seimbang antara kecepatan dan kelengkapan. Gunakan struktur paragraf/bullet point yang rapi.")
                maxTokens = 750
                temperature = 0.65f
                topP = 0.90f
            }
            RailxChatMode.PRO -> {
                promptBuilder.append("\n\n[MODE AKTIF: PRO]\n- Analisis mendalam, komprehensif, bedah konsep dasar, logika, contoh konkret, hingga kesimpulan lengkap.")
                maxTokens = 2048
                temperature = 0.75f
                topP = 0.95f
            }
        }

        return ChatConfig(
            systemPrompt = promptBuilder.toString(),
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP
        )
    }
}
