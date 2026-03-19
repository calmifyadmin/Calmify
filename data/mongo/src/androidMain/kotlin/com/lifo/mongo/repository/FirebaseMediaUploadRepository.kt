package com.lifo.mongo.repository

import com.google.firebase.storage.FirebaseStorage
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.MediaUploadRepository.MediaUpload
import com.lifo.util.repository.MediaUploadRepository.UploadProgress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Storage implementation of MediaUploadRepository.
 *
 * MVP implementation using Firebase Storage directly.
 * Production: Cloud Storage → Transcoder API → Media CDN.
 *
 * Storage structure:
 *   social-media/{userId}/{uuid}.{ext}
 */
@Singleton
class FirebaseMediaUploadRepository @Inject constructor(
    private val storage: FirebaseStorage
) : MediaUploadRepository {

    companion object {
        private const val SOCIAL_MEDIA_PATH = "social-media"
    }

    override suspend fun uploadImage(
        userId: String,
        imageBytes: ByteArray,
        mimeType: String
    ): RequestState<MediaUpload> {
        return try {
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val filename = "${UUID.randomUUID()}.$extension"
            val ref = storage.reference.child("$SOCIAL_MEDIA_PATH/$userId/$filename")

            ref.putBytes(imageBytes).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            RequestState.Success(
                MediaUpload(
                    url = downloadUrl,
                    mimeType = mimeType,
                    sizeBytes = imageBytes.size.toLong(),
                    uploadedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun uploadWithProgress(
        userId: String,
        data: ByteArray,
        mimeType: String
    ): Flow<UploadProgress> = callbackFlow {
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            else -> "jpg"
        }
        val filename = "${UUID.randomUUID()}.$extension"
        val ref = storage.reference.child("$SOCIAL_MEDIA_PATH/$userId/$filename")

        val uploadTask = ref.putBytes(data)

        uploadTask.addOnProgressListener { snapshot ->
            trySend(UploadProgress.InProgress(snapshot.bytesTransferred, snapshot.totalByteCount))
        }

        uploadTask.addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                trySend(
                    UploadProgress.Complete(
                        MediaUpload(
                            url = uri.toString(),
                            mimeType = mimeType,
                            sizeBytes = data.size.toLong(),
                            uploadedAt = System.currentTimeMillis()
                        )
                    )
                )
                close()
            }
        }

        uploadTask.addOnFailureListener { exception ->
            trySend(UploadProgress.Failed(exception))
            close()
        }

        awaitClose { uploadTask.cancel() }
    }

    override suspend fun resolveImageUrls(remotePaths: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        remotePaths.forEach { path ->
            if (path.isBlank()) return@forEach
            if (path.startsWith("http")) {
                result[path] = path
                return@forEach
            }
            try {
                val url = storage.reference.child(path.trim()).downloadUrl.await().toString()
                result[path] = url
            } catch (_: Exception) {
                // Skip failed paths
            }
        }
        return result
    }

    override suspend fun deleteAllUserMedia(userId: String): RequestState<Boolean> {
        return try {
            // Delete social-media/{userId}/ directory
            val socialRef = storage.reference.child("$SOCIAL_MEDIA_PATH/$userId")
            val socialItems = socialRef.listAll().await()
            socialItems.items.forEach { it.delete().await() }

            // Delete images/{userId}/ directory (diary images)
            val imagesRef = storage.reference.child("images/$userId")
            val imageItems = imagesRef.listAll().await()
            imageItems.items.forEach { it.delete().await() }

            RequestState.Success(true)
        } catch (e: Exception) {
            // Best-effort: some files may already be deleted
            RequestState.Success(true)
        }
    }

    override suspend fun deleteMedia(mediaUrl: String): RequestState<Boolean> {
        return try {
            val ref = storage.getReferenceFromUrl(mediaUrl)
            ref.delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}
