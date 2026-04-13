package com.lifo.server.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.firebase.cloud.StorageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * MediaService — presigned URL pattern for GCS media uploads.
 *
 * Flow:
 *  1. Client asks `/media/upload-url` with mimeType → server returns `{ uploadUrl, path, expiresAt }`.
 *  2. Client PUTs bytes directly to `uploadUrl` (15 min expiry, V4 signature).
 *  3. Server stores only the `path` (relative blob name) in Firestore — never tokenized URLs.
 *  4. Client resolves paths to short-lived signed GET URLs via `/media/resolve-urls` (1 h expiry).
 *
 * Storage layout matches legacy Firebase Storage paths for backward compatibility:
 *   social-media/{userId}/{uuid}.{ext}   — thread/profile images
 *   images/{userId}/{uuid}.{ext}         — diary images
 *
 * Signed URLs require the Cloud Run service account to hold
 * `roles/iam.serviceAccountTokenCreator` on itself (IAM signBlob fallback).
 */
class MediaService(
    private val bucketName: String = System.getenv("GCS_BUCKET") ?: "calmify-388723.appspot.com",
) {
    private val log = LoggerFactory.getLogger(MediaService::class.java)

    private val storage: Storage
        get() = StorageClient.getInstance().bucket(bucketName).storage

    @Serializable
    data class UploadUrlResult(
        val uploadUrl: String,
        val path: String,
        val expiresAt: Long,
    )

    @Serializable
    data class ResolvedUrl(
        val path: String,
        val url: String,
    )

    suspend fun createUploadUrl(
        userId: String,
        mimeType: String,
        folder: String,
    ): UploadUrlResult = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "userId required" }
        require(mimeType.isNotBlank()) { "mimeType required" }
        val safeFolder = sanitizeFolder(folder)
        val extension = extensionFor(mimeType)
        val path = "$safeFolder/$userId/${UUID.randomUUID()}.$extension"

        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, path))
            .setContentType(mimeType)
            .build()

        val expiryMinutes = 15L
        val signedUrl = storage.signUrl(
            blobInfo,
            expiryMinutes, TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withContentType(),
            Storage.SignUrlOption.withV4Signature(),
        )
        val expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes)
        log.info("media.upload-url user=$userId folder=$safeFolder path=$path mime=$mimeType")
        UploadUrlResult(uploadUrl = signedUrl.toString(), path = path, expiresAt = expiresAt)
    }

    suspend fun resolveReadUrls(paths: List<String>): List<ResolvedUrl> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext emptyList()
        val expiryHours = 1L
        paths.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            if (trimmed.startsWith("http")) {
                // Legacy tokenized Firebase Storage URLs — return as-is.
                return@mapNotNull ResolvedUrl(path = trimmed, url = trimmed)
            }
            runCatching {
                val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, trimmed)).build()
                val signed = storage.signUrl(
                    blobInfo,
                    expiryHours, TimeUnit.HOURS,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature(),
                )
                ResolvedUrl(path = trimmed, url = signed.toString())
            }.getOrElse {
                log.warn("media.resolve failed path=$trimmed: ${it.message}")
                null
            }
        }
    }

    suspend fun deletePath(userId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = path.trim().removePrefix("/")
        if (!isOwnedBy(userId, trimmed)) {
            log.warn("media.delete denied user=$userId path=$trimmed (ownership mismatch)")
            return@withContext false
        }
        val deleted = runCatching { storage.delete(BlobId.of(bucketName, trimmed)) }.getOrDefault(false)
        log.info("media.delete user=$userId path=$trimmed success=$deleted")
        deleted
    }

    suspend fun deleteAllForUser(userId: String): Int = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "userId required" }
        var deleted = 0
        OWNED_FOLDERS.forEach { folder ->
            val prefix = "$folder/$userId/"
            val page = storage.list(
                bucketName,
                Storage.BlobListOption.prefix(prefix),
            )
            page.iterateAll().forEach { blob ->
                if (runCatching { blob.delete() }.getOrDefault(false)) deleted++
            }
        }
        log.info("media.deleteAll user=$userId blobs=$deleted")
        deleted
    }

    private fun isOwnedBy(userId: String, path: String): Boolean {
        if (userId.isBlank() || path.isBlank()) return false
        return OWNED_FOLDERS.any { path.startsWith("$it/$userId/") }
    }

    private fun sanitizeFolder(folder: String): String {
        val normalized = folder.trim().trim('/').ifBlank { DEFAULT_FOLDER }
        return if (normalized in ALLOWED_FOLDERS) normalized else DEFAULT_FOLDER
    }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "video/mp4" -> "mp4"
        else -> "jpg"
    }

    companion object {
        private const val DEFAULT_FOLDER = "social-media"
        // Folders the server is willing to mint upload URLs for and mass-delete on GDPR request.
        private val ALLOWED_FOLDERS = setOf("social-media", "images")
        private val OWNED_FOLDERS = listOf("social-media", "images")
    }
}
