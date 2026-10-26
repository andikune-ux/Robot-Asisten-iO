package dev.andikune.robotasisten.io

import dev.andikune.robotasisten.io.logic.QuestionDetector
import dev.andikune.robotasisten.io.logic.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit test verifying QuestionDetector classification logic.
 */
class QuestionDetectorTest {

    @Test
    fun testDetectMultipleChoice() {
        val raw = "Apa ibu kota Indonesia?\nA. Jakarta\nB. Bandung\nC. Nusantara\nD. Surabaya"
        val detected = QuestionDetector.detect(raw)
        assertEquals(QuestionType.MULTIPLE_CHOICE, detected.type)
        assertEquals(4, detected.options.size)
    }

    @Test
    fun testDetectCalculation() {
        val raw = "Hitunglah 15 * 4"
        val detected = QuestionDetector.detect(raw)
        assertEquals(QuestionType.CALCULATION, detected.type)
    }

    @Test
    fun testDetectEssay() {
        val raw = "Jelaskan faktor-faktor yang mempengaruhi terjadinya pemanasan global dan dampaknya terhadap ekosistem kutub."
        val detected = QuestionDetector.detect(raw)
        assertEquals(QuestionType.ESSAY, detected.type)
    }
}
