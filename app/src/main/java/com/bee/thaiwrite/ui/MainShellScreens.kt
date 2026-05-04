package com.bee.thaiwrite.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bee.thaiwrite.BuildConfig
import com.bee.thaiwrite.data.repo.LessonOverview
import com.bee.thaiwrite.data.repo.ReviewCardView
import com.bee.thaiwrite.data.repo.ReviewPromptMode
import com.bee.thaiwrite.ui.theme.Ink
import com.bee.thaiwrite.ui.theme.Palm
import com.bee.thaiwrite.ui.theme.Saffron
import kotlinx.coroutines.launch

internal enum class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Outlined.Home),
    Practice("practice-hub", "Practice", Icons.Outlined.Edit),
    Words("words", "Words", Icons.AutoMirrored.Outlined.LibraryBooks),
    Profile("profile", "Profile", Icons.Outlined.PersonOutline),
}

private val Ivory = Color(0xFFFFFBF6)
private val MintTint = Color(0xFFE8F6F3)
private val SeaGlass = Color(0xFF2F9D98)
private val Coral = Color(0xFFFF8E5F)
private val CoralDeep = Color(0xFFFF7A4E)
private val LavenderTint = Color(0xFFF1EBFB)
private val Lavender = Color(0xFF8A6FCD)
private val Cloud = Color(0xFFFFFFFF)
private val CloudEdge = Color(0xFFE9E3DA)
private val Slate = Color(0xFF6C757C)

@Composable
internal fun DashboardHomeScreen(
    uiState: AppUiState,
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
    onPlayAudio: suspend (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = uiState.snapshot ?: return
    DashboardScaffold(
        selected = selected,
        onNavigate = onNavigate,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        val totalTargets = snapshot.totalWritingCount.coerceAtLeast(1)
        val goalProgress = snapshot.masteredWritingCount.toFloat() / totalTargets.toFloat()
        val todayWords = snapshot.focusWords.take(3)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                AppHeader(
                    title = "Thai Writer",
                    streak = snapshot.streak,
                    onBellClick = { onNavigate(MainDestination.Profile) },
                )
            }
            item {
                StreakHeroCard(
                    streak = snapshot.streak,
                    subtitle = if (snapshot.streak > 0) {
                        "Keep it up. You are building recall through writing."
                    } else {
                        "Start a streak today with one review or one writing check."
                    },
                )
            }
            item {
                SoftPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Core alphabet goal", style = ThaiSectionOverlineStyle(), color = SeaGlass)
                            Text(
                                "Bring every letter into long-term writing memory.",
                                style = ThaiSectionTitleStyle(),
                                color = Ink,
                            )
                            LinearProgressIndicator(
                                progress = { goalProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                color = SeaGlass,
                                trackColor = MintTint,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        MetricBadge(
                            value = "${snapshot.masteredWritingCount} / ${snapshot.totalWritingCount}",
                            label = "targets",
                            containerColor = MintTint,
                            accentColor = SeaGlass,
                        )
                    }
                }
            }
            if (uiState.updateSupported) {
                item {
                    UpdatePanel(
                        uiState = uiState,
                        onCheckUpdates = onCheckUpdates,
                        onInstallUpdate = onInstallUpdate,
                        onOpenUpdatePage = onOpenUpdatePage,
                    )
                }
            }
            item {
                SectionRow(title = "Today's words", action = "Open deck", onAction = onOpenLibrary)
            }
            items(todayWords) { item ->
                FocusWordRow(
                    thai = item.thai,
                    transliteration = item.transliteration,
                    meaning = item.english,
                    tag = wordTag(item.id),
                    chipColor = wordTagColor(item.id),
                    onPlayAudio = { onPlayAudio(item.audioText) },
                    onOpen = onOpenLibrary,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.PlayArrow,
                        title = "Start writing",
                        body = "Jump into the next lesson or due review.",
                        accent = MintTint,
                        iconTint = SeaGlass,
                        onClick = { onNavigate(MainDestination.Practice) },
                    )
                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                        title = "Loved words",
                        body = "Study the tiny names and words deck.",
                        accent = Color(0xFFFFF4E9),
                        iconTint = Saffron,
                        onClick = onOpenLibrary,
                    )
                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.PersonOutline,
                        title = "Progress",
                        body = "See streaks, milestones, and setup.",
                        accent = LavenderTint,
                        iconTint = Lavender,
                        onClick = { onNavigate(MainDestination.Profile) },
                    )
                }
            }
            item {
                SectionRow(title = "Recent momentum")
            }
            item {
                SoftPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (snapshot.dueCards.isEmpty()) {
                                "Queue clear. You can either advance the next lesson or replay the loved words deck."
                            } else {
                                "${snapshot.dueCards.size} cards are waiting right now: ${snapshot.dueRecognitionCount} recall, ${snapshot.dueWritingCount} writing, ${snapshot.dueAudioCount} audio."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                        )
                        Text(
                            if (snapshot.nextLessonId != null) {
                                "Next unlock is ready when you are."
                            } else {
                                "Everything unlocked so far is in motion."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(onClick = { onNavigate(MainDestination.Practice) }) {
                                Text("Review now")
                            }
                            Button(
                                onClick = { snapshot.nextLessonId?.let(onOpenLesson) },
                                enabled = snapshot.nextLessonId != null,
                                colors = ButtonDefaults.buttonColors(containerColor = SeaGlass),
                            ) {
                                Text("Continue lesson")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PracticeHubScreen(
    uiState: AppUiState,
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    onOpenReview: () -> Unit,
    onOpenLesson: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = uiState.snapshot ?: return
    val nextLesson = snapshot.lessons.firstOrNull { it.lesson.id == snapshot.nextLessonId }

    DashboardScaffold(
        selected = selected,
        onNavigate = onNavigate,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                AppHeader(
                    title = "Practice",
                    streak = snapshot.streak,
                    onBellClick = { onNavigate(MainDestination.Profile) },
                )
            }
            item {
                SoftPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Due right now", style = ThaiSectionTitleStyle(), color = Ink)
                        Text(
                            "Switch between recall, writing, and audio until the queue is empty.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiniMetricTile(
                                modifier = Modifier.weight(1f),
                                value = snapshot.dueRecognitionCount.toString(),
                                label = "Recall",
                                accent = MintTint,
                                tint = SeaGlass,
                            )
                            MiniMetricTile(
                                modifier = Modifier.weight(1f),
                                value = snapshot.dueWritingCount.toString(),
                                label = "Writing",
                                accent = Color(0xFFFFF4E9),
                                tint = Saffron,
                            )
                            MiniMetricTile(
                                modifier = Modifier.weight(1f),
                                value = snapshot.dueAudioCount.toString(),
                                label = "Audio",
                                accent = LavenderTint,
                                tint = Lavender,
                            )
                        }
                        Button(
                            onClick = onOpenReview,
                            enabled = snapshot.dueCards.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SeaGlass),
                        ) {
                            Text(if (snapshot.dueCards.isEmpty()) "No cards due" else "Start due review")
                        }
                    }
                }
            }
            item {
                GradientBanner(
                    title = "Baby-step lessons stay in order",
                    body = if (nextLesson != null) {
                        "Next up: ${nextLesson.lesson.title}. Keep the course tight and focused."
                    } else {
                        "All unlocked lessons are already in motion. Replay any lesson whenever you want cleaner handwriting."
                    },
                    buttonText = if (nextLesson != null) "Open next lesson" else "Browse words",
                    onClick = {
                        if (nextLesson != null) {
                            onOpenLesson(nextLesson.lesson.id)
                        } else {
                            onNavigate(MainDestination.Words)
                        }
                    },
                )
            }
            item {
                SectionRow(title = "Queue preview")
            }
            items(snapshot.dueCards.take(4)) { card ->
                DueCardPreview(card = card)
            }
        }
    }
}

@Composable
internal fun WordsDeckScreen(
    uiState: AppUiState,
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    onOpenLesson: (String) -> Unit,
    onPlayAudio: suspend (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = uiState.snapshot ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf("All") }
    val tags = listOf("All", "Family", "Names", "Home", "Heart", "Pets")
    val wordsLessonId = snapshot.lessons.firstOrNull { it.lesson.stage == "Words" }?.lesson?.id
    val filteredWords = snapshot.focusWords.filter { item ->
        val matchesQuery = query.isBlank() ||
            item.thai.contains(query, ignoreCase = true) ||
            item.transliteration.contains(query, ignoreCase = true) ||
            item.english.contains(query, ignoreCase = true)
        val itemTag = wordTag(item.id)
        val matchesTag = selectedTag == "All" || selectedTag == itemTag
        matchesQuery && matchesTag
    }

    DashboardScaffold(
        selected = selected,
        onNavigate = onNavigate,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                AppHeader(
                    title = "Loved Words",
                    streak = snapshot.streak,
                    onBellClick = { onNavigate(MainDestination.Profile) },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = Slate)
                        },
                        placeholder = { Text("Search names and words") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    MetricBadge(
                        value = filteredWords.size.toString(),
                        label = "words",
                        containerColor = MintTint,
                        accentColor = SeaGlass,
                    )
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChipPill(
                            label = tag,
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                        )
                    }
                }
            }
            item {
                GradientBanner(
                    title = "Small deck, high emotional value",
                    body = "This starter list is hard-coded on purpose so you can memorize the names and words that matter first.",
                    buttonText = if (wordsLessonId != null) "Practice this deck" else "Open practice",
                    onClick = {
                        if (wordsLessonId != null) {
                            onOpenLesson(wordsLessonId)
                        } else {
                            onNavigate(MainDestination.Practice)
                        }
                    },
                )
            }
            item {
                SoftPanel(contentPadding = PaddingValues(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredWords.forEachIndexed { index, item ->
                            FocusWordRow(
                                thai = item.thai,
                                transliteration = item.transliteration,
                                meaning = item.english,
                                tag = wordTag(item.id),
                                chipColor = wordTagColor(item.id),
                                onPlayAudio = { onPlayAudio(item.audioText) },
                                onOpen = {
                                    if (wordsLessonId != null) {
                                        onOpenLesson(wordsLessonId)
                                    }
                                },
                                divider = index < filteredWords.lastIndex,
                            )
                        }
                    }
                }
            }
            item {
                SectionRow(title = "Core alphabet lessons")
            }
            items(snapshot.lessons.take(4)) { lesson ->
                StyledLessonRow(
                    lesson = lesson,
                    onOpenLesson = { if (lesson.unlocked) onOpenLesson(lesson.lesson.id) },
                )
            }
        }
    }
}

@Composable
internal fun ProfileScreen(
    uiState: AppUiState,
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    onReminderSelected: (Int, Int) -> Unit,
    onRedownloadModel: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val snapshot = uiState.snapshot ?: return
    val presets = listOf(7 to 0, 12 to 0, 19 to 0, 21 to 0)

    DashboardScaffold(
        selected = selected,
        onNavigate = onNavigate,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                AppHeader(
                    title = "Progress",
                    streak = snapshot.streak,
                    onBellClick = {},
                )
            }
            item {
                StreakHeroCard(
                    streak = snapshot.streak,
                    subtitle = "Best run so far: ${snapshot.maxStreak} days.",
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiniMetricTile(
                        modifier = Modifier.weight(1f),
                        value = snapshot.startedLessonCount.toString(),
                        label = "Started",
                        accent = MintTint,
                        tint = SeaGlass,
                    )
                    MiniMetricTile(
                        modifier = Modifier.weight(1f),
                        value = snapshot.completedLessonCount.toString(),
                        label = "Complete",
                        accent = Color(0xFFFFF4E9),
                        tint = Saffron,
                    )
                    MiniMetricTile(
                        modifier = Modifier.weight(1f),
                        value = snapshot.dueCards.size.toString(),
                        label = "Due now",
                        accent = LavenderTint,
                        tint = Lavender,
                    )
                }
            }
            item {
                SectionRow(title = "Milestones")
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MilestoneChip("Streak ${snapshot.streak}d", Coral)
                    MilestoneChip("${snapshot.masteredWritingCount} writing targets", SeaGlass)
                    MilestoneChip("${snapshot.focusWords.size} loved words", Lavender)
                    MilestoneChip("${snapshot.completedLessonCount} lessons complete", Ink)
                }
            }
            item {
                SoftPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Reminder", style = ThaiSectionTitleStyle(), color = Ink)
                        Text(
                            "Current daily reminder: ${AppViewModel.formatTime(snapshot.reminderHour, snapshot.reminderMinute)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Slate,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            presets.forEach { (hour, minute) ->
                                FilterChipPill(
                                    label = AppViewModel.formatTime(hour, minute),
                                    selected = snapshot.reminderHour == hour && snapshot.reminderMinute == minute,
                                    onClick = { onReminderSelected(hour, minute) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                StatusPanel(
                    title = "Handwriting model",
                    body = if (uiState.handwritingModelReady) {
                        "Thai handwriting recognition is installed and ready."
                    } else {
                        "Download the Thai handwriting model so the app can check your writing offline."
                    },
                    accent = MintTint,
                    buttonText = if (uiState.handwritingModelReady) "Redownload" else "Download model",
                    onClick = { onRedownloadModel(false) },
                )
            }
            item {
                StatusPanel(
                    title = "Thai audio",
                    body = if (uiState.thaiAudioReady) {
                        "Thai TextToSpeech voice data is available on this device."
                    } else {
                        "Install or enable Thai voice data if audio cards stay silent."
                    },
                    accent = LavenderTint,
                    buttonText = null,
                    onClick = null,
                )
            }
            item {
                NotificationPermissionPanel()
            }
            item {
                UpdatePanel(
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
private fun DashboardScaffold(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = Cloud,
                tonalElevation = 8.dp,
            ) {
                MainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { onNavigate(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Ivory, Color(0xFFF4F8F7), Color(0xFFFFFBF6)),
                    ),
                ),
        ) {
            content(padding)
        }
    }
}

@Composable
private fun AppHeader(
    title: String,
    streak: Int,
    onBellClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color(0xFF063E43),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Cloud,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = CoralDeep,
                    )
                    Text(
                        text = streak.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                    )
                }
            }
            Surface(
                onClick = onBellClick,
                shape = CircleShape,
                color = Cloud,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = "Profile",
                        tint = Ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakHeroCard(
    streak: Int,
    subtitle: String,
) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val activeDays = when {
        streak <= 0 -> 0
        streak >= 7 -> 7
        else -> streak
    }
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF9B62), CoralDeep),
                    ),
                )
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFFE3A4),
                        modifier = Modifier.size(70.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "$streak day streak",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        dayLabels.forEachIndexed { index, label ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index < activeDays) Color.White else Color.White.copy(alpha = 0.18f),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (index < activeDays) {
                                        Text("✓", color = CoralDeep, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Cloud),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun GradientBanner(
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFE0F6F2), Color(0xFFFFF1E4)),
                    ),
                )
                .border(1.dp, Color(0xFFE8DED2), RoundedCornerShape(28.dp))
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = ThaiSectionTitleStyle(), color = Ink)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = Slate)
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(buttonText)
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Cloud),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(accent, Cloud),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = ThaiSectionTitleStyle(), color = Ink)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Slate)
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.84f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = iconTint)
                }
            }
        }
    }
}

@Composable
private fun FocusWordRow(
    thai: String,
    transliteration: String,
    meaning: String,
    tag: String,
    chipColor: Color,
    onPlayAudio: suspend () -> Unit,
    onOpen: () -> Unit,
    divider: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(chipColor.copy(alpha = 0.18f))
                    .clickable { scope.launch { onPlayAudio() } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Headphones, contentDescription = "Play audio", tint = chipColor)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(thai, style = MaterialTheme.typography.headlineMedium, color = Ink)
                    Text(transliteration, style = MaterialTheme.typography.titleLarge, color = chipColor)
                }
                Text(meaning, style = MaterialTheme.typography.bodyLarge, color = Slate)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TagPill(label = tag, tint = chipColor)
                IconButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open", tint = Ink)
                }
            }
        }
        if (divider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CloudEdge),
            )
        }
    }
}

@Composable
private fun DueCardPreview(card: ReviewCardView) {
    val accent = when (card.promptMode) {
        ReviewPromptMode.RECOGNITION -> SeaGlass
        ReviewPromptMode.WRITING -> Saffron
        ReviewPromptMode.AUDIO -> Lavender
    }
    SoftPanel(contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagPill(
                    label = when (card.promptMode) {
                        ReviewPromptMode.RECOGNITION -> "Recall"
                        ReviewPromptMode.WRITING -> "Write"
                        ReviewPromptMode.AUDIO -> "Audio"
                    },
                    tint = accent,
                )
                Text(card.primaryPrompt, style = ThaiSectionTitleStyle(), color = Ink)
                Text(card.item.thai, style = MaterialTheme.typography.bodyLarge, color = Slate)
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = accent)
            }
        }
    }
}

@Composable
private fun StyledLessonRow(
    lesson: LessonOverview,
    onOpenLesson: () -> Unit,
) {
    val accent = when (lesson.lesson.stage) {
        "Consonants" -> SeaGlass
        "Vowels" -> Saffron
        "Tone marks" -> Lavender
        "Words" -> CoralDeep
        else -> Palm
    }
    SoftPanel(
        modifier = Modifier.clickable(enabled = lesson.unlocked, onClick = onOpenLesson),
        contentPadding = PaddingValues(20.dp),
    ) {
        Text(lesson.lesson.stage, style = ThaiSectionOverlineStyle(), color = accent)
        Text(lesson.lesson.title, style = ThaiSectionTitleStyle(), color = Ink)
        Text(lesson.lesson.description, style = MaterialTheme.typography.bodyMedium, color = Slate)
        Text(
            if (lesson.unlocked) {
                "${lesson.masteredCount}/${lesson.totalCount} writing targets stable"
            } else {
                "Locked until the previous lesson is mastered"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (lesson.unlocked) Ink else Slate,
        )
    }
}

@Composable
private fun StatusPanel(
    title: String,
    body: String,
    accent: Color,
    buttonText: String?,
    onClick: (() -> Unit)?,
) {
    SoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = ThaiSectionTitleStyle(), color = Ink)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Slate)
            if (buttonText != null && onClick != null) {
                OutlinedButton(onClick = onClick) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
private fun UpdatePanel(
    uiState: AppUiState,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
) {
    val update = uiState.updateInfo
    SoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("App updates", style = ThaiSectionTitleStyle(), color = Ink)
            Text(
                when {
                    !uiState.updateSupported -> "GitHub release updates work only in the signed release build."
                    uiState.updateDownloading -> "Downloading ${update?.latestVersionName ?: "update"}${uiState.updateDownloadProgress?.let { " ($it%)" } ?: ""}."
                    uiState.updateChecking -> "Checking GitHub Releases for a newer APK."
                    update != null -> "Version ${update.latestVersionName} is available. You are on ${update.currentVersionName}."
                    else -> "Current version ${BuildConfig.VERSION_NAME}. Open GitHub Releases any time."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    enabled = uiState.updateSupported && !uiState.updateChecking && !uiState.updateDownloading,
                    onClick = onCheckUpdates,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Check")
                }
                OutlinedButton(onClick = onOpenUpdatePage) {
                    Text(if (update != null) "Open release" else "Open releases")
                }
                if (update != null) {
                    Button(
                        enabled = !uiState.updateDownloading,
                        onClick = onInstallUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = SeaGlass),
                    ) {
                        Text("Install update")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionPanel() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationPermissionGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
    }

    SoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Notifications", style = ThaiSectionTitleStyle(), color = Ink)
            Text(
                if (granted) {
                    "Daily reminder notifications are enabled."
                } else {
                    "Android still needs notification permission before reminders can appear."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
            )
            if (!granted && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Enable notifications")
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ThaiSectionTitleStyle(), color = Ink)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action)
                Spacer(modifier = Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun MetricBadge(
    value: String,
    label: String,
    containerColor: Color,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accentColor)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = accentColor)
        }
    }
}

@Composable
private fun MiniMetricTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    accent: Color,
    tint: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = tint)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TagPill(
    label: String,
    tint: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MintTint else Cloud,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) SeaGlass else Ink,
        )
    }
}

@Composable
private fun MilestoneChip(
    label: String,
    tint: Color,
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, color = tint) },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            disabledContainerColor = tint.copy(alpha = 0.12f),
            disabledLabelColor = tint,
        ),
        border = null,
    )
}

@Composable
private fun ThaiSectionTitleStyle() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun ThaiSectionOverlineStyle() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

private fun notificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun wordTag(itemId: String): String = when (itemId) {
    "mae", "pho", "yai" -> "Family"
    "beam", "ann" -> "Names"
    "ban", "nam", "na" -> "Home"
    "chai" -> "Heart"
    "maeo" -> "Pets"
    else -> "Words"
}

private fun wordTagColor(itemId: String): Color = when (wordTag(itemId)) {
    "Family" -> SeaGlass
    "Names" -> Saffron
    "Home" -> Color(0xFF3D9E92)
    "Heart" -> Lavender
    "Pets" -> CoralDeep
    else -> Palm
}
