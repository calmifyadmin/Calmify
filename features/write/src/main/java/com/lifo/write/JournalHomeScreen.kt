package com.lifo.write

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
import com.lifo.ui.emotion.MiniMoodShape
import com.lifo.util.auth.AuthProvider
import com.lifo.util.auth.UserIdentityResolver
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.NotificationRepository
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

/**
 * Journal Home screen — the primary journaling experience.
 * Shows a daily prompt, recent diary entries, and a FAB to write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalHomeScreen(
    onWriteClick: () -> Unit,
    onDiaryClick: (String) -> Unit,
    onInsightClick: (String) -> Unit,
    onMenuClicked: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    mongoRepository: MongoRepository = koinInject(),
    authProvider: AuthProvider = koinInject(),
    notificationRepository: NotificationRepository = koinInject(),
) {
    val diariesState by mongoRepository.getAllDiaries().collectAsState(initial = RequestState.Loading)

    // Flatten and sort diaries
    val diaries = remember(diariesState) {
        when (val state = diariesState) {
            is RequestState.Success -> state.data.values.flatten().sortedByDescending { it.dateMillis }
            else -> emptyList()
        }
    }

    val isLoading = diariesState is RequestState.Loading
    val userName = UserIdentityResolver.resolveFirstName(
        authDisplayName = authProvider.currentUserDisplayName,
        authEmail = authProvider.currentUserEmail,
    )
    val greeting = remember { getTimeBasedGreeting() }

    // Diaries with photos for carousel
    val diariesWithImages = remember(diaries) {
        diaries.filter { it.images.isNotEmpty() }
    }

    // Notification unread count
    val userId = authProvider.currentUserId
    val unreadCount by remember(userId) {
        if (userId != null) notificationRepository.getUnreadCount(userId)
        else flowOf(0)
    }.collectAsState(initial = 0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Journal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClicked) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifiche"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        // FAB removed — handled by global contextual FAB in DecomposeApp
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 24.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting header
            item(key = "greeting") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (userName.isNotBlank()) "$greeting, $userName" else greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Il tuo spazio per riflettere",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Daily prompt card — contextual based on last diary, streak, mood
            item(key = "prompt") {
                DailyPromptCard(
                    diaries = diaries,
                    onWriteClick = onWriteClick
                )
            }

            // Photo carousel — diaries with images
            if (diariesWithImages.isNotEmpty()) {
                item(key = "photo-carousel") {
                    DiaryPhotoCarousel(
                        diariesWithImages = diariesWithImages,
                        onDiaryClick = onDiaryClick
                    )
                }
            }

            // Recent diaries section header
            if (diaries.isNotEmpty()) {
                item(key = "recent-header") {
                    Text(
                        text = "Le tue riflessioni",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Loading indicator
            if (isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Diary list
            items(
                items = diaries,
                key = { "journal-${it._id}" }
            ) { diary ->
                JournalDiaryItem(
                    diary = diary,
                    onClick = { onDiaryClick(diary._id) },
                    onInsightClick = { onInsightClick(diary._id) }
                )
            }

            // Empty state
            if (!isLoading && diaries.isEmpty()) {
                item(key = "empty") {
                    EmptyJournalState()
                }
            }
        }
    }
}

@Composable
private fun DailyPromptCard(
    diaries: List<Diary>,
    onWriteClick: () -> Unit
) {
    val prompt = remember(diaries) { getContextualPrompt(diaries) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onWriteClick),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        tonalElevation = 0.dp
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
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Spunto del giorno",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Text(
                text = "Tocca per iniziare a scrivere",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun JournalDiaryItem(
    diary: Diary,
    onClick: () -> Unit,
    onInsightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mood = try { Mood.valueOf(diary.mood) } catch (e: Exception) { Mood.Neutral }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mood shape
            MiniMoodShape(
                mood = mood,
                modifier = Modifier.size(28.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = diary.title.ifBlank { "Senza titolo" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (diary.description.isNotBlank()) {
                    Text(
                        text = diary.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = formatDiaryTimestamp(diary.dateMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Insight button
            IconButton(
                onClick = onInsightClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "View Insights",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Diary Photo Carousel (Material 3 Expressive)
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryPhotoCarousel(
    diariesWithImages: List<Diary>,
    onDiaryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Flatten to (diary, remotePath) pairs for the carousel items
    val carouselItems = remember(diariesWithImages) {
        diariesWithImages.flatMap { diary ->
            diary.images.map { path -> diary to path }
        }
    }

    // Resolve Firebase Storage paths to download URLs
    val resolvedUrls = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(carouselItems) {
        val storage = FirebaseStorage.getInstance().reference
        carouselItems.forEach { (_, path) ->
            if (path.startsWith("http")) {
                resolvedUrls.value = resolvedUrls.value + (path to path)
            } else {
                storage.child(path.trim()).downloadUrl
                    .addOnSuccessListener { uri ->
                        resolvedUrls.value = resolvedUrls.value + (path to uri.toString())
                    }
            }
        }
    }

    val carouselState = rememberCarouselState { carouselItems.size }

    // Fullscreen image viewer state
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "I tuoi ricordi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${diariesWithImages.size} diari con foto",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.maskClip(RoundedCornerShape(16.dp))
            )
        }
    }

    // Fullscreen image viewer
    expandedImageUrl?.let { url ->
        FullscreenImageViewer(
            imageUrl = url,
            onDismiss = { expandedImageUrl = null }
        )
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onImageClick)
    ) {
        if (imageUrl != null) {
            // Photo
            AsyncImage(
                model = imageUrl,
                contentDescription = diary.title.ifBlank { "Foto diario" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Loading placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // Gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        // Three-dot menu top right → opens diary
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opzioni",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Vai al diario") },
                    onClick = {
                        showMenu = false
                        onDiaryClick()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    }
                )
            }
        }

        // Bottom info overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = diary.title.ifBlank { "Senza titolo" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDiaryTimestamp(diary.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = mood.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Chiudi",
                    tint = Color.White
                )
            }

            // Zoomable image
            AsyncImage(
                model = imageUrl,
                contentDescription = "Immagine ingrandita",
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun EmptyJournalState() {
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "Il tuo diario e' vuoto",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Inizia a scrivere le tue riflessioni",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun getTimeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Buongiorno"
        in 12..17 -> "Buon pomeriggio"
        in 18..22 -> "Buonasera"
        else -> "Buonanotte"
    }
}

/**
 * Contextual daily prompt based on:
 * - Last diary mood (empathetic follow-up)
 * - Writing streak / gap (encouragement or welcome back)
 * - Time of day (morning vs evening)
 * - Day of week (weekend vs weekday)
 */
private fun getContextualPrompt(diaries: List<Diary>): String {
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val today = LocalDate.now()

    val lastDiary = diaries.firstOrNull()

    // No diaries at all — first time
    if (lastDiary == null) {
        return "Questo e' il tuo spazio sicuro. Come stai, davvero?"
    }

    val lastDate = Instant.ofEpochMilli(lastDiary.dateMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val daysSinceLast = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today).toInt()
    val lastMood = try { Mood.valueOf(lastDiary.mood) } catch (_: Exception) { Mood.Neutral }

    // Already wrote today — encourage a second entry or reflection
    if (daysSinceLast == 0) {
        return when (hour) {
            in 5..12 -> "Hai gia' scritto oggi. Come si e' evoluta la giornata?"
            in 13..18 -> "Un secondo pensiero per oggi? A volte le cose cambiano nel pomeriggio."
            else -> "Prima di dormire: cosa porti con te di questa giornata?"
        }
    }

    // Gap > 3 days — gentle welcome back
    if (daysSinceLast > 3) {
        return "Bentornato. Non importa quanto tempo e' passato — sei qui adesso."
    }

    // Calculate streak: consecutive days with at least one diary
    val streak = calculateStreak(diaries)

    // Streak acknowledgment (3+ days)
    if (streak >= 7) {
        return "$streak giorni consecutivi. Stai costruendo qualcosa di importante."
    }
    if (streak >= 3) {
        return "$streak giorni di fila — la costanza fa la differenza. Come va oggi?"
    }

    // Mood-based follow-up (wrote yesterday or day before)
    if (daysSinceLast <= 2) {
        val moodFollowUp = getMoodFollowUp(lastMood, hour)
        if (moodFollowUp != null) return moodFollowUp
    }

    // Fallback: time-of-day + day-of-week contextual prompts
    val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    return when {
        hour in 5..9 && isWeekend -> "Sabato/domenica senza fretta. Come ti senti stamattina?"
        hour in 5..9 -> "Una nuova giornata. Con che intenzione vuoi viverla?"
        hour in 10..13 -> "Meta' mattina. Qualcosa ha catturato la tua attenzione?"
        hour in 14..17 && isWeekend -> "Come stai spendendo questo tempo libero?"
        hour in 14..17 -> "Il pomeriggio e' un buon momento per fermarsi. Cosa noti dentro di te?"
        hour in 18..21 -> "La giornata si chiude. Qual e' stato il momento piu' vero?"
        else -> "Prima di lasciar andare questa giornata — cosa vuoi ricordare?"
    }
}

private fun getMoodFollowUp(lastMood: Mood, hour: Int): String? = when (lastMood) {
    Mood.Depressed, Mood.Awful -> "L'ultima volta non era facile. Come stai oggi?"
    Mood.Lonely -> "Ti sentivi solo/a. Qualcosa e' cambiato?"
    Mood.Tense -> "C'era tensione nell'aria. Si e' sciolta un po'?"
    Mood.Angry -> "La rabbia di ieri — e' ancora li' o si e' trasformata?"
    Mood.Disappointed -> "Ieri c'era delusione. Oggi riesci a vederla diversamente?"
    Mood.Shameful -> "Hai scritto qualcosa di difficile. Come va ora?"
    Mood.Happy, Mood.Romantic -> if (hour < 14) {
        "Ieri eri di buon umore. Riesci a portare quell'energia anche oggi?"
    } else null
    Mood.Calm -> if (hour >= 18) {
        "La calma di ieri — riesci a ritrovarla anche stasera?"
    } else null
    else -> null
}

private fun calculateStreak(diaries: List<Diary>): Int {
    if (diaries.isEmpty()) return 0
    val today = LocalDate.now()
    var streak = 0
    var checkDate = today

    val diaryDates = diaries.map { diary ->
        Instant.ofEpochMilli(diary.dateMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
    }.toSet()

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
        zonedDateTime.toLocalDate() == now.toLocalDate() -> {
            "Oggi, ${zonedDateTime.format(DateTimeFormatter.ofPattern("H:mm"))}"
        }
        zonedDateTime.toLocalDate() == now.minusDays(1).toLocalDate() -> {
            "Ieri, ${zonedDateTime.format(DateTimeFormatter.ofPattern("H:mm"))}"
        }
        else -> {
            zonedDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }
}
