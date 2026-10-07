package com.example.model

import com.example.util.AppError

sealed interface DataState<out T> {
    object Idle : DataState<Nothing>
    object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    object Empty : DataState<Nothing>
    data class Offline<T>(val data: T, val lastUpdated: Long = System.currentTimeMillis(), val message: String = "Showing cached offline catalog") : DataState<T>
    data class Error(val error: AppError) : DataState<Nothing>
}
