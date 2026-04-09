package com.lifo.server.model

import io.ktor.server.application.*

data class PaginationParams(
    val cursor: String?,
    val limit: Int,
    val direction: String,
) {
    companion object {
        fun fromCall(call: ApplicationCall): PaginationParams {
            return PaginationParams(
                cursor = call.parameters["cursor"],
                limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20,
                direction = call.parameters["direction"] ?: "desc",
            )
        }
    }
}
