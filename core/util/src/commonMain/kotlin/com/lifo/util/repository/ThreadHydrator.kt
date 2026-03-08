package com.lifo.util.repository

import com.lifo.util.repository.ThreadRepository.Thread

/**
 * ThreadHydrator Interface
 *
 * Enriches Thread objects with data from external collections:
 * - Author info (displayName, avatarUrl, isVerified) from user_profiles
 * - Like status (isLikedByCurrentUser) from threads/{id}/likes subcollection
 *
 * Lives in the repository layer — ViewModels never perform joins directly.
 */
interface ThreadHydrator {
    suspend fun hydrate(threads: List<Thread>, currentUserId: String): List<Thread>
    suspend fun hydrateSingle(thread: Thread, currentUserId: String): Thread
}
