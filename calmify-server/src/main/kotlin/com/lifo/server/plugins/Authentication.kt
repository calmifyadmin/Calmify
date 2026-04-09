package com.lifo.server.plugins

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.lifo.server.security.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Authentication")

fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("firebase") {
            authenticate { tokenCredential ->
                try {
                    val decoded = FirebaseAuth.getInstance()
                        .verifyIdToken(tokenCredential.token)
                    UserPrincipal(
                        uid = decoded.uid,
                        email = decoded.email,
                    )
                } catch (e: FirebaseAuthException) {
                    logger.warn("Invalid Firebase token: ${e.authErrorCode}")
                    null
                } catch (e: Exception) {
                    logger.error("Token validation error", e)
                    null
                }
            }
        }
    }
}
