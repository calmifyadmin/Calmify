package com.lifo.write

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.google.firebase.storage.FirebaseStorage
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.ui.emotion.MiniMoodShape
import com.lifo.ui.emotion.MoodShapeIndicator
import com.lifo.ui.providers.MoodUiProvider
import com.lifo.util.model.BodySensation
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.util.model.Trigger
import com.lifo.write.wizard.IntensityShapes
import com.lifo.write.wizard.MetricSymbolicColors
import com.lifo.write.wizard.drawIntensityShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// ── Dimensions ──────────────────────────────────────────────────
private val HeroExpandedHeight = 340.dp
private val ToolbarHeight      = 80.dp
private val IconExpandedSize   = 64.dp
private val IconCollapsedSize  = 44.dp
private val OrbExpandedSize    = 112.dp
private val OrbCollapsedSize   = 58.dp

/**
 * DiaryDetailScreen con aurora emotion-driven.
 *
 * Il bagliore parte dall'orb nell'header e si propaga verso il basso
 * attraverso tutto lo schermo — come un sole che "illumina dal top".
 * Il colore è unico per ogni emozione (moodColor) ed è un glow
 * stratificato con più orb radiali sovrapposti.
 *
 * Il body ha uno sfondo SEMI-TRASPARENTE sopra il gradiente globale,
 * così il bagliore "filtra" anche nelle card.
 */
@Composable
internal fun DiaryDetailScreen(
    diary: Diary,
    onBackPressed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onInsightClicked: (diaryId: String) -> Unit = {},
) {
    val mood      = remember(diary.mood) { Mood.valueOf(diary.mood) }
    val moodColor = remember(mood)       { MoodUiProvider.getContainerColor(mood) }

    val formattedDate = remember(diary.dateMillis) {
        DateTimeFormatter
            .ofPattern("EEEE d MMMM yyyy • HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(diary.dateMillis))
            .replaceFirstChar { it.uppercase() }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    val scrollState      = rememberScrollState()
    val density          = LocalDensity.current

    val collapseRange = with(density) { (HeroExpandedHeight - ToolbarHeight).toPx() }
    val collapseProgress by remember {
        derivedStateOf { (scrollState.value / collapseRange).coerceIn(0f, 1f) }
    }

    // M3 tokens — catturati qui, usati sia nel gradiente che nelle card
    val surface            = MaterialTheme.colorScheme.surface
    val primaryContainer   = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ── BASE: surface M3, adattivo light/dark ──
            .background(surface),
    ) {

        val isLight = remember(surface) {
            surface.red * 0.299f + surface.green * 0.587f + surface.blue * 0.114f > 0.5f
        }
        // ════════════════════════════════════════════════════════
        // AURORA GLOBALE — copre l'INTERO schermo, NON scrolla.
        // Il bagliore è centrato sull'header e si propaga verso
        // il basso con decadimento naturale.
        // In dark mode: sembra una nebulosa colorata su nero.
        // In light mode: lavaggio pastello leggero e luminoso.
        // ════════════════════════════════════════════════════════
        AuroraBackground(
            moodColor        = moodColor,
            primaryContainer = primaryContainer,
            secondaryContainer = secondaryContainer,
            surface          = surface,
            modifier         = Modifier.fillMaxSize(),
        )

        // ── SCROLLABLE CONTENT ───────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // Zona hero — trasparente: l'aurora si vede
            Spacer(Modifier.height(HeroExpandedHeight).fillMaxWidth())

            // Body: trasparente — l'aurora lava lamp filtra attraverso tutto
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to surface.copy(alpha = 0.00f),   // hero bottom: trasparente
                                0.05f to surface.copy(alpha = 0.15f),   // leggerissimo velo
                                0.15f to surface.copy(alpha = 0.30f),   // aurora filtra forte
                                0.40f to surface.copy(alpha = 0.45f),   // semi-trasparente
                                0.70f to surface.copy(alpha = 0.55f),   // aurora ancora visibile
                                1.00f to surface.copy(alpha = 0.65f),   // fondo: aurora filtra ancora
                            )
                        )
                    ),
            ){
                Spacer(Modifier.height(20.dp))

                // Data
                Text(
                    text     = formattedDate,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 20.dp),
                )

                if (diary.description.isNotBlank()) {
                    DescriptionCard(description = diary.description, moodColor = moodColor)
                    Spacer(Modifier.height(20.dp))
                }

                MetricsDashboard(diary = diary)
                Spacer(Modifier.height(20.dp))

                ContextChipsRow(diary = diary)

                if (diary.images.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    ImagesRow(images = diary.images)
                }

                Spacer(Modifier.height(96.dp))
            }
        }

        // ── HERO OVERLAY ─────────────────────────────────────────
        CollapsingHeroOverlay(
            diary            = diary,
            mood             = mood,
            moodColor        = moodColor,
            surface          = surface,
            collapseProgress = collapseProgress,
            onBackPressed    = onBackPressed,
            onDeleteClicked  = { showDeleteDialog = true },
        )

        // ── FAB Insight ──────────────────────────────────────────
        FloatingActionButton(
            onClick        = { onInsightClicked(diary._id) },
            containerColor = moodColor,
            contentColor   = surface,
            shape          = RoundedCornerShape(20.dp),
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text       = "Insight",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        GrainOverlay(
            modifier  = Modifier.fillMaxSize(),
            intensity = 0.030f,       // 0.02 sottile → 0.06 molto visibile
            isDark    = !isLight,     // bianco in dark, nero in light
            seed      = 42L,          // fisso = no flickering durante scroll
        )
    }

    DisplayAlertDialog(
        title          = "Elimina",
        message        = "Sei sicuro di voler eliminare '${diary.title}'?",
        dialogOpened   = showDeleteDialog,
        onDialogClosed = { showDeleteDialog = false },
        onYesClicked   = onDeleteConfirmed,
    )
}



// ─── Lava Lamp Aurora Background ────────────────────────────────────────────

@Composable
fun AuroraBackground(
    moodColor: Color,
    primaryContainer: Color,
    secondaryContainer: Color,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    val isLight = remember(surface) {
        surface.red * 0.299f + surface.green * 0.587f + surface.blue * 0.114f > 0.5f
    }

    // Alpha contenuto → surface domina, colore è aura organica
    val a = if (isLight) 0.20f else 0.32f

    val c1 = moodColor.vivify(if (isLight) 0.5f else 0.7f)
    val c2 = moodColor.rotateHue(40f).vivify(if (isLight) 0.45f else 0.65f)
    val c3 = moodColor.rotateHue(-30f).vivify(if (isLight) 0.4f else 0.6f)

    Box(modifier = modifier.drawBehind {
        val W = size.width
        val H = size.height

        drawRect(color = surface)

        // ── Blob A: enorme, parzialmente fuori schermo a sinistra-alto ──
        // Solo l'arco destro è visibile → forma organica, non cerchio
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to c1.copy(alpha = a * 0.9f),
                    0.4f to c1.copy(alpha = a * 0.4f),
                    0.7f to c1.copy(alpha = a * 0.1f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(-W * 0.15f, -H * 0.02f),
                radius = W * 0.75f,
            ),
            center = Offset(-W * 0.15f, -H * 0.02f),
            radius = W * 0.75f,
        )

        // ── Blob B: fuori schermo a destra, metà visibile ──
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to c2.copy(alpha = a * 0.7f),
                    0.35f to c2.copy(alpha = a * 0.35f),
                    0.7f to c2.copy(alpha = a * 0.08f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(W * 1.15f, H * 0.18f),
                radius = W * 0.65f,
            ),
            center = Offset(W * 1.15f, H * 0.18f),
            radius = W * 0.65f,
        )

        // ── Blob C: centro schermo, grande e molto diffuso ──
        // Si sovrappone ad A e B dove si incontrano → fusione lava lamp
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to c3.copy(alpha = a * 0.5f),
                    0.3f to c1.copy(alpha = a * 0.25f),
                    0.6f to c2.copy(alpha = a * 0.08f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(W * 0.55f, H * 0.30f),
                radius = W * 0.60f,
            ),
            center = Offset(W * 0.55f, H * 0.30f),
            radius = W * 0.60f,
        )

        // ── Blob D: basso-sinistra, parzialmente fuori schermo ──
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to c2.copy(alpha = a * 0.55f),
                    0.4f to c3.copy(alpha = a * 0.2f),
                    0.75f to c1.copy(alpha = a * 0.05f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(-W * 0.05f, H * 0.65f),
                radius = W * 0.55f,
            ),
            center = Offset(-W * 0.05f, H * 0.65f),
            radius = W * 0.55f,
        )

        // ── Blob E: basso-destra, piccolo accento ──
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to c3.copy(alpha = a * 0.4f),
                    0.5f to c1.copy(alpha = a * 0.12f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(W * 0.85f, H * 0.75f),
                radius = W * 0.40f,
            ),
            center = Offset(W * 0.85f, H * 0.75f),
            radius = W * 0.40f,
        )

        // ── Blob F: accento piccolo centro-alto ──
        // Rompe la simmetria tra A e B
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to c1.copy(alpha = a * 0.35f),
                    0.45f to c3.copy(alpha = a * 0.1f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(W * 0.35f, H * 0.12f),
                radius = W * 0.30f,
            ),
            center = Offset(W * 0.35f, H * 0.12f),
            radius = W * 0.30f,
        )
    })
}


private fun Color.rotateHue(degrees: Float): Color {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (abs(max - min) < 0.001f) return this

    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r    -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
        g    -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }

    val hNew = ((h + degrees / 360f) % 1f + 1f) % 1f

    fun hue2rgb(p: Float, q: Float, t: Float): Float {
        val tt = (t % 1f + 1f) % 1f
        return when {
            tt < 1/6f -> p + (q - p) * 6f * tt
            tt < 1/2f -> q
            tt < 2/3f -> p + (q - p) * (2/3f - tt) * 6f
            else      -> p
        }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    return Color(
        red   = hue2rgb(p, q, hNew + 1f/3f),
        green = hue2rgb(p, q, hNew),
        blue  = hue2rgb(p, q, hNew - 1f/3f),
        alpha = alpha,
    )
}



// ── Utilities di colore ───────────────────────────────────────────

/** Interpola linearmente tra due colori. t=0 → a, t=1 → b */
private fun blendColors(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f,
)

/** Aumenta la saturazione di un colore senza alterare la luminosità. */
private fun Color.vivify(factor: Float): Color {
    val avg = (red + green + blue) / 3f
    return Color(
        red   = (avg + (red   - avg) * factor).coerceIn(0f, 1f),
        green = (avg + (green - avg) * factor).coerceIn(0f, 1f),
        blue  = (avg + (blue  - avg) * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// GrainOverlay — texture granulosa sopra l'aurora.
// Usare come ULTIMO figlio del Box:
//   GrainOverlay(modifier = Modifier.fillMaxSize(), isDark = !isLight)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GrainOverlay(
    modifier: Modifier = Modifier,
    intensity: Float = 0.032f,
    isDark: Boolean = false,
    seed: Long = 42L,
) {
    val grainPoints = remember(seed) {
        val rng = Random(seed)
        List(20_000) {
            GrainPoint(
                xFraction = rng.nextFloat(),
                yFraction = rng.nextFloat(),
                alpha     = rng.nextFloat() * intensity,
                size      = rng.nextFloat() * 1.6f + 0.3f,
            )
        }
    }
    val grainColor = if (isDark) Color.White else Color.Black

    Canvas(modifier = modifier) {
        val W = size.width; val H = size.height
        grainPoints.forEach { p ->
            drawCircle(
                color  = grainColor.copy(alpha = p.alpha),
                radius = p.size,
                center = Offset(p.xFraction * W, p.yFraction * H),
            )
        }
    }
}

private data class GrainPoint(
    val xFraction: Float,
    val yFraction: Float,
    val alpha: Float,
    val size: Float,
)




// ── Collapsing Hero Overlay ──────────────────────────────────────

@Composable
private fun CollapsingHeroOverlay(
    diary: Diary,
    mood: Mood,
    moodColor: Color,
    surface: Color,
    collapseProgress: Float,
    onBackPressed: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    val heroHeight = lerp(HeroExpandedHeight, ToolbarHeight, collapseProgress)
    val iconSize   = lerp(IconExpandedSize, IconCollapsedSize, collapseProgress)
    val orbSize    = lerp(OrbExpandedSize,  OrbCollapsedSize,  collapseProgress)

    // Expanded visibile fino a ~40% scroll, collapsed appare da ~40%
    val expandedAlpha  = (1f - collapseProgress * 2.5f).coerceIn(0f, 1f)
    val collapsedAlpha = ((collapseProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)

    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .drawBehind {
                if (collapsedAlpha > 0f) {
                    drawRect(color = surfaceContainerHigh.copy(alpha = collapsedAlpha))
                }
            },
    ) {
        // ── Expanded content (orb + chip + title) ──
        if (expandedAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = ToolbarHeight, bottom = 8.dp)
                    .graphicsLayer { alpha = expandedAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                // Orb + mood shape
                MoodShapeIndicator(
                    mood = mood,
                    modifier = Modifier.size(iconSize),
                    animated = true,
                    showGlow = true,
                )

                // Chip + Title on same row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = moodColor.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text          = mood.name.uppercase(),
                            modifier      = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            style         = MaterialTheme.typography.labelSmall,
                            fontWeight    = FontWeight.Bold,
                            color         = moodColor,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    Text(
                        text       = diary.title,
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        // ── Toolbar (always visible) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(ToolbarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint               = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Collapsed: icon + title inline
            if (collapsedAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = collapsedAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniMoodShape(
                        mood = mood,
                        modifier = Modifier.size(IconCollapsedSize),
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text       = diary.title,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        Text(
                            text  = mood.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = moodColor,
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            IconButton(onClick = onDeleteClicked) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Elimina",
                    tint               = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Description Card ─────────────────────────────────────────────

@Composable
private fun DescriptionCard(description: String, moodColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape    = RoundedCornerShape(24.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(moodColor),
                )
                Text(
                    text       = "Come mi sentivo",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text       = description,
                style      = MaterialTheme.typography.bodyLarge,
                color      = MaterialTheme.colorScheme.onSurface,
                lineHeight = 25.sp,
            )
        }
    }
}

// ── Metrics Dashboard ────────────────────────────────────────────

@Composable
private fun MetricsDashboard(diary: Diary) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Come stavi?")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmotionMetricCard(value = diary.emotionIntensity, modifier = Modifier.weight(1f))
            StressMetricCard(value  = diary.stressLevel,      modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EnergyMetricCard(value = diary.energyLevel,      modifier = Modifier.weight(1f))
            CalmMetricCard(value   = diary.calmAnxietyLevel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface,
        modifier   = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

// ── Metric Cards ─────────────────────────────────────────────────

@Composable
private fun EmotionMetricCard(value: Int, modifier: Modifier = Modifier) {
    val shapeColor = IntensityShapes.getColorForIntensity(value)
    MetricCardShell("Intensità", value, shapeColor, modifier) {
        key(value) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawIntensityShape(
                    intensity = value,
                    color     = shapeColor,
                    center    = Offset(size.width / 2, size.height / 2),
                    radius    = size.minDimension / 2 * 0.9f,
                )
            }
        }
    }
}

@Composable
private fun StressMetricCard(value: Int, modifier: Modifier = Modifier) {
    val color   = MetricSymbolicColors.getStressColor(value)
    val fillPct by animateFloatAsState(value / 10f, spring(0.6f), label = "stress")
    MetricCardShell("Stress", value, color, modifier) {
        Canvas(Modifier.size(width = 24.dp, height = 48.dp)) {
            val w = size.width; val h = size.height
            val br = w / 2; val tw = w * 0.4f; val th = h - br * 2
            drawRoundRect(color.copy(.2f), Offset((w-tw)/2,0f), androidx.compose.ui.geometry.Size(tw,th), androidx.compose.ui.geometry.CornerRadius(tw/2))
            val fh = th * fillPct
            drawRoundRect(color, Offset((w-tw)/2, th-fh), androidx.compose.ui.geometry.Size(tw,fh), androidx.compose.ui.geometry.CornerRadius(tw/2))
            drawCircle(color, br * 0.8f, Offset(w/2, h-br))
        }
    }
}

@Composable
private fun EnergyMetricCard(value: Int, modifier: Modifier = Modifier) {
    val color   = MetricSymbolicColors.getEnergyColor(value)
    val fillPct by animateFloatAsState(value / 10f, spring(0.6f), label = "energy")
    MetricCardShell("Energia", value, color, modifier) {
        Canvas(Modifier.size(width = 32.dp, height = 48.dp)) {
            val w = size.width; val h = size.height
            val cH = h*.08f; val cW = w*.3f; val bH = h-cH; val cr = w*.15f; val p = w*.1f
            drawRoundRect(color.copy(.5f), Offset((w-cW)/2,0f), androidx.compose.ui.geometry.Size(cW,cH), androidx.compose.ui.geometry.CornerRadius(cr/2))
            drawRoundRect(color.copy(.25f), Offset(0f,cH), androidx.compose.ui.geometry.Size(w,bH), androidx.compose.ui.geometry.CornerRadius(cr), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            if (fillPct > 0) drawRoundRect(color, Offset(p, cH+p+(bH-p*2)*(1-fillPct)), androidx.compose.ui.geometry.Size(w-p*2,(bH-p*2)*fillPct), androidx.compose.ui.geometry.CornerRadius(cr*.5f))
        }
    }
}

@Composable
private fun CalmMetricCard(value: Int, modifier: Modifier = Modifier) {
    val color = MetricSymbolicColors.getCalmColor(value)
    val amp   by animateFloatAsState((10-value)/10f, spring(0.6f), label = "calm")
    MetricCardShell("Calma", value, color, modifier) {
        Canvas(Modifier.size(width = 56.dp, height = 36.dp)) {
            val w = size.width; val h = size.height; val mY = h/2
            val mA = h*.3f*amp.coerceIn(.1f,1f); val f = if(amp>.5f) .08f else .05f
            val path = Path().apply {
                moveTo(0f,mY)
                for (x in 0..w.toInt() step 3) lineTo(x.toFloat(), mY + sin(x*f)*mA)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
    }
}

@Composable
private fun MetricCardShell(
    title: String,
    value: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    visual: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(24.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visual()
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(20.dp), color = accentColor.copy(alpha = 0.12f)) {
                Text(
                    "$value/10",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }
    }
}

// ── Context Chips ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextChipsRow(diary: Diary) {
    val trigger = remember(diary.primaryTrigger) {
        try { Trigger.valueOf(diary.primaryTrigger) } catch (_: Exception) { Trigger.NONE }
    }
    val bodySensation = remember(diary.dominantBodySensation) {
        try { BodySensation.valueOf(diary.dominantBodySensation) } catch (_: Exception) { BodySensation.NONE }
    }
    if (trigger == Trigger.NONE && bodySensation == BodySensation.NONE) return

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        SectionHeader("Cosa ha influito?")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
        ) {
            if (trigger != Trigger.NONE) ContextChip(trigger.icon(), trigger.displayName, "Trigger", MetricSymbolicColors.trigger)
            if (bodySensation != BodySensation.NONE) ContextChip(bodySensation.icon(), bodySensation.displayName, "Corpo", MetricSymbolicColors.bodySensation)
        }
    }
}

@Composable
private fun ContextChip(icon: ImageVector, label: String, subtitle: String, accentColor: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accentColor)
                }
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Images Row ───────────────────────────────────────────────────

@Composable
private fun ImagesRow(images: List<String>) {
    val resolvedUrls = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(images) {
        val storage = FirebaseStorage.getInstance().reference
        images.forEach { path ->
            if (path.startsWith("http")) resolvedUrls.value = resolvedUrls.value + (path to path)
            else storage.child(path.trim()).downloadUrl.addOnSuccessListener { uri ->
                resolvedUrls.value = resolvedUrls.value + (path to uri.toString())
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        SectionHeader("Ricordi")
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp),
        ) {
            items(images) { path ->
                val url = resolvedUrls.value[path]
                Surface(modifier = Modifier.size(140.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    if (url != null) {
                        SubcomposeAsyncImage(
                            model              = ImageRequest.Builder(LocalContext.current).data(url).build(),
                            contentDescription = "Foto",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                            loading = {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Trigger.icon(): ImageVector = when (this) {
    Trigger.WORK    -> Icons.Outlined.FitnessCenter
    Trigger.FAMILY  -> Icons.Outlined.Group
    Trigger.HEALTH  -> Icons.Outlined.FavoriteBorder
    Trigger.FINANCE -> Icons.Outlined.AttachMoney
    Trigger.SOCIAL  -> Icons.Outlined.AccountCircle
    Trigger.SELF    -> Icons.Outlined.Psychology
    Trigger.OTHER   -> Icons.Outlined.MoreHoriz
    Trigger.NONE    -> Icons.Outlined.HelpOutline
}

@Composable
private fun BodySensation.icon(): ImageVector = when (this) {
    BodySensation.TENSION     -> Icons.Outlined.FitnessCenter
    BodySensation.LIGHTNESS   -> Icons.Outlined.SentimentNeutral
    BodySensation.FATIGUE     -> Icons.Outlined.Psychology
    BodySensation.HEAVINESS   -> Icons.Outlined.FitnessCenter
    BodySensation.AGITATION   -> Icons.Outlined.SentimentNeutral
    BodySensation.RELAXATION  -> Icons.Outlined.FavoriteBorder
    BodySensation.NONE        -> Icons.Outlined.HelpOutline
}