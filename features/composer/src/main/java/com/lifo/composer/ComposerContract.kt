package com.lifo.composer

import com.lifo.util.mvi.MviContract

object ComposerContract {

    enum class Visibility { PUBLIC, FOLLOWERS_ONLY, PRIVATE }

    val MOOD_TAGS = listOf("Happy", "Calm", "Grateful", "Motivated", "Anxious", "Sad", "Angry", "Neutral")

    sealed interface Intent : MviContract.Intent {
        data class UpdateContent(val content: String) : Intent
        data class SetVisibility(val visibility: Visibility) : Intent
        data class SetMoodTag(val moodTag: String?) : Intent
        data class SetShareFromJournal(val enabled: Boolean) : Intent
        data object Submit : Intent
        data object Discard : Intent
    }

    data class State(
        val content: String = "",
        val visibility: Visibility = Visibility.PUBLIC,
        val moodTag: String? = null,
        val isFromJournal: Boolean = false,
        val isSubmitting: Boolean = false,
        val characterCount: Int = 0,
        val maxCharacters: Int = 500,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object PostCreated : Effect
        data object Discarded : Effect
        data class ShowError(val message: String) : Effect
    }
}
