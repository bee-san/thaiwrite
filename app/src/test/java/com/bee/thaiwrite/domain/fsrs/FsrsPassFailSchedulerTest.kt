package com.bee.thaiwrite.domain.fsrs

import com.bee.thaiwrite.data.db.CardState
import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.StudyCardEntity
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsPassFailSchedulerTest {
    private val scheduler = FsrsPassFailScheduler()
    private val now = Instant.parse("2026-05-04T12:00:00Z")

    @Test
    fun `new card fail stays in learning and records failure`() {
        val result = scheduler.review(newCard(), now, passed = false).updatedCard

        assertEquals(CardState.LEARNING.name, result.state)
        assertEquals(0, result.learningStep)
        assertEquals(false, result.lastOutcomePass)
        assertTrue(result.dueAt > now.toEpochMilli())
    }

    @Test
    fun `review pass stays in review and grows interval`() {
        val reviewCard = newCard(
            state = CardState.REVIEW.name,
            stability = 5.0,
            difficulty = 5.5,
            lastReviewedAt = now.minus(Duration.ofDays(3)).toEpochMilli(),
            dueAt = now.toEpochMilli(),
        )

        val result = scheduler.review(reviewCard, now, passed = true).updatedCard

        assertEquals(CardState.REVIEW.name, result.state)
        assertTrue(result.scheduledDays >= 1)
        assertTrue(result.dueAt > now.toEpochMilli())
        assertTrue(result.stability > 0.0)
    }

    @Test
    fun `review fail moves card into relearning`() {
        val reviewCard = newCard(
            state = CardState.REVIEW.name,
            stability = 7.0,
            difficulty = 5.0,
            lastReviewedAt = now.minus(Duration.ofDays(7)).toEpochMilli(),
            dueAt = now.toEpochMilli(),
        )

        val result = scheduler.review(reviewCard, now, passed = false).updatedCard

        assertEquals(CardState.RELEARNING.name, result.state)
        assertEquals(1, result.lapses)
        assertFalse(result.lastOutcomePass ?: true)
        assertTrue(result.dueAt > now.toEpochMilli())
    }

    private fun newCard(
        state: String = CardState.NEW.name,
        dueAt: Long = now.toEpochMilli(),
        stability: Double = 0.0,
        difficulty: Double = 0.0,
        lastReviewedAt: Long? = null,
    ): StudyCardEntity = StudyCardEntity(
        itemId = "ko_kai",
        cardType = CardType.WRITING.name,
        state = state,
        dueAt = dueAt,
        stability = stability,
        difficulty = difficulty,
        lastReviewedAt = lastReviewedAt,
        scheduledDays = 0,
        learningStep = 0,
        reps = 0,
        lapses = 0,
        lastOutcomePass = null,
        seedOrder = 1,
    )
}
