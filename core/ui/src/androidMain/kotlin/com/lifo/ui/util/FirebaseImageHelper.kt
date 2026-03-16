package com.lifo.ui.util

import com.google.firebase.storage.FirebaseStorage

/**
 * Download images from Firebase asynchronously.
 * Calls [onImageDownload] with the download URL as a String after each successful download.
 */
fun fetchImagesFromFirebase(
    remoteImagePaths: List<String>,
    onImageDownload: (String) -> Unit,
    onImageDownloadFailed: (Exception) -> Unit = {},
    onReadyToDisplay: () -> Unit = {}
) {
    if (remoteImagePaths.isNotEmpty()) {
        remoteImagePaths.forEachIndexed { index, remoteImagePath ->
            if (remoteImagePath.trim().isNotEmpty()) {
                FirebaseStorage.getInstance().reference.child(remoteImagePath.trim()).downloadUrl
                    .addOnSuccessListener {
                        onImageDownload(it.toString())
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
