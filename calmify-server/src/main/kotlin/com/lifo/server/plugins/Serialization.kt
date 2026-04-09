package com.lifo.server.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        // Protobuf preferred (mobile clients send Accept: application/protobuf)
        protobuf()
        // JSON fallback (browser, curl, debug)
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = false
        })
    }
}
