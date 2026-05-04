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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.bee.thaiwrite.domain.practice.writingTarget
import com.bee.thaiwrite.data.repo.ReviewPromptMode
import com.bee.thaiwrite.ui.components.WritingCanvas
import com.bee.thaiwrite.ui.components.rememberWritingPadState
import com.bee.thaiwrite.ui.theme.Clay
import com.bee.thaiwrite.ui.theme.Cloud as StudyCloud
import com.bee.thaiwrite.ui.theme.CloudEdge as StudyCloudEdge
import com.bee.thaiwrite.ui.theme.CoralDeep as StudyCoralDeep
import com.bee.thaiwrite.ui.theme.Ink
import com.bee.thaiwrite.ui.theme.Lavender as StudyLavender
import com.bee.thaiwrite.ui.theme.LavenderTint as StudyLavenderTint
import com.bee.thaiwrite.ui.theme.MintTint as StudyMintTint
import com.bee.thaiwrite.ui.theme.Palm
import com.bee.thaiwrite.ui.theme.Saffron
import com.bee.thaiwrite.ui.theme.Slate as StudySlate
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
    val accent = lessonAccent(overview.lesson.stage)
    StudyScreenScaffold(
        title = overview.lesson.title,
        subtitle = overview.lesson.stage,
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StudyHeroCard(
                    label = overview.lesson.stage,
                    title = overview.lesson.title,
                    body = overview.lesson.description,
                    accent = accent,
                    badge = "${overview.masteredCount}/${overview.totalCount} stable",
                    detail = if (overview.unlocked) {
                        "${overview.dueCount} cards from this lesson are already circulating in review."
                    } else {
                        "This lesson stays locked until the previous batch is stable."
                    },
                )
            }
            items(overview.items) { item ->
                StudyPanel(
                    borderColor = accent.copy(alpha = 0.16f),
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            StudyInlinePill(
                                text = item.item.transliteration,
                                tint = accent,
                                background = accent.copy(alpha = 0.12f),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(item.item.thai, style = MaterialTheme.typography.headlineMedium, color = Ink)
                            Text(item.item.english, style = MaterialTheme.typography.bodyLarge, color = StudySlate)
                            item.guide?.tip?.let { tip ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(tip, style = MaterialTheme.typography.bodyMedium, color = Clay)
                            }
                        }
                        IconButton(
                            onClick = { scope.launch { onPlayAudio(item.item.audioText) } },
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(accent.copy(alpha = 0.12f)),
                        ) {
                            Icon(Icons.Outlined.Headphones, contentDescription = "Play audio", tint = accent)
                        }
                    }
                }
            }
            item {
                StudyPanel(borderColor = accent.copy(alpha = 0.16f)) {
                    Text("Ready to write?", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        "Work through the prompts one by one, check each answer, and keep the handwriting clean.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = StudySlate,
                    )
                    Button(
                        onClick = onPractice,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = overview.unlocked,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text(if (overview.started) "Continue lesson drill" else "Start lesson drill")
                    }
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
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showHint by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WritingAssessment?>(null) }
    val padState = rememberWritingPadState()
    val scope = rememberCoroutineScope()
    val current = overview.items.getOrNull(index)
    val writingTarget = current?.item?.writingTarget()
    val accent = lessonAccent(overview.lesson.stage)

    LaunchedEffect(index) {
        padState.clear()
        result = null
        showGuide = false
        showHint = false
    }

    StudyScreenScaffold(
        title = overview.lesson.title,
        subtitle = "Lesson drill",
        onBack = onBack,
    ) { padding ->
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                StudyCompletionCard(
                    title = "Lesson complete",
                    body = "You finished ${overview.lesson.title}. Leave now or run it again later if you want cleaner strokes.",
                    buttonText = "Back to study",
                    accent = accent,
                    onClick = onBack,
                )
            }
            return@StudyScreenScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StudyHeroCard(
                    label = "${overview.lesson.stage} practice",
                    title = current.item.prompt,
                    body = "Item ${index + 1} of ${overview.items.size}. ${if (showGuide) "Guide is visible so you can clean up the shape." else "Write from memory before you check it."}",
                    accent = accent,
                    badge = "${index + 1}/${overview.items.size}",
                    detail = writingTarget?.supportText ?: "Write the Thai neatly, then let the recognizer score it.",
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StudyInlinePill(
                        text = "${overview.items.size - index - 1} left",
                        tint = accent,
                        background = accent.copy(alpha = 0.12f),
                    )
                    StudyInlinePill(
                        text = if (showGuide) "Guide on" else "Guide off",
                        tint = Ink,
                        background = StudyMintTint,
                    )
                    StudyInlinePill(
                        text = if (showHint) "Hint open" else "Hint hidden",
                        tint = if (showHint) Clay else StudySlate,
                        background = if (showHint) Color(0xFFFFF1E4) else Color(0xFFF7F2EB),
                    )
                }
            }
            if (showHint) {
                item {
                    StudyPanel(borderColor = accent.copy(alpha = 0.16f)) {
                        Text("Hint", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text(current.item.transliteration, style = MaterialTheme.typography.bodyLarge, color = Ink)
                        current.guide?.tip?.let { tip ->
                            Text(tip, style = MaterialTheme.typography.bodyMedium, color = Clay)
                        }
                    }
                }
            }
            item {
                StudyCanvasPanel(
                    title = if (showGuide) "Write over the guide or freehand" else "Write from memory",
                    body = "Use the full height of the canvas. Clear and retry as often as you want before checking.",
                    accent = accent,
                ) {
                    WritingCanvas(
                        state = padState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        guideText = writingTarget?.displayText ?: current.item.thai,
                        showGuide = showGuide,
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { padState.clear() }, shape = RoundedCornerShape(20.dp)) { Text("Clear canvas") }
                    OutlinedButton(onClick = { showGuide = !showGuide }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (showGuide) "Hide guide" else "Show guide")
                    }
                    OutlinedButton(onClick = { showHint = !showHint }, shape = RoundedCornerShape(20.dp)) {
                        Text(if (showHint) "Hide hint" else "Show hint")
                    }
                    OutlinedButton(onClick = { scope.launch { viewModel.playAudio(current.item.audioText) } }, shape = RoundedCornerShape(20.dp)) {
                        Text("Play audio")
                    }
                }
            }
            if (result == null) {
                item {
                    Button(
                        enabled = !checking && !padState.isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        onClick = {
                            checking = true
                            scope.launch {
                                runCatching {
                                    viewModel.assessWriting(
                                        itemId = current.item.id,
                                        acceptedTargets = writingTarget?.acceptedTexts ?: listOf(current.item.thai),
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
            }
            result?.let { assessment ->
                item {
                    FeedbackCard(
                        passed = assessment.passed,
                        expected = writingTarget?.displayText ?: current.item.thai,
                        topCandidate = assessment.topCandidate,
                    )
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (assessment.passed) Palm else accent),
                        onClick = {
                            if (assessment.passed) {
                                index += 1
                            } else {
                                padState.clear()
                                result = null
                            }
                        },
                    ) {
                        Text(if (assessment.passed) "Next item" else "Reset and try again")
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
    val writingTarget = current?.item?.writingTarget()
    var revealed by rememberSaveable(current?.item?.id, current?.card?.cardType) { mutableStateOf(false) }
    var showHint by rememberSaveable(current?.item?.id, current?.card?.cardType) { mutableStateOf(false) }
    var recallSubmitting by rememberSaveable(current?.item?.id, current?.card?.cardType) { mutableStateOf(false) }
    val cardMode = current?.promptMode
    val accent = current?.promptMode?.let(::reviewAccent) ?: Palm

    LaunchedEffect(current?.item?.id, current?.card?.cardType) {
        revealed = false
        showHint = false
        checking = false
        recallSubmitting = false
        result = null
        padState.clear()
    }

    LaunchedEffect(current?.item?.id, current?.card?.cardType, cardMode) {
        if (current != null && cardMode == ReviewPromptMode.AUDIO) {
            viewModel.playAudio(current.item.audioText)
        }
    }

    StudyScreenScaffold(
        title = "Review queue",
        subtitle = "Due now",
        onBack = onBack,
    ) { padding ->
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                StudyCompletionCard(
                    title = "Queue clear",
                    body = "Nothing is due right now. Leave the review screen and come back when the next batch surfaces.",
                    buttonText = "Back to study",
                    accent = Palm,
                    onClick = onBack,
                )
            }
            return@StudyScreenScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                StudyHeroCard(
                    label = reviewModeLabel(current.promptMode),
                    title = current.primaryPrompt,
                    body = reviewModeBody(current),
                    accent = accent,
                    badge = "${snapshot.dueCards.size} due",
                    detail = if (current.requiresWriting) {
                        writingTarget?.supportText ?: "Write the Thai answer before you check it."
                    } else {
                        "Reveal only after you have genuinely tried to recall it."
                    },
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StudyInlinePill(
                        text = "${snapshot.dueRecognitionCount} recall",
                        tint = Palm,
                        background = StudyMintTint,
                    )
                    StudyInlinePill(
                        text = "${snapshot.dueWritingCount} writing",
                        tint = Saffron,
                        background = Color(0xFFFFF1E4),
                    )
                    StudyInlinePill(
                        text = "${snapshot.dueAudioCount} audio",
                        tint = StudyLavender,
                        background = StudyLavenderTint,
                    )
                    if (current.requiresWriting) {
                        StudyInlinePill(
                            text = if (revealed) "Guide shown" else "Guide hidden",
                            tint = Ink,
                            background = Color(0xFFF7F2EB),
                        )
                    }
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { scope.launch { viewModel.playAudio(current.item.audioText) } },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(if (current.promptMode == ReviewPromptMode.AUDIO) "Replay prompt" else "Play audio")
                    }
                    if (current.requiresWriting) {
                        OutlinedButton(onClick = { showHint = !showHint }, shape = RoundedCornerShape(20.dp)) {
                            Text(if (showHint) "Hide hint" else "Show hint")
                        }
                    }
                }
            }
            if (current.requiresWriting && showHint) {
                item {
                    StudyPanel(borderColor = accent.copy(alpha = 0.16f)) {
                        Text("Hint", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text(current.secondaryPrompt, style = MaterialTheme.typography.bodyLarge, color = Ink)
                        current.guide?.tip?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Clay)
                        }
                    }
                }
            }
            if (current.requiresWriting) {
                item {
                    StudyCanvasPanel(
                        title = if (revealed) "Guide overlay is visible" else "Write before you reveal the guide",
                        body = "Keep the answer hidden until you need it. The goal is recall first, then cleanup.",
                        accent = accent,
                    ) {
                        WritingCanvas(
                            state = padState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            guideText = writingTarget?.displayText ?: current.item.thai,
                            showGuide = revealed,
                        )
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { padState.clear() }, shape = RoundedCornerShape(20.dp)) { Text("Clear canvas") }
                        OutlinedButton(onClick = { revealed = !revealed }, shape = RoundedCornerShape(20.dp)) {
                            Text(if (revealed) "Hide guide" else "Show guide")
                        }
                    }
                }
                if (result == null) {
                    item {
                        Button(
                            enabled = !checking && !padState.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            onClick = {
                                checking = true
                                scope.launch {
                                    runCatching {
                                        viewModel.assessWriting(
                                            itemId = current.item.id,
                                            acceptedTargets = writingTarget?.acceptedTexts ?: listOf(current.item.thai),
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
                }
                result?.let { assessment ->
                    item {
                        FeedbackCard(
                            passed = assessment.passed,
                            expected = writingTarget?.displayText ?: current.item.thai,
                            topCandidate = assessment.topCandidate,
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                result = null
                                padState.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (assessment.passed) Palm else accent),
                        ) {
                            Text(if (assessment.passed) "Next card" else "Reset and try again")
                        }
                    }
                }
            } else {
                item {
                    StudyPanel(
                        borderColor = accent.copy(alpha = 0.16f),
                        contentPadding = PaddingValues(24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (revealed) {
                                Text(
                                    current.item.thai,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Ink,
                                )
                                Text(current.item.transliteration, style = MaterialTheme.typography.titleLarge, color = accent)
                                Text(current.item.english, style = MaterialTheme.typography.bodyLarge, color = StudySlate)
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
                        Button(
                            onClick = { revealed = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                        ) {
                            Text(if (current.promptMode == ReviewPromptMode.AUDIO) "Reveal answer" else "Reveal")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    recallSubmitting = true
                                    viewModel.recordRecallReview(current.item.id, current.card.cardType, false)
                                },
                                enabled = !recallSubmitting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(22.dp),
                            ) {
                                Text("Missed it")
                            }
                            Button(
                                onClick = {
                                    recallSubmitting = true
                                    viewModel.recordRecallReview(current.item.id, current.card.cardType, true)
                                },
                                enabled = !recallSubmitting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                            ) {
                                Text("Got it")
                            }
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
private fun StudyScreenScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFBF6), Color(0xFFF4F8F7), Color(0xFFFFF8F0)),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(subtitle, style = MaterialTheme.typography.labelLarge, color = StudySlate)
                            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
                        }
                    },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            content(padding)
        }
    }
}

@Composable
private fun StudyHeroCard(
    label: String,
    title: String,
    body: String,
    accent: Color,
    badge: String,
    detail: String,
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Ink,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                StudyInlinePill(
                    text = label,
                    tint = accent,
                    background = accent.copy(alpha = 0.18f),
                )
                StudyInlinePill(
                    text = badge,
                    tint = Color.White,
                    background = Color.White.copy(alpha = 0.12f),
                )
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.74f))
        }
    }
}

@Composable
private fun StudyPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    borderColor: Color = StudyCloudEdge,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = StudyCloud),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun StudyInlinePill(
    text: String,
    tint: Color,
    background: Color,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

@Composable
private fun StudyCanvasPanel(
    title: String,
    body: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    StudyPanel(borderColor = accent.copy(alpha = 0.16f)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = StudySlate)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.18f)), RoundedCornerShape(26.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun StudyCompletionCard(
    title: String,
    body: String,
    buttonText: String,
    accent: Color,
    onClick: () -> Unit,
) {
    StudyPanel(
        modifier = Modifier.padding(horizontal = 20.dp),
        borderColor = accent.copy(alpha = 0.16f),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = StudySlate)
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
        ) {
            Text(buttonText)
        }
    }
}

private fun lessonAccent(stage: String): Color = when (stage) {
    "Consonants" -> Palm
    "Vowels" -> Saffron
    "Tone marks" -> StudyLavender
    "Words" -> StudyCoralDeep
    else -> Palm
}

private fun reviewAccent(mode: ReviewPromptMode): Color = when (mode) {
    ReviewPromptMode.RECOGNITION -> Palm
    ReviewPromptMode.WRITING -> Saffron
    ReviewPromptMode.AUDIO -> StudyLavender
}

private fun reviewModeLabel(mode: ReviewPromptMode): String = when (mode) {
    ReviewPromptMode.RECOGNITION -> "Recall card"
    ReviewPromptMode.WRITING -> "Writing card"
    ReviewPromptMode.AUDIO -> "Audio card"
}

private fun reviewModeBody(card: com.bee.thaiwrite.data.repo.ReviewCardView): String = when (card.promptMode) {
    ReviewPromptMode.RECOGNITION ->
        "Try to recall the Thai before revealing it. Score yourself only after you have genuinely tried."
    ReviewPromptMode.WRITING ->
        "Write the Thai from memory first. Reveal the guide only if you need a correction pass."
    ReviewPromptMode.AUDIO ->
        "Listen, say it back, and picture the Thai before flipping the answer."
}

@Composable
private fun FeedbackCard(
    passed: Boolean,
    expected: String,
    topCandidate: String?,
) {
    StudyPanel(
        borderColor = if (passed) Palm.copy(alpha = 0.2f) else Clay.copy(alpha = 0.2f),
        contentPadding = PaddingValues(18.dp),
    ) {
        StudyInlinePill(
            text = if (passed) "Recognized" else "Needs another pass",
            tint = if (passed) Palm else Clay,
            background = if (passed) Color(0xFFE7F4E4) else Color(0xFFFCE7E3),
        )
        Text(
            if (passed) "That answer is good enough to move on." else "The recognizer did not get a clean match yet.",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
        )
        Text("Expected: $expected", style = MaterialTheme.typography.bodyLarge, color = Ink)
        Text(
            "Recognizer heard: ${topCandidate ?: "Nothing clear"}",
            style = MaterialTheme.typography.bodyMedium,
            color = StudySlate,
        )
    }
}
