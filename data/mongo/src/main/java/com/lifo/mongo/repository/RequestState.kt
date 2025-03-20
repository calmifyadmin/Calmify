package com.lifo.mongo.repository

sealed class RequestState<out T> {
    object Idle : RequestState<Nothing>()
    object Loading : RequestState<Nothing>()
    data class Success<out T>(val data: T) : RequestState<T>()
    data class Error(val error: Exception) : RequestState<Nothing>() // <-- NON parametrizzata
}
