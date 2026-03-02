package com.lifo.composer

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.launch

class ComposerViewModel(
    private val threadRepository: ThreadRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<ComposerContract.Intent, ComposerContract.State, ComposerContract.Effect>(
    initialState = ComposerContract.State()
) {

    override fun handleIntent(intent: ComposerContract.Intent) {
        when (intent) {
            is ComposerContract.Intent.UpdateContent -> handleUpdateContent(intent.content)
            is ComposerContract.Intent.SetVisibility -> handleSetVisibility(intent.visibility)
            is ComposerContract.Intent.SetMoodTag -> handleSetMoodTag(intent.moodTag)
            is ComposerContract.Intent.SetShareFromJournal -> handleSetShareFromJournal(intent.enabled)
            is ComposerContract.Intent.Submit -> handleSubmit()
            is ComposerContract.Intent.Discard -> handleDiscard()
        }
    }

    private fun handleUpdateContent(content: String) {
        updateState {
            copy(
                content = content,
                characterCount = content.length,
            )
        }
    }

    private fun handleSetVisibility(visibility: ComposerContract.Visibility) {
        updateState { copy(visibility = visibility) }
    }

    private fun handleSetMoodTag(moodTag: String?) {
        updateState {
            // Toggle: if the same tag is tapped again, deselect it
            val newTag = if (this.moodTag == moodTag) null else moodTag
            copy(moodTag = newTag)
        }
    }

    private fun handleSetShareFromJournal(enabled: Boolean) {
        updateState { copy(isFromJournal = enabled) }
    }

    private fun handleSubmit() {
        val state = currentState

        // Validation
        val trimmedContent = state.content.trim()
        if (trimmedContent.isEmpty()) {
            sendEffect(ComposerContract.Effect.ShowError("Post content cannot be empty."))
            return
        }
        if (state.characterCount > state.maxCharacters) {
            sendEffect(
                ComposerContract.Effect.ShowError(
                    "Post exceeds the maximum of ${state.maxCharacters} characters."
                )
            )
            return
        }

        val userId = authProvider.currentUserId
        if (userId == null) {
            sendEffect(ComposerContract.Effect.ShowError("You must be signed in to post."))
            return
        }

        updateState { copy(isSubmitting = true) }

        scope.launch {
            val thread = ThreadRepository.Thread(
                authorId = userId,
                text = trimmedContent,
                visibility = state.visibility.name.lowercase(),
                moodTag = state.moodTag,
                isFromJournal = state.isFromJournal,
                createdAt = System.currentTimeMillis(),
            )

            when (val result = threadRepository.createThread(thread)) {
                is RequestState.Success -> {
                    updateState { copy(isSubmitting = false) }
                    sendEffect(ComposerContract.Effect.PostCreated)
                }
                is RequestState.Error -> {
                    updateState { copy(isSubmitting = false) }
                    sendEffect(ComposerContract.Effect.ShowError(result.message))
                }
                else -> {
                    updateState { copy(isSubmitting = false) }
                }
            }
        }
    }

    private fun handleDiscard() {
        sendEffect(ComposerContract.Effect.Discarded)
    }
}
