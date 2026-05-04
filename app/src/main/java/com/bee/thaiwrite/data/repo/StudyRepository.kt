package com.bee.thaiwrite.data.repo

import android.content.Context
import com.bee.thaiwrite.data.db.CardState
import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.DailyStreakEntity
import com.bee.thaiwrite.data.db.LessonProgressEntity
import com.bee.thaiwrite.data.db.ModelDownloadStateEntity
import com.bee.thaiwrite.data.db.ReviewLogEntity
import com.bee.thaiwrite.data.db.StudyCardEntity
import com.bee.thaiwrite.data.db.StudyDao
import com.bee.thaiwrite.data.model.GuideSeed
import com.bee.thaiwrite.data.model.LessonSeed
import com.bee.thaiwrite.data.model.SeedBundle
import com.bee.thaiwrite.data.model.SeedLoader
import com.bee.thaiwrite.data.model.StudyItemSeed
import com.bee.thaiwrite.domain.fsrs.FsrsPassFailScheduler
import com.bee.thaiwrite.system.AppSettings
import com.bee.thaiwrite.system.SettingsState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LessonItemProgress(
    val item: StudyItemSeed,
    val guide: GuideSeed?,
    val recognitionCard: StudyCardEntity?,
    val writingCard: StudyCardEntity?,
    val audioCard: StudyCardEntity?,
)

enum class ReviewPromptMode {
    RECOGNITION,
    WRITING,
    AUDIO,
}

data class LessonOverview(
    val lesson: LessonSeed,
    val unlocked: Boolean,
    val started: Boolean,
    val masteredCount: Int,
    val totalCount: Int,
    val dueCount: Int,
    val items: List<LessonItemProgress>,
)

data class ReviewCardView(
    val item: StudyItemSeed,
    val card: StudyCardEntity,
    val guide: GuideSeed?,
    val promptMode: ReviewPromptMode,
    val primaryPrompt: String,
    val secondaryPrompt: String,
    val requiresWriting: Boolean,
)

data class LibrarySnapshot(
    val onboardingComplete: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val streak: Int,
    val maxStreak: Int,
    val modelDownloaded: Boolean,
    val lessons: List<LessonOverview>,
    val dueCards: List<ReviewCardView>,
    val startedLessonCount: Int,
    val completedLessonCount: Int,
    val masteredWritingCount: Int,
    val totalWritingCount: Int,
    val dueRecognitionCount: Int,
    val dueWritingCount: Int,
    val dueAudioCount: Int,
    val focusWords: List<StudyItemSeed>,
    val nextLessonId: String?,
)

class StudyRepository(
    context: Context,
    private val dao: StudyDao,
    private val settings: AppSettings,
    private val scheduler: FsrsPassFailScheduler,
) {
    private val seeds: SeedBundle = SeedLoader.load(context)
    private val lessonsById = seeds.lessons.associateBy { it.id }
    private val itemsById = seeds.items.associateBy { it.id }
    private val zoneId = ZoneId.systemDefault()

    val snapshot: Flow<LibrarySnapshot> = combine(
        dao.observeCards(),
        dao.observeLessonProgress(),
        dao.observeStreak(),
        dao.observeModelState(),
        settings.settings,
    ) { cards, progress, streak, modelState, settingsState ->
        buildSnapshot(
            cards = cards,
            progress = progress,
            streak = streak ?: DailyStreakEntity(currentStreak = 0, maxStreak = 0, lastStudyDay = null),
            modelState = modelState,
            settingsState = settingsState,
        )
    }

    suspend fun seedIfNeeded() {
        if (dao.getStreak() == null) {
            dao.upsertStreak(DailyStreakEntity(id = 1, currentStreak = 0, maxStreak = 0, lastStudyDay = null))
        }
        if (dao.getModelState() == null) {
            dao.upsertModelState(ModelDownloadStateEntity(id = 1, downloadedAt = null, lastError = null))
        }
        dao.getLessonProgress().forEach { progress ->
            lessonsById[progress.lessonId]?.let { lesson ->
                ensureCardsForLesson(lesson)
            }
        }
    }

    suspend fun markModelDownloaded(downloaded: Boolean, error: String? = null) {
        dao.upsertModelState(
            ModelDownloadStateEntity(
                id = 1,
                downloadedAt = if (downloaded) Instant.now().toEpochMilli() else null,
                lastError = error,
            ),
        )
    }

    suspend fun startLesson(lessonId: String) {
        val lesson = lessonsById.getValue(lessonId)
        ensureCardsForLesson(lesson)
        dao.upsertLessonProgress(
            LessonProgressEntity(
                lessonId = lesson.id,
                lessonOrder = lesson.order,
                startedAt = Instant.now().toEpochMilli(),
            ),
        )
    }

    suspend fun submitWritingReview(
        itemId: String,
        passed: Boolean,
        recognizedText: String?,
        responseMs: Long,
        reviewedAt: Instant = Instant.now(),
    ) {
        reviewCard(
            itemId = itemId,
            cardType = CardType.WRITING,
            passed = passed,
            recognizedText = recognizedText,
            responseMs = responseMs,
            reviewedAt = reviewedAt,
        )
    }

    suspend fun submitRecognitionReview(
        itemId: String,
        passed: Boolean,
        responseMs: Long,
        reviewedAt: Instant = Instant.now(),
    ) {
        submitRecallReview(
            itemId = itemId,
            cardType = CardType.RECOGNITION,
            passed = passed,
            responseMs = responseMs,
            reviewedAt = reviewedAt,
        )
    }

    suspend fun submitRecallReview(
        itemId: String,
        cardType: CardType,
        passed: Boolean,
        responseMs: Long,
        reviewedAt: Instant = Instant.now(),
    ) {
        reviewCard(
            itemId = itemId,
            cardType = cardType,
            passed = passed,
            recognizedText = null,
            responseMs = responseMs,
            reviewedAt = reviewedAt,
        )
    }

    fun lesson(lessonId: String): LessonSeed = lessonsById.getValue(lessonId)

    fun lessonItems(lessonId: String): List<StudyItemSeed> =
        seeds.items.filter { it.lessonId == lessonId }.sortedBy { it.sortOrder }

    fun guideFor(itemId: String): GuideSeed? = seeds.guides[itemId]

    private suspend fun ensureCardsForLesson(lesson: LessonSeed) {
        val newCards = mutableListOf<StudyCardEntity>()
        lesson.itemIds.forEach { itemId ->
            val item = itemsById.getValue(itemId)
            if (dao.getCard(itemId, CardType.RECOGNITION.name) == null) {
                newCards += defaultCard(item, CardType.RECOGNITION)
            }
            if (dao.getCard(itemId, CardType.WRITING.name) == null) {
                newCards += defaultCard(item, CardType.WRITING)
            }
            if (item.audioText.isNotBlank() && dao.getCard(itemId, CardType.AUDIO_RECOGNITION.name) == null) {
                newCards += defaultCard(item, CardType.AUDIO_RECOGNITION)
            }
        }
        if (newCards.isNotEmpty()) {
            dao.upsertCards(newCards)
        }
    }

    private suspend fun reviewCard(
        itemId: String,
        cardType: CardType,
        passed: Boolean,
        recognizedText: String?,
        responseMs: Long,
        reviewedAt: Instant,
    ) {
        val item = itemsById.getValue(itemId)
        val existingCard = dao.getCard(itemId, cardType.name) ?: defaultCard(item, cardType)
        val scheduled = scheduler.review(existingCard, reviewedAt, passed)
        dao.upsertCard(scheduled.updatedCard)
        dao.insertReviewLog(
            ReviewLogEntity(
                itemId = itemId,
                cardType = cardType.name,
                reviewedAt = reviewedAt.toEpochMilli(),
                passed = passed,
                recognizedText = recognizedText,
                responseMs = responseMs,
                dueAt = scheduled.updatedCard.dueAt,
                scheduledDays = scheduled.updatedCard.scheduledDays,
                stability = scheduled.updatedCard.stability,
                difficulty = scheduled.updatedCard.difficulty,
                state = scheduled.updatedCard.state,
            ),
        )
        updateStreak(reviewedAt)
    }

    private suspend fun updateStreak(reviewedAt: Instant) {
        val today = LocalDate.ofInstant(reviewedAt, zoneId)
        val current = dao.getStreak() ?: DailyStreakEntity(id = 1, currentStreak = 0, maxStreak = 0, lastStudyDay = null)
        val lastDay = current.lastStudyDay?.let(LocalDate::parse)
        val nextStreak = when {
            lastDay == null -> 1
            lastDay == today -> current.currentStreak
            lastDay.plusDays(1) == today -> current.currentStreak + 1
            else -> 1
        }
        dao.upsertStreak(
            current.copy(
                currentStreak = nextStreak,
                maxStreak = maxOf(current.maxStreak, nextStreak),
                lastStudyDay = today.toString(),
            ),
        )
    }

    private fun buildSnapshot(
        cards: List<StudyCardEntity>,
        progress: List<LessonProgressEntity>,
        streak: DailyStreakEntity,
        modelState: ModelDownloadStateEntity?,
        settingsState: SettingsState,
    ): LibrarySnapshot =
        buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = progress,
            streak = streak,
            modelState = modelState,
            settingsState = settingsState,
            nowMillis = Instant.now().toEpochMilli(),
        )

    private fun defaultCard(item: StudyItemSeed, cardType: CardType): StudyCardEntity {
        val now = Instant.now().toEpochMilli()
        return StudyCardEntity(
            itemId = item.id,
            cardType = cardType.name,
            state = CardState.NEW.name,
            dueAt = now,
            stability = 0.0,
            difficulty = 0.0,
            lastReviewedAt = null,
            scheduledDays = 0,
            learningStep = 0,
            reps = 0,
            lapses = 0,
            lastOutcomePass = null,
            seedOrder = item.sortOrder * 10 + when (cardType) {
                CardType.RECOGNITION -> 1
                CardType.WRITING -> 2
                CardType.AUDIO_RECOGNITION -> 3
            },
        )
    }
}
