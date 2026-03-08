# Home Redesign Masterplan

> **Obiettivo**: Trasformare la Home in un'esperienza visiva stupefacente, efficiente e accessibile, sfruttando al massimo Material3 Expressive e i dati esistenti.
>
> **Autore**: Jarvis AI Assistant
> **Data**: 24 Dicembre 2025

---

## Executive Summary

La Home attuale presenta:
- Un eccellente **DailyInsightsChart** (grafico a pillole verticali) - **DA MANTENERE**
- Dati ricchissimi **sottoutilizzati** (sentiment, cognitive patterns, topics, wellbeing snapshots)
- Fondamenta Material3 Expressive solide ma **non sfruttate appieno**

**Visione**: Una Home che racconta la storia emotiva dell'utente attraverso visualizzazioni eleganti, accessibili e scientificamente informate.

---

## 1. Design Philosophy

### 1.1 Principi Guida

```
┌─────────────────────────────────────────────────────────────────┐
│                    DESIGN PRINCIPLES                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. STORYTELLING VISIVO                                         │
│     → I dati raccontano una storia, non sono solo numeri        │
│     → Ogni visualizzazione ha un "perché" emotivo               │
│                                                                  │
│  2. PROGRESSIVE DISCLOSURE                                       │
│     → Informazioni essenziali in primo piano                    │
│     → Dettagli accessibili con interazione                      │
│                                                                  │
│  3. EMOTIONAL DESIGN                                            │
│     → Colori che riflettono lo stato emotivo                    │
│     → Micro-animazioni che creano connessione                   │
│                                                                  │
│  4. ACCESSIBILITY FIRST                                         │
│     → Contrasti WCAG AAA                                        │
│     → Touch targets 48dp minimo                                 │
│     → Screen reader friendly                                    │
│                                                                  │
│  5. PERFORMANCE                                                 │
│     → Lazy loading per tutto                                    │
│     → Skeleton states eleganti                                  │
│     → Animazioni 60fps                                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Material3 Expressive Guidelines

| Elemento | Specifica M3 Expressive |
|----------|-------------------------|
| **Corner Radius** | Extra-large: 28dp, Large: 24dp, Medium: 16dp, Small: 12dp |
| **Elevation** | Surface tint invece di shadow per depth |
| **Motion** | Emphasized easing, 400-500ms duration per transitions |
| **Color** | Dynamic color con 6 accent colors harmonized |
| **Typography** | Display per hero numbers, Headline per sezioni, Body per content |
| **Spacing** | 4dp grid, 16dp standard padding, 24dp section spacing |

---

## 2. Information Architecture

### 2.1 Content Hierarchy

```
HOME SCREEN
│
├── 🎯 HERO SECTION (Above the fold)
│   ├── Greeting personalizzato + Weather mood
│   ├── Today's Emotional Pulse (numero grande + trend)
│   └── Quick Actions (3 azioni principali)
│
├── 📊 WEEKLY INSIGHTS (Grafico esistente - MANTENUTO)
│   ├── DailyInsightsChart (pillole verticali)
│   ├── Week navigation
│   └── Selected day detail tooltip
│
├── 🔮 INSIGHTS FEED (NUOVO)
│   ├── Mood Distribution Card
│   ├── Cognitive Patterns Timeline
│   ├── Topics Cloud
│   └── Wellbeing Trend
│
├── 📝 RECENT ACTIVITY FEED (Migliorato)
│   ├── Unified diary/chat cards
│   ├── Smart grouping by time
│   └── Quick preview con sentiment indicator
│
└── 🏆 ACHIEVEMENTS & STREAKS (NUOVO)
    ├── Writing streak counter
    ├── Mood awareness badges
    └── Weekly goals progress
```

### 2.2 Data Sources Mapping

| Visualizzazione | Data Source | Refresh Rate |
|-----------------|-------------|--------------|
| Today's Pulse | `dailyInsights[today]` | Real-time |
| Weekly Chart | `dailyInsights` (7 days) | On week change |
| Mood Distribution | `getAllInsights()` aggregated | Daily |
| Cognitive Patterns | `DiaryInsight.cognitivePatterns` | Weekly |
| Topics Cloud | `DiaryInsight.topics` | Weekly |
| Wellbeing Trend | `WellbeingSnapshot` history | On snapshot |
| Activity Feed | `UnifiedHomeState.items` | Real-time |
| Streaks | Custom calculation | Daily |

---

## 3. Component Specifications

### 3.1 Hero Section

#### 3.1.1 Greeting Card

```kotlin
/**
 * Greeting Card - Saluto personalizzato con contesto emotivo
 *
 * Layout:
 * ┌────────────────────────────────────────────────┐
 * │  Buongiorno, Marco                       ☀️   │
 * │  Come ti senti oggi?                          │
 * │                                               │
 * │  ┌─────────────────────────────────────────┐ │
 * │  │  😊  7.2  ↑ 0.8 rispetto a ieri         │ │
 * │  │       Tendenza positiva questa settimana │ │
 * │  └─────────────────────────────────────────┘ │
 * └────────────────────────────────────────────────┘
 */

@Composable
fun HeroGreetingCard(
    userName: String,
    todayPulse: Float,           // 0-10
    trendDirection: TrendDirection,
    trendDelta: Float,
    weekSummary: String,
    modifier: Modifier = Modifier
)

// Specifications:
// - Card: FilledTonalCard con surface tint
// - Corner radius: 28.dp (extra-large)
// - Padding: 24.dp
// - Greeting: Typography.headlineMedium
// - Pulse number: Typography.displayLarge + emotion color
// - Trend indicator: Animated arrow + Typography.labelLarge
// - Elevation: Level2 (3.dp)
```

#### 3.1.2 Quick Actions Row

```kotlin
/**
 * Quick Actions - 3 azioni principali con Material3 Expressive buttons
 *
 * Layout:
 * ┌──────────┐  ┌──────────┐  ┌──────────┐
 * │    ✏️    │  │    🎤    │  │    📊    │
 * │  Scrivi  │  │   Live   │  │ Snapshot │
 * └──────────┘  └──────────┘  └──────────┘
 */

@Composable
fun QuickActionsRow(
    onWriteDiary: () -> Unit,
    onStartLive: () -> Unit,
    onTakeSnapshot: () -> Unit,
    snapshotDueIndicator: Boolean = false
)

// Specifications:
// - Button type: FilledTonalButton o OutlinedButton
// - Shape: RoundedCornerShape(16.dp)
// - Icon: 24.dp with animated entrance
// - Label: Typography.labelLarge
// - Haptic feedback on press
// - Snapshot button: Badge se dovuto (7+ giorni)
```

### 3.2 Weekly Insights Section (Esistente - Enhancement)

```kotlin
/**
 * Mantenere il DailyInsightsChart esistente con miglioramenti:
 *
 * Enhancement:
 * 1. Aggiungere sparkline trend sotto le pillole
 * 2. Migliorare tooltip con cognitive patterns del giorno
 * 3. Aggiungere gesture swipe per navigazione settimana
 * 4. Animazione morphing tra settimane
 */

// Nuove proprietà per tooltip espanso:
data class DayDetailTooltip(
    val date: ZonedDateTime,
    val sentimentMagnitude: Float,
    val dominantEmotion: SentimentLabel,
    val diaryCount: Int,
    val topTopic: String?,           // NUOVO
    val cognitivePattern: String?,   // NUOVO
    val keyMoment: String?           // NUOVO - breve estratto
)
```

### 3.3 Insights Feed Cards (NUOVI)

#### 3.3.1 Mood Distribution Card

```kotlin
/**
 * Mood Distribution - Donut chart con breakdown emotivo
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  Distribuzione Emotiva          Ultimi 30 giorni│
 * │                                                 │
 * │       ┌─────────┐      Positivo    45% ████░   │
 * │      /   45%    \      Neutro      30% ███░░   │
 * │     │           │      Negativo    25% ██░░░   │
 * │      \   30%   /                               │
 * │       └───25%──┘      Emozione dominante:      │
 * │                       😊 Sereno                │
 * └─────────────────────────────────────────────────┘
 */

@Composable
fun MoodDistributionCard(
    distribution: MoodDistribution,
    timeRange: TimeRange,
    dominantMood: Mood,
    onTimeRangeChange: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
)

data class MoodDistribution(
    val positive: Float,    // 0-1
    val neutral: Float,     // 0-1
    val negative: Float,    // 0-1
    val detailedBreakdown: Map<SentimentLabel, Float>
)

// Specifications:
// - Card: ElevatedCard con Level3
// - Donut chart: Canvas custom con animazione reveal
// - Legend: Typography.bodyMedium + color dots
// - Time range selector: SegmentedButton (7d, 30d, 90d)
// - Animation: Donut segments animate in sequenza
```

#### 3.3.2 Cognitive Patterns Card

```kotlin
/**
 * Cognitive Patterns - Timeline dei pattern CBT identificati
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  Pattern Cognitivi                    Questa    │
 * │                                       settimana │
 * │                                                 │
 * │  ● Pensiero catastrofico          3 occorrenze │
 * │    └─ "Tendenza a immaginare il peggio"        │
 * │                                                 │
 * │  ● Generalizzazione               2 occorrenze │
 * │    └─ "Estendere singoli eventi a tutto"       │
 * │                                                 │
 * │  ● Pensiero positivo              5 occorrenze │
 * │    └─ "Riconoscimento dei progressi"           │
 * │                                                 │
 * │  [Scopri di più sui tuoi pattern →]            │
 * └─────────────────────────────────────────────────┘
 */

@Composable
fun CognitivePatternsCard(
    patterns: List<CognitivePatternSummary>,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier
)

data class CognitivePatternSummary(
    val patternName: String,
    val description: String,
    val occurrences: Int,
    val trend: TrendDirection,      // Increasing, Decreasing, Stable
    val sentiment: PatternSentiment // Adaptive, Maladaptive, Neutral
)

// Specifications:
// - Card: OutlinedCard con border color basato su sentiment prevalente
// - Timeline: Vertical line con dots colorati
// - Pattern name: Typography.titleSmall + bold
// - Description: Typography.bodySmall + secondary color
// - Occurrences: Badge con numero
// - Max 3 pattern visibili, "Mostra tutti" se > 3
```

#### 3.3.3 Topics Cloud Card

```kotlin
/**
 * Topics Cloud - Visualizzazione temi ricorrenti
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  I tuoi temi                                    │
 * │                                                 │
 * │     lavoro        famiglia                      │
 * │          salute                                 │
 * │    relazioni    creatività    sport            │
 * │         sonno       amici                       │
 * │                                                 │
 * │  Tema emergente: 📈 creatività (+40%)          │
 * └─────────────────────────────────────────────────┘
 */

@Composable
fun TopicsCloudCard(
    topics: List<TopicFrequency>,
    emergingTopic: TopicTrend?,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier
)

data class TopicFrequency(
    val topic: String,
    val frequency: Int,
    val sentimentAverage: Float,    // Colore del chip
    val isEmerging: Boolean
)

data class TopicTrend(
    val topic: String,
    val changePercent: Float,
    val direction: TrendDirection
)

// Specifications:
// - Card: FilledTonalCard
// - Cloud layout: FlowRow con chips di dimensioni variabili
// - Chip size: Basato su frequency (small, medium, large)
// - Chip color: Basato su sentimentAverage
// - Animation: Chips entrano con stagger delay
// - Emerging topic: Highlighted con glow effect
```

#### 3.3.4 Wellbeing Trend Card

```kotlin
/**
 * Wellbeing Trend - Andamento snapshot nel tempo
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  Il tuo Benessere              Ultimo: 2g fa   │
 * │                                                 │
 * │  8.2  ╭─────────────────╮                      │
 * │       │    ╭──╮   ╭──╮  │     Eccellente       │
 * │  6.0  │╭──╯    ╰─╯    ╰─│                      │
 * │       ││                │     ↑ 0.5 vs media   │
 * │  4.0  ╰┴────────────────╯                      │
 * │        Gen  Feb  Mar  Apr                      │
 * │                                                 │
 * │  ┌─────────────────────────────────────────┐   │
 * │  │  Compila il tuo snapshot settimanale →  │   │
 * │  └─────────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────┘
 */

@Composable
fun WellbeingTrendCard(
    snapshots: List<WellbeingSnapshot>,
    latestScore: Float,
    scoreLabel: String,
    trendVsAverage: Float,
    daysSinceLastSnapshot: Int,
    onTakeSnapshot: () -> Unit,
    modifier: Modifier = Modifier
)

// Specifications:
// - Card: ElevatedCard con gradiente sottile basato su score
// - Chart: Line chart con area fill
// - Score display: Typography.displayMedium + color-coded
// - Label: Typography.titleMedium (Eccellente, Buono, etc.)
// - CTA button: FilledTonalButton se > 7 giorni, TextButton altrimenti
// - Animation: Line draws progressivamente
```

### 3.4 Activity Feed (Migliorato)

```kotlin
/**
 * Activity Feed - Stream unificato di diary e chat
 *
 * Miglioramenti:
 * 1. Grouping intelligente per periodo (Oggi, Ieri, Questa settimana)
 * 2. Sentiment indicator visivo su ogni card
 * 3. Preview più ricca con key insight
 * 4. Swipe actions (archive, favorite)
 */

@Composable
fun ActivityFeed(
    items: List<HomeContentItem>,
    groupedByPeriod: Map<TimePeriod, List<HomeContentItem>>,
    onItemClick: (HomeContentItem) -> Unit,
    onItemSwipeAction: (HomeContentItem, SwipeAction) -> Unit,
    modifier: Modifier = Modifier
)

// Card Enhancement:
@Composable
fun EnhancedActivityCard(
    item: HomeContentItem,
    sentimentIndicator: SentimentIndicator,  // Colored bar on left
    keyInsight: String?,                      // "Hai parlato di lavoro e stress"
    onClick: () -> Unit
)

// Specifications:
// - Section headers: Typography.titleSmall + sticky
// - Cards: ElevatedCard con sentiment color accent
// - Sentiment indicator: 4dp vertical bar on leading edge
// - Preview: Max 2 lines + "..." truncation
// - Key insight: Typography.bodySmall + tertiary color
// - Time: Relative formatting (2 ore fa, Ieri, etc.)
```

### 3.5 Achievements Section (NUOVO)

```kotlin
/**
 * Achievements - Gamification leggera per engagement
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  I tuoi progressi                               │
 * │                                                 │
 * │  🔥 7 giorni      ✍️ 23 entries    🎯 80%      │
 * │     di streak        questo mese      goal     │
 * │                                                 │
 * │  ┌─────────────────────────────────────────┐   │
 * │  │ 🏆 Nuovo badge: "Mindful Writer"        │   │
 * │  │    Hai scritto per 7 giorni consecutivi │   │
 * │  └─────────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────┘
 */

@Composable
fun AchievementsRow(
    streak: Int,
    entriesThisMonth: Int,
    weeklyGoalProgress: Float,
    latestBadge: Badge?,
    onViewAllBadges: () -> Unit,
    modifier: Modifier = Modifier
)

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,           // Emoji o icon resource
    val earnedAt: ZonedDateTime,
    val rarity: BadgeRarity     // Common, Rare, Epic, Legendary
)

// Specifications:
// - Stats row: 3 columns equal width
// - Stat number: Typography.headlineMedium
// - Stat label: Typography.labelSmall
// - Badge card: Animated entrance con confetti particles
// - Badge rarity: Border glow color (gold, purple, blue, gray)
```

---

## 4. Color System Enhancement

### 4.1 Emotion-Aware Dynamic Colors

```kotlin
/**
 * Sistema di colori che si adatta allo stato emotivo prevalente
 */

object EmotionAwareColors {
    // Primary emotion gradients
    val positiveGradient = listOf(
        Color(0xFF81C784),  // Light green
        Color(0xFF4CAF50)   // Green
    )

    val neutralGradient = listOf(
        Color(0xFF90A4AE),  // Blue-gray light
        Color(0xFF607D8B)   // Blue-gray
    )

    val negativeGradient = listOf(
        Color(0xFFE57373),  // Light red
        Color(0xFFEF5350)   // Red
    )

    // Sentiment-to-color mapping con Material3 tonal palette
    fun getSentimentColor(
        sentiment: Float,           // -1 to +1
        scheme: ColorScheme
    ): Color {
        return when {
            sentiment > 0.3f -> scheme.tertiary      // Positive
            sentiment < -0.3f -> scheme.error        // Negative
            else -> scheme.secondary                 // Neutral
        }
    }

    // Surface tint basato su sentiment prevalente della giornata
    fun getDaySurfaceTint(
        dominantSentiment: SentimentLabel
    ): Color {
        return when (dominantSentiment) {
            SentimentLabel.VERY_POSITIVE -> Color(0x1A4CAF50)
            SentimentLabel.POSITIVE -> Color(0x1A81C784)
            SentimentLabel.NEUTRAL -> Color(0x1A607D8B)
            SentimentLabel.NEGATIVE -> Color(0x1AFF7043)
            SentimentLabel.VERY_NEGATIVE -> Color(0x1AEF5350)
        }
    }
}
```

### 4.2 Typography Scale Expansion

```kotlin
/**
 * Typography scale espansa per data visualization
 */

val CalmifyTypography = Typography(
    // Hero numbers (sentiment scores, streaks)
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),

    // Section scores
    displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),

    // Card titles
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),

    // Subsection titles
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),

    // Card content titles
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    // Labels and small titles
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Chart labels
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),

    // Body text
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    // Secondary body text
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
)
```

---

## 5. Animation Specifications

### 5.1 Micro-interactions

```kotlin
/**
 * Animazioni per feedback immediato e delight
 */

object HomeAnimations {
    // Card press feedback
    val cardPressScale = animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    // Number counter animation
    fun animatedCounter(
        targetValue: Float,
        durationMs: Int = 1000
    ): Float {
        return animateFloatAsState(
            targetValue = targetValue,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        ).value
    }

    // Staggered list entrance
    fun staggeredEntrance(
        index: Int,
        baseDelayMs: Int = 50
    ): AnimatedVisibilityScope.() -> Unit = {
        fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * baseDelayMs,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * baseDelayMs
            )
        )
    }

    // Chart reveal animation
    val chartRevealSpec = tween<Float>(
        durationMillis = 800,
        easing = FastOutSlowInEasing
    )

    // Donut segment animation
    fun donutSegmentSpec(index: Int) = tween<Float>(
        durationMillis = 500,
        delayMillis = index * 100,
        easing = FastOutSlowInEasing
    )
}
```

### 5.2 Transition Animations

```kotlin
/**
 * Transizioni tra stati e schermate
 */

// Week transition nel chart
val weekTransitionSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

// Card expansion
val cardExpansionSpec = spring<Dp>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// Refresh indicator
val pullRefreshSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow
)
```

---

## 6. Accessibility Requirements

### 6.1 WCAG Compliance

| Requirement | Implementation |
|-------------|----------------|
| **Color Contrast** | Minimum 4.5:1 per text, 3:1 per graphics |
| **Touch Targets** | Minimum 48dp x 48dp |
| **Focus Indicators** | Visible focus ring 2dp con primary color |
| **Motion** | Rispettare `prefers-reduced-motion` |
| **Screen Reader** | ContentDescription per tutti gli elementi interattivi |

### 6.2 Semantic Annotations

```kotlin
/**
 * Accessibilità per ogni componente
 */

// Chart accessibility
Modifier.semantics {
    contentDescription = "Grafico settimanale del benessere. " +
        "Lunedì: sentiment 7.2, positivo. " +
        "Martedì: sentiment 5.5, neutro. ..."
    role = Role.Image
}

// Interactive card accessibility
Modifier.semantics {
    contentDescription = "Diario di oggi, ore 14:30. " +
        "Sentiment positivo. Tema principale: lavoro. " +
        "Tocca due volte per aprire."
    role = Role.Button
}

// Number accessibility
Text(
    text = "7.2",
    modifier = Modifier.semantics {
        contentDescription = "Punteggio di benessere: 7.2 su 10"
    }
)
```

---

## 7. Performance Optimization

### 7.1 Lazy Loading Strategy

```kotlin
/**
 * Strategia di caricamento per performance ottimale
 */

// Home sections loading priority
enum class LoadPriority {
    CRITICAL,   // Hero section, today's pulse
    HIGH,       // Weekly chart
    MEDIUM,     // Insights cards
    LOW         // Activity feed (paginated)
}

// Lazy loading con placeholder
@Composable
fun LazyInsightCard(
    loadPriority: LoadPriority,
    content: @Composable () -> Unit
) {
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(loadPriority) {
        delay(loadPriority.ordinal * 100L)
        isLoaded = true
    }

    AnimatedContent(
        targetState = isLoaded,
        transitionSpec = { fadeIn() with fadeOut() }
    ) { loaded ->
        if (loaded) content()
        else InsightCardSkeleton()
    }
}
```

### 7.2 State Optimization

```kotlin
/**
 * Ottimizzazioni per ridurre recomposizioni
 */

// Stable keys per liste
@Immutable
data class StableHomeContentItem(
    val id: String,
    val type: ContentType,
    val timestamp: Long,
    val sentimentScore: Float
)

// Derived state per calcoli
val weekSummary by remember(dailyInsights) {
    derivedStateOf {
        calculateWeekSummary(dailyInsights)
    }
}

// Remember con custom equality
val moodDistribution = remember(insights) {
    calculateMoodDistribution(insights)
}
```

---

## 8. Module Structure

### 8.1 New Files Organization

```
features/home/
├── src/main/java/com/lifo/home/
│   ├── presentation/
│   │   ├── screen/
│   │   │   ├── HomeScreen.kt              # ESISTENTE (modificato)
│   │   │   └── SnapshotScreen.kt          # ESISTENTE
│   │   │
│   │   ├── viewmodel/
│   │   │   ├── HomeViewModel.kt           # ESISTENTE (esteso)
│   │   │   └── SnapshotViewModel.kt       # ESISTENTE
│   │   │
│   │   └── components/
│   │       ├── hero/                      # NUOVO
│   │       │   ├── HeroGreetingCard.kt
│   │       │   ├── TodayPulseIndicator.kt
│   │       │   └── QuickActionsRow.kt
│   │       │
│   │       ├── insights/                  # NUOVO
│   │       │   ├── DailyInsightsChart.kt  # SPOSTATO da HomeContent
│   │       │   ├── MoodDistributionCard.kt
│   │       │   ├── CognitivePatternsCard.kt
│   │       │   ├── TopicsCloudCard.kt
│   │       │   └── WellbeingTrendCard.kt
│   │       │
│   │       ├── feed/                      # NUOVO
│   │       │   ├── ActivityFeed.kt
│   │       │   ├── EnhancedActivityCard.kt
│   │       │   └── FeedSectionHeader.kt
│   │       │
│   │       ├── achievements/              # NUOVO
│   │       │   ├── AchievementsRow.kt
│   │       │   ├── StreakCounter.kt
│   │       │   └── BadgeCard.kt
│   │       │
│   │       ├── charts/                    # NUOVO
│   │       │   ├── DonutChart.kt
│   │       │   ├── SparklineChart.kt
│   │       │   ├── LineChart.kt
│   │       │   └── ChartAnimations.kt
│   │       │
│   │       └── common/                    # ESISTENTE (rinominato)
│   │           ├── UnifiedContentCard.kt
│   │           ├── FilterChipRow.kt
│   │           ├── UnifiedSearchBar.kt
│   │           └── SkeletonLoaders.kt     # NUOVO
│   │
│   ├── domain/
│   │   ├── model/                         # NUOVO
│   │   │   ├── HomeUiModels.kt
│   │   │   ├── InsightAggregations.kt
│   │   │   └── AchievementModels.kt
│   │   │
│   │   └── usecase/                       # NUOVO
│   │       ├── CalculateMoodDistributionUseCase.kt
│   │       ├── AggregateCognitivePatternsUseCase.kt
│   │       ├── CalculateTopicsFrequencyUseCase.kt
│   │       ├── CalculateStreaksUseCase.kt
│   │       └── GetWeekSummaryUseCase.kt
│   │
│   └── util/
│       ├── DateFormatters.kt              # NUOVO
│       └── ColorUtils.kt                  # NUOVO
```

### 8.2 Dependency Graph

```
HomeScreen
    ├── HeroGreetingCard
    │   └── TodayPulseIndicator
    │
    ├── QuickActionsRow
    │
    ├── DailyInsightsChart (esistente, migliorato)
    │
    ├── InsightsFeed
    │   ├── MoodDistributionCard
    │   │   └── DonutChart
    │   ├── CognitivePatternsCard
    │   ├── TopicsCloudCard
    │   └── WellbeingTrendCard
    │       └── LineChart
    │
    ├── ActivityFeed
    │   ├── FeedSectionHeader
    │   └── EnhancedActivityCard
    │
    └── AchievementsRow
        ├── StreakCounter
        └── BadgeCard

HomeViewModel
    ├── InsightRepository
    ├── DiaryRepository
    ├── ChatRepository
    ├── WellbeingRepository
    │
    ├── CalculateMoodDistributionUseCase
    ├── AggregateCognitivePatternsUseCase
    ├── CalculateTopicsFrequencyUseCase
    ├── CalculateStreaksUseCase
    └── GetWeekSummaryUseCase
```

---

## 9. Implementation Phases

### Phase 1: Foundation (Week 1)

| Task | Priority | Effort |
|------|----------|--------|
| Creare struttura cartelle | High | 1h |
| Definire HomeUiModels.kt | High | 2h |
| Implementare color system enhancement | High | 3h |
| Implementare typography scale | High | 2h |
| Creare skeleton loaders | Medium | 2h |

**Deliverable**: Infrastruttura pronta, design system esteso

### Phase 2: Hero Section (Week 1-2)

| Task | Priority | Effort |
|------|----------|--------|
| HeroGreetingCard | High | 4h |
| TodayPulseIndicator con animazioni | High | 3h |
| QuickActionsRow | High | 2h |
| Integrare in HomeScreen | High | 2h |

**Deliverable**: Hero section completa e animata

### Phase 3: Charts & Visualizations (Week 2)

| Task | Priority | Effort |
|------|----------|--------|
| DonutChart component | High | 6h |
| LineChart component | High | 6h |
| SparklineChart component | Medium | 3h |
| MoodDistributionCard | High | 4h |
| WellbeingTrendCard | High | 4h |

**Deliverable**: Sistema di chart riutilizzabile

### Phase 4: Insights Cards (Week 2-3)

| Task | Priority | Effort |
|------|----------|--------|
| CognitivePatternsCard | Medium | 4h |
| TopicsCloudCard | Medium | 5h |
| Use cases per aggregazione dati | High | 6h |
| Integrare in HomeViewModel | High | 3h |

**Deliverable**: Insights feed completo

### Phase 5: Activity Feed Enhancement (Week 3)

| Task | Priority | Effort |
|------|----------|--------|
| EnhancedActivityCard | High | 4h |
| FeedSectionHeader con grouping | Medium | 2h |
| Swipe actions | Low | 3h |
| Sentiment indicators | High | 2h |

**Deliverable**: Feed migliorato con grouping

### Phase 6: Achievements (Week 3-4)

| Task | Priority | Effort |
|------|----------|--------|
| CalculateStreaksUseCase | Medium | 3h |
| AchievementModels | Medium | 2h |
| StreakCounter component | Medium | 2h |
| BadgeCard con animazioni | Low | 4h |
| Badge system backend | Low | 4h |

**Deliverable**: Sistema achievements funzionante

### Phase 7: Polish & Optimization (Week 4)

| Task | Priority | Effort |
|------|----------|--------|
| Performance profiling | High | 4h |
| Accessibility audit | High | 4h |
| Animation fine-tuning | Medium | 4h |
| Edge cases e error states | High | 4h |
| Testing | High | 6h |

**Deliverable**: Home production-ready

---

## 10. Success Metrics

### 10.1 Performance KPIs

| Metric | Target | Measurement |
|--------|--------|-------------|
| Initial render | < 300ms | Android Profiler |
| Chart animation | 60fps | Frame timing |
| Memory usage | < 50MB increase | Memory Profiler |
| Scroll performance | 0 dropped frames | Systrace |

### 10.2 User Experience KPIs

| Metric | Target | Measurement |
|--------|--------|-------------|
| Time to first insight | < 2s | Analytics |
| Interaction rate | +30% vs current | Event tracking |
| Daily active usage | +20% | Firebase Analytics |
| Accessibility score | 100% | Accessibility Scanner |

### 10.3 Design Quality Checklist

- [ ] Tutti i colori rispettano la palette Material3
- [ ] Typography scale consistente
- [ ] Spacing segue 4dp grid
- [ ] Corner radius consistenti (12, 16, 24, 28dp)
- [ ] Elevation levels corretti
- [ ] Animazioni smooth e purposeful
- [ ] Dark mode completo
- [ ] Responsive su tutti i form factor

---

## 11. Visual Mockup Reference

```
┌─────────────────────────────────────────────────────────────────┐
│ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│ ░                                                             ░ │
│ ░   Buongiorno, Marco                                    ☀️  ░ │
│ ░   Come ti senti oggi?                                      ░ │
│ ░                                                             ░ │
│ ░   ┌─────────────────────────────────────────────────────┐  ░ │
│ ░   │                                                     │  ░ │
│ ░   │     😊  7.2   ↑ 0.8                                │  ░ │
│ ░   │          Tendenza positiva                         │  ░ │
│ ░   │                                                     │  ░ │
│ ░   └─────────────────────────────────────────────────────┘  ░ │
│ ░                                                             ░ │
│ ░   ┌─────────┐  ┌─────────┐  ┌─────────┐                    ░ │
│ ░   │   ✏️   │  │   🎤   │  │   📊   │                    ░ │
│ ░   │ Scrivi  │  │  Live   │  │Snapshot │                    ░ │
│ ░   └─────────┘  └─────────┘  └─────────┘                    ░ │
│ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│                                                                 │
│  La tua settimana                              ← Dec 16-22 →   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │         ██                                              │   │
│  │    ██   ██   ██        ██                              │   │
│  │    ██   ██   ██   ██   ██   ██                         │   │
│  │    ██   ██   ██   ██   ██   ██   ██                    │   │
│  │    L    M    M    G    V    S    D                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────┐  ┌──────────────────────┐            │
│  │ Distribuzione Emotiva│  │ Pattern Cognitivi    │            │
│  │                      │  │                      │            │
│  │      ┌─────┐         │  │ ● Pensiero positivo  │            │
│  │     /  45% \         │  │   5 occorrenze       │            │
│  │    │       │         │  │                      │            │
│  │     \ 30% /          │  │ ● Generalizzazione   │            │
│  │      └─25%─┘         │  │   2 occorrenze       │            │
│  │                      │  │                      │            │
│  └──────────────────────┘  └──────────────────────┘            │
│                                                                 │
│  I tuoi temi                                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │    lavoro     famiglia    salute                        │   │
│  │         relazioni    creatività    sport                │   │
│  │              sonno       amici                          │   │
│  │                                                         │   │
│  │  📈 Tema emergente: creatività (+40%)                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  🔥 7 giorni     ✍️ 23 entries    🎯 80%                       │
│     di streak        questo mese      goal                     │
│                                                                 │
│  Attività recente                                               │
│  ─────────────────────────────────────────────────             │
│  OGGI                                                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ● 14:30  Riflessione sul lavoro                        │   │
│  │          "Oggi ho fatto progressi sul progetto..."      │   │
│  │          😊 Positivo · lavoro, crescita                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ● 09:15  Chat con Calmify AI                           │   │
│  │          12 messaggi · 15 minuti                        │   │
│  │          🤖 Gemini 2.0 · Live mode                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  IERI                                                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ● 22:00  Gratitudine serale                            │   │
│  │          "Sono grato per la famiglia..."                │   │
│  │          😊 Positivo · gratitudine, famiglia            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 12. References

### Material3 Expressive Documentation
- [Material Design 3 - Expressive](https://m3.material.io/)
- [Color System](https://m3.material.io/styles/color/overview)
- [Typography](https://m3.material.io/styles/typography/overview)
- [Motion](https://m3.material.io/styles/motion/overview)

### Jetpack Compose
- [Compose Material3](https://developer.android.com/jetpack/compose/designsystems/material3)
- [Canvas API](https://developer.android.com/jetpack/compose/graphics/draw/overview)
- [Animation](https://developer.android.com/jetpack/compose/animation)

### Accessibility
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility)

---

*Piano generato da Jarvis AI Assistant - Calmify Project*
*"L'eleganza è importante quanto la funzionalità, Sir."*
