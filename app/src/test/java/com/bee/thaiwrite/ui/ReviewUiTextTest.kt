package com.bee.thaiwrite.ui

import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.StudyCardEntity
import com.bee.thaiwrite.data.model.ItemType
import com.bee.thaiwrite.data.model.StudyItemSeed
import com.bee.thaiwrite.data.repo.ReviewCardView
import com.bee.thaiwrite.data.repo.ReviewPromptMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewUiTextTest {
    @Test
    fun `recognition hero flips from Thai prompt to English answer after reveal`() {
        val card = recognitionCard()

        assertEquals("ก", reviewHeroTitle(card, revealed = false))
        assertEquals("chicken consonant", reviewHeroTitle(card, revealed = true))
        assertEquals(
            "Read the Thai and recall the English before you reveal the answer.",
            reviewModeBody(card, revealed = false),
        )
        assertEquals(
            "English answer shown. Replay the audio if you need it.",
            reviewModeBody(card, revealed = true),
        )
    }

    private fun recognitionCard() = ReviewCardView(
        item = StudyItemSeed(
            id = "ko_kai",
            lessonId = "lesson_1",
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
        ),
        card = StudyCardEntity(
            itemId = "ko_kai",
            cardType = CardType.RECOGNITION.name,
            state = "REVIEW",
            dueAt = 0L,
            stability = 0.0,
            difficulty = 0.0,
            lastReviewedAt = null,
            scheduledDays = 0,
            learningStep = 0,
            reps = 0,
            lapses = 0,
            lastOutcomePass = null,
            seedOrder = 1,
        ),
        guide = null,
        promptMode = ReviewPromptMode.RECOGNITION,
        primaryPrompt = "ก",
        secondaryPrompt = "chicken consonant",
        requiresWriting = false,
        breakdown = emptyList(),
    )
}
