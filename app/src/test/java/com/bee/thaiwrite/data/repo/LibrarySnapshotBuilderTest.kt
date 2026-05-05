package com.bee.thaiwrite.data.repo

import com.bee.thaiwrite.data.db.CardState
import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.DailyStreakEntity
import com.bee.thaiwrite.data.db.LessonProgressEntity
import com.bee.thaiwrite.data.db.ModelDownloadStateEntity
import com.bee.thaiwrite.data.db.StudyCardEntity
import com.bee.thaiwrite.data.model.GuideSeed
import com.bee.thaiwrite.data.model.ItemType
import com.bee.thaiwrite.data.model.ItemComponentSeed
import com.bee.thaiwrite.data.model.LessonIntroSeed
import com.bee.thaiwrite.data.model.LessonKind
import com.bee.thaiwrite.data.model.LessonSeed
import com.bee.thaiwrite.data.model.SeedBundle
import com.bee.thaiwrite.data.model.StudyItemSeed
import com.bee.thaiwrite.data.model.TeachingMode
import com.bee.thaiwrite.system.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySnapshotBuilderTest {
    private val nowMillis = 1_700_000_000_000L
    private val lessonOne = LessonSeed(
        id = "lesson_1",
        order = 1,
        stage = "Starter symbols",
        kind = LessonKind.SYMBOL_BATCH,
        title = "Lesson One",
        description = "First lesson",
        intro = intro("First lesson"),
        itemIds = listOf("ko_kai", "kho_khai"),
    )
    private val lessonTwo = LessonSeed(
        id = "lesson_2",
        order = 2,
        stage = "Useful words",
        kind = LessonKind.WORD_BRIDGE,
        title = "Lesson Two",
        description = "Second lesson",
        intro = intro("Second lesson"),
        itemIds = listOf("mae"),
    )
    private val koKai = item(
        id = "ko_kai",
        lessonId = lessonOne.id,
        sortOrder = 1,
        type = ItemType.CONSONANT,
        thai = "ก",
        transliteration = "ko kai",
        english = "chicken consonant",
        audioText = "ก ไก่",
        prompt = "Write ko kai.",
    )
    private val khoKhai = item(
        id = "kho_khai",
        lessonId = lessonOne.id,
        sortOrder = 2,
        type = ItemType.CONSONANT,
        thai = "ข",
        transliteration = "kho khai",
        english = "egg consonant",
        audioText = "ข ไข่",
        prompt = "Write kho khai.",
    )
    private val mae = item(
        id = "mae",
        lessonId = lessonTwo.id,
        sortOrder = 3,
        type = ItemType.WORD,
        thai = "แม่",
        transliteration = "mae",
        english = "mother",
        audioText = "แม่",
        prompt = "Write mother in Thai.",
        category = "Family",
        teachingNote = "A family word.",
        teachingMode = TeachingMode.BUILD,
        components = listOf(
            ItemComponentSeed(itemId = "ko_kai", note = "first part"),
        ),
    )
    private val seeds = SeedBundle(
        lessons = listOf(lessonOne, lessonTwo),
        items = listOf(koKai, khoKhai, mae),
        guides = mapOf(
            mae.id to GuideSeed(
                itemId = mae.id,
                guideType = "glyph-outline",
                tip = "Write the whole word slowly.",
            ),
        ),
    )
    private val streak = DailyStreakEntity(id = 1, currentStreak = 3, maxStreak = 8, lastStudyDay = "2026-05-04")
    private val settings = SettingsState(onboardingComplete = true, reminderHour = 7, reminderMinute = 30)

    @Test
    fun `snapshot hides due cards from locked lessons`() {
        val cards = listOf(
            card(koKai.id, CardType.RECOGNITION, dueAt = nowMillis, seedOrder = 11),
            card(koKai.id, CardType.WRITING, dueAt = nowMillis, seedOrder = 12),
            card(koKai.id, CardType.AUDIO_RECOGNITION, dueAt = nowMillis, seedOrder = 13),
            card(khoKhai.id, CardType.WRITING, dueAt = nowMillis + 60_000, seedOrder = 22),
            card(mae.id, CardType.RECOGNITION, dueAt = nowMillis, seedOrder = 31),
        )

        val snapshot = buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = emptyList(),
            streak = streak,
            modelState = null,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        assertTrue(snapshot.lessons[0].unlocked)
        assertFalse(snapshot.lessons[1].unlocked)
        assertEquals(3, snapshot.dueCards.size)
        assertEquals(1, snapshot.dueRecognitionCount)
        assertEquals(1, snapshot.dueWritingCount)
        assertEquals(1, snapshot.dueAudioCount)
        assertEquals(lessonOne.id, snapshot.nextLessonId)
        assertEquals(nowMillis + 60_000, snapshot.nextDueAtMillis)
        assertEquals(nowMillis + 60_000, snapshot.lessons.first().nextDueWritingAtMillis)
        assertTrue(snapshot.dueCards.all { it.item.lessonId == lessonOne.id })
    }

    @Test
    fun `snapshot unlocks next lesson once writing cards are mastered`() {
        val cards = listOf(
            card(koKai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 12),
            card(khoKhai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 22),
            card(mae.id, CardType.RECOGNITION, dueAt = nowMillis, seedOrder = 31),
            card(mae.id, CardType.WRITING, dueAt = nowMillis, seedOrder = 32),
            card(mae.id, CardType.AUDIO_RECOGNITION, dueAt = nowMillis, seedOrder = 33),
        )
        val progress = listOf(LessonProgressEntity(lessonId = lessonOne.id, lessonOrder = 1, startedAt = nowMillis - 1000))
        val modelState = ModelDownloadStateEntity(id = 1, downloadedAt = nowMillis - 5000, lastError = null)

        val snapshot = buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = progress,
            streak = streak,
            modelState = modelState,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        assertTrue(snapshot.lessons[1].unlocked)
        assertEquals(lessonTwo.id, snapshot.nextLessonId)
        assertEquals(1, snapshot.completedLessonCount)
        assertEquals(1, snapshot.startedLessonCount)
        assertTrue(snapshot.modelDownloaded)
        assertEquals(listOf(mae), snapshot.usefulWords)
        assertEquals("mother", snapshot.dueCards.first { it.card.cardType == CardType.RECOGNITION.name }.primaryPrompt)
    }

    @Test
    fun `preview items do not block required lesson progression`() {
        val previewWord = item(
            id = "preview_word",
            lessonId = lessonOne.id,
            sortOrder = 4,
            type = ItemType.WORD,
            thai = "ไม่",
            transliteration = "mai",
            english = "not",
            audioText = "ไม่",
            prompt = "Write not in Thai.",
            category = "Essentials",
            teachingNote = "Preview word.",
            teachingMode = TeachingMode.PREVIEW,
            components = listOf(ItemComponentSeed(itemId = koKai.id, note = "known part")),
        )
        val previewLessonOne = lessonOne.copy(itemIds = listOf(koKai.id, previewWord.id))
        val localSeeds = SeedBundle(
            lessons = listOf(previewLessonOne, lessonTwo),
            items = listOf(koKai, previewWord, mae),
            guides = emptyMap(),
        )
        val cards = listOf(
            card(koKai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 12),
            card(previewWord.id, CardType.WRITING, state = CardState.NEW.name, dueAt = nowMillis + 100_000, seedOrder = 42),
            card(mae.id, CardType.WRITING, dueAt = nowMillis, seedOrder = 32),
        )

        val snapshot = buildLibrarySnapshot(
            seeds = localSeeds,
            cards = cards,
            progress = emptyList(),
            streak = streak,
            modelState = null,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        assertEquals(1, snapshot.lessons.first().requiredMasteredCount)
        assertEquals(1, snapshot.lessons.first().requiredTotalCount)
        assertEquals(1, snapshot.lessons.first().masteredCount)
        assertEquals(2, snapshot.lessons.first().totalCount)
        assertTrue(snapshot.lessons[1].unlocked)
        assertEquals(lessonTwo.id, snapshot.nextLessonId)
        assertEquals(1, snapshot.completedLessonCount)
    }

    @Test
    fun `snapshot sorts due cards by due time then seed order`() {
        val cards = listOf(
            card(mae.id, CardType.AUDIO_RECOGNITION, dueAt = nowMillis, seedOrder = 33),
            card(mae.id, CardType.WRITING, dueAt = nowMillis, seedOrder = 32),
            card(mae.id, CardType.RECOGNITION, dueAt = nowMillis, seedOrder = 31),
            card(koKai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 12),
            card(khoKhai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 22),
        )

        val snapshot = buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = emptyList(),
            streak = streak,
            modelState = null,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        assertEquals(
            listOf(CardType.RECOGNITION.name, CardType.WRITING.name, CardType.AUDIO_RECOGNITION.name),
            snapshot.dueCards.map { it.card.cardType },
        )
    }

    @Test
    fun `snapshot maps prompts per card type and carries app metadata`() {
        val cards = listOf(
            card(koKai.id, CardType.RECOGNITION, dueAt = nowMillis, seedOrder = 11),
            card(koKai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 12),
            card(khoKhai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 22),
            card(mae.id, CardType.WRITING, dueAt = nowMillis, seedOrder = 32),
            card(mae.id, CardType.AUDIO_RECOGNITION, dueAt = nowMillis, seedOrder = 33),
        )
        val modelState = ModelDownloadStateEntity(id = 1, downloadedAt = nowMillis - 1000, lastError = null)

        val snapshot = buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = listOf(LessonProgressEntity(lessonId = lessonOne.id, lessonOrder = 1, startedAt = nowMillis - 2000)),
            streak = streak,
            modelState = modelState,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        val recognition = snapshot.dueCards.first { it.card.cardType == CardType.RECOGNITION.name }
        val writing = snapshot.dueCards.first { it.card.cardType == CardType.WRITING.name }
        val audio = snapshot.dueCards.first { it.card.cardType == CardType.AUDIO_RECOGNITION.name }

        assertEquals("ko kai", recognition.primaryPrompt)
        assertEquals("ก ไก่", recognition.secondaryPrompt)
        assertFalse(recognition.requiresWriting)
        assertEquals("Write mother in Thai.", writing.primaryPrompt)
        assertEquals("mae", writing.secondaryPrompt)
        assertTrue(writing.requiresWriting)
        assertEquals("Listen, then picture the Thai spelling.", audio.primaryPrompt)
        assertEquals("mother", audio.secondaryPrompt)
        assertEquals(1, writing.breakdown.size)
        assertFalse(writing.breakdown.first().comingSoon)
        assertEquals("Write the whole word slowly.", writing.guide?.tip)
        assertTrue(snapshot.onboardingComplete)
        assertEquals(7, snapshot.reminderHour)
        assertEquals(30, snapshot.reminderMinute)
        assertEquals(3, snapshot.streak)
        assertEquals(8, snapshot.maxStreak)
        assertTrue(snapshot.modelDownloaded)
    }

    @Test
    fun `snapshot returns null next lesson when every lesson is mastered`() {
        val cards = listOf(
            card(koKai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 12),
            card(khoKhai.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 22),
            card(mae.id, CardType.WRITING, state = CardState.REVIEW.name, dueAt = nowMillis + 100_000, seedOrder = 32),
        )

        val snapshot = buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = emptyList(),
            streak = streak,
            modelState = null,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        assertEquals(2, snapshot.completedLessonCount)
        assertEquals(3, snapshot.masteredWritingCount)
        assertEquals(3, snapshot.totalWritingCount)
        assertEquals(null, snapshot.nextLessonId)
    }

    @Test
    fun `snapshot lesson due counts ignore future cards`() {
        val cards = listOf(
            card(koKai.id, CardType.RECOGNITION, dueAt = nowMillis, seedOrder = 11),
            card(koKai.id, CardType.WRITING, dueAt = nowMillis + 1_000, seedOrder = 12),
            card(khoKhai.id, CardType.AUDIO_RECOGNITION, dueAt = nowMillis - 1_000, seedOrder = 23),
        )

        val snapshot = buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = emptyList(),
            streak = streak,
            modelState = null,
            settingsState = settings,
            nowMillis = nowMillis,
        )

        assertEquals(2, snapshot.lessons.first().dueCount)
        assertEquals(2, snapshot.dueCards.size)
    }

    private fun item(
        id: String,
        lessonId: String,
        sortOrder: Int,
        type: ItemType,
        thai: String,
        transliteration: String,
        english: String,
        audioText: String,
        prompt: String,
        category: String? = null,
        teachingNote: String? = null,
        teachingMode: TeachingMode? = null,
        components: List<ItemComponentSeed> = emptyList(),
    ) = StudyItemSeed(
        id = id,
        lessonId = lessonId,
        sortOrder = sortOrder,
        type = type,
        thai = thai,
        transliteration = transliteration,
        english = english,
        audioText = audioText,
        prompt = prompt,
        category = category,
        teachingNote = teachingNote,
        teachingMode = teachingMode,
        components = components,
    )

    private fun intro(example: String) = LessonIntroSeed(
        whatThisIs = "What",
        howItBehaves = "How",
        whyItMatters = "Why",
        example = example,
    )

    private fun card(
        itemId: String,
        cardType: CardType,
        state: String = CardState.NEW.name,
        dueAt: Long,
        seedOrder: Int,
    ) = StudyCardEntity(
        itemId = itemId,
        cardType = cardType.name,
        state = state,
        dueAt = dueAt,
        stability = 1.0,
        difficulty = 5.0,
        lastReviewedAt = null,
        scheduledDays = 0,
        learningStep = 0,
        reps = 0,
        lapses = 0,
        lastOutcomePass = null,
        seedOrder = seedOrder,
    )
}
