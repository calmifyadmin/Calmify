package com.lifo.composer

import com.lifo.ui.i18n.Strings
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ThreadRepository
import org.jetbrains.compose.resources.StringResource

data class ThreadDraft(val text: String = "", val mediaUris: List<String> = emptyList())

object ComposerContract {

    enum class Visibility { PUBLIC, FOLLOWERS_ONLY, PRIVATE }

    enum class ReplyPermission { Everyone, Followers, Mentioned }

    enum class PostCategory(val labelRes: StringResource) {
        SCOPERTA(Strings.ComposerCategory.discovery),
        SFIDA(Strings.ComposerCategory.challenge),
        DOMANDA(Strings.ComposerCategory.question),
    }

    val MOOD_TAGS = listOf(
        "Felice", "Sereno", "Grato", "Motivato",
        "Ansioso", "Triste", "Arrabbiato", "Neutrale",
    )

    sealed interface Intent : MviContract.Intent {
        data class UpdateContent(val content: String) : Intent
        data class SetVisibility(val visibility: Visibility) : Intent
        data class SetMoodTag(val moodTag: String?) : Intent
        data class SetCategory(val category: PostCategory) : Intent
        data class SetShareFromJournal(val enabled: Boolean) : Intent
        data class AddMedia(val uri: String) : Intent
        data class RemoveMedia(val index: Int) : Intent
        data class ChangeReplyPermission(val permission: ReplyPermission) : Intent
        data object AddThreadPost : Intent
        data class RemoveThreadPost(val index: Int) : Intent
        data class UpdateThreadPost(val index: Int, val text: String) : Intent
        data object Submit : Intent
        data object Discard : Intent
    }

    data class State(
        val content: String = "",
        val visibility: Visibility = Visibility.PUBLIC,
        val moodTag: String? = null,
        val category: PostCategory? = null,
        val isFromJournal: Boolean = false,
        val isSubmitting: Boolean = false,
        val isUploading: Boolean = false,
        val characterCount: Int = 0,
        val maxCharacters: Int = 500,
        val mediaUris: List<String> = emptyList(),
        val uploadProgress: Map<Int, Float> = emptyMap(),
        val maxMedia: Int = 4,
        val parentThreadId: String? = null,
        val replyToAuthorName: String? = null,
        val parentThread: ThreadRepository.Thread? = null,
        val replyPermission: ReplyPermission = ReplyPermission.Everyone,
        val threadDrafts: List<ThreadDraft> = emptyList(),
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object PostCreated : Effect
        data object Discarded : Effect
        data class ShowError(val message: String) : Effect
    }
}
