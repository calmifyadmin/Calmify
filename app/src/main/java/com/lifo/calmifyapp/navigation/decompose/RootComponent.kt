package com.lifo.calmifyapp.navigation.decompose

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.router.stack.ChildStack

/**
 * Root navigation component for Calmify.
 *
 * Replaces NavHostController with Decompose's StackNavigation.
 * Each Child wraps a RootDestination — the composable renderer in DecomposeApp
 * matches on the destination to render the correct feature screen.
 */
class RootComponent(
    componentContext: ComponentContext,
    initialDestination: RootDestination = RootDestination.Auth,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<RootDestination>()

    val childStack: Value<ChildStack<RootDestination, Child>> =
        childStack(
            source = navigation,
            serializer = RootDestination.serializer(),
            initialConfiguration = initialDestination,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    private fun createChild(
        destination: RootDestination,
        context: ComponentContext,
    ): Child = when (destination) {
        is RootDestination.Auth -> Child.Auth(context)
        is RootDestination.Onboarding -> Child.Onboarding(context)
        is RootDestination.Home -> Child.Home(context)
        is RootDestination.Write -> Child.Write(context, destination.diaryId, destination.isBrainDump, destination.isGratitude, destination.isEnergyCheckIn, destination.isSleepLog, destination.isReframe, destination.isBlock, destination.isMovement)
        is RootDestination.Chat -> Child.Chat(context, destination.sessionId)
        is RootDestination.LiveChat -> Child.LiveChat(context)
        is RootDestination.History -> Child.History(context)
        is RootDestination.ChatHistoryFull -> Child.ChatHistoryFull(context)
        is RootDestination.DiaryHistoryFull -> Child.DiaryHistoryFull(context)
        is RootDestination.Humanoid -> Child.Humanoid(context, destination.avatarId)
        is RootDestination.Settings -> Child.Settings(context)
        is RootDestination.SettingsPersonalInfo -> Child.SettingsPersonalInfo(context)
        is RootDestination.SettingsHealthInfo -> Child.SettingsHealthInfo(context)
        is RootDestination.SettingsLifestyle -> Child.SettingsLifestyle(context)
        is RootDestination.SettingsGoals -> Child.SettingsGoals(context)
        is RootDestination.SettingsAiPreferences -> Child.SettingsAiPreferences(context)
        is RootDestination.Profile -> Child.Profile(context)
        is RootDestination.Insight -> Child.Insight(context, destination.diaryId)
        is RootDestination.WellbeingSnapshot -> Child.WellbeingSnapshot(context)
        // Journal Home (Improvement Plan Phase 1)
        is RootDestination.JournalHome -> Child.JournalHome(context)
        // Social Features (Wave 7)
        is RootDestination.Feed -> Child.Feed(context)
        is RootDestination.UserProfile -> Child.UserProfile(context, destination.userId)
        is RootDestination.Composer -> Child.Composer(context, destination.parentThreadId, destination.replyToAuthorName, destination.prefilledContent)
        is RootDestination.Search -> Child.Search(context)
        is RootDestination.Notifications -> Child.Notifications(context)
        // Social Features (Wave 8)
        is RootDestination.Messaging -> Child.Messaging(context, destination.conversationId)
        is RootDestination.ThreadDetail -> Child.ThreadDetail(context, destination.threadId)
        is RootDestination.EditProfile -> Child.EditProfile(context, destination.userId)
        is RootDestination.FollowList -> Child.FollowList(context, destination.userId, destination.showFollowers)
        // Monetization (Wave 9)
        is RootDestination.Subscription -> Child.Subscription(context)
        // Avatar System (Wave 10)
        is RootDestination.AvatarCreator -> Child.AvatarCreator(context)
        is RootDestination.AvatarList -> Child.AvatarList(context)
        // Holistic Growth (Sprint 1+2)
        is RootDestination.Habits -> Child.Habits(context)
        is RootDestination.Meditation -> Child.Meditation(context)
        is RootDestination.Environment -> Child.Environment(context)
        is RootDestination.Dashboard -> Child.Dashboard(context)
        is RootDestination.RecurringThoughts -> Child.RecurringThoughts(context)
        is RootDestination.Values -> Child.Values(context)
        is RootDestination.Ikigai -> Child.Ikigai(context)
        is RootDestination.Awe -> Child.Awe(context)
        is RootDestination.Silence -> Child.Silence(context)
        is RootDestination.Connection -> Child.Connection(context)
        is RootDestination.Inspiration -> Child.Inspiration(context)
        is RootDestination.Percorso -> Child.Percorso(context)
        is RootDestination.ActivityGarden -> Child.ActivityGarden(context)
        is RootDestination.Garden -> Child.Garden(context)
        is RootDestination.AvatarDebug -> Child.AvatarDebug(context)
    }

    // ===== Navigation Helpers =====

    @OptIn(DelicateDecomposeApi::class)
    fun navigateTo(destination: RootDestination) {
        navigation.push(destination)
    }

    fun navigateBack() {
        navigation.pop()
    }

    /**
     * Replace the entire stack with a single destination.
     * Used for auth->home transitions where we want to clear the back stack.
     */
    fun replaceAll(destination: RootDestination) {
        navigation.replaceAll(destination)
    }

    /**
     * Switch between bottom nav tabs using bringToFront.
     * Maintains stack history so back button navigates to previous tab naturally.
     */
    fun switchTab(destination: RootDestination) {
        navigation.bringToFront(destination)
    }

    // ----- Convenience navigation methods -----

    fun navigateToHome() {
        replaceAll(RootDestination.Home)
    }

    fun navigateToAuth() {
        replaceAll(RootDestination.Auth)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToChat(sessionId: String? = null) {
        navigation.push(RootDestination.Chat(sessionId = sessionId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToWrite(diaryId: String? = null) {
        navigation.push(RootDestination.Write(diaryId = diaryId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToBrainDump() {
        navigation.push(RootDestination.Write(isBrainDump = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToGratitude() {
        navigation.push(RootDestination.Write(isGratitude = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToEnergyCheckIn() {
        navigation.push(RootDestination.Write(isEnergyCheckIn = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSleepLog() {
        navigation.push(RootDestination.Write(isSleepLog = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToReframe() {
        navigation.push(RootDestination.Write(isReframe = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToLiveChat() {
        navigation.push(RootDestination.LiveChat)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSettings() {
        navigation.push(RootDestination.Settings)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToInsight(diaryId: String) {
        navigation.push(RootDestination.Insight(diaryId = diaryId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToWellbeingSnapshot() {
        navigation.push(RootDestination.WellbeingSnapshot)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToHumanoid(avatarId: String? = null) {
        navigation.push(RootDestination.Humanoid(avatarId = avatarId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToHistory() {
        navigation.push(RootDestination.History)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToProfile() {
        navigation.push(RootDestination.Profile)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToChatHistoryFull() {
        navigation.push(RootDestination.ChatHistoryFull)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToDiaryHistoryFull() {
        navigation.push(RootDestination.DiaryHistoryFull)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSettingsPersonalInfo() {
        navigation.push(RootDestination.SettingsPersonalInfo)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSettingsHealthInfo() {
        navigation.push(RootDestination.SettingsHealthInfo)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSettingsLifestyle() {
        navigation.push(RootDestination.SettingsLifestyle)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSettingsGoals() {
        navigation.push(RootDestination.SettingsGoals)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSettingsAiPreferences() {
        navigation.push(RootDestination.SettingsAiPreferences)
    }

    // ----- Journal Home navigation -----

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToJournalHome() {
        navigation.push(RootDestination.JournalHome)
    }

    // ----- Social navigation methods (Wave 7) -----

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToFeed() {
        navigation.push(RootDestination.Feed)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToUserProfile(userId: String) {
        navigation.push(RootDestination.UserProfile(userId = userId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToComposer(parentThreadId: String? = null, replyToAuthorName: String? = null, prefilledContent: String? = null) {
        navigation.push(RootDestination.Composer(parentThreadId = parentThreadId, replyToAuthorName = replyToAuthorName, prefilledContent = prefilledContent))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSearch() {
        navigation.push(RootDestination.Search)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToNotifications() {
        navigation.push(RootDestination.Notifications)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToMessaging(conversationId: String? = null) {
        navigation.push(RootDestination.Messaging(conversationId = conversationId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToThreadDetail(threadId: String) {
        navigation.push(RootDestination.ThreadDetail(threadId = threadId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToEditProfile(userId: String) {
        navigation.push(RootDestination.EditProfile(userId = userId))
    }

    // ----- Monetization navigation methods (Wave 9) -----

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSubscription() {
        navigation.push(RootDestination.Subscription)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToFollowList(userId: String, showFollowers: Boolean = true) {
        navigation.push(RootDestination.FollowList(userId = userId, showFollowers = showFollowers))
    }

    // ----- Avatar System navigation methods (Wave 10) -----

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToAvatarCreator() {
        navigation.push(RootDestination.AvatarCreator)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToAvatarList() {
        navigation.push(RootDestination.AvatarList)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToHabits() {
        navigation.push(RootDestination.Habits)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToMovement() {
        navigation.push(RootDestination.Write(isMovement = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToBlock() {
        navigation.push(RootDestination.Write(isBlock = true))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToMeditation() {
        navigation.push(RootDestination.Meditation)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToEnvironment() {
        navigation.push(RootDestination.Environment)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToDashboard() {
        navigation.push(RootDestination.Dashboard)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToRecurringThoughts() {
        navigation.push(RootDestination.RecurringThoughts)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToValues() {
        navigation.push(RootDestination.Values)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToIkigai() {
        navigation.push(RootDestination.Ikigai)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToAwe() {
        navigation.push(RootDestination.Awe)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToSilence() {
        navigation.push(RootDestination.Silence)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToConnection() {
        navigation.push(RootDestination.Connection)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToInspiration() {
        navigation.push(RootDestination.Inspiration)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToPercorso() {
        navigation.push(RootDestination.Percorso)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToActivityGarden() {
        navigation.push(RootDestination.ActivityGarden)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToGarden() {
        navigation.push(RootDestination.Garden)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToAvatarDebug() {
        navigation.push(RootDestination.AvatarDebug)
    }

    /**
     * Returns the current active destination by inspecting the top of the stack.
     */
    val currentDestination: RootDestination
        get() = childStack.value.active.configuration

    /**
     * Child sealed interface — typed children wrapping ComponentContext.
     * The composable renderer in DecomposeApp matches on these to render screens.
     */
    sealed interface Child {
        val componentContext: ComponentContext

        data class Auth(override val componentContext: ComponentContext) : Child
        data class Onboarding(override val componentContext: ComponentContext) : Child
        data class Home(override val componentContext: ComponentContext) : Child
        data class Write(override val componentContext: ComponentContext, val diaryId: String?, val isBrainDump: Boolean = false, val isGratitude: Boolean = false, val isEnergyCheckIn: Boolean = false, val isSleepLog: Boolean = false, val isReframe: Boolean = false, val isBlock: Boolean = false, val isMovement: Boolean = false) : Child
        data class Chat(override val componentContext: ComponentContext, val sessionId: String?) : Child
        data class LiveChat(override val componentContext: ComponentContext) : Child
        data class History(override val componentContext: ComponentContext) : Child
        data class ChatHistoryFull(override val componentContext: ComponentContext) : Child
        data class DiaryHistoryFull(override val componentContext: ComponentContext) : Child
        data class Humanoid(override val componentContext: ComponentContext, val avatarId: String? = null) : Child
        data class Settings(override val componentContext: ComponentContext) : Child
        data class SettingsPersonalInfo(override val componentContext: ComponentContext) : Child
        data class SettingsHealthInfo(override val componentContext: ComponentContext) : Child
        data class SettingsLifestyle(override val componentContext: ComponentContext) : Child
        data class SettingsGoals(override val componentContext: ComponentContext) : Child
        data class SettingsAiPreferences(override val componentContext: ComponentContext) : Child
        data class Profile(override val componentContext: ComponentContext) : Child
        data class Insight(override val componentContext: ComponentContext, val diaryId: String) : Child
        data class WellbeingSnapshot(override val componentContext: ComponentContext) : Child
        // Journal Home (Improvement Plan Phase 1)
        data class JournalHome(override val componentContext: ComponentContext) : Child
        // Social Features (Wave 7)
        data class Feed(override val componentContext: ComponentContext) : Child
        data class UserProfile(override val componentContext: ComponentContext, val userId: String) : Child
        data class Composer(override val componentContext: ComponentContext, val parentThreadId: String? = null, val replyToAuthorName: String? = null, val prefilledContent: String? = null) : Child
        data class Search(override val componentContext: ComponentContext) : Child
        data class Notifications(override val componentContext: ComponentContext) : Child
        // Social Features (Wave 8)
        data class Messaging(override val componentContext: ComponentContext, val conversationId: String?) : Child
        data class ThreadDetail(override val componentContext: ComponentContext, val threadId: String) : Child
        data class EditProfile(override val componentContext: ComponentContext, val userId: String) : Child
        data class FollowList(override val componentContext: ComponentContext, val userId: String, val showFollowers: Boolean) : Child
        // Monetization (Wave 9)
        data class Subscription(override val componentContext: ComponentContext) : Child
        // Avatar System (Wave 10)
        data class AvatarCreator(override val componentContext: ComponentContext) : Child
        data class AvatarList(override val componentContext: ComponentContext) : Child
        // Holistic Growth (Sprint 1+2)
        data class Habits(override val componentContext: ComponentContext) : Child
        data class Meditation(override val componentContext: ComponentContext) : Child
        data class Environment(override val componentContext: ComponentContext) : Child
        data class Dashboard(override val componentContext: ComponentContext) : Child
        data class RecurringThoughts(override val componentContext: ComponentContext) : Child
        data class Values(override val componentContext: ComponentContext) : Child
        data class Ikigai(override val componentContext: ComponentContext) : Child
        data class Awe(override val componentContext: ComponentContext) : Child
        data class Silence(override val componentContext: ComponentContext) : Child
        data class Connection(override val componentContext: ComponentContext) : Child
        data class Inspiration(override val componentContext: ComponentContext) : Child
        data class Percorso(override val componentContext: ComponentContext) : Child
        data class ActivityGarden(override val componentContext: ComponentContext) : Child
        data class Garden(override val componentContext: ComponentContext) : Child
        data class AvatarDebug(override val componentContext: ComponentContext) : Child
    }
}
