package com.bee.thaiwrite.domain.practice

import com.bee.thaiwrite.data.model.ItemType
import com.bee.thaiwrite.data.model.StudyItemSeed
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

    @Test
    fun `matchesAnyExpected accepts carrier form candidates`() {
        val matched = HandwritingRecognitionService.matchesAnyExpected(
            expectedForms = listOf("อิ", "ิ"),
            candidates = listOf("อึ", "อิ", "อี"),
        )

        assertTrue(matched)
    }

    @Test
    fun `writing target uses carrier form for dependent marks`() {
        val vowelItem = StudyItemSeed(
            id = "sara_i",
            lessonId = "vowels_1",
            sortOrder = 1,
            type = ItemType.VOWEL,
            thai = "ิ",
            transliteration = "sara i",
            english = "short i vowel",
            audioText = "สระ อิ",
            prompt = "Write sara i.",
            category = null,
            teachingNote = null,
            teachingMode = null,
            components = emptyList(),
        )
        val toneItem = StudyItemSeed(
            id = "mai_ek",
            lessonId = "tones_1",
            sortOrder = 2,
            type = ItemType.TONE,
            thai = "่",
            transliteration = "mai ek",
            english = "first tone mark",
            audioText = "ไม้ เอก",
            prompt = "Write mai ek.",
            category = null,
            teachingNote = null,
            teachingMode = null,
            components = emptyList(),
        )

        assertEquals("อิ", vowelItem.writingTarget().displayText)
        assertEquals(listOf("อิ", "ิ"), vowelItem.writingTarget().acceptedTexts)
        assertEquals("อ่", toneItem.writingTarget().displayText)
        assertEquals(listOf("อ่", "่"), toneItem.writingTarget().acceptedTexts)
        assertTrue(vowelItem.writingTarget().supportText!!.contains("above the consonant"))
        assertTrue(toneItem.writingTarget().supportText!!.contains("above the consonant stack"))
    }

    @Test
    fun `writing target keeps standalone symbols and words unchanged`() {
        val consonantItem = StudyItemSeed(
            id = "ko_kai",
            lessonId = "consonants_1",
            sortOrder = 1,
            type = ItemType.CONSONANT,
            thai = "ก",
            transliteration = "ko kai",
            english = "chicken consonant",
            audioText = "ก ไก่",
            prompt = "Write ko kai.",
            category = null,
            teachingNote = null,
            teachingMode = null,
            components = emptyList(),
        )
        val wordItem = StudyItemSeed(
            id = "mae",
            lessonId = "words_1",
            sortOrder = 2,
            type = ItemType.WORD,
            thai = "แม่",
            transliteration = "mae",
            english = "mother",
            audioText = "แม่",
            prompt = "Write mother in Thai.",
            category = null,
            teachingNote = null,
            teachingMode = null,
            components = emptyList(),
        )

        assertEquals("ก", consonantItem.writingTarget().displayText)
        assertEquals(listOf("ก"), consonantItem.writingTarget().acceptedTexts)
        assertEquals("แม่", wordItem.writingTarget().displayText)
        assertEquals(listOf("แม่"), wordItem.writingTarget().acceptedTexts)
    }

    @Test
    fun `matchesAnyExpected accepts candidate without carrier when allowed`() {
        val matched = HandwritingRecognitionService.matchesAnyExpected(
            expectedForms = listOf("อ่", "่"),
            candidates = listOf("า", "่", "้"),
        )

        assertTrue(matched)
    }

    @Test
    fun `prepareInkForRecognition centers and scales strokes`() {
        val prepared = prepareInkForRecognition(
            strokes = listOf(
                listOf(
                    StrokePoint(100f, 100f, 0L),
                    StrokePoint(200f, 300f, 10L),
                ),
            ),
            width = 500f,
            height = 500f,
        )

        assertEquals(1000f, prepared.width)
        assertEquals(1000f, prepared.height)
        assertEquals(320f, prepared.strokes.first().first().x)
        assertEquals(140f, prepared.strokes.first().first().y)
        assertEquals(680f, prepared.strokes.first().last().x)
        assertEquals(860f, prepared.strokes.first().last().y)
    }

    @Test
    fun `simplifyStrokeForRecognition removes jitter and preserves endpoints`() {
        val simplified = simplifyStrokeForRecognition(
            listOf(
                StrokePoint(0f, 0f, 0L),
                StrokePoint(0.3f, 0.2f, 1L),
                StrokePoint(0.4f, 0.3f, 2L),
                StrokePoint(5f, 0f, 3L),
            ),
        )

        assertEquals(2, simplified.size)
        assertEquals(0f, simplified.first().x)
        assertEquals(5f, simplified.last().x)
    }
}
