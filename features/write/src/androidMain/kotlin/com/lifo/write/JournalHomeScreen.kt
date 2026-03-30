package com.lifo.write

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lifo.util.repository.MediaUploadRepository
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lifo.ui.components.CalmifyTopBar
import com.lifo.ui.emotion.MiniMoodShape
import com.lifo.util.auth.UserIdentityResolver
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

/**
 * Journal Home -- Diary list + activity pills for quick access to tools.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalHomeScreen(
    onWriteClick: () -> Unit,
    onBrainDumpClick: () -> Unit = {},
    onGratitudeClick: () -> Unit = {},
    onDiaryClick: (String) -> Unit,
    onInsightClick: (String) -> Unit,
    onMenuClicked: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    // Activity navigation
    onEnergyClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {},
    onMeditationClick: () -> Unit = {},
    onMovementClick: () -> Unit = {},
    onAllActivitiesClick: () -> Unit = {},
    mongoRepository: MongoRepository = koinInject(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val diariesState by mongoRepository.getAllDiaries().collectAsState(initial = RequestState.Loading)

    val diaries = remember(diariesState) {
        when (val state = diariesState) {
            is RequestState.Success -> state.data.values.flatten().sortedByDescending { it.dateMillis }
            else -> emptyList()
        }
    }

    val isLoading = diariesState is RequestState.Loading

    val diariesWithImages = remember(diaries) {
        diaries.filter { it.images.isNotEmpty() }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colorScheme.background,
        topBar = {
            CalmifyTopBar(title = "Journal", scrollBehavior = scrollBehavior)
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Daily Prompt
            item(key = "prompt") {
                DailyPromptCard(
                    diaries = diaries,
                    onWriteClick = onWriteClick,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Activity pills — horizontal scrollable row
            item(key = "activity-pills") {
                ActivityPillsRow(
                    onWriteClick = onWriteClick,
                    onBrainDumpClick = onBrainDumpClick,
                    onGratitudeClick = onGratitudeClick,
                    onEnergyClick = onEnergyClick,
                    onSleepClick = onSleepClick,
                    onHabitsClick = onHabitsClick,
                    onMeditationClick = onMeditationClick,
                    onMovementClick = onMovementClick,
                    onAllActivitiesClick = onAllActivitiesClick,
                )
            }

            // Photo Carousel
            if (diariesWithImages.isNotEmpty()) {
                item(key = "photo-carousel") {
                    DiaryPhotoCarousel(
                        diariesWithImages = diariesWithImages,
                        onDiaryClick = onDiaryClick,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // Recent Diaries
            if (diaries.isNotEmpty()) {
                item(key = "recent-header") {
                    Text(
                        text = "Le tue riflessioni",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            if (isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(items = diaries, key = { "journal-${it._id}" }) { diary ->
                JournalDiaryItem(
                    diary = diary,
                    onClick = { onDiaryClick(diary._id) },
                    onInsightClick = { onInsightClick(diary._id) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            if (!isLoading && diaries.isEmpty()) {
                item(key = "empty") { EmptyJournalState() }
            }
        }
    }
}

// ==================== ACTIVITY PILLS ROW ====================

private data class ActivityPill(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit,
)

@Composable
private fun ActivityPillsRow(
    onWriteClick: () -> Unit,
    onBrainDumpClick: () -> Unit,
    onGratitudeClick: () -> Unit,
    onEnergyClick: () -> Unit,
    onSleepClick: () -> Unit,
    onHabitsClick: () -> Unit,
    onMeditationClick: () -> Unit,
    onMovementClick: () -> Unit,
    onAllActivitiesClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    val pills = remember(
        onWriteClick, onBrainDumpClick, onGratitudeClick,
        onEnergyClick, onSleepClick, onHabitsClick,
        onMeditationClick, onMovementClick, onAllActivitiesClick
    ) {
        listOf(
            ActivityPill(Icons.Outlined.Edit, "Diario", Color(0xFF42A5F5), onWriteClick),
            ActivityPill(Icons.Outlined.Favorite, "Gratitudine", Color(0xFFE91E63), onGratitudeClick),
            ActivityPill(Icons.Outlined.BatteryChargingFull, "Energia", Color(0xFFFF9800), onEnergyClick),
            ActivityPill(Icons.Outlined.Bedtime, "Sonno", Color(0xFF5C6BC0), onSleepClick),
            ActivityPill(Icons.Outlined.SelfImprovement, "Meditazione", Color(0xFF7E57C2), onMeditationClick),
            ActivityPill(Icons.Outlined.CheckCircle, "Abitudini", Color(0xFF26A69A), onHabitsClick),
            ActivityPill(Icons.Outlined.CloudDownload, "Brain Dump", Color(0xFF78909C), onBrainDumpClick),
            ActivityPill(Icons.AutoMirrored.Outlined.DirectionsRun, "Movimento", Color(0xFFEF6C00), onMovementClick),
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        // "Tutte" pill first
        item(key = "all") {
            FilledTonalButton(
                onClick = onAllActivitiesClick,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tutte",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }

        items(pills.size, key = { "pill-$it" }) { index ->
            val pill = pills[index]
            FilledTonalButton(
                onClick = pill.onClick,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = pill.color.copy(alpha = 0.1f),
                    contentColor = pill.color
                )
            ) {
                Icon(pill.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    pill.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

// ==================== DAILY PROMPT ====================

@Composable
private fun DailyPromptCard(
    diaries: List<Diary>,
    onWriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val prompt = remember(diaries) { getContextualPrompt(diaries) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onWriteClick),
        color = colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Text(
                    "Spunto del giorno",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onPrimaryContainer
                )
            }
            Text(
                prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Text(
                "Tocca per iniziare a scrivere",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )
        }
    }
}

// ==================== DIARY ITEM ====================

@Composable
private fun JournalDiaryItem(
    diary: Diary,
    onClick: () -> Unit,
    onInsightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val mood = try { Mood.valueOf(diary.mood) } catch (e: Exception) { Mood.Neutral }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniMoodShape(mood = mood, modifier = Modifier.size(28.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    diary.title.ifBlank { "Senza titolo" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurface
                )
                if (diary.description.isNotBlank()) {
                    Text(
                        diary.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Text(
                    formatDiaryTimestamp(diary.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onInsightClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Insights",
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.tertiary
                )
            }
        }
    }
}

// ==================== PHOTO CAROUSEL ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryPhotoCarousel(
    diariesWithImages: List<Diary>,
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    mediaRepository: MediaUploadRepository = koinInject(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val carouselItems = remember(diariesWithImages) {
        diariesWithImages.flatMap { diary -> diary.images.map { path -> diary to path } }
    }
    val resolvedUrls = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(carouselItems) {
        val paths = carouselItems.map { it.second }
        resolvedUrls.value = mediaRepository.resolveImageUrls(paths)
    }
    val carouselState = rememberCarouselState { carouselItems.size }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "I tuoi ricordi",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )
            Text(
                "${diariesWithImages.size} diari con foto",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 200.dp,
            itemSpacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) { index ->
            val (diary, path) = carouselItems[index]
            val imageUrl = resolvedUrls.value[path]
            DiaryPhotoCard(
                diary = diary,
                imageUrl = imageUrl,
                onImageClick = { imageUrl?.let { expandedImageUrl = it } },
                onDiaryClick = { onDiaryClick(diary._id) },
                modifier = Modifier.maskClip(RoundedCornerShape(20.dp))
            )
        }
    }

    expandedImageUrl?.let { url ->
        FullscreenImageViewer(imageUrl = url, onDismiss = { expandedImageUrl = null })
    }
}

@Composable
private fun DiaryPhotoCard(
    diary: Diary,
    imageUrl: String?,
    onImageClick: () -> Unit,
    onDiaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mood = try { Mood.valueOf(diary.mood) } catch (_: Exception) { Mood.Neutral }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().clickable(onClick = onImageClick)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = diary.title.ifBlank { "Foto diario" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opzioni", modifier = Modifier.size(16.dp), tint = Color.White)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Vai al diario") },
                    onClick = { showMenu = false; onDiaryClick() },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                diary.title.ifBlank { "Senza titolo" },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDiaryTimestamp(diary.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        mood.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
            }
            AsyncImage(
                model = imageUrl,
                contentDescription = "Immagine ingrandita",
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                            else { offsetX = 0f; offsetY = 0f }
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun EmptyJournalState() {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                "Il tuo diario e' vuoto",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )
            Text(
                "Inizia a scrivere le tue riflessioni",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ======================================================================
// Helper functions
// ======================================================================

private fun getContextualPrompt(diaries: List<Diary>): String {
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val today = LocalDate.now()
    val lastDiary = diaries.firstOrNull() ?: return "Questo e' il tuo spazio sicuro. Come stai, davvero?"

    val lastDate = Instant.ofEpochMilli(lastDiary.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val daysSinceLast = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today).toInt()
    val lastMood = try { Mood.valueOf(lastDiary.mood) } catch (_: Exception) { Mood.Neutral }

    if (daysSinceLast == 0) return when (hour) {
        in 5..12 -> "Hai gia' scritto oggi. Come si e' evoluta la giornata?"
        in 13..18 -> "Un secondo pensiero per oggi? A volte le cose cambiano nel pomeriggio."
        else -> "Prima di dormire: cosa porti con te di questa giornata?"
    }

    if (daysSinceLast > 3) return "Bentornato. Non importa quanto tempo e' passato -- sei qui adesso."

    val streak = calculateStreak(diaries)
    if (streak >= 7) return "$streak giorni consecutivi. Stai costruendo qualcosa di importante."
    if (streak >= 3) return "$streak giorni di fila -- la costanza fa la differenza. Come va oggi?"

    if (daysSinceLast <= 2) {
        val moodFollowUp = getMoodFollowUp(lastMood, hour)
        if (moodFollowUp != null) return moodFollowUp
    }

    val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    return when {
        hour in 5..9 && isWeekend -> "Sabato/domenica senza fretta. Come ti senti stamattina?"
        hour in 5..9 -> "Una nuova giornata. Con che intenzione vuoi viverla?"
        hour in 10..13 -> "Meta' mattina. Qualcosa ha catturato la tua attenzione?"
        hour in 14..17 && isWeekend -> "Come stai spendendo questo tempo libero?"
        hour in 14..17 -> "Il pomeriggio e' un buon momento per fermarsi. Cosa noti dentro di te?"
        hour in 18..21 -> "La giornata si chiude. Qual e' stato il momento piu' vero?"
        else -> "Prima di lasciar andare questa giornata -- cosa vuoi ricordare?"
    }
}

private fun getMoodFollowUp(lastMood: Mood, hour: Int): String? = when (lastMood) {
    Mood.Depressed, Mood.Awful -> "L'ultima volta non era facile. Come stai oggi?"
    Mood.Lonely -> "Ti sentivi solo/a. Qualcosa e' cambiato?"
    Mood.Tense -> "C'era tensione nell'aria. Si e' sciolta un po'?"
    Mood.Angry -> "La rabbia di ieri -- e' ancora li' o si e' trasformata?"
    Mood.Disappointed -> "Ieri c'era delusione. Oggi riesci a vederla diversamente?"
    Mood.Shameful -> "Hai scritto qualcosa di difficile. Come va ora?"
    Mood.Happy, Mood.Romantic -> if (hour < 14) "Ieri eri di buon umore. Riesci a portare quell'energia anche oggi?" else null
    Mood.Calm -> if (hour >= 18) "La calma di ieri -- riesci a ritrovarla anche stasera?" else null
    else -> null
}

private fun calculateStreak(diaries: List<Diary>): Int {
    if (diaries.isEmpty()) return 0
    val today = LocalDate.now()
    var streak = 0
    var checkDate = today
    val diaryDates = diaries.map { Instant.ofEpochMilli(it.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
    while (diaryDates.contains(checkDate)) {
        streak++
        checkDate = checkDate.minusDays(1)
    }
    return streak
}

private fun formatDiaryTimestamp(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val now = java.time.ZonedDateTime.now()
    return when {
        zonedDateTime.toLocalDate() == now.toLocalDate() -> "Oggi, ${zonedDateTime.format(DateTimeFormatter.ofPattern("H:mm"))}"
        zonedDateTime.toLocalDate() == now.minusDays(1).toLocalDate() -> "Ieri, ${zonedDateTime.format(DateTimeFormatter.ofPattern("H:mm"))}"
        else -> zonedDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
