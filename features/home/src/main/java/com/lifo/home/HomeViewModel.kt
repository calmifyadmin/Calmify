package com.lifo.home

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lifo.mongo.database.ImageToDeleteDao
import com.lifo.mongo.database.entity.ImageToDelete
import com.lifo.mongo.repository.Diaries
import com.lifo.mongo.repository.MongoDB
import com.lifo.util.connectivity.ConnectivityObserver
import com.lifo.util.connectivity.NetworkConnectivityObserver
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

// ViewModel state representation
data class HomeUiState(
    val diaries: Diaries = RequestState.Loading,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dateIsSelected: Boolean = false,
    val selectedDate: ZonedDateTime? = null,
    val error: String? = null
)

@RequiresApi(Build.VERSION_CODES.N)
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val connectivity: NetworkConnectivityObserver,
    private val imageToDeleteDao: ImageToDeleteDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_DATE_SELECTED = "date_is_selected"
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val RETRY_DELAY = 1000L
        private const val MAX_RETRIES = 3
    }

    // Jobs management
    private var diariesJob: Job? = null
    private var refreshJob: Job? = null

    // Network status
    private val _networkStatus = MutableStateFlow(ConnectivityObserver.Status.Unavailable)
    val networkStatus: StateFlow<ConnectivityObserver.Status> = _networkStatus.asStateFlow()

    // UI State using StateFlow for better state management
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Legacy state holders for backward compatibility
    var diaries: MutableState<Diaries> = mutableStateOf(RequestState.Loading)
        private set

    var dateIsSelected by mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_DATE_SELECTED) ?: false
    )
        private set

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Restore state if available
        restoreState()

        // Start observing network connectivity
        viewModelScope.launch {
            connectivity.observe()
                .catch { e -> Log.e("HomeViewModel", "Network observation error", e) }
                .collect { status ->
                    _networkStatus.value = status
                    // Retry loading if we regain connectivity and had an error
                    if (status == ConnectivityObserver.Status.Available &&
                        _uiState.value.error != null) {
                        retryLoading()
                    }
                }
        }

        // Initial load
        loadDiaries()
    }

    private fun restoreState() {
        savedStateHandle.get<Long>(KEY_SELECTED_DATE)?.let { millis ->
            // Restore selected date if available
            try {
                val selectedDate = ZonedDateTime.now().withNano(millis.toInt())
                _uiState.update { it.copy(
                    selectedDate = selectedDate,
                    dateIsSelected = true
                )}
                dateIsSelected = true
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error restoring date", e)
            }
        }
    }

    private fun saveState() {
        savedStateHandle[KEY_DATE_SELECTED] = dateIsSelected
        _uiState.value.selectedDate?.let {
            savedStateHandle[KEY_SELECTED_DATE] = it.toEpochSecond()
        }
    }

    fun loadDiaries() {
        getDiaries(_uiState.value.selectedDate)
    }

    fun refreshDiaries() {
        if (_uiState.value.isRefreshing) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            _isLoading.value = true

            try {
                // Add minimum refresh time for better UX
                delay(500)
                getDiaries(_uiState.value.selectedDate)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
                _isLoading.value = false
            }
        }
    }

    fun getDiaries(zonedDateTime: ZonedDateTime? = null) {
        dateIsSelected = zonedDateTime != null
        _uiState.update { it.copy(
            dateIsSelected = dateIsSelected,
            selectedDate = zonedDateTime,
            error = null
        )}

        // Save state
        saveState()

        // Update legacy state
        diaries.value = RequestState.Loading

        // Cancel previous job
        diariesJob?.cancel()

        diariesJob = viewModelScope.launch {
            retryWithBackoff(
                times = MAX_RETRIES,
                initialDelay = RETRY_DELAY
            ) {
                if (dateIsSelected && zonedDateTime != null) {
                    observeFilteredDiaries(zonedDateTime)
                } else {
                    observeAllDiaries()
                }
            }
        }
    }

    private suspend fun observeAllDiaries() {
        MongoDB.getAllDiaries()
            .catch { e ->
                handleError(e)
                emit(RequestState.Error(e))
            }
            .collect { result ->
                updateDiariesState(result)
            }
    }

    private suspend fun observeFilteredDiaries(zonedDateTime: ZonedDateTime) {
        MongoDB.getFilteredDiaries(zonedDateTime = zonedDateTime)
            .catch { e ->
                handleError(e)
                emit(RequestState.Error(e))
            }
            .collect { result ->
                updateDiariesState(result)
            }
    }

    private fun updateDiariesState(result: Diaries) {
        // Update new state
        _uiState.update { currentState ->
            currentState.copy(
                diaries = result,
                isLoading = result is RequestState.Loading,
                error = if (result is RequestState.Error) {
                    result.error.message ?: "Unknown error"
                } else null
            )
        }

        // Update legacy state
        diaries.value = result
        _isLoading.value = result is RequestState.Loading
    }

    private fun handleError(error: Throwable) {
        Log.e("HomeViewModel", "Error loading diaries", error)
        val errorMessage = when {
            _networkStatus.value != ConnectivityObserver.Status.Available ->
                "No internet connection"
            error.message?.contains("timeout", ignoreCase = true) == true ->
                "Connection timeout. Please try again."
            else -> error.message ?: "Failed to load diaries"
        }

        _uiState.update { it.copy(error = errorMessage) }
    }

    private fun retryLoading() {
        if (_uiState.value.error != null && !_uiState.value.isLoading) {
            getDiaries(_uiState.value.selectedDate)
        }
    }

    // Retry mechanism with exponential backoff
    private suspend fun <T> retryWithBackoff(
        times: Int,
        initialDelay: Long = 100,
        maxDelay: Long = 5000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Retry attempt ${attempt + 1} failed", e)
                if (attempt == times - 2) throw e
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block()
    }

    fun getUserPhotoUrl(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error getting user photo", e)
            null
        }
    }

    fun deleteAllDiaries(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (_networkStatus.value != ConnectivityObserver.Status.Available) {
            onError(Exception("No Internet Connection."))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("User not authenticated")

                deleteAllDiariesInternal(userId, onSuccess, onError)
            } catch (e: Exception) {
                onError(e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun deleteAllDiariesInternal(
        userId: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val imagesDirectory = "images/$userId"
        val storage = FirebaseStorage.getInstance().reference

        try {
            // Delete images from Firebase Storage
            val listResult = storage.child(imagesDirectory).listAll().await()

            // Delete each image
            listResult.items.forEach { ref ->
                try {
                    ref.delete().await()
                } catch (e: Exception) {
                    // Queue for later deletion if fails
                    val imagePath = "images/$userId/${ref.name}"
                    withContext(Dispatchers.IO) {
                        imageToDeleteDao.addImageToDelete(
                            ImageToDelete(remoteImagePath = imagePath)
                        )
                    }
                }
            }

            // Delete diaries from MongoDB
            withContext(Dispatchers.IO) {
                when (val result = MongoDB.deleteAllDiaries()) {
                    is RequestState.Success -> {
                        withContext(Dispatchers.Main) {
                            onSuccess()
                            // Refresh diaries after deletion
                            getDiaries()
                        }
                    }
                    is RequestState.Error -> {
                        withContext(Dispatchers.Main) {
                            onError(result.error)
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    // Aliases for backward compatibility
    fun reloadDiaries() = loadDiaries()
    fun fetchDiaries(zonedDateTime: ZonedDateTime? = null) = getDiaries(zonedDateTime)
    fun loadStuff() = refreshDiaries()

    override fun onCleared() {
        super.onCleared()
        // Cancel all jobs
        diariesJob?.cancel()
        refreshJob?.cancel()
    }
}

// Extension function to await Firebase tasks
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result, null)
        }
        addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
    }
}