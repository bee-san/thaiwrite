@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.bee.thaiwrite.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bee.thaiwrite.BuildConfig
import com.bee.thaiwrite.data.repo.LessonOverview
import com.bee.thaiwrite.data.repo.ReviewPromptMode
import com.bee.thaiwrite.ui.components.WritingCanvas
import com.bee.thaiwrite.ui.components.rememberWritingPadState
import com.bee.thaiwrite.ui.theme.Clay
import com.bee.thaiwrite.ui.theme.Ink
import com.bee.thaiwrite.ui.theme.Palm
import com.bee.thaiwrite.ui.theme.Saffron
import kotlinx.coroutines.launch

@Composable
fun ThaiWriteApp(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            uiState.loading -> LoadingScreen()
            uiState.snapshot == null -> LoadingScreen()
            !uiState.snapshot!!.onboardingComplete -> {
                OnboardingScreen(
                    uiState = uiState,
                    onDownloadModel = viewModel::downloadHandwritingModel,
                    onOpenThaiAudioSetup = viewModel::openThaiAudioSetup,
                    onRefreshSupportState = viewModel::refreshSupportState,
                    onFinish = viewModel::finishOnboarding,
                    snackbarHostState = snackbarHostState,
                )
            }
            else -> {
                StudyNavHost(
                    uiState = uiState,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Saffron)
    }
}

@Composable
private fun OnboardingScreen(
    uiState: AppUiState,
    onDownloadModel: (Boolean) -> Unit,
    onOpenThaiAudioSetup: () -> Unit,
    onRefreshSupportState: () -> Unit,
    onFinish: (Int, Int) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var reminderChoice by rememberSaveable { mutableIntStateOf(2) }
    val presets = listOf(7 to 0, 12 to 0, 19 to 0, 21 to 0)
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = "ThaiWrite",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A writing-first Thai alphabet trainer. Trace, write from memory, hear the sound, then let spaced repetition bring it back at the right time.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                FeatureCard(
                    title = "Baby steps only",
                    body = "Lessons unlock in order: consonants, vowels, tones, then a tiny names-and-words deck tied to people and things you care about.",
                )
            }
            item {
                FeatureCard(
                    title = "Handwriting checks on device",
                    body = if (uiState.handwritingModelReady) {
                        "Thai handwriting model is ready."
                    } else {
                        "Download the Thai handwriting model once so the app can auto-check what you write."
                    },
                    action = {
                        Button(
                            enabled = !uiState.busy,
                            onClick = { onDownloadModel(false) },
                        ) {
                            Text(if (uiState.handwritingModelReady) "Redownload model" else "Download model")
                        }
                    },
                )
            }
            item {
                FeatureCard(
                    title = "Audio cards",
                    body = uiState.thaiAudioStatus,
                    action = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!uiState.thaiAudioReady) {
                                OutlinedButton(onClick = onOpenThaiAudioSetup) {
                                    Text("Fix audio")
                                }
                            }
                            TextButton(onClick = onRefreshSupportState) {
                                Text("Refresh")
                            }
                        }
                    },
                )
            }
            item {
                NotificationPermissionCard()
            }
            item {
                Text(
                    text = "Daily reminder",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEachIndexed { index, (hour, minute) ->
                        AssistChip(
                            onClick = { reminderChoice = index },
                            label = { Text(AppViewModel.formatTime(hour, minute)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
            item {
                Button(
                    enabled = uiState.handwritingModelReady,
                    onClick = {
                        val (hour, minute) = presets[reminderChoice]
                        onFinish(hour, minute)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start learning")
                }
            }
        }
    }
}

@Composable
private fun StudyNavHost(
    uiState: AppUiState,
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = uiState.snapshot ?: return
    val navController = rememberNavController()
    fun openStudyFlow() {
        when {
            snapshot.dueCards.isNotEmpty() -> navController.navigate("review")
            snapshot.nextLessonId != null -> {
                val lessonId = snapshot.nextLessonId
                val lesson = snapshot.lessons.firstOrNull { it.lesson.id == lessonId }
                if (lesson?.started == true) {
                    viewModel.startLesson(lessonId)
                    navController.navigate("practice/$lessonId")
                } else {
                    navController.navigate("lesson/$lessonId")
                }
            }
            else -> navController.navigate(MainDestination.Words.route)
        }
    }
    fun navigateTopLevel(destination: MainDestination) {
        navController.navigate(destination.route) {
            launchSingleTop = true
            restoreState = true
            popUpTo("home") {
                saveState = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            DashboardHomeScreen(
                uiState = uiState,
                selected = MainDestination.Home,
                onNavigate = ::navigateTopLevel,
                onOpenStudy = ::openStudyFlow,
                onOpenLesson = { lessonId -> navController.navigate("lesson/$lessonId") },
                onOpenLibrary = { navigateTopLevel(MainDestination.Words) },
                onOpenThaiAudioSetup = viewModel::openThaiAudioSetup,
                onCheckUpdates = { viewModel.refreshUpdateState(manual = true) },
                onInstallUpdate = viewModel::downloadAndInstallUpdate,
                onOpenUpdatePage = viewModel::openUpdatePage,
                onPlayAudio = viewModel::playAudio,
                snackbarHostState = snackbarHostState,
            )
        }
        composable("practice-hub") {
            PracticeHubScreen(
                uiState = uiState,
                selected = MainDestination.Practice,
                onNavigate = ::navigateTopLevel,
                onOpenStudy = ::openStudyFlow,
                onOpenLesson = { lessonId -> navController.navigate("lesson/$lessonId") },
                snackbarHostState = snackbarHostState,
            )
        }
        composable("words") {
            WordsDeckScreen(
                uiState = uiState,
                selected = MainDestination.Words,
                onNavigate = ::navigateTopLevel,
                onOpenLesson = { lessonId -> navController.navigate("lesson/$lessonId") },
                onPlayAudio = viewModel::playAudio,
                snackbarHostState = snackbarHostState,
            )
        }
        composable("profile") {
            ProfileScreen(
                uiState = uiState,
                selected = MainDestination.Profile,
                onNavigate = ::navigateTopLevel,
                onReminderSelected = viewModel::updateReminder,
                onRedownloadModel = viewModel::downloadHandwritingModel,
                onOpenThaiAudioSetup = viewModel::openThaiAudioSetup,
                onRefreshSupportState = viewModel::refreshSupportState,
                onCheckUpdates = { viewModel.refreshUpdateState(manual = true) },
                onInstallUpdate = viewModel::downloadAndInstallUpdate,
                onOpenUpdatePage = viewModel::openUpdatePage,
                snackbarHostState = snackbarHostState,
            )
        }
        composable(
            route = "lesson/{lessonId}",
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: return@composable
            val overview = snapshot.lessons.firstOrNull { it.lesson.id == lessonId } ?: return@composable
            LessonScreen(
                overview = overview,
                onBack = { navController.popBackStack() },
                onPractice = {
                    viewModel.startLesson(lessonId)
                    navController.navigate("practice/$lessonId")
                },
                onPlayAudio = viewModel::playAudio,
            )
        }
        composable(
            route = "practice/{lessonId}",
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: return@composable
            val overview = snapshot.lessons.firstOrNull { it.lesson.id == lessonId } ?: return@composable
            PracticeScreen(
                overview = overview,
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
        composable("review") {
            ReviewScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: AppUiState,
    onOpenLesson: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = uiState.snapshot ?: return
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ThaiWrite") },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = "Library")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF9F2))) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Study streak", style = MaterialTheme.typography.labelLarge, color = Palm)
                        Text("${snapshot.streak} days", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${snapshot.dueCards.size} review cards due now. Best streak so far: ${snapshot.maxStreak}.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item {
                FeatureCard(
                    title = "Learning path",
                    body = "${snapshot.startedLessonCount}/${snapshot.lessons.size} lessons opened, ${snapshot.completedLessonCount} completed, and ${snapshot.masteredWritingCount}/${snapshot.totalWritingCount} writing targets are in long-term review.",
                )
            }
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFBF4))) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Due today", style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = onOpenReview,
                                label = { Text("${snapshot.dueRecognitionCount} recall") },
                            )
                            AssistChip(
                                onClick = onOpenReview,
                                label = { Text("${snapshot.dueWritingCount} writing") },
                            )
                            AssistChip(
                                onClick = onOpenReview,
                                label = { Text("${snapshot.dueAudioCount} audio") },
                            )
                        }
                    }
                }
            }
            if (uiState.updateSupported) {
                item {
                    UpdateCard(
                        uiState = uiState,
                        onCheckUpdates = onCheckUpdates,
                        onInstallUpdate = onInstallUpdate,
                        onOpenUpdatePage = onOpenUpdatePage,
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onOpenReview,
                        enabled = snapshot.dueCards.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Review")
                    }
                    Button(
                        onClick = { snapshot.nextLessonId?.let(onOpenLesson) },
                        enabled = snapshot.nextLessonId != null,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Continue lesson")
                    }
                }
            }
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFBF4))) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("People and words to keep close", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "These starter words are hard-coded examples for the people and things you want to remember first.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            snapshot.focusWords.take(10).forEach { item ->
                                AssistChip(
                                    onClick = onOpenLibrary,
                                    label = { Text("${item.thai} • ${item.transliteration}") },
                                )
                            }
                        }
                    }
                }
            }
            items(snapshot.lessons.take(5)) { lesson ->
                LessonCard(
                    lesson = lesson,
                    onOpenLesson = { onOpenLesson(lesson.lesson.id) },
                )
            }
        }
    }
}

@Composable
private fun LessonScreen(
    overview: LessonOverview,
    onBack: () -> Unit,
    onPractice: () -> Unit,
    onPlayAudio: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(overview.lesson.title) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(overview.lesson.description, style = MaterialTheme.typography.bodyLarge)
            }
            items(overview.items) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.item.thai, style = MaterialTheme.typography.headlineMedium)
                            Text(item.item.transliteration, style = MaterialTheme.typography.titleLarge)
                            Text(item.item.english, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { scope.launch { onPlayAudio(item.item.audioText) } }) {
                            Icon(Icons.Outlined.Headphones, contentDescription = "Play audio")
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onPractice,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overview.unlocked,
                ) {
                    Text("Practice writing this lesson")
                }
            }
        }
    }
}

@Composable
private fun PracticeScreen(
    overview: LessonOverview,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var showGuide by rememberSaveable { mutableStateOf(true) }
    var showHint by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WritingAssessment?>(null) }
    val padState = rememberWritingPadState()
    val scope = rememberCoroutineScope()
    val current = overview.items.getOrNull(index)

    LaunchedEffect(index) {
        padState.clear()
        result = null
        showGuide = true
        showHint = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice ${overview.lesson.title}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Lesson done.", style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = onBack) { Text("Return home") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Item ${index + 1} of ${overview.items.size}", style = MaterialTheme.typography.labelLarge, color = Palm)
                    Text(current.item.prompt, style = MaterialTheme.typography.headlineMedium)
                    if (showHint) {
                        Text("Hint: ${current.item.transliteration}", style = MaterialTheme.typography.bodyLarge)
                        current.guide?.tip?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Clay)
                        }
                    }
                }
            }
            item {
                WritingCanvas(
                    state = padState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    guideText = current.item.thai,
                    showGuide = showGuide,
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { padState.clear() }) { Text("Clear") }
                    OutlinedButton(onClick = { showGuide = !showGuide }) { Text(if (showGuide) "Hide guide" else "Show guide") }
                    OutlinedButton(onClick = { showHint = !showHint }) { Text(if (showHint) "Hide hint" else "Show hint") }
                    OutlinedButton(onClick = { scope.launch { viewModel.playAudio(current.item.audioText) } }) { Text("Play audio") }
                }
            }
            item {
                Button(
                    enabled = !checking && !padState.isEmpty(),
                    onClick = {
                        checking = true
                        scope.launch {
                            runCatching {
                                viewModel.assessWriting(
                                    itemId = current.item.id,
                                    expectedThai = current.item.thai,
                                    strokes = padState.strokes(),
                                    canvasWidth = padState.canvasSize.width.toFloat(),
                                    canvasHeight = padState.canvasSize.height.toFloat(),
                                )
                            }.onSuccess { assessment ->
                                result = assessment
                            }.onFailure { error ->
                                viewModel.postMessage(error.message ?: "Unable to check your writing.")
                            }
                            checking = false
                        }
                    },
                ) {
                    Text(if (checking) "Checking..." else "Check writing")
                }
            }
            result?.let { assessment ->
                item {
                    FeedbackCard(
                        passed = assessment.passed,
                        expected = current.item.thai,
                        topCandidate = assessment.topCandidate,
                    )
                }
                item {
                    Button(
                        onClick = {
                            if (assessment.passed) {
                                index += 1
                            } else {
                                padState.clear()
                                result = null
                            }
                        },
                    ) {
                        Text(if (assessment.passed) "Next item" else "Try again")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    uiState: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val snapshot = uiState.snapshot ?: return
    val nextDueCard = snapshot.dueCards.firstOrNull()
    val scope = rememberCoroutineScope()
    val padState = rememberWritingPadState()
    var displayedCard by remember { mutableStateOf(nextDueCard) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WritingAssessment?>(null) }
    LaunchedEffect(nextDueCard?.card?.id, nextDueCard?.card?.cardType, result == null) {
        if (result == null) {
            displayedCard = nextDueCard
        }
    }
    val current = displayedCard
    var revealed by rememberSaveable(current?.item?.id, current?.card?.cardType) { mutableStateOf(false) }
    var showHint by rememberSaveable(current?.item?.id, current?.card?.cardType) { mutableStateOf(false) }
    val cardMode = current?.promptMode

    LaunchedEffect(current?.item?.id, current?.card?.cardType) {
        revealed = false
        showHint = false
        checking = false
        result = null
        padState.clear()
    }

    LaunchedEffect(current?.item?.id, current?.card?.cardType, cardMode) {
        if (current != null && cardMode == ReviewPromptMode.AUDIO) {
            viewModel.playAudio(current.item.audioText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Due review") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Nothing due right now.", style = MaterialTheme.typography.headlineMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${snapshot.dueCards.size} cards due", style = MaterialTheme.typography.labelLarge, color = Palm)
                    Text(current.primaryPrompt, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when (current.promptMode) {
                            ReviewPromptMode.RECOGNITION -> "Recognition card"
                            ReviewPromptMode.WRITING -> "Writing card"
                            ReviewPromptMode.AUDIO -> "Audio card"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Clay,
                    )
                    if (current.requiresWriting && showHint) {
                        Text(current.secondaryPrompt, style = MaterialTheme.typography.bodyLarge)
                        current.guide?.tip?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Clay)
                        }
                    }
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = { scope.launch { viewModel.playAudio(current.item.audioText) } }) {
                        Icon(Icons.Outlined.Headphones, contentDescription = "Play audio")
                    }
                    if (current.requiresWriting) {
                        OutlinedButton(onClick = { showHint = !showHint }) {
                            Text(if (showHint) "Hide hint" else "Show hint")
                        }
                    }
                }
            }
            if (current.requiresWriting) {
                item {
                    WritingCanvas(
                        state = padState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        guideText = current.item.thai,
                        showGuide = revealed,
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { padState.clear() }) { Text("Clear") }
                        OutlinedButton(onClick = { revealed = !revealed }) { Text(if (revealed) "Hide guide" else "Show guide") }
                    }
                }
                item {
                    Button(
                        enabled = !checking && !padState.isEmpty(),
                        onClick = {
                            checking = true
                            scope.launch {
                                runCatching {
                                    viewModel.assessWriting(
                                        itemId = current.item.id,
                                        expectedThai = current.item.thai,
                                        strokes = padState.strokes(),
                                        canvasWidth = padState.canvasSize.width.toFloat(),
                                        canvasHeight = padState.canvasSize.height.toFloat(),
                                    )
                                }.onSuccess { assessment ->
                                    result = assessment
                                }.onFailure { error ->
                                    viewModel.postMessage(error.message ?: "Unable to check your writing.")
                                }
                                checking = false
                            }
                        },
                    ) {
                        Text(if (checking) "Checking..." else "Check answer")
                    }
                }
                result?.let { assessment ->
                    item {
                        FeedbackCard(
                            passed = assessment.passed,
                            expected = current.item.thai,
                            topCandidate = assessment.topCandidate,
                        )
                    }
                    item {
                        TextButton(onClick = {
                            result = null
                            padState.clear()
                        }) { Text("Continue") }
                    }
                }
            } else {
                item {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFBF4))) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (revealed) {
                                Text(
                                    current.item.thai,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Ink,
                                )
                                Text(current.item.transliteration, style = MaterialTheme.typography.titleLarge)
                                Text(current.item.english, style = MaterialTheme.typography.bodyLarge)
                            } else {
                                Text(
                                    if (current.promptMode == ReviewPromptMode.AUDIO) {
                                        "Listen closely, say it back, and try to picture the Thai before flipping."
                                    } else {
                                        "Try to recall the Thai before flipping."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (current.promptMode == ReviewPromptMode.AUDIO) {
                                    Text(
                                        "Use the headphones button to replay the prompt as often as you need.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Clay,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    if (!revealed) {
                        Button(onClick = { revealed = true }) {
                            Text(if (current.promptMode == ReviewPromptMode.AUDIO) "Reveal answer" else "Reveal")
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.recordRecallReview(current.item.id, current.card.cardType, false) }) { Text("Fail") }
                            Button(onClick = { viewModel.recordRecallReview(current.item.id, current.card.cardType, true) }) { Text("Pass") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onReminderSelected: (Int, Int) -> Unit,
    onRedownloadModel: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
) {
    val snapshot = uiState.snapshot ?: return
    val presets = listOf(7 to 0, 12 to 0, 19 to 0, 21 to 0)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                FeatureCard(
                    title = "Reminder",
                    body = "Current daily reminder: ${AppViewModel.formatTime(snapshot.reminderHour, snapshot.reminderMinute)}",
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { (hour, minute) ->
                        AssistChip(
                            onClick = { onReminderSelected(hour, minute) },
                            label = { Text(AppViewModel.formatTime(hour, minute)) },
                        )
                    }
                }
            }
            item {
                FeatureCard(
                    title = "Handwriting model",
                    body = if (uiState.handwritingModelReady) "Thai model installed." else "Thai model missing.",
                    action = {
                        Button(onClick = { onRedownloadModel(false) }) { Text("Download") }
                    },
                )
            }
            item {
                FeatureCard(
                    title = "Thai audio",
                    body = if (uiState.thaiAudioReady) "Thai TTS voice available." else "Thai voice data still missing or disabled.",
                )
            }
            item {
                NotificationPermissionCard()
            }
            item {
                UpdateCard(
                    uiState = uiState,
                    onCheckUpdates = onCheckUpdates,
                    onInstallUpdate = onInstallUpdate,
                    onOpenUpdatePage = onOpenUpdatePage,
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    lessons: List<LessonOverview>,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(lessons) { lesson ->
                LessonCard(
                    lesson = lesson,
                    onOpenLesson = { if (lesson.unlocked) onOpenLesson(lesson.lesson.id) },
                )
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: LessonOverview,
    onOpenLesson: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (lesson.unlocked) Color(0xFFFFFCF7) else Color(0xFFF0ECE4)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = lesson.unlocked, onClick = onOpenLesson),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lesson.lesson.stage, style = MaterialTheme.typography.labelLarge, color = Palm)
            Text(lesson.lesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(lesson.lesson.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (lesson.unlocked) {
                    "${lesson.masteredCount}/${lesson.totalCount} writing cards mastered, ${lesson.dueCount} due"
                } else {
                    "Locked until the previous lesson is mastered"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (lesson.unlocked) Ink else Clay,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    body: String,
    action: @Composable (() -> Unit)? = null,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFBF4))) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            action?.invoke()
        }
    }
}

@Composable
private fun UpdateCard(
    uiState: AppUiState,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
) {
    val update = uiState.updateInfo
    val body = when {
        !uiState.updateSupported -> "GitHub release updates are enabled only in the signed release APK."
        uiState.updateDownloading -> "Downloading ${update?.latestVersionName ?: "update"}${uiState.updateDownloadProgress?.let { " ($it%)" } ?: ""}."
        uiState.updateChecking -> "Checking GitHub Releases for a newer APK."
        update != null -> {
            val headline = "Update ${update.latestVersionName} is available. You are on ${update.currentVersionName}."
            val note = update.releaseNotes
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotEmpty() }
            if (note.isNullOrEmpty()) headline else "$headline\n$note"
        }
        else -> "Current version ${BuildConfig.VERSION_NAME}. Check GitHub Releases for a newer APK."
    }

    FeatureCard(
        title = "App updates",
        body = body,
        action = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = uiState.updateSupported && !uiState.updateChecking && !uiState.updateDownloading,
                    onClick = onCheckUpdates,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Check now")
                }
                OutlinedButton(
                    enabled = uiState.updateSupported && !uiState.updateDownloading,
                    onClick = onOpenUpdatePage,
                ) {
                    Text(if (update != null) "Open release" else "Open releases")
                }
                if (update != null) {
                    Button(
                        enabled = !uiState.updateDownloading,
                        onClick = onInstallUpdate,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (uiState.updateDownloading) "Downloading" else "Install update")
                    }
                }
            }
        },
    )
}

@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationPermissionGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
    }

    FeatureCard(
        title = "Notifications",
        body = if (granted) {
            "Daily reminder notifications can appear on this device."
        } else {
            "Android still needs notification permission before daily review reminders can appear."
        },
        action = if (!granted && Build.VERSION.SDK_INT >= 33) {
            {
                Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Enable notifications")
                }
            }
        } else {
            null
        },
    )
}

private fun notificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun FeedbackCard(
    passed: Boolean,
    expected: String,
    topCandidate: String?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (passed) Color(0xFFE7F4E4) else Color(0xFFFCE7E3),
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (passed) "Pass" else "Try again", style = MaterialTheme.typography.titleLarge, color = if (passed) Palm else Clay)
            Text("Expected: $expected", style = MaterialTheme.typography.bodyLarge)
            Text("Recognizer heard: ${topCandidate ?: "Nothing clear"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
