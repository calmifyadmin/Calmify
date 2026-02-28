package com.lifo.util

import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date

// FIRESTORE UTILITIES (2025 Stack)

/**
 * Converts Firestore Timestamp to Instant
 */
fun Timestamp.toInstant(): Instant {
    return Instant.ofEpochSecond(this.seconds, this.nanoseconds.toLong())
}

/**
 * Converts Instant to Firestore Timestamp
 */
fun Instant.toTimestamp(): Timestamp {
    return Timestamp(this.epochSecond, this.nano)
}

/**
 * Converts Date to Instant
 */
fun Date.toInstant(): Instant {
    return Instant.ofEpochMilli(this.time)
}

/**
 * Converts Instant to Date
 */
fun Instant.toDate(): Date {
    return Date.from(this)
}
