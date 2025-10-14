package com.lifo.util.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.util.Date

/**
 * Diary Model - Firestore Compatible
 *
 * Migrato da Realm a Firestore (2025 Stack)
 * No more RealmObject - now a standard Kotlin data class
 */
data class Diary(
    @DocumentId
    var _id: String = "",
    var ownerId: String = "",
    var mood: String = Mood.Neutral.name,
    var title: String = "",
    var description: String = "",
    var images: List<String> = emptyList(),
    @ServerTimestamp
    var date: Date = Date.from(Instant.now())
) {
    // No-arg constructor richiesto da Firestore
    constructor() : this(
        _id = "",
        ownerId = "",
        mood = Mood.Neutral.name,
        title = "",
        description = "",
        images = emptyList(),
        date = Date.from(Instant.now())
    )

    companion object {
        fun fromTimestamp(timestamp: Timestamp): Instant {
            return Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
        }
    }
}
