package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * MediaUploadRepository Interface
 *
 * Manages media uploads to Cloud Storage.
 * Supports images and videos with progress tracking.
 * Production: Cloud Storage → Transcoder API → Media CDN.
 * MVP: Firebase Storage with direct URLs.
 */
interface MediaUploadRepository {
    suspend fun uploadImage(
        userId: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): RequestState<MediaUpload>

    fun uploadWithProgress(
        userId: String,
        data: ByteArray,
        mimeType: String
    ): Flow<UploadProgress>

    suspend fun deleteMedia(mediaUrl: String): RequestState<Boolean>

    data class MediaUpload(
        val url: String = "",
        val thumbnailUrl: String? = null,
        val mimeType: String = "",
        val sizeBytes: Long = 0,
        val uploadedAt: Long = 0
    )

    sealed interface UploadProgress {
        data class InProgress(val bytesTransferred: Long, val totalBytes: Long) : UploadProgress {
            val percentage: Float get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
        }
        data class Complete(val upload: MediaUpload) : UploadProgress
        data class Failed(val error: Throwable) : UploadProgress
    }
}
