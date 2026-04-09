package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.InsightService
import com.lifo.shared.api.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.insightRoutes() {
    val insightService by inject<InsightService>()

    authenticate("firebase") {
        route("/api/v1/insights") {

            // GET /api/v1/insights?cursor=xxx&limit=20
            get {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = insightService.getInsights(user.uid, params)
                call.respond(DiaryInsightListResponse(data = result.items, meta = result.meta))
            }

            // GET /api/v1/insights/diary/{diaryId}
            get("/diary/{diaryId}") {
                val user = call.principal<UserPrincipal>()!!
                val diaryId = call.parameters["diaryId"]!!
                val insight = insightService.getInsightByDiaryId(user.uid, diaryId)
                if (insight != null) {
                    call.respond(DiaryInsightResponse(data = insight))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        DiaryInsightResponse(error = ApiError(code = "NOT_FOUND", message = "Insight not found")),
                    )
                }
            }
        }
    }
}
