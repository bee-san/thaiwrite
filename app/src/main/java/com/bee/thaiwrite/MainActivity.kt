package com.bee.thaiwrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.bee.thaiwrite.data.db.StudyDatabase
import com.bee.thaiwrite.data.repo.StudyRepository
import com.bee.thaiwrite.domain.fsrs.FsrsPassFailScheduler
import com.bee.thaiwrite.domain.practice.HandwritingRecognitionService
import com.bee.thaiwrite.system.AppSettings
import com.bee.thaiwrite.system.AudioPromptPlayer
import com.bee.thaiwrite.system.GithubReleaseUpdater
import com.bee.thaiwrite.system.ReminderScheduler
import com.bee.thaiwrite.ui.AppViewModel
import com.bee.thaiwrite.ui.ThaiWriteApp
import com.bee.thaiwrite.ui.theme.ThaiWriteTheme

class MainActivity : ComponentActivity() {
    private val database by lazy { StudyDatabase.build(applicationContext) }
    private val settings by lazy { AppSettings(applicationContext) }
    private val repository by lazy {
        StudyRepository(
            context = applicationContext,
            dao = database.studyDao(),
            settings = settings,
            scheduler = FsrsPassFailScheduler(),
        )
    }
    private val handwriting by lazy { HandwritingRecognitionService(applicationContext) }
    private val reminders by lazy { ReminderScheduler(applicationContext) }
    private val audioPromptPlayer by lazy { AudioPromptPlayer(applicationContext) }
    private val githubReleaseUpdater by lazy { GithubReleaseUpdater(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(
            this,
            AppViewModel.factory(
                repository = repository,
                settings = settings,
                handwriting = handwriting,
                reminderScheduler = reminders,
                audioPromptPlayer = audioPromptPlayer,
                githubReleaseUpdater = githubReleaseUpdater,
            ),
        )[AppViewModel::class.java]

        setContent {
            ThaiWriteTheme {
                ThaiWriteApp(viewModel = viewModel)
            }
        }
    }
}
