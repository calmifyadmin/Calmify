package com.lifo.write

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.lifo.mongo.database.dao.ImageToDeleteDao
import com.lifo.mongo.database.dao.ImageToUploadDao
import com.lifo.mongo.database.entity.ImageToDelete
import com.lifo.mongo.database.entity.ImageToUpload
import com.lifo.mongo.repository.MongoDB
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.lifo.util.fetchImagesFromFirebase
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.util.model.RequestState
import com.lifo.util.toRealmInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
internal class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageToUploadDao: ImageToUploadDao,
    private val imageToDeleteDao: ImageToDeleteDao,
    private val context: Application // Aggiungi questo parametro
) : ViewModel() {
    private val _selectedGalleryImageIndex = mutableStateOf<Int?>(null)
    private val _isUploadingImages = mutableStateOf(false)
    val isUploadingImages: State<Boolean> = _isUploadingImages

    val galleryState = GalleryState()
    var uiState by mutableStateOf(UiState())
        private set

    init {
        getDiaryIdArgument()
        // Tenta di ripristinare lo stato se è stato salvato precedentemente
        savedStateHandle.get<String>("saved_title")?.let {
            uiState = uiState.copy(title = it)
        }
        savedStateHandle.get<String>("saved_description")?.let {
            uiState = uiState.copy(description = it)
        }
        // Poi procedi con il normale caricamento
        fetchSelectedDiary()
    }

    // Derived state to track image counts for UI updates
    val displayImageCount = derivedStateOf {
        galleryState.images.size
    }

    val pendingImageCount = derivedStateOf {
        galleryState.images.count { it.isLoading }
    }

    // Image count excluding those marked for deletion - helps with counter display issue
    private val _imageCount = derivedStateOf {
        galleryState.images.size - galleryState.imagesToBeDeleted.size
    }
    val imageCount: State<Int> = _imageCount

    var selectedImageIndex by mutableStateOf<Int?>(null)
        private set

    fun onImageSelected(index: Int) {
        selectedImageIndex = index
    }

    private fun getDiaryIdArgument() {
        uiState = uiState.copy(
            selectedDiaryId = savedStateHandle.get<String>(
                key = WRITE_SCREEN_ARGUMENT_KEY
            )
        )
    }

    private fun fetchSelectedDiary() {
        if (uiState.selectedDiaryId != null) {
            viewModelScope.launch {
                MongoDB.getSelectedDiary(diaryId = ObjectId.invoke(uiState.selectedDiaryId!!))
                    .catch {
                        emit(RequestState.Error(Exception("Diary is already deleted.")))
                    }
                    .collect { diary ->
                        if (diary is RequestState.Success) {
                            setMood(mood = Mood.valueOf(diary.data.mood))
                            setSelectedDiary(diary = diary.data)
                            setTitle(title = diary.data.title)
                            setDescription(description = diary.data.description)

                            fetchImagesFromFirebase(
                                remoteImagePaths = diary.data.images,
                                onImageDownload = { downloadedImage ->
                                    galleryState.addImage(
                                        GalleryImage(
                                            image = downloadedImage,
                                            remoteImagePath = extractImagePath(
                                                fullImageUrl = downloadedImage.toString()
                                            ),
                                        )
                                    )
                                }
                            )
                        }
                    }
            }
        }
    }

    private fun setSelectedDiary(diary: Diary) {
        uiState = uiState.copy(selectedDiary = diary)
    }

    fun setTitle(title: String) {
        uiState = uiState.copy(title = title)
        // Salva lo stato
        savedStateHandle["saved_title"] = title
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
        // Salva lo stato
        savedStateHandle["saved_description"] = description
    }

    private fun setMood(mood: Mood) {
        uiState = uiState.copy(mood = mood)

        savedStateHandle["saved_mood"] = mood
    }

    @SuppressLint("NewApi")
    fun updateDateTime(zonedDateTime: ZonedDateTime) {
        uiState = uiState.copy(updatedDateTime = zonedDateTime.toInstant().toRealmInstant())
    }

    fun upsertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.selectedDiaryId != null) {
                updateDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            } else {
                insertDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            }
        }
    }

    private suspend fun insertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val result = MongoDB.insertDiary(diary = diary.apply {
            if (uiState.updatedDateTime != null) {
                date = uiState.updatedDateTime!!
            }
        })
        if (result is RequestState.Success) {
            uploadImageToFirebase()
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources
        viewModelScope.cancel()
    }

    private suspend fun updateDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = MongoDB.updateDiary(diary = diary.apply {
            _id = ObjectId.invoke(uiState.selectedDiaryId!!)
            date = if (uiState.updatedDateTime != null) {
                uiState.updatedDateTime!!
            } else {
                uiState.selectedDiary!!.date
            }
        })
        if (result is RequestState.Success) {
            // Upload and delete images
            uploadImageToFirebase()
            deleteImagesFromFirebase()

            // Clear the images to be deleted list after successful update
            galleryState.clearImagesToBeDeleted()

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    fun deleteDiary(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.selectedDiaryId != null) {
                val result = MongoDB.deleteDiary(id = ObjectId.invoke(uiState.selectedDiaryId!!))
                if (result is RequestState.Success) {
                    withContext(Dispatchers.Main) {
                        uiState.selectedDiary?.let {
                            deleteImagesFromFirebase(images = it.images)
                        }
                        onSuccess()
                    }
                } else if (result is RequestState.Error) {
                    withContext(Dispatchers.Main) {
                        onError(result.error.message.toString())
                    }
                }
            }
        }
    }

    fun addImage(image: Uri, imageType: String) {
        Log.d("WriteViewModel", "Adding image: $image")

        // Add permission persistence
        try {
            // Take persistent URI permission
            val contentResolver = context.contentResolver
            contentResolver.takePersistableUriPermission(
                image,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.e("WriteViewModel", "Failed to take persistent permission", e)
            // Continue anyway, as we might still be able to use the URI in some cases
        }

        val remoteImagePath = "images/${FirebaseAuth.getInstance().currentUser?.uid}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"

        // Create the gallery image
        val newImage = GalleryImage(
            image = image,
            remoteImagePath = remoteImagePath,
            isLoading = true
        )

        // Add to gallery state
        galleryState.addImage(newImage)

        // Start upload
        uploadImageToFirebase()
    }
    private fun copyImageToAppStorage(uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val fileName = "${System.currentTimeMillis()}.jpg"
            val outputFile = File(context.filesDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d("WriteViewModel", "Image copied to local storage: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("WriteViewModel", "Failed to copy image to local storage", e)
            null
        }
    }
    // Function to check if all images are uploaded - used for Save button state
    fun areAllImagesUploaded(): Boolean {
        return galleryState.images.all { !it.isLoading }
    }

    // Main image upload function - handles all images that need uploading
    private fun uploadImageToFirebase() {
        _isUploadingImages.value = true
        val storage = FirebaseStorage.getInstance().reference

        // Create a copy of the images list to avoid concurrent modification issues
        val imagesToUpload = galleryState.images.filter { it.isLoading }.toList()

        Log.d("WriteViewModel", "Uploading ${imagesToUpload.size} images")
        if (imagesToUpload.isEmpty()) {
            _isUploadingImages.value = false
            return
        }

        var completedUploads = 0

        // Process each image that is still in loading state
        imagesToUpload.forEach { galleryImage ->
            val imagePath = storage.child(galleryImage.remoteImagePath)

            // Use local file if available
            val fileToUpload = galleryImage.localFilePath?.let { File(it) }

            val uploadTask = if (fileToUpload != null && fileToUpload.exists()) {
                // Upload from local file
                imagePath.putFile(Uri.fromFile(fileToUpload))
            } else {
                try {
                    // Try using the original URI but this might fail
                    imagePath.putFile(galleryImage.image)
                } catch (e: SecurityException) {
                    Log.e("WriteViewModel", "Security exception while uploading, skipping", e)
                    // Mark as failed
                    viewModelScope.launch(Dispatchers.Main) {
                        val index = galleryState.images.indexOfFirst {
                            it.remoteImagePath == galleryImage.remoteImagePath
                        }
                        if (index >= 0) {
                            galleryState.images[index] = galleryState.images[index].copy(isLoading = false)
                        }
                        completedUploads++
                        if (completedUploads >= imagesToUpload.size) {
                            _isUploadingImages.value = false
                        }
                    }
                    return@forEach
                }
            }

            // Add listeners for upload status
            uploadTask.addOnSuccessListener {
                viewModelScope.launch(Dispatchers.IO) {
                    // Remove from "to upload" database if it was there
                    imageToUploadDao.deleteImageToUpload(galleryImage.remoteImagePath)

                    withContext(Dispatchers.Main) {
                        // Find the image in the current list and update its state
                        val index = galleryState.images.indexOfFirst {
                            it.remoteImagePath == galleryImage.remoteImagePath
                        }
                        if (index >= 0) {
                            val updatedImage = galleryState.images[index].copy(isLoading = false)
                            galleryState.images[index] = updatedImage
                            Log.d("WriteViewModel", "Image upload successful: ${galleryImage.remoteImagePath}")
                        }

                        // Update completion counter
                        completedUploads++
                        if (completedUploads >= imagesToUpload.size) {
                            _isUploadingImages.value = false
                        }
                    }
                }
            }.addOnFailureListener { exception ->
                Log.e("WriteViewModel", "Upload failed for ${galleryImage.remoteImagePath}", exception)

                viewModelScope.launch(Dispatchers.Main) {
                    // Even if it fails, we need to update the UI to show the error state
                    val index = galleryState.images.indexOfFirst {
                        it.remoteImagePath == galleryImage.remoteImagePath
                    }
                    if (index >= 0) {
                        val updatedImage = galleryState.images[index].copy(isLoading = false)
                        galleryState.images[index] = updatedImage
                    }

                    // Update completion counter
                    completedUploads++
                    if (completedUploads >= imagesToUpload.size) {
                        _isUploadingImages.value = false
                    }

                    // Could add retry logic here if needed
                }
            }.addOnProgressListener { taskSnapshot ->
                val sessionUri = taskSnapshot.uploadSessionUri
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()

                // Log progress but don't overload the logs
                if (progress % 20 == 0) {  // Log only at 0%, 20%, 40%, 60%, 80%, 100%
                    Log.d("WriteViewModel", "Upload progress for ${galleryImage.remoteImagePath}: $progress%")
                }

                if (sessionUri != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        // Store upload session for possible resuming later
                        imageToUploadDao.addImageToUpload(
                            ImageToUpload(
                                remoteImagePath = galleryImage.remoteImagePath,
                                imageUri = galleryImage.image.toString(),
                                sessionUri = sessionUri.toString()
                            )
                        )
                    }
                }
            }.addOnCanceledListener {
                Log.w("WriteViewModel", "Upload canceled for ${galleryImage.remoteImagePath}")

                viewModelScope.launch(Dispatchers.Main) {
                    val index = galleryState.images.indexOfFirst {
                        it.remoteImagePath == galleryImage.remoteImagePath
                    }
                    if (index >= 0) {
                        val updatedImage = galleryState.images[index].copy(isLoading = false)
                        galleryState.images[index] = updatedImage
                    }

                    // Update completion counter
                    completedUploads++
                    if (completedUploads >= imagesToUpload.size) {
                        _isUploadingImages.value = false
                    }
                }
            }
        }
    }

    private fun deleteImagesFromFirebase(images: List<String>? = null) {
        val storage = FirebaseStorage.getInstance().reference

        // Handle specified images
        if (images != null) {
            images.forEach { remotePath ->
                val imageRef = storage.child(remotePath)
                imageRef.getDownloadUrl().addOnSuccessListener {
                    // File exists, proceed with deletion
                    imageRef.delete().addOnFailureListener {
                        // Handle deletion failure
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(remoteImagePath = remotePath)
                            )
                        }
                    }
                }.addOnFailureListener {
                    // File doesn't exist, handle absence
                    Log.w("WriteViewModel", "File doesn't exist for deletion: $remotePath")
                }
            }
        } else {
            // Handle images marked for deletion in galleryState
            galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach { remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(remoteImagePath = remotePath)
                            )
                        }
                    }
            }
        }
    }

    private fun extractImagePath(fullImageUrl: String): String {
        val chunks = fullImageUrl.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${Firebase.auth.currentUser?.uid}/$imageName"
    }

    private fun updateImageLoadingState(galleryImage: GalleryImage, isLoading: Boolean) {
        val index = galleryState.images.indexOfFirst { it.remoteImagePath == galleryImage.remoteImagePath }
        if (index >= 0) {
            // Create a new instance with the updated loading state
            val updatedImage = galleryState.images[index].copy(isLoading = isLoading)
            // Replace the old instance in the mutableStateList
            galleryState.images[index] = updatedImage

            Log.d("WriteViewModel", "Updated image loading state: ${galleryImage.remoteImagePath}, isLoading: $isLoading")
        } else {
            Log.w("WriteViewModel", "Image not found in galleryState.images: ${galleryImage.remoteImagePath}")
        }
    }
}

internal data class UiState(
    val selectedDiaryId: String? = null,
    val selectedDiary: Diary? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val updatedDateTime: RealmInstant? = null
)