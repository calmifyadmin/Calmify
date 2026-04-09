package com.lifo.server.model

object ErrorCodes {
    const val NOT_FOUND = "NOT_FOUND"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val FIRESTORE_ERROR = "FIRESTORE_ERROR"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
}
