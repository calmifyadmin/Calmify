package com.lifo.write

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lifo.mongo.database.dao.ImageToDeleteDao
import com.lifo.mongo.database.dao.ImageToUploadDao
import com.lifo.mongo.database.entity.ImageToDelete
import com.lifo.mongo.database.entity.ImageToUpload
import com.lifo.util.repository.MongoRepository
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.lifo.ui.util.fetchImagesFromFirebase
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.util.Date
import javax.inject.Inject

@HiltViewModel
internal class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val imageToUploadDao: ImageToUploadDao,
    private val imageToDeleteDao: ImageToDeleteDao,
    private val diaryRepository: MongoRepository,
    private val context: Application
) : ViewModel() {
    private val _selectedGalleryImageIndex = mutableStateOf<Int?>(null)
    private val _isUploadingImages = mutableStateOf(false)
    val isUploadingImages: State<Boolean> = _isUploadingImages

    val galleryState = GalleryState()
    var uiState by mutableStateOf(UiState())
        private set

    init {
        getDiaryIdArgument()

        // Se non c'è un diaryId, pulisci tutto lo stato salvato
        // (nuovo diary da zero)
        if (uiState.selectedDiaryId == null) {
            println("[WriteViewModel] No diaryId - clearing saved state for new diary")
            savedStateHandle.remove<String>("saved_title")
            savedStateHandle.remove<String>("saved_description")
            savedStateHandle.remove<String>("saved_mood")
        } else {
            // Tenta di ripristinare lo stato se è stato salvato precedentemente
            savedStateHandle.get<String>("saved_title")?.let {
                uiState = uiState.copy(title = it)
            }
            savedStateHandle.get<String>("saved_description")?.let {
                uiState = uiState.copy(description = it)
            }
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
        val diaryId = savedStateHandle.get<String>(key = WRITE_SCREEN_ARGUMENT_KEY)
        println("[WriteViewModel] getDiaryIdArgument: diaryId = $diaryId")
        uiState = uiState.copy(selectedDiaryId = diaryId)
    }

    private fun fetchSelectedDiary() {
        if (uiState.selectedDiaryId != null) {
            viewModelScope.launch {
                diaryRepository.getSelectedDiary(diaryId = uiState.selectedDiaryId!!)
                    .catch {
                        emit(RequestState.Error(Exception("Diary is already deleted.")))
                    }
                    .collect { diary ->
                        if (diary is RequestState.Success) {
                            setMood(mood = Mood.valueOf(diary.data.mood))
                            setSelectedDiary(diary = diary.data)
                            setTitle(title = diary.data.title)
                            setDescription(description = diary.data.description)

                            // Load psychological metrics
                            setPsychologicalMetrics(
                                emotionIntensity = diary.data.emotionIntensity,
                                stressLevel = diary.data.stressLevel,
                                energyLevel = diary.data.energyLevel,
                                calmAnxietyLevel = diary.data.calmAnxietyLevel,
                                primaryTrigger = com.lifo.util.model.Trigger.valueOf(diary.data.primaryTrigger),
                                dominantBodySensation = com.lifo.util.model.BodySensation.valueOf(diary.data.dominantBodySensation)
                            )

                            fetchImagesFromFirebase(
                                remoteImagePaths = diary.data.images,
                                onImageDownload = { downloadedImage ->
                                    galleryState.addImage(
                                        GalleryImage(
                                            image = downloadedImage,
                                            remoteImagePath = extractImagePath(
                                                fullImageUrl = downloadedImage
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

    fun setEmotionIntensity(intensity: Int) {
        uiState = uiState.copy(emotionIntensity = intensity)
    }

    fun setStressLevel(level: Int) {
        uiState = uiState.copy(stressLevel = level)
    }

    fun setEnergyLevel(level: Int) {
        uiState = uiState.copy(energyLevel = level)
    }

    fun setCalmAnxietyLevel(level: Int) {
        uiState = uiState.copy(calmAnxietyLevel = level)
    }

    fun setPrimaryTrigger(trigger: com.lifo.util.model.Trigger) {
        uiState = uiState.copy(primaryTrigger = trigger)
    }

    fun setDominantBodySensation(sensation: com.lifo.util.model.BodySensation) {
        uiState = uiState.copy(dominantBodySensation = sensation)
    }

    // Wizard state management
    fun openMetricsWizard() {
        uiState = uiState.copy(showMetricsWizard = true)
    }

    fun closeMetricsWizard() {
        uiState = uiState.copy(showMetricsWizard = false)
    }

    fun completeMetricsWizard() {
        uiState = uiState.copy(
            showMetricsWizard = false,
            metricsCompleted = true
        )
    }

    private fun setPsychologicalMetrics(
        emotionIntensity: Int,
        stressLevel: Int,
        energyLevel: Int,
        calmAnxietyLevel: Int,
        primaryTrigger: com.lifo.util.model.Trigger,
        dominantBodySensation: com.lifo.util.model.BodySensation
    ) {
        uiState = uiState.copy(
            emotionIntensity = emotionIntensity,
            stressLevel = stressLevel,
            energyLevel = energyLevel,
            calmAnxietyLevel = calmAnxietyLevel,
            primaryTrigger = primaryTrigger,
            dominantBodySensation = dominantBodySensation
        )
    }

    @SuppressLint("NewApi")
    fun updateDateTime(zonedDateTime: ZonedDateTime) {
        uiState = uiState.copy(updatedDateTime = Date.from(zonedDateTime.toInstant()))
    }

    fun upsertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            println("[WriteViewModel] upsertDiary: selectedDiaryId = ${uiState.selectedDiaryId}, selectedDiary = ${uiState.selectedDiary?._id}")
            if (uiState.selectedDiaryId != null) {
                println("[WriteViewModel] Calling updateDiary")
                updateDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            } else {
                println("[WriteViewModel] Calling insertDiary")
                insertDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            }
        }
    }

    private suspend fun insertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val result = diaryRepository.insertDiary(diary = diary.apply {
            if (uiState.updatedDateTime != null) {
                date = uiState.updatedDateTime!!
            }
            // Set dayKey and timezone from current device context
            val zoneId = java.time.ZoneId.systemDefault()
            dayKey = date.toInstant()
                .atZone(zoneId)
                .toLocalDate()
                .toString() // "YYYY-MM-DD"
            timezone = zoneId.id // e.g., "Europe/Rome"
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
        val result = diaryRepository.updateDiary(diary = diary.apply {
            _id = uiState.selectedDiaryId!!
            // IMPORTANTE: Mantieni l'ownerId del diary originale!
            ownerId = uiState.selectedDiary?.ownerId ?: auth.currentUser?.uid ?: ""
            date = when {
                uiState.updatedDateTime != null -> uiState.updatedDateTime!!
                uiState.selectedDiary != null -> uiState.selectedDiary!!.date
                else -> Date() // Fallback to current date
            }
            // Set dayKey and timezone from current device context
            val zoneId = java.time.ZoneId.systemDefault()
            dayKey = date.toInstant()
                .atZone(zoneId)
                .toLocalDate()
                .toString() // "YYYY-MM-DD"
            timezone = zoneId.id // e.g., "Europe/Rome"
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
                val result = diaryRepository.deleteDiary(id = uiState.selectedDiaryId!!)
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
        println("[WriteViewModel] Adding image: $image")

        // Add permission persistence
        try {
            // Take persistent URI permission
            val contentResolver = context.contentResolver
            contentResolver.takePersistableUriPermission(
                image,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            println("[WriteViewModel] ERROR: Failed to take persistent permission: ${e.message}")
            // Continue anyway, as we might still be able to use the URI in some cases
        }

        val remoteImagePath = "images/${auth.currentUser?.uid}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"

        // Create the gallery image
        val newImage = GalleryImage(
            image = image.toString(),
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

            println("[WriteViewModel] Image copied to local storage: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            println("[WriteViewModel] ERROR: Failed to copy image to local storage: ${e.message}")
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
        val storageRef = storage.reference

        // Create a copy of the images list to avoid concurrent modification issues
        val imagesToUpload = galleryState.images.filter { it.isLoading }.toList()

        println("[WriteViewModel] Uploading ${imagesToUpload.size} images")
        if (imagesToUpload.isEmpty()) {
            _isUploadingImages.value = false
            return
        }

        var completedUploads = 0

        // Process each image that is still in loading state
        imagesToUpload.forEach { galleryImage ->
            val imagePath = storageRef.child(galleryImage.remoteImagePath)

            // Use local file if available
            val fileToUpload = galleryImage.localFilePath?.let { File(it) }

            val uploadTask = if (fileToUpload != null && fileToUpload.exists()) {
                // Upload from local file
                imagePath.putFile(Uri.fromFile(fileToUpload))
            } else {
                try {
                    // Try using the original URI but this might fail
                    imagePath.putFile(Uri.parse(galleryImage.image))
                } catch (e: SecurityException) {
                    println("[WriteViewModel] ERROR: Security exception while uploading, skipping: ${e.message}")
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
                            println("[WriteViewModel] Image upload successful: ${galleryImage.remoteImagePath}")
                        }

                        // Update completion counter
                        completedUploads++
                        if (completedUploads >= imagesToUpload.size) {
                            _isUploadingImages.value = false
                        }
                    }
                }
            }.addOnFailureListener { exception ->
                println("[WriteViewModel] ERROR: Upload failed for ${galleryImage.remoteImagePath}: ${exception.message}")

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
                    println("[WriteViewModel] Upload progress for ${galleryImage.remoteImagePath}: $progress%")
                }

                if (sessionUri != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        // Store upload session for possible resuming later
                        imageToUploadDao.addImageToUpload(
                            ImageToUpload(
                                remoteImagePath = galleryImage.remoteImagePath,
                                imageUri = galleryImage.image,
                                sessionUri = sessionUri.toString()
                            )
                        )
                    }
                }
            }.addOnCanceledListener {
                println("[WriteViewModel] WARN: Upload canceled for ${galleryImage.remoteImagePath}")

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
        val storageRef = storage.reference

        // Handle specified images
        if (images != null) {
            images.forEach { remotePath ->
                val imageRef = storageRef.child(remotePath)
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
                    println("[WriteViewModel] WARN: File doesn't exist for deletion: $remotePath")
                }
            }
        } else {
            // Handle images marked for deletion in galleryState
            galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach { remotePath ->
                storageRef.child(remotePath).delete()
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
        return "images/${auth.currentUser?.uid}/$imageName"
    }

    private fun updateImageLoadingState(galleryImage: GalleryImage, isLoading: Boolean) {
        val index = galleryState.images.indexOfFirst { it.remoteImagePath == galleryImage.remoteImagePath }
        if (index >= 0) {
            // Create a new instance with the updated loading state
            val updatedImage = galleryState.images[index].copy(isLoading = isLoading)
            // Replace the old instance in the mutableStateList
            galleryState.images[index] = updatedImage

            println("[WriteViewModel] Updated image loading state: ${galleryImage.remoteImagePath}, isLoading: $isLoading")
        } else {
            println("[WriteViewModel] WARN: Image not found in galleryState.images: ${galleryImage.remoteImagePath}")
        }
    }
}

internal data class UiState(
    val selectedDiaryId: String? = null,
    val selectedDiary: Diary? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val updatedDateTime: Date? = null,
    // Psychological metrics
    val emotionIntensity: Int = 5,
    val stressLevel: Int = 5,
    val energyLevel: Int = 5,
    val calmAnxietyLevel: Int = 5,
    val primaryTrigger: com.lifo.util.model.Trigger = com.lifo.util.model.Trigger.NONE,
    val dominantBodySensation: com.lifo.util.model.BodySensation = com.lifo.util.model.BodySensation.NONE,
    // Wizard state
    val showMetricsWizard: Boolean = false,
    val metricsCompleted: Boolean = false
)