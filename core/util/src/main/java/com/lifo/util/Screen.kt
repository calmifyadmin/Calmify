package com.lifo.util

import com.lifo.util.Constants.WRITE_SCREEN_ARGUMENT_KEY

sealed class Screen(val route: String) {
    object Authentication : Screen(route = "authentication_screen")
    object Onboarding : Screen(route = "onboarding_screen")
    object Home : Screen(route = "home_screen")
    object History : Screen(route = "history_screen")
    object Settings : Screen(route = "settings_screen")
    object ChatHistoryFull : Screen(route = "chat_history_full_screen")
    object DiaryHistoryFull : Screen(route = "diary_history_full_screen")
    object Chat : Screen(route = "chat_screen")
    object LiveChat : Screen(route = "live_chat_screen")
    object GeminiLiveChat : Screen(route = "gemini_live_chat_screen")
    object Profile : Screen(route = "profile_screen")
    object WellbeingSnapshot : Screen(route = "wellbeing_snapshot_screen")
    object Insight : Screen(route = "insight_screen?diaryId={diaryId}") {
        fun passDiaryId(diaryId: String) = "insight_screen?diaryId=$diaryId"
    }
    object Write : Screen(route = "write_screen?$WRITE_SCREEN_ARGUMENT_KEY=" +
            "{$WRITE_SCREEN_ARGUMENT_KEY}") {
        // Navigate to Write screen for creating a NEW diary (no ID)
        val routeNew: String = "write_screen"

        // Navigate to Write screen for EDITING an existing diary (with ID)
        fun passDiaryId(diaryId: String) =
            "write_screen?$WRITE_SCREEN_ARGUMENT_KEY=$diaryId"
    }
}
