package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.MediaService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Media routes — presigned GCS URL pattern.
 *
 *  POST /api/v1/media/upload-url          → mint V4 PUT URL (15 min)
 *  POST /api/v1/media/resolve-urls        → batch V4 GET URLs (1 h)
 *  POST /api/v1/media/delete              → delete a single blob owned by user
 *  DELETE /api/v1/media/user/me/all       → GDPR Art.17 — wipe all user media
 */
fun Route.mediaRoutes() {
    val service by inject<MediaService>()

    authenticate("firebase") {
        route("/api/v1/media") {

            post("/upload-url") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<UploadUrlRequestDto>()
                if (body.mimeType.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, MediaErrorDto("mimeType required"))
                    return@post
                }
                val result = service.createUploadUrl(
                    userId = user.uid,
                    mimeType = body.mimeType,
                    folder = body.folder.ifBlank { "social-media" },
                )
                call.respond(
                    UploadUrlResponseDto(
                        uploadUrl = result.uploadUrl,
                        path = result.path,
                        expiresAt = result.expiresAt,
                    )
                )
            }

            post("/resolve-urls") {
                call.principal<UserPrincipal>()!!
                val body = call.receive<ResolveUrlsRequestDto>()
                val paths = body.paths.take(MAX_RESOLVE_BATCH)
                val resolved = service.resolveReadUrls(paths)
                call.respond(
                    ResolveUrlsResponseDto(
                        urls = resolved.map { ResolvedUrlDto(path = it.path, url = it.url) }
                    )
                )
            }

            post("/delete") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<DeleteMediaRequestDto>()
                if (body.path.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, MediaErrorDto("path required"))
                    return@post
                }
                val ok = service.deletePath(userId = user.uid, path = body.path)
                if (ok) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, MediaErrorDto("not owned or missing"))
            }

            delete("/user/me/all") {
                val user = call.principal<UserPrincipal>()!!
                val count = service.deleteAllForUser(user.uid)
                call.respond(DeleteAllResponseDto(deletedCount = count))
            }
        }
    }
}

private const val MAX_RESOLVE_BATCH = 50

@Serializable
data class UploadUrlRequestDto(
    val mimeType: String = "",
    val folder: String = "social-media",
)

@Serializable
data class UploadUrlResponseDto(
    val uploadUrl: String = "",
    val path: String = "",
    val expiresAt: Long = 0,
)

@Serializable
data class ResolveUrlsRequestDto(val paths: List<String> = emptyList())

@Serializable
data class ResolvedUrlDto(val path: String = "", val url: String = "")

@Serializable
data class ResolveUrlsResponseDto(val urls: List<ResolvedUrlDto> = emptyList())

@Serializable
data class DeleteMediaRequestDto(val path: String = "")

@Serializable
data class DeleteAllResponseDto(val deletedCount: Int = 0)

@Serializable
private data class MediaErrorDto(val error: String = "")
