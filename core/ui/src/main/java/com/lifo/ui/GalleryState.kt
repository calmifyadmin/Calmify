package com.lifo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

@Composable
fun rememberGalleryState(): GalleryState {
    return remember { GalleryState() }
}

class GalleryState {
    var images = mutableStateListOf<GalleryImage>()
    val imagesToBeDeleted = mutableStateListOf<GalleryImage>()

    fun addImage(galleryImage: GalleryImage) {
        images.add(galleryImage)
    }

    fun removeImage(galleryImage: GalleryImage) {
        images.remove(galleryImage)
        imagesToBeDeleted.add(galleryImage)
    }

    fun clearImagesToBeDeleted(){
        imagesToBeDeleted.clear()
    }
}

/**
 * A class that represents a single Image within a Gallery.
 * @param image Image URL stored as String (Uri.toString()) for KMP compatibility.
 * @param remoteImagePath The path of the [image] where you plan to upload it.
 */
data class GalleryImage(
    val image: String,
    val remoteImagePath: String,
    var isLoading: Boolean = false,
    val localFilePath: String? = null
)