package com.lifo.util.repository

import com.lifo.util.model.Avatar
import com.lifo.util.model.AvatarCreationForm
import com.lifo.util.model.AvatarStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Avatar CRUD operations.
 * Implementation uses Firestore at path: users/{userId}/avatars/{avatarId}
 */
interface AvatarRepository {

    /**
     * Submit the creation form. Creates a Firestore doc with status PENDING,
     * then triggers the Cloud Function for system prompt generation.
     * Returns the generated avatarId.
     */
    suspend fun createAvatar(userId: String, form: AvatarCreationForm): String

    /**
     * Observe a single avatar's status changes in real-time (Firestore snapshot listener).
     */
    fun observeAvatar(userId: String, avatarId: String): Flow<Avatar?>

    /**
     * Observe all avatars for a user.
     */
    fun observeUserAvatars(userId: String): Flow<List<Avatar>>

    /**
     * Get a single avatar (one-shot).
     */
    suspend fun getAvatar(userId: String, avatarId: String): Avatar?

    /**
     * Get all avatars for a user (one-shot).
     */
    suspend fun getUserAvatars(userId: String): List<Avatar>

    /**
     * Delete an avatar.
     */
    suspend fun deleteAvatar(userId: String, avatarId: String)

    /**
     * Update avatar status locally (used during creation flow).
     */
    suspend fun updateAvatarStatus(userId: String, avatarId: String, status: AvatarStatus)
}
