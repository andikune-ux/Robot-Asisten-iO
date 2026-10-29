package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionType

/** Builds language-adaptive prompts for screen questions and study requests. */
object PromptBuilder {
    private const val SAME_LANGUAGE = "Detect the language used in the question and answer in that same language. Do not default to Indonesian if another language is used. "

    fun buildPrompt(question: String, type: QuestionType, options: List<String> = emptyList()): String {
        return when (type) {
            QuestionType.MULTIPLE_CHOICE -> {
                val optionsText = if (options.isNotEmpty()) {
                    "\nOptions:\n" + options.mapIndexed { index, option -> "${'A' + index}. $option" }.joinToString("\n")
                } else ""
                SAME_LANGUAGE + "Choose the most accurate option. State its label and answer text, then give a short reason. Do not answer unrelated screen/interface text.\nQuestion: $question$optionsText"
            }
            QuestionType.CALCULATION -> SAME_LANGUAGE +
                "Solve the math/science problem accurately. Show a concise calculation and the final result.\nQuestion: $question"
            QuestionType.FILL_IN_THE_BLANK -> SAME_LANGUAGE +
                "Complete the blank accurately and concisely.\nQuestion: $question"
            QuestionType.ESSAY -> SAME_LANGUAGE +
                "Answer clearly, directly, and proportionally to the question. Include necessary reasoning and be honest when context is insufficient.\nQuestion: $question"
        }
    }

    /**
     * Tool 1: Siapkan Jawaban (View-Only).
     * High precision direct answer without hallucination or unrelated text.
     */
    fun buildDirectAnswerPrompt(question: String, type: QuestionType, options: List<String> = emptyList()): String {
        val optionsText = if (options.isNotEmpty()) {
            "\nPilihan Opsi:\n" + options.mapIndexed { index, option -> "${'A' + index}. $option" }.joinToString("\n")
        } else ""

        return SAME_LANGUAGE + buildString {
            append("Anda adalah asisten cerdas berakurasi tinggi. Berikan jawaban yang tepat dan pasti untuk pertanyaan berikut.")
            append(" DILARANG mengarang fakta atau berhalusinasi. Jika konteks tidak lengkap, berikan estimasi jawaban terbaik yang paling masuk akal.\n\n")
            when (type) {
                QuestionType.MULTIPLE_CHOICE -> {
                    append("FORMAT JAWABAN: Sebutkan secara langsung huruf opsi dan teks jawabannya (misal: 'Jawaban: B. Nusantara'). Tambahkan ringkasan alasan 1 kalimat saja.\n")
                }
                QuestionType.CALCULATION -> {
                    append("FORMAT JAWABAN: Tuliskan hasil akhir perhitungan secara jelas dan langkah ringkasnya.\n")
                }
                QuestionType.FILL_IN_THE_BLANK -> {
                    append("FORMAT JAWABAN: Tuliskan kata atau frasa yang tepat untuk mengisi bagian yang kosong.\n")
                }
                QuestionType.ESSAY -> {
                    append("FORMAT JAWABAN: Tuliskan jawaban yang padat, akurat, dan langsung ke inti pertanyaan.\n")
                }
            }
            append("Pertanyaan:\n").append(question).append(optionsText)
        }
    }

    /**
     * Tool 2: Analisis Layar (Explanation / Rationale mendalam).
     * Provides structured in-depth analysis and explanation for why the answer is correct.
     */
    fun buildDeepAnalysisPrompt(question: String, type: QuestionType, options: List<String> = emptyList()): String {
        val optionsText = if (options.isNotEmpty()) {
            "\nPilihan Jawaban:\n" + options.mapIndexed { index, option -> "${'A' + index}. $option" }.joinToString("\n")
        } else ""

        return SAME_LANGUAGE + buildString {
            append("Lakukan pemindaian dan analisis mendalam terhadap konten soal di layar berikut.")
            append(" Jelaskan secara terstruktur, ilmiah, dan mendalam mengapa jawaban tersebut benar.\n\n")
            append("STRUKTUR OUTPUT:\n")
            append("🎯 JAWABAN TEPAT: [Sebutkan huruf opsi dan teks jawaban yang benar]\n\n")
            append("📖 PEMBAHASAN MENDALAM & RATIONALE:\n[Uraikan penjelasan mendalam mengenai konsep, teori, aturan rumus, atau latar belakang fakta terkait]\n\n")
            if (type == QuestionType.MULTIPLE_CHOICE && options.isNotEmpty()) {
                append("💡 ANALISIS OPSI LAIN:\n[Jelaskan mengapa pilihan jawaban lainnya salah/kurang tepat]\n\n")
            }
            append("Pertanyaan:\n").append(question).append(optionsText)
        }
    }

    /**
     * Tool 3: RAILX Power (Automasi Penuh Real-Time).
     * Explicit target candidate output for automated node clicking/filling.
     */
    fun buildPowerAutoFillPrompt(question: String, type: QuestionType, options: List<String> = emptyList()): String {
        val optionsText = if (options.isNotEmpty()) {
            "\nOpsi:\n" + options.mapIndexed { index, option -> "${'A' + index}. $option" }.joinToString("\n")
        } else ""

        return SAME_LANGUAGE + buildString {
            append("Tentukan target jawaban pasti yang akan dieksekusi secara otomatis pada antarmuka aplikasi.\n")
            append("Baris 1 wajib memuat target jawaban spesifik (misal: 'TARGET: B. Nusantara' atau 'TARGET: Jawaban teks').\n")
            append("Baris berikutnya memuat ringkasan singkat.\n")
            append("Pertanyaan:\n").append(question).append(optionsText)
        }
    }
}
