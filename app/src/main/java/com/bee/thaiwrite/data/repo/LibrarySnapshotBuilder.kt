package com.bee.thaiwrite.data.repo

import com.bee.thaiwrite.data.db.CardState
import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.DailyStreakEntity
import com.bee.thaiwrite.data.db.LessonProgressEntity
import com.bee.thaiwrite.data.db.ModelDownloadStateEntity
import com.bee.thaiwrite.data.db.StudyCardEntity
import com.bee.thaiwrite.data.model.ItemType
import com.bee.thaiwrite.data.model.SeedBundle
import com.bee.thaiwrite.data.model.StudyItemSeed
import com.bee.thaiwrite.data.model.TeachingMode
import com.bee.thaiwrite.system.SettingsState

internal fun buildLibrarySnapshot(
    seeds: SeedBundle,
    cards: List<StudyCardEntity>,
    progress: List<LessonProgressEntity>,
    streak: DailyStreakEntity,
    modelState: ModelDownloadStateEntity?,
    settingsState: SettingsState,
    nowMillis: Long,
): LibrarySnapshot {
    val itemsById = seeds.items.associateBy { it.id }
    val lessonOrderById = seeds.lessons.associate { it.id to it.order }
    val progressByLesson = progress.associateBy { it.lessonId }
    val lessonItems = seeds.lessons.associateWith { lesson ->
        lesson.itemIds.map { itemId ->
            val item = itemsById.getValue(itemId)
            LessonItemProgress(
                item = item,
                guide = seeds.guides[item.id],
                recognitionCard = cards.firstOrNull { it.itemId == itemId && it.cardType == CardType.RECOGNITION.name },
                writingCard = cards.firstOrNull { it.itemId == itemId && it.cardType == CardType.WRITING.name },
                audioCard = cards.firstOrNull { it.itemId == itemId && it.cardType == CardType.AUDIO_RECOGNITION.name },
                breakdown = buildBreakdown(item, itemsById, lessonOrderById),
            )
        }
    }
    val itemLessonMap = seeds.lessons.flatMap { lesson -> lesson.itemIds.map { it to lesson.id } }.toMap()

    var previousMastered = true
    val lessons = seeds.lessons.map { lesson ->
        val items = lessonItems.getValue(lesson)
        val requiredItems = items.filter { it.item.isRequiredForProgress() }
        val masteredCount = items.count { it.writingCard?.state == CardState.REVIEW.name }
        val totalCount = items.size
        val requiredMasteredCount = requiredItems.count { it.writingCard?.state == CardState.REVIEW.name }
        val requiredTotalCount = requiredItems.size
        val unlocked = previousMastered
        val dueCount = items.sumOf { item ->
            listOfNotNull(item.recognitionCard, item.writingCard, item.audioCard).count { it.dueAt <= nowMillis }
        }
        val nextDueWritingAtMillis = items
            .mapNotNull { it.writingCard?.dueAt }
            .filter { it > nowMillis }
            .minOrNull()
        val overview = LessonOverview(
            lesson = lesson,
            unlocked = unlocked,
            started = progressByLesson[lesson.id]?.startedAt != null,
            masteredCount = masteredCount,
            totalCount = totalCount,
            requiredMasteredCount = requiredMasteredCount,
            requiredTotalCount = requiredTotalCount,
            dueCount = dueCount,
            nextDueWritingAtMillis = nextDueWritingAtMillis,
            items = items,
        )
        previousMastered = unlocked && requiredMasteredCount == requiredTotalCount
        overview
    }

    val unlockedLessons = lessons.filter { it.unlocked }.associateBy { it.lesson.id }
    val unlockedLessonIds = unlockedLessons.keys
    val dueCards = cards
        .filter { it.dueAt <= nowMillis && unlockedLessonIds.contains(itemLessonMap[it.itemId]) }
        .sortedWith(compareBy<StudyCardEntity> { it.dueAt }.thenBy { it.seedOrder })
        .map { card ->
            val item = itemsById.getValue(card.itemId)
            val cardType = CardType.valueOf(card.cardType)
            ReviewCardView(
                item = item,
                card = card,
                guide = seeds.guides[item.id],
                promptMode = when (cardType) {
                    CardType.RECOGNITION -> ReviewPromptMode.RECOGNITION
                    CardType.WRITING -> ReviewPromptMode.WRITING
                    CardType.AUDIO_RECOGNITION -> ReviewPromptMode.AUDIO
                },
                primaryPrompt = when (cardType) {
                    CardType.RECOGNITION -> if (item.type == ItemType.WORD) item.english else item.transliteration
                    CardType.WRITING -> item.prompt
                    CardType.AUDIO_RECOGNITION -> "Listen, then picture the Thai spelling."
                },
                secondaryPrompt = when (cardType) {
                    CardType.RECOGNITION -> item.audioText
                    CardType.WRITING -> item.transliteration
                    CardType.AUDIO_RECOGNITION -> if (item.type == ItemType.WORD) item.english else item.transliteration
                },
                requiresWriting = card.cardType == CardType.WRITING.name,
                breakdown = buildBreakdown(item, itemsById, lessonOrderById),
            )
        }

    val nextLessonId = lessons.firstOrNull {
        it.unlocked && it.requiredMasteredCount < it.requiredTotalCount
    }?.lesson?.id
    val nextDueAtMillis = cards
        .filter { unlockedLessonIds.contains(itemLessonMap[it.itemId]) }
        .map { it.dueAt }
        .filter { it > nowMillis }
        .minOrNull()
    val startedLessonCount = lessons.count { it.started }
    val completedLessonCount = lessons.count { it.requiredMasteredCount == it.requiredTotalCount }
    val dueRecognitionCount = dueCards.count { it.promptMode == ReviewPromptMode.RECOGNITION }
    val dueWritingCount = dueCards.count { it.promptMode == ReviewPromptMode.WRITING }
    val dueAudioCount = dueCards.count { it.promptMode == ReviewPromptMode.AUDIO }

    return LibrarySnapshot(
        onboardingComplete = settingsState.onboardingComplete,
        reminderHour = settingsState.reminderHour,
        reminderMinute = settingsState.reminderMinute,
        streak = streak.currentStreak,
        maxStreak = streak.maxStreak,
        modelDownloaded = modelState?.downloadedAt != null,
        lessons = lessons,
        dueCards = dueCards,
        startedLessonCount = startedLessonCount,
        completedLessonCount = completedLessonCount,
        masteredWritingCount = lessons.sumOf { it.masteredCount },
        totalWritingCount = lessons.sumOf { it.totalCount },
        dueRecognitionCount = dueRecognitionCount,
        dueWritingCount = dueWritingCount,
        dueAudioCount = dueAudioCount,
        usefulWords = seeds.items.filter { it.type == ItemType.WORD },
        nextLessonId = nextLessonId,
        nextDueAtMillis = nextDueAtMillis,
    )
}

private fun StudyItemSeed.isRequiredForProgress(): Boolean =
    teachingMode != TeachingMode.PREVIEW

private fun buildBreakdown(
    item: com.bee.thaiwrite.data.model.StudyItemSeed,
    itemsById: Map<String, com.bee.thaiwrite.data.model.StudyItemSeed>,
    lessonOrderById: Map<String, Int>,
): List<ItemBreakdownView> {
    val itemLessonOrder = lessonOrderById.getValue(item.lessonId)
    return item.components.map { component ->
        val componentItem = itemsById.getValue(component.itemId)
        ItemBreakdownView(
            thai = componentItem.thai,
            transliteration = componentItem.transliteration,
            english = componentItem.english,
            note = component.note,
            comingSoon = lessonOrderById.getValue(componentItem.lessonId) >= itemLessonOrder,
        )
    }
}
