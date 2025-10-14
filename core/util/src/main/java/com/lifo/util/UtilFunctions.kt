package com.lifo.util

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import java.time.Instant
import java.util.Date

/**
 * Download images from Firebase asynchronously.
 * This function returns imageUri after each successful download.
 */
fun fetchImagesFromFirebase(
    remoteImagePaths: List<String>,
    onImageDownload: (Uri) -> Unit,
    onImageDownloadFailed: (Exception) -> Unit = {},
    onReadyToDisplay: () -> Unit = {}
) {
    if (remoteImagePaths.isNotEmpty()) {
        remoteImagePaths.forEachIndexed { index, remoteImagePath ->
            if (remoteImagePath.trim().isNotEmpty()) {
                FirebaseStorage.getInstance().reference.child(remoteImagePath.trim()).downloadUrl
                    .addOnSuccessListener {
                        Log.d("DownloadURL", "$it")
                        onImageDownload(it)
                        if (remoteImagePaths.lastIndexOf(remoteImagePaths.last()) == index) {
                            onReadyToDisplay()
                        }
                    }.addOnFailureListener {
                        onImageDownloadFailed(it)
                    }
            }
        }
    }
}

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
