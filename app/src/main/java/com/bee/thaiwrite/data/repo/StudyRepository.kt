package com.bee.thaiwrite.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.bee.thaiwrite.data.db.CardState
import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.DailyStreakEntity
import com.bee.thaiwrite.data.db.LessonProgressEntity
import com.bee.thaiwrite.data.db.ModelDownloadStateEntity
import com.bee.thaiwrite.data.db.ReviewLogEntity
import com.bee.thaiwrite.data.db.StudyCardEntity
import com.bee.thaiwrite.data.db.StudyDao
import com.bee.thaiwrite.data.db.StudyDatabase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

data class LessonItemProgress(
    val item: StudyItemSeed,
    val guide: GuideSeed?,
    val recognitionCard: StudyCardEntity?,
    val writingCard: StudyCardEntity?,
    val audioCard: StudyCardEntity?,
    val breakdown: List<ItemBreakdownView>,
)

enum class ReviewPromptMode {
    RECOGNITION,
    WRITING,
    AUDIO,
}

data class ItemBreakdownView(
    val thai: String,
    val transliteration: String,
    val english: String,
    val note: String,
    val comingSoon: Boolean,
)

data class LessonOverview(
    val lesson: LessonSeed,
    val unlocked: Boolean,
    val started: Boolean,
    val masteredCount: Int,
    val totalCount: Int,
    val requiredMasteredCount: Int,
    val requiredTotalCount: Int,
    val dueCount: Int,
    val nextDueWritingAtMillis: Long?,
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
    val breakdown: List<ItemBreakdownView>,
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
    val usefulWords: List<StudyItemSeed>,
    val nextLessonId: String?,
    val nextDueAtMillis: Long?,
)

private data class SnapshotInputs(
    val cards: List<StudyCardEntity>,
    val progress: List<LessonProgressEntity>,
    val streak: DailyStreakEntity,
    val modelState: ModelDownloadStateEntity?,
    val settingsState: SettingsState,
)

class StudyRepository(
    context: Context,
    private val database: StudyDatabase,
    private val dao: StudyDao,
    private val settings: AppSettings,
    private val scheduler: FsrsPassFailScheduler,
) {
    private val seeds: SeedBundle = SeedLoader.load(context)
    private val lessonsById = seeds.lessons.associateBy { it.id }
    private val itemsById = seeds.items.associateBy { it.id }
    private val zoneId = ZoneId.systemDefault()

    private val cardsFlow = dao.observeCards()

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: Flow<LibrarySnapshot> = combine(
        combine(
            cardsFlow,
            dao.observeLessonProgress(),
            dao.observeStreak(),
            dao.observeModelState(),
            settings.settings,
        ) { cards, progress, streak, modelState, settingsState ->
            SnapshotInputs(
                cards = cards,
                progress = progress,
                streak = streak ?: DailyStreakEntity(currentStreak = 0, maxStreak = 0, lastStudyDay = null),
                modelState = modelState,
                settingsState = settingsState,
            )
        },
        cardsFlow.flatMapLatest(::snapshotClock),
    ) { inputs, nowMillis ->
        buildSnapshot(
            cards = inputs.cards,
            progress = inputs.progress,
            streak = inputs.streak,
            modelState = inputs.modelState,
            settingsState = inputs.settingsState,
            nowMillis = nowMillis,
        )
    }

    suspend fun seedIfNeeded() = database.withTransaction {
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
        database.withTransaction {
            ensureCardsForLesson(lesson)
            dao.upsertLessonProgress(
                LessonProgressEntity(
                    lessonId = lesson.id,
                    lessonOrder = lesson.order,
                    startedAt = Instant.now().toEpochMilli(),
                ),
            )
        }
    }

    suspend fun submitWritingReview(
        itemId: String,
        passed: Boolean,
        recognizedText: String?,
        responseMs: Long,
        reviewedAt: Instant = Instant.now(),
        expectedCard: StudyCardEntity? = null,
    ): Boolean = reviewCard(
            itemId = itemId,
            cardType = CardType.WRITING,
            passed = passed,
            recognizedText = recognizedText,
            responseMs = responseMs,
            reviewedAt = reviewedAt,
            expectedCard = expectedCard,
        )

    suspend fun submitRecognitionReview(
        itemId: String,
        passed: Boolean,
        responseMs: Long,
        reviewedAt: Instant = Instant.now(),
        expectedCard: StudyCardEntity? = null,
    ): Boolean = submitRecallReview(
            itemId = itemId,
            cardType = CardType.RECOGNITION,
            passed = passed,
            responseMs = responseMs,
            reviewedAt = reviewedAt,
            expectedCard = expectedCard,
        )

    suspend fun submitRecallReview(
        itemId: String,
        cardType: CardType,
        passed: Boolean,
        responseMs: Long,
        reviewedAt: Instant = Instant.now(),
        expectedCard: StudyCardEntity? = null,
    ): Boolean = reviewCard(
            itemId = itemId,
            cardType = cardType,
            passed = passed,
            recognizedText = null,
            responseMs = responseMs,
            reviewedAt = reviewedAt,
            expectedCard = expectedCard,
        )

    fun lesson(lessonId: String): LessonSeed = lessonsById.getValue(lessonId)

    fun lessonItems(lessonId: String): List<StudyItemSeed> =
        seeds.items.filter { it.lessonId == lessonId }.sortedBy { it.sortOrder }

    fun guideFor(itemId: String): GuideSeed? = seeds.guides[itemId]

    private suspend fun ensureCardsForLesson(lesson: LessonSeed) {
        val nowMillis = Instant.now().toEpochMilli()
        val existingCards = buildMap<Pair<String, CardType>, StudyCardEntity> {
            lesson.itemIds.forEach { itemId ->
                dao.getCard(itemId, CardType.WRITING.name)?.let { put(itemId to CardType.WRITING, it) }
                dao.getCard(itemId, CardType.RECOGNITION.name)?.let { put(itemId to CardType.RECOGNITION, it) }
                dao.getCard(itemId, CardType.AUDIO_RECOGNITION.name)?.let { put(itemId to CardType.AUDIO_RECOGNITION, it) }
            }
        }
        val newCards = createLessonStartCards(
            lesson = lesson,
            itemsById = itemsById,
            existingCards = existingCards,
            nowMillis = nowMillis,
        )
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
        expectedCard: StudyCardEntity? = null,
    ): Boolean = database.withTransaction {
        val item = itemsById.getValue(itemId)
        val nowMillis = reviewedAt.toEpochMilli()
        val existingCard = dao.getCard(itemId, cardType.name) ?: return@withTransaction false
        if (!acceptsSubmission(existingCard, expectedCard, nowMillis)) {
            return@withTransaction false
        }
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
        if (passed) {
            when (cardType) {
                CardType.WRITING -> {
                    val unlockedCard = activateDependentCard(
                        item = item,
                        reviewedCardType = cardType,
                        existingCard = dao.getCard(itemId, CardType.RECOGNITION.name),
                        nowMillis = nowMillis,
                    )
                    if (unlockedCard != null) {
                        dao.upsertCard(unlockedCard)
                    }
                }

                CardType.RECOGNITION -> {
                    val unlockedCard = activateDependentCard(
                        item = item,
                        reviewedCardType = cardType,
                        existingCard = dao.getCard(itemId, CardType.AUDIO_RECOGNITION.name),
                        nowMillis = nowMillis,
                    )
                    if (unlockedCard != null) {
                        dao.upsertCard(unlockedCard)
                    }
                }

                CardType.AUDIO_RECOGNITION -> Unit
            }
        }
        updateStreak(reviewedAt)
        true
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
        nowMillis: Long,
    ): LibrarySnapshot =
        buildLibrarySnapshot(
            seeds = seeds,
            cards = cards,
            progress = progress,
            streak = streak,
            modelState = modelState,
            settingsState = settingsState,
            nowMillis = nowMillis,
        )
}

internal const val IMMEDIATE_LESSON_QUEUE_LIMIT = 4
internal const val SNAPSHOT_FALLBACK_REFRESH_MILLIS = 60_000L

private fun snapshotClock(cards: List<StudyCardEntity>): Flow<Long> = flow {
    while (true) {
        val nowMillis = Instant.now().toEpochMilli()
        emit(nowMillis)
        delay(
            nextSnapshotRefreshDelayMillis(
                dueAtMillis = cards.map { it.dueAt },
                nowMillis = nowMillis,
            ),
        )
    }
}

internal fun nextSnapshotRefreshDelayMillis(
    dueAtMillis: List<Long>,
    nowMillis: Long,
    fallbackMillis: Long = SNAPSHOT_FALLBACK_REFRESH_MILLIS,
): Long {
    val nextFutureDueAt = dueAtMillis.filter { it > nowMillis }.minOrNull()
    return (nextFutureDueAt?.minus(nowMillis) ?: fallbackMillis)
        .coerceIn(1L, fallbackMillis)
}

internal fun createLessonStartCards(
    lesson: LessonSeed,
    itemsById: Map<String, StudyItemSeed>,
    existingCards: Map<Pair<String, CardType>, StudyCardEntity>,
    nowMillis: Long,
): List<StudyCardEntity> = lesson.itemIds.mapIndexedNotNull { index, itemId ->
    if (existingCards[itemId to CardType.WRITING] != null) {
        null
    } else {
        seedCard(
            item = itemsById.getValue(itemId),
            cardType = CardType.WRITING,
            dueAt = lessonStartDueAt(index, nowMillis),
        )
    }
}

internal fun activateDependentCard(
    item: StudyItemSeed,
    reviewedCardType: CardType,
    existingCard: StudyCardEntity?,
    nowMillis: Long,
): StudyCardEntity? {
    val nextCardType = when (reviewedCardType) {
        CardType.WRITING -> CardType.RECOGNITION
        CardType.RECOGNITION -> if (item.audioText.isBlank()) null else CardType.AUDIO_RECOGNITION
        CardType.AUDIO_RECOGNITION -> null
    } ?: return null

    return when {
        existingCard == null -> seedCard(item, nextCardType, nowMillis)
        existingCard.lastReviewedAt == null && existingCard.dueAt > nowMillis -> existingCard.copy(dueAt = nowMillis)
        else -> null
    }
}

internal fun acceptsSubmission(
    currentCard: StudyCardEntity,
    expectedCard: StudyCardEntity?,
    nowMillis: Long,
): Boolean = expectedCard != null && currentCard == expectedCard && currentCard.dueAt <= nowMillis

internal fun seedCard(
    item: StudyItemSeed,
    cardType: CardType,
    dueAt: Long,
): StudyCardEntity = StudyCardEntity(
    itemId = item.id,
    cardType = cardType.name,
    state = CardState.NEW.name,
    dueAt = dueAt,
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

private fun lessonStartDueAt(index: Int, nowMillis: Long): Long =
    if (index < IMMEDIATE_LESSON_QUEUE_LIMIT) {
        nowMillis
    } else {
        nowMillis + ((index - IMMEDIATE_LESSON_QUEUE_LIMIT + 1) * 60_000L)
    }
