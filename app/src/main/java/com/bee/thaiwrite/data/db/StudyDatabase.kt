package com.bee.thaiwrite.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudyCardEntity::class,
        LessonProgressEntity::class,
        ReviewLogEntity::class,
        DailyStreakEntity::class,
        ModelDownloadStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        fun build(context: Context): StudyDatabase =
            Room.databaseBuilder(
                context,
                StudyDatabase::class.java,
                "thaiwrite.db",
            ).fallbackToDestructiveMigration(true).build()
    }
}
