package com.lifo.util.model

sealed class RequestState<out T> {
    object Idle : RequestState<Nothing>()
    object Loading : RequestState<Nothing>()
    data class Success<out T>(val data: T) : RequestState<T>()
    data class Error(val error: Exception) : RequestState<Nothing>() {
        val message: String
            get() = error.message ?: "Unknown error occurred"
    }

    // Helper functions
    fun isLoading() = this is Loading
    fun isSuccess() = this is Success
    fun isError() = this is Error

    // Get data safely
    fun getDataOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    // Map function for transformations
    inline fun <R> map(transform: (T) -> R): RequestState<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
        is Idle -> Idle
    }
}