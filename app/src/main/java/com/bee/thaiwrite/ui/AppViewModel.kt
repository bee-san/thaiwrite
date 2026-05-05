package com.bee.thaiwrite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bee.thaiwrite.data.db.CardType
import com.bee.thaiwrite.data.db.StudyCardEntity
import com.bee.thaiwrite.data.repo.LibrarySnapshot
import com.bee.thaiwrite.data.repo.StudyRepository
import com.bee.thaiwrite.domain.practice.HandwritingRecognitionService
import com.bee.thaiwrite.domain.practice.StrokePoint
import com.bee.thaiwrite.system.AppSettings
import com.bee.thaiwrite.system.AudioPromptPlayer
import com.bee.thaiwrite.system.GithubReleaseUpdate
import com.bee.thaiwrite.system.GithubReleaseUpdater
import com.bee.thaiwrite.system.ReminderScheduler
import com.bee.thaiwrite.system.ThaiAudioSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val snapshot: LibrarySnapshot? = null,
    val handwritingModelReady: Boolean = false,
    val thaiAudioReady: Boolean = false,
    val thaiAudioStatus: String = "Checking Thai audio voice...",
    val thaiAudioEngine: String? = null,
    val updateSupported: Boolean = false,
    val updateChecking: Boolean = false,
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Int? = null,
    val updateInfo: GithubReleaseUpdate? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

data class WritingAssessment(
    val passed: Boolean,
    val topCandidate: String?,
    val candidates: List<String>,
    val reviewRecorded: Boolean,
)

class AppViewModel(
    private val repository: StudyRepository,
    private val settings: AppSettings,
    private val handwriting: HandwritingRecognitionService,
    private val reminderScheduler: ReminderScheduler,
    private val audioPromptPlayer: AudioPromptPlayer,
    private val githubReleaseUpdater: GithubReleaseUpdater,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState(updateSupported = githubReleaseUpdater.isSupported()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfNeeded()
            repository.snapshot.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        snapshot = snapshot,
                    )
                }
            }
        }
        refreshSupportState()
        refreshUpdateState(manual = false)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun postMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun refreshSupportState() {
        viewModelScope.launch {
            val modelReady = runCatching { handwriting.isModelDownloaded() }.getOrDefault(false)
            repository.markModelDownloaded(modelReady)
            val audioSupport = runCatching { audioPromptPlayer.diagnoseThaiSupport() }.getOrElse { error ->
                ThaiAudioSupport(
                    ready = false,
                    engineLabel = null,
                    enginePackage = null,
                    message = error.message ?: "Unable to inspect Thai audio on this device.",
                )
            }
            _uiState.update {
                it.copy(
                    handwritingModelReady = modelReady,
                    thaiAudioReady = audioSupport.ready,
                    thaiAudioStatus = audioSupport.message,
                    thaiAudioEngine = audioSupport.engineLabel,
                    updateSupported = githubReleaseUpdater.isSupported(),
                )
            }
        }
    }

    fun openThaiAudioSetup() {
        runCatching {
            audioPromptPlayer.openThaiSetup()
        }.onSuccess {
            postMessage("Open Android Text-to-speech settings, install a Thai voice, then return and refresh.")
        }.onFailure { error ->
            postMessage(error.message ?: "Unable to open Android Text-to-speech settings.")
        }
    }

    fun refreshUpdateState(manual: Boolean = true) {
        if (!githubReleaseUpdater.isSupported()) {
            _uiState.update {
                it.copy(
                    updateSupported = false,
                    updateChecking = false,
                    updateInfo = null,
                )
            }
            if (manual) {
                postMessage("GitHub release updates are enabled only in the signed release APK.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateSupported = true,
                    updateChecking = true,
                )
            }
            runCatching {
                githubReleaseUpdater.checkForUpdate()
            }.onSuccess { update ->
                _uiState.update {
                    it.copy(
                        updateChecking = false,
                        updateDownloading = false,
                        updateInfo = update,
                    )
                }
                if (manual) {
                    postMessage(
                        if (update == null) {
                            "You already have the latest GitHub release."
                        } else {
                            "Update ${update.latestVersionName} is available."
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(updateChecking = false) }
                if (manual) {
                    postMessage(error.message ?: "Unable to check GitHub Releases right now.")
                }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val update = _uiState.value.updateInfo
        if (update == null) {
            postMessage("No update is ready to install.")
            return
        }
        if (!githubReleaseUpdater.canRequestPackageInstalls()) {
            githubReleaseUpdater.openInstallPermissionSettings()
            postMessage("Allow installs from ThaiWrite, then tap install again.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateDownloading = true,
                    updateDownloadProgress = 0,
                )
            }
            runCatching {
                val apkFile = githubReleaseUpdater.downloadUpdateApk(update) { progress ->
                    _uiState.update { state -> state.copy(updateDownloadProgress = progress) }
                }
                githubReleaseUpdater.launchInstaller(apkFile)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        updateDownloading = false,
                        updateDownloadProgress = 100,
                    )
                }
                postMessage("Downloaded ${update.latestVersionName}. Android installer opened.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        updateDownloading = false,
                        updateDownloadProgress = null,
                    )
                }
                postMessage(error.message ?: "Unable to download the update.")
            }
        }
    }

    fun openUpdatePage() {
        runCatching {
            githubReleaseUpdater.openReleasePage(_uiState.value.updateInfo?.releaseUrl)
        }.onFailure { error ->
            postMessage(error.message ?: "Unable to open the release page.")
        }
    }

    fun downloadHandwritingModel(requireWifi: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching {
                handwriting.ensureModelDownloaded(requireWifi)
            }.onSuccess {
                repository.markModelDownloaded(true)
                _uiState.update {
                    it.copy(
                        busy = false,
                        handwritingModelReady = true,
                        message = "Thai handwriting model downloaded.",
                    )
                }
            }.onFailure { error ->
                val stillDownloaded = runCatching { handwriting.isModelDownloaded() }.getOrDefault(false)
                repository.markModelDownloaded(stillDownloaded, error.message)
                _uiState.update {
                    it.copy(
                        busy = false,
                        handwritingModelReady = stillDownloaded,
                        message = error.message ?: "Unable to download the handwriting model.",
                    )
                }
            }
        }
    }

    fun finishOnboarding(hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.updateReminder(hour, minute)
            settings.updateOnboardingComplete(true)
            reminderScheduler.scheduleDaily(hour, minute)
        }
    }

    fun updateReminder(hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.updateReminder(hour, minute)
            reminderScheduler.scheduleDaily(hour, minute)
            _uiState.update { it.copy(message = "Reminder moved to ${formatTime(hour, minute)}.") }
        }
    }

    fun startLesson(lessonId: String, onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                repository.startLesson(lessonId)
            }.onSuccess {
                onStarted()
            }.onFailure { error ->
                postMessage(error.message ?: "Unable to start this lesson.")
            }
        }
    }

    suspend fun recordRecallReview(
        itemId: String,
        cardType: String,
        passed: Boolean,
        expectedCard: StudyCardEntity,
    ): Boolean {
        val parsedCardType = runCatching { CardType.valueOf(cardType) }.getOrElse {
            postMessage("Unknown review prompt type: $cardType.")
            return false
        }
        val accepted = repository.submitRecallReview(
            itemId = itemId,
            cardType = parsedCardType,
            passed = passed,
            responseMs = 0L,
            expectedCard = expectedCard,
        )
        if (!accepted) {
            postMessage("That prompt was already saved. Move to the next one.")
        }
        return accepted
    }

    suspend fun recordManualWritingReview(
        itemId: String,
        passed: Boolean,
        expectedCard: StudyCardEntity,
    ): Boolean {
        val accepted = repository.submitWritingReview(
            itemId = itemId,
            passed = passed,
            recognizedText = null,
            responseMs = 0L,
            expectedCard = expectedCard,
        )
        if (!accepted) {
            postMessage("That writing prompt was already saved. Move to the next one.")
        }
        return accepted
    }

    suspend fun recognizeWriting(
        acceptedTargets: List<String>,
        strokes: List<List<StrokePoint>>,
        canvasWidth: Float,
        canvasHeight: Float,
    ): WritingAssessment {
        require(strokes.any { it.isNotEmpty() }) { "Write something before checking." }
        require(canvasWidth > 0f && canvasHeight > 0f) { "The writing area is still loading. Try again." }
        val result = handwriting.recognize(
            strokes = strokes,
            width = canvasWidth,
            height = canvasHeight,
        )
        val passed = HandwritingRecognitionService.matchesAnyExpected(acceptedTargets, result.candidates)
        return WritingAssessment(
            passed = passed,
            topCandidate = result.topText,
            candidates = result.candidates,
            reviewRecorded = false,
        )
    }

    suspend fun assessDueWriting(
        itemId: String,
        acceptedTargets: List<String>,
        strokes: List<List<StrokePoint>>,
        canvasWidth: Float,
        canvasHeight: Float,
        responseMs: Long = 0L,
        expectedCard: StudyCardEntity,
    ): WritingAssessment {
        val result = recognizeWriting(
            acceptedTargets = acceptedTargets,
            strokes = strokes,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
        )
        val accepted = repository.submitWritingReview(
            itemId = itemId,
            passed = result.passed,
            recognizedText = result.topCandidate,
            responseMs = responseMs,
            expectedCard = expectedCard,
        )
        check(accepted) { "That writing prompt was already saved. Move to the next one." }
        return result.copy(reviewRecorded = true)
    }

    suspend fun playAudio(text: String) {
        runCatching {
            audioPromptPlayer.play(text)
        }.onFailure { error ->
            postMessage(error.message ?: "Unable to play Thai audio.")
            refreshSupportState()
        }
    }

    override fun onCleared() {
        audioPromptPlayer.release()
        super.onCleared()
    }

    companion object {
        fun factory(
            repository: StudyRepository,
            settings: AppSettings,
            handwriting: HandwritingRecognitionService,
            reminderScheduler: ReminderScheduler,
            audioPromptPlayer: AudioPromptPlayer,
            githubReleaseUpdater: GithubReleaseUpdater,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    repository = repository,
                    settings = settings,
                    handwriting = handwriting,
                    reminderScheduler = reminderScheduler,
                    audioPromptPlayer = audioPromptPlayer,
                    githubReleaseUpdater = githubReleaseUpdater,
                ) as T
            }
        }

        fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
    }
}
