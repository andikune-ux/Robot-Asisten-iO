package dev.andikune.robotasisten.io

import dev.andikune.robotasisten.io.logic.CaptureMode
import dev.andikune.robotasisten.io.logic.CaptureStrategy
import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.logic.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionDetectorTest {
    @Test fun testDetectMultipleChoice() {
        val detected = QuestionDetector.detect("Apa ibu kota Indonesia?\nA. Jakarta\nB. Bandung\nC. Nusantara\nD. Surabaya")
        assertEquals(QuestionType.MULTIPLE_CHOICE, detected.type)
        assertEquals(listOf("Jakarta", "Bandung", "Nusantara", "Surabaya"), detected.options)
    }

    @Test fun testDetectCalculation() {
        assertEquals(QuestionType.CALCULATION, QuestionDetector.detect("Hitunglah 15 * 4").type)
        assertEquals(QuestionType.CALCULATION, QuestionDetector.detect("15   +   4").type)
    }

    @Test fun inlineAAndBMarkersAreNotOptions() {
        val detected = QuestionDetector.detect("A. Apa itu? B. Jelaskan secara singkat dalam satu kalimat")
        assertEquals(QuestionType.ESSAY, detected.type)
        assertEquals(emptyList<String>(), detected.options)
    }

    @Test fun optionExtractionRequiresLineAnchorsAndExtractsLabels() {
        val detected = QuestionDetector.detect("Pilih jawaban berikut:\nA) Satu\nB. Dua")
        assertEquals(QuestionType.MULTIPLE_CHOICE, detected.type)
        assertEquals(listOf("Satu", "Dua"), detected.options)
    }

    @Test fun accessibilityDefaultsToOcrUnlessExplicitlyDetected() {
        assertEquals(CaptureMode.SCREEN_OCR, CaptureStrategy.selectStrategy())
        assertEquals(CaptureMode.ACCESSIBILITY_NODES, CaptureStrategy.selectStrategy(true))
    }

    @Test fun testDetectEssay() {
        assertEquals(QuestionType.ESSAY, QuestionDetector.detect("Jelaskan faktor-faktor yang mempengaruhi terjadinya pemanasan global dan dampaknya terhadap ekosistem kutub.").type)
    }

    @Test fun nonQuestionScreenTextIsNotTreatedAsAQuestion() {
        assertEquals(false, QuestionDetector.isLikelyQuestion("Selamat datang. Daftar materi pembelajaran tersedia di sini."))
        assertEquals(true, QuestionDetector.isLikelyQuestion("Apa ibu kota Indonesia?"))
        assertEquals(true, QuestionDetector.isLikelyQuestion("Hitunglah 15 * 4"))
    }

    @Test fun screenAnalysisFocusesOnQuestionAndOptionsInsteadOfNavigationNoise() {
        val detected = QuestionDetector.detect(
            "RELX AI\nHome\nBeranda\nBab 1: Geography\nWhat is the capital of France?\nA. Paris\nB. Rome\nC. Madrid"
        )
        assertEquals(QuestionType.MULTIPLE_CHOICE, detected.type)
        org.junit.Assert.assertTrue(detected.questionText.contains("What is the capital of France?"))
        assertEquals(listOf("Paris", "Rome", "Madrid"), detected.options)
        org.junit.Assert.assertFalse(detected.questionText.contains("Beranda"))
    }

    @Test fun commonEnglishQuestionWithoutPunctuationIsDetected() {
        assertEquals(true, QuestionDetector.isLikelyQuestion("What is the capital of France"))
        assertEquals(true, QuestionDetector.detect("What is the capital of France").questionText.contains("capital of France"))
    }
}
