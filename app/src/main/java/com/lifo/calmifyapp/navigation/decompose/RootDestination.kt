package com.lifo.calmifyapp.navigation.decompose

import kotlinx.serialization.Serializable

/**
 * All possible navigation destinations in the app.
 * Each destination carries its own parameters (if any) as data class fields,
 * replacing the old string-based route + navArgument pattern.
 */
@Serializable
sealed interface RootDestination {
    // === Wellness Features ===
    @Serializable data object Auth : RootDestination
    @Serializable data object Onboarding : RootDestination
    @Serializable data object Home : RootDestination
    @Serializable data class Write(val diaryId: String? = null) : RootDestination
    @Serializable data class Chat(val sessionId: String? = null) : RootDestination
    @Serializable data object LiveChat : RootDestination
    @Serializable data object History : RootDestination
    @Serializable data object ChatHistoryFull : RootDestination
    @Serializable data object DiaryHistoryFull : RootDestination
    @Serializable data class Humanoid(val avatarId: String? = null) : RootDestination
    @Serializable data object Settings : RootDestination
    @Serializable data object SettingsPersonalInfo : RootDestination
    @Serializable data object SettingsHealthInfo : RootDestination
    @Serializable data object SettingsLifestyle : RootDestination
    @Serializable data object SettingsGoals : RootDestination
    @Serializable data object SettingsAiPreferences : RootDestination
    @Serializable data object Profile : RootDestination
    @Serializable data class Insight(val diaryId: String) : RootDestination
    @Serializable data object WellbeingSnapshot : RootDestination

    // === Journal Home (Improvement Plan Phase 1) ===
    @Serializable data object JournalHome : RootDestination

    // === Social Features (Wave 7) ===
    @Serializable data object Feed : RootDestination
    @Serializable data class UserProfile(val userId: String) : RootDestination
    @Serializable data class Composer(
        val parentThreadId: String? = null,
        val replyToAuthorName: String? = null,
        val prefilledContent: String? = null,
    ) : RootDestination
    @Serializable data object Search : RootDestination
    @Serializable data object Notifications : RootDestination
    // === Social Features (Wave 8) ===
    @Serializable data class Messaging(val conversationId: String? = null) : RootDestination
    @Serializable data class ThreadDetail(val threadId: String) : RootDestination
    @Serializable data class EditProfile(val userId: String) : RootDestination
    @Serializable data class FollowList(val userId: String, val showFollowers: Boolean = true) : RootDestination
    // === Monetization (Wave 9) ===
    @Serializable data object Subscription : RootDestination
    // === Avatar System (Wave 10) ===
    @Serializable data object AvatarCreator : RootDestination
    @Serializable data object AvatarList : RootDestination
}
