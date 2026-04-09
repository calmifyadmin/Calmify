package com.lifo.server.plugins

import com.lifo.server.security.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory

private val auditLogger = LoggerFactory.getLogger("AuditLog")

/**
 * Audit logging for security-relevant actions.
 *
 * Logs: who (userId or "anon"), what (method + path), when (timestamp),
 * result (status code), request ID for correlation.
 *
 * SECURITY: Never logs request/response bodies, tokens, or PII.
 * Structured format for Cloud Logging ingestion.
 */
fun Application.configureAuditLog() {
    intercept(ApplicationCallPipeline.Monitoring) {
        // Record start time
        val startTime = System.currentTimeMillis()

        // Let the request proceed
        proceed()

        // After response is committed, log the audit entry
        val user = call.principal<UserPrincipal>()
        val requestId = call.attributes.getOrNull(RequestIdKey) ?: "none"
        val method = call.request.httpMethod.value
        val path = call.request.path()
        val status = call.response.status()?.value ?: 0
        val duration = System.currentTimeMillis() - startTime
        val ip = call.request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: "direct"

        // Only log mutating operations and auth failures in detail
        // GET requests on non-sensitive paths are too noisy
        val isMutating = method != "GET" && method != "OPTIONS" && method != "HEAD"
        val isAuthFailure = status == 401 || status == 403
        val isServerError = status >= 500
        val isSensitivePath = path.contains("/ai/") || path.contains("/social/") ||
            path.contains("/sync/batch") || path.contains("/gdpr/")

        if (isMutating || isAuthFailure || isServerError || isSensitivePath) {
            auditLogger.info(
                "AUDIT | req={} | user={} | action={} {} | status={} | duration={}ms | ip={}",
                requestId,
                user?.uid ?: "anon",
                method,
                path,
                status,
                duration,
                ip,
            )
        }

        // Warn on suspicious patterns
        if (isAuthFailure) {
            auditLogger.warn(
                "AUTH_FAILURE | req={} | action={} {} | status={} | ip={}",
                requestId, method, path, status, ip,
            )
        }
    }
}
