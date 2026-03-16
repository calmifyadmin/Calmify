package com.lifo.composer

import android.app.Application
import android.net.Uri
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ComposerViewModel(
    private val threadRepository: ThreadRepository,
    private val authProvider: AuthProvider,
    private val mediaUploadRepository: MediaUploadRepository,
    private val appContext: Application,
    parentThreadId: String? = null,
    replyToAuthorName: String? = null,
    prefilledContent: String? = null,
) : MviViewModel<ComposerContract.Intent, ComposerContract.State, ComposerContract.Effect>(
    initialState = ComposerContract.State(
        parentThreadId = parentThreadId,
        replyToAuthorName = replyToAuthorName,
        content = prefilledContent ?: "",
        characterCount = prefilledContent?.length ?: 0,
        isFromJournal = prefilledContent != null,
    )
) {

    init {
        // Load parent thread data for reply context display
        if (parentThreadId != null) {
            scope.launch {
                threadRepository.getThreadById(parentThreadId).collect { result ->
                    if (result is RequestState.Success && result.data != null) {
                        updateState { copy(parentThread = result.data) }
                    }
                }
            }
        }
    }

    override fun handleIntent(intent: ComposerContract.Intent) {
        when (intent) {
            is ComposerContract.Intent.UpdateContent -> handleUpdateContent(intent.content)
            is ComposerContract.Intent.SetVisibility -> handleSetVisibility(intent.visibility)
            is ComposerContract.Intent.SetMoodTag -> handleSetMoodTag(intent.moodTag)
            is ComposerContract.Intent.SetCategory -> handleSetCategory(intent.category)
            is ComposerContract.Intent.SetShareFromJournal -> handleSetShareFromJournal(intent.enabled)
            is ComposerContract.Intent.AddMedia -> handleAddMedia(intent.uri)
            is ComposerContract.Intent.RemoveMedia -> handleRemoveMedia(intent.index)
            is ComposerContract.Intent.ChangeReplyPermission -> handleChangeReplyPermission(intent.permission)
            is ComposerContract.Intent.AddThreadPost -> handleAddThreadPost()
            is ComposerContract.Intent.RemoveThreadPost -> handleRemoveThreadPost(intent.index)
            is ComposerContract.Intent.UpdateThreadPost -> handleUpdateThreadPost(intent.index, intent.text)
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
            val newTag = if (this.moodTag == moodTag) null else moodTag
            copy(moodTag = newTag)
        }
    }

    private fun handleSetCategory(category: ComposerContract.PostCategory) {
        updateState {
            val newCategory = if (this.category == category) null else category
            copy(category = newCategory)
        }
    }

    private fun handleSetShareFromJournal(enabled: Boolean) {
        updateState { copy(isFromJournal = enabled) }
    }

    private fun handleChangeReplyPermission(permission: ComposerContract.ReplyPermission) {
        updateState { copy(replyPermission = permission) }
    }

    private fun handleAddMedia(uri: String) {
        updateState {
            if (mediaUris.size < maxMedia) {
                copy(mediaUris = mediaUris + uri)
            } else {
                this
            }
        }
    }

    private fun handleRemoveMedia(index: Int) {
        updateState {
            if (index in mediaUris.indices) {
                copy(
                    mediaUris = mediaUris.toMutableList().apply { removeAt(index) },
                    uploadProgress = uploadProgress - index,
                )
            } else {
                this
            }
        }
    }

    private fun handleAddThreadPost() {
        updateState { copy(threadDrafts = threadDrafts + ThreadDraft()) }
    }

    private fun handleRemoveThreadPost(index: Int) {
        updateState {
            if (index in threadDrafts.indices) {
                copy(threadDrafts = threadDrafts.toMutableList().apply { removeAt(index) })
            } else this
        }
    }

    private fun handleUpdateThreadPost(index: Int, text: String) {
        updateState {
            if (index in threadDrafts.indices) {
                copy(threadDrafts = threadDrafts.toMutableList().apply {
                    set(index, get(index).copy(text = text))
                })
            } else this
        }
    }

    private fun handleSubmit() {
        val state = currentState

        val trimmedContent = state.content.trim()
        if (trimmedContent.isEmpty()) {
            sendEffect(ComposerContract.Effect.ShowError("Il contenuto del post non puo' essere vuoto."))
            return
        }
        if (state.characterCount > state.maxCharacters) {
            sendEffect(
                ComposerContract.Effect.ShowError(
                    "Il post supera il limite di ${state.maxCharacters} caratteri."
                )
            )
            return
        }

        // Mood tag obbligatorio per i post (non per le reply)
        if (state.parentThreadId == null && state.moodTag == null) {
            sendEffect(ComposerContract.Effect.ShowError("Seleziona come ti senti prima di pubblicare."))
            return
        }

        val userId = authProvider.currentUserId
        if (userId == null) {
            sendEffect(ComposerContract.Effect.ShowError("Devi essere autenticato per pubblicare."))
            return
        }

        updateState { copy(isSubmitting = true) }

        scope.launch {
            // Upload media if present
            val uploadedUrls = if (state.mediaUris.isNotEmpty()) {
                updateState { copy(isUploading = true) }
                val results = uploadAllMedia(userId, state.mediaUris)
                updateState { copy(isUploading = false) }

                if (results == null) {
                    updateState { copy(isSubmitting = false) }
                    return@launch
                }
                results
            } else {
                emptyList()
            }

            val thread = ThreadRepository.Thread(
                authorId = userId,
                parentThreadId = state.parentThreadId,
                text = trimmedContent,
                visibility = state.visibility.name.lowercase(),
                moodTag = state.moodTag,
                postCategory = state.category?.name?.lowercase(),
                isFromJournal = state.isFromJournal,
                mediaUrls = uploadedUrls,
                createdAt = System.currentTimeMillis(),
            )

            when (val result = threadRepository.createThread(thread)) {
                is RequestState.Success -> {
                    // Submit additional thread drafts as linked replies (thread chain)
                    var parentId = result.data
                    for (draft in state.threadDrafts) {
                        if (draft.text.isBlank()) continue
                        val chainPost = ThreadRepository.Thread(
                            authorId = userId,
                            parentThreadId = parentId,
                            text = draft.text.trim(),
                            visibility = state.visibility.name.lowercase(),
                            createdAt = System.currentTimeMillis(),
                        )
                        when (val chainResult = threadRepository.createThread(chainPost)) {
                            is RequestState.Success -> parentId = chainResult.data
                            is RequestState.Error -> {
                                updateState { copy(isSubmitting = false) }
                                sendEffect(ComposerContract.Effect.ShowError("Failed to post thread chain: ${chainResult.message}"))
                                return@launch
                            }
                            else -> {}
                        }
                    }
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

    /**
     * Uploads all media URIs to Firebase Storage in parallel.
     * Returns list of download URLs on success, null on failure.
     */
    private suspend fun uploadAllMedia(
        userId: String,
        uris: List<String>,
    ): List<String>? {
        return try {
            val deferreds = uris.mapIndexed { index, uriStr ->
                scope.async {
                    val bytes = readBytesFromUri(uriStr)
                    if (bytes == null) {
                        updateState {
                            copy(uploadProgress = uploadProgress + (index to -1f))
                        }
                        sendEffect(ComposerContract.Effect.ShowError("Failed to read media file."))
                        return@async null
                    }

                    val mimeType = resolveMimeType(uriStr)

                    updateState {
                        copy(uploadProgress = uploadProgress + (index to 0f))
                    }

                    when (val result = mediaUploadRepository.uploadImage(userId, bytes, mimeType)) {
                        is RequestState.Success -> {
                            updateState {
                                copy(uploadProgress = uploadProgress + (index to 1f))
                            }
                            result.data.url
                        }
                        is RequestState.Error -> {
                            sendEffect(ComposerContract.Effect.ShowError("Upload failed: ${result.message}"))
                            null
                        }
                        else -> null
                    }
                }
            }

            val results = deferreds.awaitAll()
            if (results.any { it == null }) null else results.filterNotNull()
        } catch (e: Exception) {
            sendEffect(ComposerContract.Effect.ShowError("Upload failed: ${e.message}"))
            null
        }
    }

    private fun readBytesFromUri(uriStr: String): ByteArray? {
        return try {
            val uri = Uri.parse(uriStr)
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveMimeType(uriStr: String): String {
        return try {
            val uri = Uri.parse(uriStr)
            appContext.contentResolver.getType(uri) ?: "image/jpeg"
        } catch (_: Exception) {
            "image/jpeg"
        }
    }

    private fun handleDiscard() {
        sendEffect(ComposerContract.Effect.Discarded)
    }
}
