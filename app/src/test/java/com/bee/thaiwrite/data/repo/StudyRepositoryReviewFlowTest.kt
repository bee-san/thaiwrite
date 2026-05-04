package com.bee.thaiwrite.data.repo

import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.model.ItemType
import com.bee.thaiwrite.data.model.LessonIntroSeed
import com.bee.thaiwrite.data.model.LessonKind
import com.bee.thaiwrite.data.model.LessonSeed
import com.bee.thaiwrite.data.model.StudyItemSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRepositoryReviewFlowTest {
    private val nowMillis = 1_700_000_000_000L

    @Test
    fun `starting a lesson seeds writing cards only and caps immediate queue at four`() {
        val lesson = LessonSeed(
            id = "lesson_1",
            order = 1,
            stage = "Starter symbols",
            kind = LessonKind.SYMBOL_BATCH,
            title = "Starter lesson",
            description = "Starter lesson",
            intro = intro(),
            itemIds = listOf("i1", "i2", "i3", "i4", "i5"),
        )
        val itemsById = lesson.itemIds.mapIndexed { index, itemId ->
            symbolItem(
                id = itemId,
                lessonId = lesson.id,
                sortOrder = index + 1,
            )
        }.associateBy { it.id }

        val cards = createLessonStartCards(
            lesson = lesson,
            itemsById = itemsById,
            existingCards = emptyMap(),
            nowMillis = nowMillis,
        )

        assertEquals(5, cards.size)
        assertTrue(cards.all { it.cardType == CardType.WRITING.name })
        assertEquals(IMMEDIATE_LESSON_QUEUE_LIMIT, cards.count { it.dueAt == nowMillis })
        assertTrue(cards.last().dueAt > nowMillis)
    }

    @Test
    fun `successful writing unlocks recognition and successful recognition unlocks audio`() {
        val item = symbolItem(
            id = "mae",
            lessonId = "lesson_1",
            sortOrder = 1,
            audioText = "แม่",
        )

        val recognition = activateDependentCard(
            item = item,
            reviewedCardType = CardType.WRITING,
            existingCard = null,
            nowMillis = nowMillis,
        )
        val audio = activateDependentCard(
            item = item,
            reviewedCardType = CardType.RECOGNITION,
            existingCard = null,
            nowMillis = nowMillis,
        )
        val noNext = activateDependentCard(
            item = item.copy(audioText = ""),
            reviewedCardType = CardType.RECOGNITION,
            existingCard = null,
            nowMillis = nowMillis,
        )

        assertEquals(CardType.RECOGNITION.name, recognition?.cardType)
        assertEquals(nowMillis, recognition?.dueAt)
        assertEquals(CardType.AUDIO_RECOGNITION.name, audio?.cardType)
        assertEquals(nowMillis, audio?.dueAt)
        assertNull(noNext)
    }

    @Test
    fun `stale submission is rejected once the card snapshot changes`() {
        val item = symbolItem(
            id = "ko_kai",
            lessonId = "lesson_1",
            sortOrder = 1,
        )
        val expected = seedCard(item, CardType.WRITING, nowMillis)
        val updated = expected.copy(reps = 1, lastReviewedAt = nowMillis, dueAt = nowMillis + 60_000L)

        assertTrue(acceptsSubmission(expected, expected, nowMillis))
        assertFalse(acceptsSubmission(updated, expected, nowMillis))
    }

    @Test
    fun `submission requires a matching due card`() {
        val item = symbolItem(
            id = "ko_kai",
            lessonId = "lesson_1",
            sortOrder = 1,
        )
        val dueCard = seedCard(item, CardType.WRITING, nowMillis)
        val futureCard = dueCard.copy(dueAt = nowMillis + 60_000L)

        assertFalse(acceptsSubmission(dueCard, expectedCard = null, nowMillis = nowMillis))
        assertFalse(acceptsSubmission(futureCard, expectedCard = futureCard, nowMillis = nowMillis))
        assertTrue(acceptsSubmission(futureCard, expectedCard = futureCard, nowMillis = nowMillis + 60_000L))
    }

    @Test
    fun `snapshot refresh delay targets next future due time with fallback bound`() {
        assertEquals(
            5_000L,
            nextSnapshotRefreshDelayMillis(
                dueAtMillis = listOf(nowMillis - 1_000L, nowMillis + 5_000L, nowMillis + 90_000L),
                nowMillis = nowMillis,
                fallbackMillis = 60_000L,
            ),
        )
        assertEquals(
            60_000L,
            nextSnapshotRefreshDelayMillis(
                dueAtMillis = listOf(nowMillis - 1_000L, nowMillis + 90_000L),
                nowMillis = nowMillis,
                fallbackMillis = 60_000L,
            ),
        )
        assertEquals(
            1L,
            nextSnapshotRefreshDelayMillis(
                dueAtMillis = listOf(nowMillis),
                nowMillis = nowMillis,
                fallbackMillis = 1L,
            ),
        )
    }

    private fun symbolItem(
        id: String,
        lessonId: String,
        sortOrder: Int,
        audioText: String = "เสียง",
    ) = StudyItemSeed(
        id = id,
        lessonId = lessonId,
        sortOrder = sortOrder,
        type = ItemType.CONSONANT,
        thai = id,
        transliteration = id,
        english = id,
        audioText = audioText,
        prompt = "Write $id.",
        category = null,
        teachingNote = null,
        teachingMode = null,
        components = emptyList(),
    )

    private fun intro() = LessonIntroSeed(
        whatThisIs = "What",
        howItBehaves = "How",
        whyItMatters = "Why",
        example = "Example",
    )
}
