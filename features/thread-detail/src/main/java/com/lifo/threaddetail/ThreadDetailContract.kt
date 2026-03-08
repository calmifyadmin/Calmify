package com.lifo.threaddetail

import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ThreadRepository.Thread

enum class ReplySortOrder { MostPopular, MostRecent }

object ThreadDetailContract {

    sealed interface Intent : MviContract.Intent {
        data class LoadThread(val threadId: String) : Intent
        data class UpdateReplyText(val text: String) : Intent
        data object SubmitReply : Intent
        data class LikeThread(val threadId: String) : Intent
        data class UnlikeThread(val threadId: String) : Intent
        data class ReplyToReply(val thread: Thread) : Intent
        data object ClearReplyTarget : Intent
        data class ChangeSortOrder(val order: ReplySortOrder) : Intent
        data class ToggleNestedReplies(val parentReplyId: String) : Intent
    }

    data class State(
        val thread: RequestState<Thread?> = RequestState.Idle,
        val replies: RequestState<List<Thread>> = RequestState.Idle,
        val replyText: String = "",
        val isSendingReply: Boolean = false,
        val replyingToReply: Thread? = null,
        val sortOrder: ReplySortOrder = ReplySortOrder.MostPopular,
        val expandedReplies: Set<String> = emptySet(),
        val nestedReplies: Map<String, RequestState<List<Thread>>> = emptyMap(),
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShareIntent(val text: String) : Effect
        data object ReplyPosted : Effect
        data class ShowError(val message: String) : Effect
    }
}
