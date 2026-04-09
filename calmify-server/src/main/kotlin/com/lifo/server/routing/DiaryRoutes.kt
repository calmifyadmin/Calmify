package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.DiaryService
import com.lifo.shared.api.ApiError
import com.lifo.shared.api.DiaryListResponse
import com.lifo.shared.api.DiaryResponse
import com.lifo.shared.model.DiaryProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.diaryRoutes() {
    val diaryService by inject<DiaryService>()

    authenticate("firebase") {
        route("/api/v1/diaries") {

            // GET /api/v1/diaries?cursor=xxx&limit=20&direction=desc
            get {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = diaryService.getDiaries(user.uid, params)
                call.respond(DiaryListResponse(data = result.items, meta = result.meta))
            }

            // GET /api/v1/diaries/{id}
            get("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing diary id")
                val diary = diaryService.getDiaryById(user.uid, id)
                if (diary != null) {
                    call.respond(DiaryResponse(data = diary))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        DiaryResponse(error = ApiError(code = "NOT_FOUND", message = "Diary not found")),
                    )
                }
            }

            // GET /api/v1/diaries/range?start=xxx&end=xxx
            get("/range") {
                val user = call.principal<UserPrincipal>()!!
                val start = call.parameters["start"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing 'start' parameter (epoch millis)")
                val end = call.parameters["end"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing 'end' parameter (epoch millis)")
                if (end < start) {
                    throw IllegalArgumentException("'end' must be >= 'start'")
                }
                val maxRangeMs = 90L * 24 * 60 * 60 * 1000 // 90 days
                if (end - start > maxRangeMs) {
                    throw IllegalArgumentException("Date range cannot exceed 90 days")
                }
                val diaries = diaryService.getDiariesByDateRange(user.uid, start, end)
                call.respond(DiaryListResponse(data = diaries))
            }

            // POST /api/v1/diaries
            post {
                val user = call.principal<UserPrincipal>()!!
                val diary = call.receive<DiaryProto>()
                val created = diaryService.createDiary(user.uid, diary)
                call.respond(HttpStatusCode.Created, DiaryResponse(data = created))
            }

            // PUT /api/v1/diaries/{id}
            put("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing diary id")
                val diary = call.receive<DiaryProto>()
                val updated = diaryService.updateDiary(user.uid, id, diary)
                if (updated != null) {
                    call.respond(DiaryResponse(data = updated))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        DiaryResponse(error = ApiError(code = "NOT_FOUND", message = "Diary not found")),
                    )
                }
            }

            // DELETE /api/v1/diaries/{id}
            delete("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing diary id")
                val deleted = diaryService.deleteDiary(user.uid, id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(code = "NOT_FOUND", message = "Diary not found"),
                    )
                }
            }
        }
    }
}
