package com.lifo.write

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.storage.FirebaseStorage
import com.lifo.util.auth.AuthProvider
import com.lifo.mongo.database.ImageToDeleteQueries
import com.lifo.mongo.database.ImageToUploadQueries
import com.lifo.util.repository.MongoRepository
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.model.BodySensation
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.util.model.RequestState
import com.lifo.util.model.Trigger
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime

// ──────────────────────────────────────────────────────────
// Contract
// ──────────────────────────────────────────────────────────

object WriteContract {

    sealed interface Intent : MviContract.Intent {
        // Text fields
        data class SetTitle(val title: String) : Intent
        data class SetDescription(val description: String) : Intent

        // Mood
        data class SetMood(val mood: Mood) : Intent

        // Date/Time
        data class UpdateDateTime(val zonedDateTime: ZonedDateTime) : Intent

        // Psychological metrics
        data class SetEmotionIntensity(val intensity: Int) : Intent
        data class SetStressLevel(val level: Int) : Intent
        data class SetEnergyLevel(val level: Int) : Intent
        data class SetCalmAnxietyLevel(val level: Int) : Intent
        data class SetPrimaryTrigger(val trigger: Trigger) : Intent
        data class SetDominantBodySensation(val sensation: BodySensation) : Intent

        // Wizard
        data object OpenMetricsWizard : Intent
        data object CloseMetricsWizard : Intent
        data object CompleteMetricsWizard : Intent

        // Smart Capture
        data object RunSmartCapture : Intent

        // View mode
        data object SwitchToEditMode : Intent

        // Image management
        data class AddImage(val image: Uri, val imageType: String) : Intent
        data class OnImageSelected(val index: Int) : Intent

        // CRUD
        data class UpsertDiary(val diary: Diary) : Intent
        data object DeleteDiary : Intent
    }

    data class State(
        val selectedDiaryId: String? = null,
        val selectedDiary: Diary? = null,
        val title: String = "",
        val description: String = "",
        val mood: Mood = Mood.Neutral,
        val updatedDateTimeMillis: Long? = null,
        // Psychological metrics
        val emotionIntensity: Int = 5,
        val stressLevel: Int = 5,
        val energyLevel: Int = 5,
        val calmAnxietyLevel: Int = 5,
        val primaryTrigger: Trigger = Trigger.NONE,
        val dominantBodySensation: BodySensation = BodySensation.NONE,
        // Wizard state
        val showMetricsWizard: Boolean = false,
        val metricsCompleted: Boolean = false,
        // Smart Capture state
        val smartCaptureComplete: Boolean = false,
        // View mode (true when viewing existing diary, false when creating/editing)
        val isViewMode: Boolean = false,
        // Image state
        val isUploadingImages: Boolean = false,
        val selectedImageIndex: Int? = null,
        // Gallery (managed as an object within state for backward compat)
        val galleryState: GalleryState = GalleryState()
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object DiaryUpsertSuccess : Effect
        data object DiaryDeleteSuccess : Effect
        data class ShowError(val message: String) : Effect
    }
}

// ──────────────────────────────────────────────────────────
// Backward-compatible UiState type alias
// ──────────────────────────────────────────────────────────

internal typealias UiState = WriteContract.State

// ──────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────

internal class WriteViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val authProvider: AuthProvider,
    private val storage: FirebaseStorage,
    private val imageToUploadQueries: ImageToUploadQueries,
    private val imageToDeleteQueries: ImageToDeleteQueries,
    private val diaryRepository: MongoRepository,
    private val mediaRepository: MediaUploadRepository,
    private val context: Application
) : MviViewModel<WriteContract.Intent, WriteContract.State, WriteContract.Effect>(
    initialState = WriteContract.State()
) {

    // ── Compose-observable state mirror ─────────────────
    // The MVI base stores state in a StateFlow, but existing UI code reads
    // Compose `mutableStateOf` directly (without collectAsState). We bridge
    // the two by mirroring the StateFlow into a Compose-observable snapshot.

    private var _composeState by mutableStateOf(currentState)

    init {
        // Sync StateFlow -> Compose State so direct reads recompose correctly
        scope.launch {
            state.collect { newState -> _composeState = newState }
        }
    }

    // ── Backward-compatible aliases ──────────────────────

    /**
     * Compose-observable alias. Existing UI code that reads `viewModel.uiState`
     * in a @Composable function will recompose when state changes.
     */
    val uiState: WriteContract.State get() = _composeState

    /** Backward-compatible GalleryState accessor. */
    val galleryState: GalleryState get() = _composeState.galleryState

    /** Compose-observable derived state: total image count displayed. */
    val displayImageCount: androidx.compose.runtime.State<Int> =
        derivedStateOf { _composeState.galleryState.images.size }

    /** Compose-observable derived state: how many images are still uploading. */
    val pendingImageCount: androidx.compose.runtime.State<Int> =
        derivedStateOf { _composeState.galleryState.images.count { it.isLoading } }

    /** Image count excluding those marked for deletion. */
    val imageCount: androidx.compose.runtime.State<Int> =
        derivedStateOf {
            _composeState.galleryState.images.size - _composeState.galleryState.imagesToBeDeleted.size
        }

    /** Compose-observable uploading flag. */
    val isUploadingImages: androidx.compose.runtime.State<Boolean> =
        derivedStateOf { _composeState.isUploadingImages }

    /** Compose-observable selected image index. */
    val selectedImageIndex: Int? get() = _composeState.selectedImageIndex

    // ── Init ─────────────────────────────────────────────

    init {
        getDiaryIdArgument()

        // Se non c'è un diaryId, pulisci tutto lo stato salvato
        // (nuovo diary da zero)
        if (currentState.selectedDiaryId == null) {
            println("[WriteViewModel] No diaryId - clearing saved state for new diary")
            savedStateHandle.remove<String>("saved_title")
            savedStateHandle.remove<String>("saved_description")
            savedStateHandle.remove<String>("saved_mood")
        } else {
            // Tenta di ripristinare lo stato se è stato salvato precedentemente
            savedStateHandle.get<String>("saved_title")?.let { title ->
                updateState { copy(title = title) }
            }
            savedStateHandle.get<String>("saved_description")?.let { desc ->
                updateState { copy(description = desc) }
            }
        }

        // Poi procedi con il normale caricamento
        fetchSelectedDiary()
    }

    // ── MVI Intent Handler ───────────────────────────────

    override fun handleIntent(intent: WriteContract.Intent) {
        when (intent) {
            // Text
            is WriteContract.Intent.SetTitle -> handleSetTitle(intent.title)
            is WriteContract.Intent.SetDescription -> handleSetDescription(intent.description)

            // Mood
            is WriteContract.Intent.SetMood -> updateState { copy(mood = intent.mood) }

            // Date/Time
            is WriteContract.Intent.UpdateDateTime -> handleUpdateDateTime(intent.zonedDateTime)

            // Psychological metrics
            is WriteContract.Intent.SetEmotionIntensity -> updateState { copy(emotionIntensity = intent.intensity) }
            is WriteContract.Intent.SetStressLevel -> updateState { copy(stressLevel = intent.level) }
            is WriteContract.Intent.SetEnergyLevel -> updateState { copy(energyLevel = intent.level) }
            is WriteContract.Intent.SetCalmAnxietyLevel -> updateState { copy(calmAnxietyLevel = intent.level) }
            is WriteContract.Intent.SetPrimaryTrigger -> updateState { copy(primaryTrigger = intent.trigger) }
            is WriteContract.Intent.SetDominantBodySensation -> updateState { copy(dominantBodySensation = intent.sensation) }

            // Wizard
            WriteContract.Intent.OpenMetricsWizard -> updateState { copy(showMetricsWizard = true) }
            WriteContract.Intent.CloseMetricsWizard -> updateState { copy(showMetricsWizard = false) }
            WriteContract.Intent.CompleteMetricsWizard -> updateState { copy(showMetricsWizard = false, metricsCompleted = true) }

            // Smart Capture
            WriteContract.Intent.RunSmartCapture -> handleSmartCapture()

            // View mode
            WriteContract.Intent.SwitchToEditMode -> updateState { copy(isViewMode = false) }

            // Images
            is WriteContract.Intent.AddImage -> handleAddImage(intent.image, intent.imageType)
            is WriteContract.Intent.OnImageSelected -> updateState { copy(selectedImageIndex = intent.index) }

            // CRUD
            is WriteContract.Intent.UpsertDiary -> handleUpsertDiary(intent.diary)
            WriteContract.Intent.DeleteDiary -> handleDeleteDiary()
        }
    }

    // ── Backward-compatible public function wrappers ─────

    fun setTitle(title: String) = onIntent(WriteContract.Intent.SetTitle(title))
    fun setDescription(description: String) = onIntent(WriteContract.Intent.SetDescription(description))
    @SuppressLint("NewApi")
    fun updateDateTime(zonedDateTime: ZonedDateTime) = onIntent(WriteContract.Intent.UpdateDateTime(zonedDateTime))
    fun setEmotionIntensity(intensity: Int) = onIntent(WriteContract.Intent.SetEmotionIntensity(intensity))
    fun setStressLevel(level: Int) = onIntent(WriteContract.Intent.SetStressLevel(level))
    fun setEnergyLevel(level: Int) = onIntent(WriteContract.Intent.SetEnergyLevel(level))
    fun setCalmAnxietyLevel(level: Int) = onIntent(WriteContract.Intent.SetCalmAnxietyLevel(level))
    fun setPrimaryTrigger(trigger: Trigger) = onIntent(WriteContract.Intent.SetPrimaryTrigger(trigger))
    fun setDominantBodySensation(sensation: BodySensation) = onIntent(WriteContract.Intent.SetDominantBodySensation(sensation))
    fun openMetricsWizard() = onIntent(WriteContract.Intent.OpenMetricsWizard)
    fun closeMetricsWizard() = onIntent(WriteContract.Intent.CloseMetricsWizard)
    fun completeMetricsWizard() = onIntent(WriteContract.Intent.CompleteMetricsWizard)
    fun runSmartCapture() = onIntent(WriteContract.Intent.RunSmartCapture)
    fun switchToEditMode() = onIntent(WriteContract.Intent.SwitchToEditMode)
    fun addImage(image: Uri, imageType: String) = onIntent(WriteContract.Intent.AddImage(image, imageType))
    fun onImageSelected(index: Int) = onIntent(WriteContract.Intent.OnImageSelected(index))

    /**
     * Legacy callback-based upsert. Calls the internal logic directly
     * so callers do not need to collect [effects].
     * New UI code should prefer [onIntent] with [WriteContract.Intent.UpsertDiary]
     * and collect effects.
     */
    fun upsertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            println("[WriteViewModel] upsertDiary: selectedDiaryId = ${currentState.selectedDiaryId}, selectedDiary = ${currentState.selectedDiary?._id}")
            if (currentState.selectedDiaryId != null) {
                println("[WriteViewModel] Calling updateDiary")
                updateDiaryLegacy(diary = diary, onSuccess = onSuccess, onError = onError)
            } else {
                println("[WriteViewModel] Calling insertDiary")
                insertDiaryLegacy(diary = diary, onSuccess = onSuccess, onError = onError)
            }
        }
    }

    /**
     * Legacy callback-based delete.
     * New UI code should prefer [onIntent] with [WriteContract.Intent.DeleteDiary]
     * and collect effects.
     */
    fun deleteDiary(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            if (currentState.selectedDiaryId != null) {
                val result = diaryRepository.deleteDiary(id = currentState.selectedDiaryId!!)
                if (result is RequestState.Success) {
                    withContext(Dispatchers.Main) {
                        currentState.selectedDiary?.let {
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

    fun areAllImagesUploaded(): Boolean {
        return currentState.galleryState.images.all { !it.isLoading }
    }

    // ── Private intent handlers ──────────────────────────

    private fun handleSetTitle(title: String) {
        updateState { copy(title = title) }
        savedStateHandle["saved_title"] = title
    }

    private fun handleSetDescription(description: String) {
        updateState { copy(description = description) }
        savedStateHandle["saved_description"] = description
    }

    @SuppressLint("NewApi")
    private fun handleUpdateDateTime(zonedDateTime: ZonedDateTime) {
        updateState { copy(updatedDateTimeMillis = zonedDateTime.toInstant().toEpochMilli()) }
    }

    private fun handleSmartCapture() {
        val s = currentState
        if (s.title.isBlank() && s.description.isBlank()) return
        val inferred = TextAnalyzer.analyze(s.title, s.description)
        updateState {
            copy(
                mood = inferred.mood,
                emotionIntensity = inferred.emotionIntensity,
                stressLevel = inferred.stressLevel,
                energyLevel = inferred.energyLevel,
                calmAnxietyLevel = inferred.calmAnxietyLevel,
                primaryTrigger = inferred.trigger,
                dominantBodySensation = inferred.bodySensation,
                smartCaptureComplete = true,
                metricsCompleted = true
            )
        }
    }

    private fun handleAddImage(image: Uri, imageType: String) {
        println("[WriteViewModel] Adding image: $image")

        // Add permission persistence
        try {
            val contentResolver = context.contentResolver
            contentResolver.takePersistableUriPermission(
                image,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            println("[WriteViewModel] ERROR: Failed to take persistent permission: ${e.message}")
        }

        val remoteImagePath = "images/${authProvider.currentUserId}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"

        val newImage = GalleryImage(
            image = image.toString(),
            remoteImagePath = remoteImagePath,
            isLoading = true
        )

        currentState.galleryState.addImage(newImage)

        // Start upload
        uploadImageToFirebase()
    }

    private fun handleUpsertDiary(diary: Diary) {
        scope.launch(Dispatchers.IO) {
            println("[WriteViewModel] upsertDiary: selectedDiaryId = ${currentState.selectedDiaryId}, selectedDiary = ${currentState.selectedDiary?._id}")
            if (currentState.selectedDiaryId != null) {
                println("[WriteViewModel] Calling updateDiary")
                updateDiary(diary = diary)
            } else {
                println("[WriteViewModel] Calling insertDiary")
                insertDiary(diary = diary)
            }
        }
    }

    private fun handleDeleteDiary() {
        scope.launch(Dispatchers.IO) {
            if (currentState.selectedDiaryId != null) {
                val result = diaryRepository.deleteDiary(id = currentState.selectedDiaryId!!)
                if (result is RequestState.Success) {
                    withContext(Dispatchers.Main) {
                        currentState.selectedDiary?.let {
                            deleteImagesFromFirebase(images = it.images)
                        }
                        sendEffect(WriteContract.Effect.DiaryDeleteSuccess)
                    }
                } else if (result is RequestState.Error) {
                    withContext(Dispatchers.Main) {
                        sendEffect(WriteContract.Effect.ShowError(result.error.message.toString()))
                    }
                }
            }
        }
    }

    // ── Decompose entry point ────────────────────────────

    /**
     * Allows the Decompose entry point to supply a diaryId that was previously
     * provided via Navigation Compose's SavedStateHandle.
     * Only call this if the diaryId was NOT already picked up from SavedStateHandle
     * (i.e. selectedDiaryId is still null after init).
     */
    internal fun setDiaryIdAndLoad(diaryId: String?) {
        if (diaryId != null && currentState.selectedDiaryId == null) {
            updateState { copy(selectedDiaryId = diaryId, isViewMode = true) }
            fetchSelectedDiary()
        }
    }

    // ── Private business logic ───────────────────────────

    private fun getDiaryIdArgument() {
        val diaryId = savedStateHandle.get<String>(key = WRITE_SCREEN_ARGUMENT_KEY)
        println("[WriteViewModel] getDiaryIdArgument: diaryId = $diaryId")
        updateState { copy(selectedDiaryId = diaryId, isViewMode = diaryId != null) }
    }

    private fun fetchSelectedDiary() {
        if (currentState.selectedDiaryId != null) {
            scope.launch {
                diaryRepository.getSelectedDiary(diaryId = currentState.selectedDiaryId!!)
                    .catch {
                        emit(RequestState.Error(Exception("Diary is already deleted.")))
                    }
                    .collect { diary ->
                        if (diary is RequestState.Success) {
                            updateState {
                                copy(
                                    mood = Mood.valueOf(diary.data.mood),
                                    selectedDiary = diary.data,
                                    title = diary.data.title,
                                    description = diary.data.description,
                                    emotionIntensity = diary.data.emotionIntensity,
                                    stressLevel = diary.data.stressLevel,
                                    energyLevel = diary.data.energyLevel,
                                    calmAnxietyLevel = diary.data.calmAnxietyLevel,
                                    primaryTrigger = Trigger.valueOf(diary.data.primaryTrigger),
                                    dominantBodySensation = BodySensation.valueOf(diary.data.dominantBodySensation)
                                )
                            }

                            val urlMap = mediaRepository.resolveImageUrls(diary.data.images)
                            urlMap.forEach { (remotePath, downloadUrl) ->
                                currentState.galleryState.addImage(
                                    GalleryImage(
                                        image = downloadUrl,
                                        remoteImagePath = remotePath,
                                    )
                                )
                            }
                        }
                    }
            }
        }
    }

    // ── MVI-based insert/update (send Effects) ─────────

    private suspend fun insertDiary(diary: Diary) {
        val result = diaryRepository.insertDiary(diary = diary.applyDiaryDefaults())
        if (result is RequestState.Success) {
            uploadImageToFirebase()
            withContext(Dispatchers.Main) {
                sendEffect(WriteContract.Effect.DiaryUpsertSuccess)
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                sendEffect(WriteContract.Effect.ShowError(result.error.message.toString()))
            }
        }
    }

    private suspend fun updateDiary(diary: Diary) {
        val result = diaryRepository.updateDiary(diary = diary.applyDiaryUpdateDefaults())
        if (result is RequestState.Success) {
            uploadImageToFirebase()
            deleteImagesFromFirebase()
            currentState.galleryState.clearImagesToBeDeleted()
            withContext(Dispatchers.Main) {
                sendEffect(WriteContract.Effect.DiaryUpsertSuccess)
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                sendEffect(WriteContract.Effect.ShowError(result.error.message.toString()))
            }
        }
    }

    // ── Legacy callback-based insert/update (for backward compat) ──

    private suspend fun insertDiaryLegacy(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val result = diaryRepository.insertDiary(diary = diary.applyDiaryDefaults())
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

    private suspend fun updateDiaryLegacy(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = diaryRepository.updateDiary(diary = diary.applyDiaryUpdateDefaults())
        if (result is RequestState.Success) {
            uploadImageToFirebase()
            deleteImagesFromFirebase()
            currentState.galleryState.clearImagesToBeDeleted()
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    // ── Shared diary mutation helpers ─────────────────────

    private fun Diary.applyDiaryDefaults(): Diary = apply {
        if (currentState.updatedDateTimeMillis != null) {
            dateMillis = currentState.updatedDateTimeMillis!!
        }
        val zoneId = java.time.ZoneId.systemDefault()
        dayKey = java.time.Instant.ofEpochMilli(dateMillis)
            .atZone(zoneId)
            .toLocalDate()
            .toString()
        timezone = zoneId.id
    }

    private fun Diary.applyDiaryUpdateDefaults(): Diary = apply {
        _id = currentState.selectedDiaryId!!
        ownerId = currentState.selectedDiary?.ownerId ?: authProvider.currentUserId ?: ""
        dateMillis = when {
            currentState.updatedDateTimeMillis != null -> currentState.updatedDateTimeMillis!!
            currentState.selectedDiary != null -> currentState.selectedDiary!!.dateMillis
            else -> System.currentTimeMillis()
        }
        val zoneId = java.time.ZoneId.systemDefault()
        dayKey = java.time.Instant.ofEpochMilli(dateMillis)
            .atZone(zoneId)
            .toLocalDate()
            .toString()
        timezone = zoneId.id
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    // ── Firebase image handling ───────────────────────────

    private fun uploadImageToFirebase() {
        updateState { copy(isUploadingImages = true) }
        val storageRef = storage.reference

        val imagesToUpload = currentState.galleryState.images.filter { it.isLoading }.toList()

        println("[WriteViewModel] Uploading ${imagesToUpload.size} images")
        if (imagesToUpload.isEmpty()) {
            updateState { copy(isUploadingImages = false) }
            return
        }

        var completedUploads = 0

        imagesToUpload.forEach { galleryImage ->
            val imagePath = storageRef.child(galleryImage.remoteImagePath)

            val fileToUpload = galleryImage.localFilePath?.let { File(it) }

            val uploadTask = if (fileToUpload != null && fileToUpload.exists()) {
                imagePath.putFile(Uri.fromFile(fileToUpload))
            } else {
                try {
                    imagePath.putFile(Uri.parse(galleryImage.image))
                } catch (e: SecurityException) {
                    println("[WriteViewModel] ERROR: Security exception while uploading, skipping: ${e.message}")
                    scope.launch(Dispatchers.Main) {
                        val index = currentState.galleryState.images.indexOfFirst {
                            it.remoteImagePath == galleryImage.remoteImagePath
                        }
                        if (index >= 0) {
                            currentState.galleryState.images[index] =
                                currentState.galleryState.images[index].copy(isLoading = false)
                        }
                        completedUploads++
                        if (completedUploads >= imagesToUpload.size) {
                            updateState { copy(isUploadingImages = false) }
                        }
                    }
                    return@forEach
                }
            }

            uploadTask.addOnSuccessListener {
                scope.launch(Dispatchers.IO) {
                    imageToUploadQueries.deleteImageToUpload(galleryImage.remoteImagePath)

                    withContext(Dispatchers.Main) {
                        val index = currentState.galleryState.images.indexOfFirst {
                            it.remoteImagePath == galleryImage.remoteImagePath
                        }
                        if (index >= 0) {
                            val updatedImage = currentState.galleryState.images[index].copy(isLoading = false)
                            currentState.galleryState.images[index] = updatedImage
                            println("[WriteViewModel] Image upload successful: ${galleryImage.remoteImagePath}")
                        }

                        completedUploads++
                        if (completedUploads >= imagesToUpload.size) {
                            updateState { copy(isUploadingImages = false) }
                        }
                    }
                }
            }.addOnFailureListener { exception ->
                println("[WriteViewModel] ERROR: Upload failed for ${galleryImage.remoteImagePath}: ${exception.message}")

                scope.launch(Dispatchers.Main) {
                    val index = currentState.galleryState.images.indexOfFirst {
                        it.remoteImagePath == galleryImage.remoteImagePath
                    }
                    if (index >= 0) {
                        val updatedImage = currentState.galleryState.images[index].copy(isLoading = false)
                        currentState.galleryState.images[index] = updatedImage
                    }

                    completedUploads++
                    if (completedUploads >= imagesToUpload.size) {
                        updateState { copy(isUploadingImages = false) }
                    }
                }
            }.addOnProgressListener { taskSnapshot ->
                val sessionUri = taskSnapshot.uploadSessionUri
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()

                if (progress % 20 == 0) {
                    println("[WriteViewModel] Upload progress for ${galleryImage.remoteImagePath}: $progress%")
                }

                if (sessionUri != null) {
                    scope.launch(Dispatchers.IO) {
                        imageToUploadQueries.addImageToUpload(
                            remoteImagePath = galleryImage.remoteImagePath,
                            imageUri = galleryImage.image,
                            sessionUri = sessionUri.toString()
                        )
                    }
                }
            }.addOnCanceledListener {
                println("[WriteViewModel] WARN: Upload canceled for ${galleryImage.remoteImagePath}")

                scope.launch(Dispatchers.Main) {
                    val index = currentState.galleryState.images.indexOfFirst {
                        it.remoteImagePath == galleryImage.remoteImagePath
                    }
                    if (index >= 0) {
                        val updatedImage = currentState.galleryState.images[index].copy(isLoading = false)
                        currentState.galleryState.images[index] = updatedImage
                    }

                    completedUploads++
                    if (completedUploads >= imagesToUpload.size) {
                        updateState { copy(isUploadingImages = false) }
                    }
                }
            }
        }
    }

    private fun deleteImagesFromFirebase(images: List<String>? = null) {
        val storageRef = storage.reference

        if (images != null) {
            images.forEach { remotePath ->
                val imageRef = storageRef.child(remotePath)
                imageRef.getDownloadUrl().addOnSuccessListener {
                    imageRef.delete().addOnFailureListener {
                        scope.launch(Dispatchers.IO) {
                            imageToDeleteQueries.addImageToDelete(
                                remoteImagePath = remotePath
                            )
                        }
                    }
                }.addOnFailureListener {
                    println("[WriteViewModel] WARN: File doesn't exist for deletion: $remotePath")
                }
            }
        } else {
            currentState.galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach { remotePath ->
                storageRef.child(remotePath).delete()
                    .addOnFailureListener {
                        scope.launch(Dispatchers.IO) {
                            imageToDeleteQueries.addImageToDelete(
                                remoteImagePath = remotePath
                            )
                        }
                    }
            }
        }
    }

    private fun extractImagePath(fullImageUrl: String): String {
        val chunks = fullImageUrl.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${authProvider.currentUserId}/$imageName"
    }

    @Suppress("unused")
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
}
