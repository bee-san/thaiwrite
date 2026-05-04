package com.bee.thaiwrite.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardType {
    RECOGNITION,
    WRITING,
    AUDIO_RECOGNITION,
}

enum class CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING,
}

@Entity(
    tableName = "study_cards",
    indices = [Index(value = ["itemId", "cardType"], unique = true)],
)
data class StudyCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val cardType: String,
    val state: String,
    val dueAt: Long,
    val stability: Double,
    val difficulty: Double,
    val lastReviewedAt: Long?,
    val scheduledDays: Int,
    val learningStep: Int,
    val reps: Int,
    val lapses: Int,
    val lastOutcomePass: Boolean?,
    val seedOrder: Int,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val lessonOrder: Int,
    val startedAt: Long?,
)

@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val cardType: String,
    val reviewedAt: Long,
    val passed: Boolean,
    val recognizedText: String?,
    val responseMs: Long,
    val dueAt: Long,
    val scheduledDays: Int,
    val stability: Double,
    val difficulty: Double,
    val state: String,
)

@Entity(tableName = "daily_streak")
data class DailyStreakEntity(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int,
    val maxStreak: Int,
    val lastStudyDay: String?,
)

@Entity(tableName = "model_download_state")
data class ModelDownloadStateEntity(
    @PrimaryKey val id: Int = 1,
    val downloadedAt: Long?,
    val lastError: String?,
)
