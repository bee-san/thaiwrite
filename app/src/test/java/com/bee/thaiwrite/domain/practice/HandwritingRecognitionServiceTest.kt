package com.bee.thaiwrite.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingRecognitionServiceTest {
    @Test
    fun `normalizeThai removes spacing punctuation and sara am variant`() {
        val normalized = HandwritingRecognitionService.normalizeThai(" แม่... ")
        val saraAm = HandwritingRecognitionService.normalizeThai("กํา")

        assertEquals("แม่", normalized)
        assertEquals("กำ", saraAm)
    }

    @Test
    fun `matchesExpected accepts top three normalized candidates`() {
        val matched = HandwritingRecognitionService.matchesExpected(
            expected = "แม่",
            candidates = listOf("แม", "แม่ ", "แมว"),
        )

        assertTrue(matched)
    }

    @Test
    fun `matchesExpected rejects non matching candidates`() {
        val matched = HandwritingRecognitionService.matchesExpected(
            expected = "บ้าน",
            candidates = listOf("บาน", "น้ำ", "นา"),
        )

        assertFalse(matched)
    }
}
