package com.bee.thaiwrite.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_cards ORDER BY dueAt ASC, seedOrder ASC")
    fun observeCards(): Flow<List<StudyCardEntity>>

    @Query("SELECT * FROM lesson_progress ORDER BY lessonOrder ASC")
    fun observeLessonProgress(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress ORDER BY lessonOrder ASC")
    suspend fun getLessonProgress(): List<LessonProgressEntity>

    @Query("SELECT * FROM daily_streak WHERE id = 1")
    fun observeStreak(): Flow<DailyStreakEntity?>

    @Query("SELECT * FROM model_download_state WHERE id = 1")
    fun observeModelState(): Flow<ModelDownloadStateEntity?>

    @Query("SELECT * FROM daily_streak WHERE id = 1")
    suspend fun getStreak(): DailyStreakEntity?

    @Query("SELECT * FROM model_download_state WHERE id = 1")
    suspend fun getModelState(): ModelDownloadStateEntity?

    @Query("SELECT COUNT(*) FROM study_cards")
    suspend fun countCards(): Int

    @Query("SELECT * FROM study_cards WHERE itemId = :itemId AND cardType = :cardType LIMIT 1")
    suspend fun getCard(itemId: String, cardType: String): StudyCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: StudyCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCards(cards: List<StudyCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonProgress(progress: LessonProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(streak: DailyStreakEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelState(state: ModelDownloadStateEntity)

    @Insert
    suspend fun insertReviewLog(log: ReviewLogEntity)
}
