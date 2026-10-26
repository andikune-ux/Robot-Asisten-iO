package dev.andikune.robotasisten.io.data.ai

import dev.andikune.robotasisten.io.logic.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTemplateTest {
    @Test
    fun arithmeticExpressionIsCalculatedDeterministically() {
        assertEquals("6", AnswerTemplate.generateTemplate("Hitung 2 + 4", QuestionType.CALCULATION))
    }

    @Test
    fun divisionByZeroIsNotReportedAsZero() {
        assertTrue(AnswerTemplate.generateTemplate("Hitung 10 / 0", QuestionType.CALCULATION).contains("pembagian dengan nol"))
    }

    @Test
    fun unanswerableQuestionDoesNotReceiveFabricatedAnswer() {
        val answer = AnswerTemplate.generateTemplate("Apa ibu kota negara X?", QuestionType.MULTIPLE_CHOICE)
        assertTrue(answer.startsWith("Jawaban belum dapat ditentukan"))
        assertTrue(answer != "A")
    }
}
