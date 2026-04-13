package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.currentTimeMillis
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.MediaUploadRepository.MediaUpload
import com.lifo.util.repository.MediaUploadRepository.UploadProgress
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * Ktor/REST implementation of MediaUploadRepository — presigned GCS pattern.
 *
 *  upload flow
 *    1. POST /api/v1/media/upload-url  → { uploadUrl, path, expiresAt }
 *    2. PUT bytes directly to uploadUrl (raw GCS, 15 min validity)
 *    3. Persist only the `path` (e.g. `social-media/{userId}/{uuid}.jpg`) in Firestore
 *
 *  read flow
 *    POST /api/v1/media/resolve-urls with list of paths → short-lived signed GET URLs
 *
 * Paths are stable identifiers; signed URLs are regenerated on demand. This keeps DB
 * records immutable across key rotations and preserves ownership semantics server-side.
 */
class KtorMediaUploadRepository(
    private val api: KtorApiClient,
) : MediaUploadRepository {

    override suspend fun uploadImage(
        userId: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): RequestState<MediaUpload> {
        val mint = api.post<UploadUrlResponse>(
            path = "/api/v1/media/upload-url",
            body = UploadUrlRequest(mimeType = mimeType, folder = "social-media"),
        )
        val ticket = when (mint) {
            is RequestState.Success -> mint.data
            is RequestState.Error -> return RequestState.Error(mint.error)
            else -> return RequestState.Error(Exception("upload-url: unexpected state"))
        }

        return try {
            val response = api.client.put(ticket.uploadUrl) {
                setBody(imageBytes)
                contentType(ContentType.parse(mimeType))
                // Bypass the default Accept: protobuf — GCS returns no body but is picky
                headers.remove(HttpHeaders.Accept)
                accept(ContentType.Any)
            }
            if (!response.status.isSuccess()) {
                return RequestState.Error(Exception("GCS PUT failed HTTP ${response.status.value}"))
            }
            RequestState.Success(
                MediaUpload(
                    url = ticket.path,
                    mimeType = mimeType,
                    sizeBytes = imageBytes.size.toLong(),
                    uploadedAt = currentTimeMillis(),
                )
            )
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun uploadWithProgress(
        userId: String,
        data: ByteArray,
        mimeType: String,
    ): Flow<UploadProgress> = flow {
        emit(UploadProgress.InProgress(bytesTransferred = 0, totalBytes = data.size.toLong()))
        when (val result = uploadImage(userId, data, mimeType)) {
            is RequestState.Success -> {
                emit(UploadProgress.InProgress(bytesTransferred = data.size.toLong(), totalBytes = data.size.toLong()))
                emit(UploadProgress.Complete(result.data))
            }
            is RequestState.Error -> emit(UploadProgress.Failed(result.error))
            else -> emit(UploadProgress.Failed(Exception("unexpected state")))
        }
    }

    override suspend fun resolveImageUrls(remotePaths: List<String>): Map<String, String> {
        if (remotePaths.isEmpty()) return emptyMap()
        val (urls, paths) = remotePaths.partition { it.startsWith("http") }
        val passthrough = urls.associateWith { it }
        if (paths.isEmpty()) return passthrough

        val result = api.post<ResolveUrlsResponse>(
            path = "/api/v1/media/resolve-urls",
            body = ResolveUrlsRequest(paths = paths),
        )
        return when (result) {
            is RequestState.Success -> passthrough + result.data.urls.associate { it.path to it.url }
            else -> passthrough
        }
    }

    override suspend fun deleteMedia(mediaUrl: String): RequestState<Boolean> {
        val path = extractPath(mediaUrl)
        if (path.isBlank()) return RequestState.Error(Exception("cannot extract path from url"))
        val result = api.post<Unit>(
            path = "/api/v1/media/delete",
            body = DeleteMediaRequest(path = path),
        )
        return when (result) {
            is RequestState.Success -> RequestState.Success(true)
            is RequestState.Error -> RequestState.Error(result.error)
            else -> RequestState.Error(Exception("unexpected state"))
        }
    }

    override suspend fun deleteAllUserMedia(userId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/media/user/me/all")
    }

    /**
     * Accept either a bare storage path ("social-media/uid/xxx.jpg") or a signed/tokenized URL.
     * For URLs we extract the path segment after the bucket — good enough to cover both
     * legacy Firebase Storage URLs and new GCS V4 signed URLs.
     */
    private fun extractPath(urlOrPath: String): String {
        val trimmed = urlOrPath.trim()
        if (trimmed.isBlank()) return ""
        if (!trimmed.startsWith("http")) return trimmed
        // https://storage.googleapis.com/{bucket}/{path}?...
        // https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{url-encoded-path}?alt=media&token=...
        val withoutQuery = trimmed.substringBefore('?')
        return when {
            withoutQuery.contains("/o/") -> {
                val encoded = withoutQuery.substringAfter("/o/")
                decodeUrl(encoded)
            }
            withoutQuery.contains("storage.googleapis.com/") -> {
                val afterHost = withoutQuery.substringAfter("storage.googleapis.com/")
                // strip bucket
                afterHost.substringAfter('/')
            }
            else -> ""
        }
    }

    private fun decodeUrl(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    append(code.toChar())
                    i += 3
                    continue
                }
            }
            append(c)
            i++
        }
    }
}

@Serializable
private data class UploadUrlRequest(
    val mimeType: String = "",
    val folder: String = "social-media",
)

@Serializable
private data class UploadUrlResponse(
    val uploadUrl: String = "",
    val path: String = "",
    val expiresAt: Long = 0,
)

@Serializable
private data class ResolveUrlsRequest(val paths: List<String> = emptyList())

@Serializable
private data class ResolvedUrlDto(val path: String = "", val url: String = "")

@Serializable
private data class ResolveUrlsResponse(val urls: List<ResolvedUrlDto> = emptyList())

@Serializable
private data class DeleteMediaRequest(val path: String = "")
