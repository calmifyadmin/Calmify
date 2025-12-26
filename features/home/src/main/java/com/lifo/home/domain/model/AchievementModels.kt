package com.lifo.home.domain.model

import androidx.compose.runtime.Immutable
import java.time.ZonedDateTime

/**
 * Achievement Models - Gamification elements for user engagement
 * Streaks, badges, and progress tracking
 */

// ==================== BADGE MODELS ====================

/**
 * Badge rarity levels with visual styling hints
 */
enum class BadgeRarity(
    val label: String,
    val glowColor: Long  // ARGB color for glow effect
) {
    COMMON("Comune", 0xFF90A4AE),       // Gray
    RARE("Raro", 0xFF42A5F5),           // Blue
    EPIC("Epico", 0xFFAB47BC),          // Purple
    LEGENDARY("Leggendario", 0xFFFFCA28) // Gold
}

/**
 * Badge categories for organization
 */
enum class BadgeCategory(val label: String) {
    WRITING("Scrittura"),
    CONSISTENCY("Costanza"),
    MOOD_AWARENESS("Consapevolezza"),
    GROWTH("Crescita"),
    EXPLORATION("Esplorazione"),
    SPECIAL("Speciale")
}

/**
 * Badge definition with all metadata
 */
@Immutable
data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,                    // Emoji or icon resource name
    val category: BadgeCategory,
    val rarity: BadgeRarity,
    val requirement: String,             // Human-readable requirement
    val earnedAt: ZonedDateTime? = null, // Null if not yet earned
    val progress: Float = 0f,            // 0-1 progress toward earning
    val isNew: Boolean = false           // True if recently earned
)

/**
 * Known badges in the system
 */
object KnownBadges {

    // Writing badges
    val FIRST_ENTRY = BadgeDefinition(
        id = "first_entry",
        name = "Prima Parola",
        description = "Hai scritto il tuo primo diario",
        icon = "✏️",
        category = BadgeCategory.WRITING,
        rarity = BadgeRarity.COMMON,
        requirement = "Scrivi il tuo primo diario"
    )

    val PROLIFIC_WRITER = BadgeDefinition(
        id = "prolific_writer",
        name = "Scrittore Prolifico",
        description = "Hai scritto 50 diari",
        icon = "📚",
        category = BadgeCategory.WRITING,
        rarity = BadgeRarity.RARE,
        requirement = "Scrivi 50 diari"
    )

    val NOVELIST = BadgeDefinition(
        id = "novelist",
        name = "Romanziere",
        description = "Hai scritto 200 diari",
        icon = "📖",
        category = BadgeCategory.WRITING,
        rarity = BadgeRarity.EPIC,
        requirement = "Scrivi 200 diari"
    )

    // Consistency badges
    val STREAK_7 = BadgeDefinition(
        id = "streak_7",
        name = "Mindful Writer",
        description = "Hai scritto per 7 giorni consecutivi",
        icon = "🔥",
        category = BadgeCategory.CONSISTENCY,
        rarity = BadgeRarity.COMMON,
        requirement = "Scrivi per 7 giorni consecutivi"
    )

    val STREAK_30 = BadgeDefinition(
        id = "streak_30",
        name = "Habitué",
        description = "Hai scritto per 30 giorni consecutivi",
        icon = "💪",
        category = BadgeCategory.CONSISTENCY,
        rarity = BadgeRarity.RARE,
        requirement = "Scrivi per 30 giorni consecutivi"
    )

    val STREAK_100 = BadgeDefinition(
        id = "streak_100",
        name = "Inarrestabile",
        description = "Hai scritto per 100 giorni consecutivi",
        icon = "🏆",
        category = BadgeCategory.CONSISTENCY,
        rarity = BadgeRarity.LEGENDARY,
        requirement = "Scrivi per 100 giorni consecutivi"
    )

    // Mood awareness badges
    val MOOD_EXPLORER = BadgeDefinition(
        id = "mood_explorer",
        name = "Esploratore Emotivo",
        description = "Hai registrato tutte e 5 le emozioni",
        icon = "🎭",
        category = BadgeCategory.MOOD_AWARENESS,
        rarity = BadgeRarity.COMMON,
        requirement = "Registra tutte le emozioni (molto positivo, positivo, neutro, negativo, molto negativo)"
    )

    val POSITIVITY_CHAMPION = BadgeDefinition(
        id = "positivity_champion",
        name = "Campione di Positività",
        description = "7 giorni consecutivi con sentiment positivo",
        icon = "☀️",
        category = BadgeCategory.MOOD_AWARENESS,
        rarity = BadgeRarity.RARE,
        requirement = "7 giorni consecutivi con sentiment positivo"
    )

    val WELLBEING_MASTER = BadgeDefinition(
        id = "wellbeing_master",
        name = "Maestro del Benessere",
        description = "Hai completato 10 snapshot del benessere",
        icon = "🧘",
        category = BadgeCategory.MOOD_AWARENESS,
        rarity = BadgeRarity.EPIC,
        requirement = "Completa 10 snapshot del benessere"
    )

    // Growth badges
    val PATTERN_SPOTTER = BadgeDefinition(
        id = "pattern_spotter",
        name = "Cacciatore di Pattern",
        description = "Hai identificato 10 pattern cognitivi",
        icon = "🔍",
        category = BadgeCategory.GROWTH,
        rarity = BadgeRarity.COMMON,
        requirement = "Identifica 10 pattern cognitivi"
    )

    val SELF_AWARE = BadgeDefinition(
        id = "self_aware",
        name = "Autoconsapevole",
        description = "Hai riconosciuto e migliorato un pattern maladattivo",
        icon = "🌱",
        category = BadgeCategory.GROWTH,
        rarity = BadgeRarity.EPIC,
        requirement = "Riconosci e migliora un pattern maladattivo"
    )

    // Exploration badges
    val TOPIC_EXPLORER = BadgeDefinition(
        id = "topic_explorer",
        name = "Esploratore di Temi",
        description = "Hai scritto di 10 temi diversi",
        icon = "🗺️",
        category = BadgeCategory.EXPLORATION,
        rarity = BadgeRarity.COMMON,
        requirement = "Scrivi di 10 temi diversi"
    )

    val NIGHT_OWL = BadgeDefinition(
        id = "night_owl",
        name = "Gufo Notturno",
        description = "Hai scritto 10 diari dopo mezzanotte",
        icon = "🦉",
        category = BadgeCategory.EXPLORATION,
        rarity = BadgeRarity.RARE,
        requirement = "Scrivi 10 diari dopo mezzanotte"
    )

    val EARLY_BIRD = BadgeDefinition(
        id = "early_bird",
        name = "Mattiniero",
        description = "Hai scritto 10 diari prima delle 7",
        icon = "🐦",
        category = BadgeCategory.EXPLORATION,
        rarity = BadgeRarity.RARE,
        requirement = "Scrivi 10 diari prima delle 7"
    )

    // Special badges
    val ANNIVERSARY = BadgeDefinition(
        id = "anniversary",
        name = "Anniversario",
        description = "Un anno con Calmify",
        icon = "🎂",
        category = BadgeCategory.SPECIAL,
        rarity = BadgeRarity.LEGENDARY,
        requirement = "Usa Calmify per un anno"
    )

    val BETA_TESTER = BadgeDefinition(
        id = "beta_tester",
        name = "Beta Tester",
        description = "Hai partecipato alla beta di Calmify",
        icon = "🧪",
        category = BadgeCategory.SPECIAL,
        rarity = BadgeRarity.LEGENDARY,
        requirement = "Partecipa alla beta"
    )

    // List of all badges
    val allBadges = listOf(
        FIRST_ENTRY, PROLIFIC_WRITER, NOVELIST,
        STREAK_7, STREAK_30, STREAK_100,
        MOOD_EXPLORER, POSITIVITY_CHAMPION, WELLBEING_MASTER,
        PATTERN_SPOTTER, SELF_AWARE,
        TOPIC_EXPLORER, NIGHT_OWL, EARLY_BIRD,
        ANNIVERSARY, BETA_TESTER
    )

    fun getBadgeById(id: String): BadgeDefinition? {
        return allBadges.find { it.id == id }
    }

    data class BadgeDefinition(
        val id: String,
        val name: String,
        val description: String,
        val icon: String,
        val category: BadgeCategory,
        val rarity: BadgeRarity,
        val requirement: String
    ) {
        fun toBadge(earnedAt: ZonedDateTime? = null, progress: Float = 0f, isNew: Boolean = false): Badge {
            return Badge(
                id = id,
                name = name,
                description = description,
                icon = icon,
                category = category,
                rarity = rarity,
                requirement = requirement,
                earnedAt = earnedAt,
                progress = progress,
                isNew = isNew
            )
        }
    }
}

// ==================== STREAK MODELS ====================

/**
 * Writing streak data
 */
@Immutable
data class StreakData(
    val currentStreak: Int,              // Current consecutive days
    val longestStreak: Int,              // All-time longest streak
    val lastWriteDate: ZonedDateTime?,   // Last diary write date
    val isActiveToday: Boolean,          // Has written today
    val streakAtRisk: Boolean            // True if no write today and streak > 0
)

/**
 * Monthly writing statistics
 */
@Immutable
data class MonthlyStats(
    val entriesThisMonth: Int,
    val daysWithEntries: Int,
    val averageEntriesPerDay: Float,
    val comparisonVsLastMonth: Float,    // Percentage change
    val mostProductiveDay: String?       // Day name with most entries
)

/**
 * Weekly goal progress
 */
@Immutable
data class WeeklyGoal(
    val targetEntries: Int,              // User-set goal (default: 5)
    val currentEntries: Int,             // Entries this week
    val progress: Float,                 // 0-1 progress
    val daysRemaining: Int,              // Days left in week
    val isAchieved: Boolean,
    val consecutiveWeeksAchieved: Int    // Weeks in a row goal was met
)

// ==================== ACHIEVEMENTS STATE ====================

/**
 * Complete achievements state for UI
 */
@Immutable
data class AchievementsState(
    val streak: StreakData,
    val monthlyStats: MonthlyStats,
    val weeklyGoal: WeeklyGoal,
    val earnedBadges: List<Badge>,
    val latestBadge: Badge?,             // Most recently earned
    val inProgressBadges: List<Badge>,   // Badges with progress > 0
    val totalBadgesEarned: Int,
    val nextMilestone: Badge?            // Closest badge to earning
)

// ==================== ACHIEVEMENTS EVENTS ====================

/**
 * Events for achievements (for animations and notifications)
 */
sealed class AchievementEvent {
    data class BadgeEarned(val badge: Badge) : AchievementEvent()
    data class StreakMilestone(val days: Int) : AchievementEvent()
    data class WeeklyGoalAchieved(val week: Int) : AchievementEvent()
    data class MonthlyMilestone(val entries: Int, val month: String) : AchievementEvent()
}
