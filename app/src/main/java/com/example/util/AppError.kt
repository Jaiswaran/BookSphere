package com.example.util

enum class ErrorCategory {
    AUTH_ERROR,
    NETWORK_ERROR,
    STORAGE_ERROR,
    DATABASE_ERROR,
    VALIDATION_ERROR,
    PERMISSION_ERROR,
    NOT_FOUND,
    UNKNOWN_ERROR
}

data class AppError(
    val category: ErrorCategory,
    val userMessage: String,
    val technicalMessage: String? = null,
    val canRetry: Boolean = true,
    val cause: Throwable? = null
) {
    companion object {
        fun from(e: Throwable, defaultMessage: String = "An unexpected error occurred."): AppError {
            val msg = e.message ?: ""
            val lowerMsg = msg.lowercase()

            val category = when {
                e is java.net.UnknownHostException || 
                e is java.net.SocketTimeoutException || 
                e is java.net.ConnectException ||
                lowerMsg.contains("unable to resolve host") ||
                lowerMsg.contains("failed to connect") ||
                lowerMsg.contains("timeout") ||
                lowerMsg.contains("network") -> ErrorCategory.NETWORK_ERROR

                lowerMsg.contains("unauthorized") ||
                lowerMsg.contains("jwt") ||
                lowerMsg.contains("session expired") ||
                lowerMsg.contains("invalid login") ||
                lowerMsg.contains("invalid_grant") ||
                lowerMsg.contains("auth") -> ErrorCategory.AUTH_ERROR

                lowerMsg.contains("permission denied") ||
                lowerMsg.contains("forbidden") ||
                lowerMsg.contains("row-level security") ||
                lowerMsg.contains("403") ||
                lowerMsg.contains("only verified authors") -> ErrorCategory.PERMISSION_ERROR

                lowerMsg.contains("storage") ||
                lowerMsg.contains("bucket") ||
                lowerMsg.contains("upload") -> ErrorCategory.STORAGE_ERROR

                lowerMsg.contains("postgrest") ||
                lowerMsg.contains("relation") ||
                lowerMsg.contains("postgres") ||
                lowerMsg.contains("database") ||
                lowerMsg.contains("column") -> ErrorCategory.DATABASE_ERROR

                lowerMsg.contains("pdf") ||
                lowerMsg.contains("invalid") ||
                lowerMsg.contains("page count") ||
                lowerMsg.contains("validation") -> ErrorCategory.VALIDATION_ERROR

                lowerMsg.contains("not found") ||
                lowerMsg.contains("404") -> ErrorCategory.NOT_FOUND

                else -> ErrorCategory.UNKNOWN_ERROR
            }

            val friendlyUserMessage = when (category) {
                ErrorCategory.NETWORK_ERROR -> "No internet connection. Please check your network and try again."
                ErrorCategory.AUTH_ERROR -> "Authentication session expired or invalid. Please sign in again."
                ErrorCategory.PERMISSION_ERROR -> "You do not have permission to perform this action."
                ErrorCategory.STORAGE_ERROR -> "Cloud storage operation failed. Please retry in a moment."
                ErrorCategory.DATABASE_ERROR -> "Database service is temporarily unavailable. Please retry."
                ErrorCategory.VALIDATION_ERROR -> if (msg.isNotBlank()) msg else "Invalid publication data or PDF document."
                ErrorCategory.NOT_FOUND -> "The requested book or resource could not be found."
                ErrorCategory.UNKNOWN_ERROR -> if (msg.isNotBlank()) msg else defaultMessage
            }

            return AppError(
                category = category,
                userMessage = friendlyUserMessage,
                technicalMessage = msg,
                canRetry = category != ErrorCategory.PERMISSION_ERROR,
                cause = e
            )
        }
    }
}
