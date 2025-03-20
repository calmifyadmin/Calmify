package com.lifo.home

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val connectivity: NetworkConnectivityObserver,
    private val imageToDeleteDao: ImageToDeleteDao
) : ViewModel() {
    private lateinit var allDiariesJob: Job
    private lateinit var filteredDiariesJob: Job

    // Network status
    private var network by mutableStateOf(ConnectivityObserver.Status.Unavailable)

    // Diaries state for backward compatibility with existing code
    var diaries: MutableState<Diaries> = mutableStateOf(RequestState.Idle)

    // New diariesState for new code using StateFlow
    private val _diariesState = MutableStateFlow<Diaries>(RequestState.Loading)
    val diariesState: StateFlow<Diaries> = _diariesState.asStateFlow()

    // Loading indicator
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Flag for filtering (if a date is selected)
    var dateIsSelected by mutableStateOf(false)
        private set

    init {
        getDiaries()
        viewModelScope.launch {
            connectivity.observe().collect { network = it }
        }
    }

    fun loadStuff() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(3000L)
            withContext(Dispatchers.IO) {
                getDiaries()
            }
            _isLoading.value = false
        }
    }

    fun reloadDiaries() {
        getDiaries()
    }

    /**
     * Gets all diaries or filtered by date.
     * Updates both legacy diaries and new diariesState for compatibility.
     */
    fun getDiaries(zonedDateTime: ZonedDateTime? = null) {
        dateIsSelected = zonedDateTime != null
        diaries.value = RequestState.Loading
        _diariesState.value = RequestState.Loading

        if (dateIsSelected && zonedDateTime != null) {
            observeFilteredDiaries(zonedDateTime = zonedDateTime)
        } else {
            observeAllDiaries()
        }
    }

    /**
     * Alias for getDiaries to maintain compatibility with new code
     */
    fun fetchDiaries(zonedDateTime: ZonedDateTime? = null) {
        getDiaries(zonedDateTime)
    }

    private fun observeAllDiaries() {
        allDiariesJob = viewModelScope.launch {
            _isLoading.value = true
            if (::filteredDiariesJob.isInitialized && filteredDiariesJob.isActive) {
                filteredDiariesJob.cancelAndJoin()
            }
            MongoDB.getAllDiaries().collect { result ->
                // Update both state holders for compatibility
                diaries.value = result
                _diariesState.value = result
                _isLoading.value = false
            }
        }
    }

    private fun observeFilteredDiaries(zonedDateTime: ZonedDateTime) {
        filteredDiariesJob = viewModelScope.launch {
            _isLoading.value = true
            if (::allDiariesJob.isInitialized && allDiariesJob.isActive) {
                allDiariesJob.cancelAndJoin()
            }
            MongoDB.getFilteredDiaries(zonedDateTime = zonedDateTime).collect { result ->
                // Update both state holders for compatibility
                diaries.value = result
                _diariesState.value = result
                _isLoading.value = false
            }
        }
    }

    fun getUserPhotoUrl(): String? {
        return FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
    }

    fun deleteAllDiaries(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (network == ConnectivityObserver.Status.Available) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            val imagesDirectory = "images/$userId"
            val storage = FirebaseStorage.getInstance().reference
            storage.child(imagesDirectory)
                .listAll()
                .addOnSuccessListener { listResult ->
                    listResult.items.forEach { ref ->
                        val imagePath = "images/$userId/${ref.name}"
                        storage.child(imagePath).delete()
                            .addOnFailureListener { e ->
                                viewModelScope.launch(Dispatchers.IO) {
                                    imageToDeleteDao.addImageToDelete(
                                        ImageToDelete(remoteImagePath = imagePath)
                                    )
                                }
                            }
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        val result = MongoDB.deleteAllDiaries()
                        when (result) {
                            is RequestState.Success -> withContext(Dispatchers.Main) { onSuccess() }
                            is RequestState.Error -> withContext(Dispatchers.Main) { onError(result.error) }
                            else -> {}
                        }
                    }
                }
                .addOnFailureListener { onError(it) }
        } else {
            onError(Exception("No Internet Connection."))
        }
    }
}