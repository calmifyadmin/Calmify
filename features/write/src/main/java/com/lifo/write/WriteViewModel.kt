package com.lifo.write

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.lifo.mongo.database.ImageToDeleteDao
import com.lifo.mongo.database.ImageToUploadDao
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
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mongodb.kbson.ObjectId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
internal class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageToUploadDao: ImageToUploadDao,
    private val imageToDeleteDao: ImageToDeleteDao
) : ViewModel() {
    private val _selectedGalleryImageIndex = mutableStateOf<Int?>(null)
    private val _isUploadingImages = mutableStateOf(false)
    val isUploadingImages: State<Boolean> = _isUploadingImages

    val galleryState = GalleryState()
    var uiState by mutableStateOf(UiState())
        private set

    init {
        getDiaryIdArgument()
        fetchSelectedDiary()
    }

    var selectedImageIndex by mutableStateOf<Int?>(null)
        private set

    fun onImageSelected(index: Int) {
        selectedImageIndex = index
        // Qui puoi fare qualcosa con l'indice dell'immagine selezionata,
        // come mostrare l'immagine in una vista ingrandita o qualcosa del genere.
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
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(description = description)
    }

    private fun setMood(mood: Mood) {
        uiState = uiState.copy(mood = mood)
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
            uploadImageToFirebase()
            deleteImagesFromFirebase()
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
        val remoteImagePath = "images/${FirebaseAuth.getInstance().currentUser?.uid}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"

        galleryState.addImage(
            GalleryImage(
                image = image,
                remoteImagePath = remoteImagePath,
                isLoading = true // Imposta il caricamento a true
            )
        )
        uploadImageToFirebase()
    }

    // Aggiungi una funzione per controllare se tutte le immagini sono state caricate
    fun areAllImagesUploaded(): Boolean {
        return galleryState.images.all { !it.isLoading }
    }

    private fun uploadImageToFirebase() {
        _isUploadingImages.value = true
        val storage = FirebaseStorage.getInstance().reference
        val imagesCopy = galleryState.images.map { it.copy() }
        imagesCopy.forEach { galleryImage ->
            val imagePath = storage.child(galleryImage.remoteImagePath)
            imagePath.putFile(galleryImage.image)
                .addOnSuccessListener {
                    // Caricamento riuscito, possibilmente aggiorna l'interfaccia utente o il database
                    viewModelScope.launch(Dispatchers.IO) {
                        // Assumi che questa sia la tua funzione per rimuovere l'immagine dalla coda di caricamento
                        imageToUploadDao.deleteImageToUpload(galleryImage.remoteImagePath)
                        updateImageLoadingState(galleryImage, false)
                    }

                    updateImageLoadingState(galleryImage, false)
                }
                .addOnFailureListener { exception ->
                    // Caricamento fallito, gestisci l'errore qui
                    viewModelScope.launch(Dispatchers.Main) {
                        // Aggiorna lo stato dell'UI per mostrare un messaggio di errore
                        // Aggiungi qui il codice per mostrare un messaggio di errore all'utente, es. tramite Toast

                        updateImageLoadingState(galleryImage, false)
                    }

                    updateImageLoadingState(galleryImage, false)
                }
                .addOnCompleteListener {
                    // Aggiorna lo stato dell'UI per mostrare un messaggio di errore
                    // Aggiungi qui il codice per mostrare un messaggio di errore all'utente, es. tramite Toast
                    updateImageLoadingState(galleryImage, false)
                }
                .addOnProgressListener {
                    val sessionUri = it.uploadSessionUri
                    if (sessionUri != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToUploadDao.addImageToUpload(
                                ImageToUpload(
                                    remoteImagePath = galleryImage.remoteImagePath,
                                    imageUri = galleryImage.image.toString(),
                                    sessionUri = sessionUri.toString()
                                )

                            )

                            updateImageLoadingState(galleryImage, false)
                        }

                        updateImageLoadingState(galleryImage, false)
                        updateImageLoadingState(galleryImage, false)
                    }
                }

            updateImageLoadingState(galleryImage, false)
        }

        _isUploadingImages.value = false
    }

    private fun handleUploadError(exception: Exception, galleryImage: GalleryImage) {
        // Log the error
        Log.e("UploadImageToFirebase", "Upload failed for image: ${galleryImage.image}", exception)

        // Show a message to the user
        // You can use any method to show the message, such as Toast or Snackbar
        // For example: Toast.makeText(context, "Upload failed for image: ${galleryImage.image}", Toast.LENGTH_SHORT).show()

        // Retry the upload if necessary
        // For example: uploadImageToFirebase(galleryImage)
    }

    private fun deleteImagesFromFirebase(images: List<String>? = null) {
        val storage = FirebaseStorage.getInstance().reference
        images?.forEach { remotePath ->
            val imageRef = storage.child(remotePath)
            imageRef.getDownloadUrl().addOnSuccessListener {
                // File esiste, procedi con l'eliminazione
                imageRef.delete().addOnFailureListener {
                    // Gestisci il fallimento dell'eliminazione
                }
            }.addOnFailureListener {
                // File non esiste, gestisci l'assenza del file
            }
        }
        if (images != null) {
            images.forEach { remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener {
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(remoteImagePath = remotePath)
                            )
                        }
                    }
            }
        } else {
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

    private fun updateImageLoadingState(galleryImage: GalleryImage, isLoading: Boolean): Boolean {
        val imageToUpdate = galleryState.images.find { it == galleryImage }

        return if (imageToUpdate != null) {
            // Aggiungi un controllo per verificare se lo stato di caricamento è già impostato sul valore desiderato
            if (imageToUpdate.isLoading != isLoading) {
                imageToUpdate.isLoading = isLoading
            }
            true
        } else {
            // Log a warning instead of throwing an exception
            Log.w("UpdateImageLoadingState", "Image not found in galleryState.images")
            false
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
