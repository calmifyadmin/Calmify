package com.lifo.server.security

import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun RoutingCall.userOrThrow(): UserPrincipal {
    return principal<UserPrincipal>()
        ?: throw IllegalStateException("No authenticated user — this route must be inside authenticate(\"firebase\")")
}
