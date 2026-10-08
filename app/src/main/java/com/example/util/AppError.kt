package com.example.util

import org.json.JSONObject

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

        /**
         * Extracts detailed message and error code from raw exception or JSON payload.
         */
        fun extractDetailedMessage(raw: String): Pair<String?, String?> {
            if (raw.isBlank()) return Pair(null, null)

            // Try parsing JSON block if present
            try {
                val jsonStart = raw.indexOf('{')
                val jsonEnd = raw.lastIndexOf('}')
                if (jsonStart in 0 until jsonEnd) {
                    val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
                    val json = JSONObject(jsonStr)

                    val msg = json.optString("msg").takeIf { it.isNotBlank() }
                        ?: json.optString("message").takeIf { it.isNotBlank() }
                        ?: json.optString("error_description").takeIf { it.isNotBlank() }
                        ?: json.optString("description").takeIf { it.isNotBlank() }
                        ?: json.optString("details").takeIf { it.isNotBlank() }

                    val code = json.optString("error_code").takeIf { it.isNotBlank() }
                        ?: json.optString("code").takeIf { it.isNotBlank() }
                        ?: json.optString("error").takeIf { it.isNotBlank() }

                    if (msg != null || code != null) {
                        return Pair(msg, code)
                    }
                }
            } catch (_: Exception) {
                // Fall back to regex parsing
            }

            // Regex extraction for common JSON fields
            val msgMatch = Regex("\"(?:msg|message|error_description|description)\"\\s*:\\s*\"([^\"]+)\"").find(raw)
            val codeMatch = Regex("\"(?:error_code|code|error)\"\\s*:\\s*\"([^\"]+)\"").find(raw)
            val msg = msgMatch?.groupValues?.get(1)
            val code = codeMatch?.groupValues?.get(1)

            return Pair(msg, code)
        }

        fun from(e: Throwable, defaultMessage: String = "An unexpected error occurred."): AppError {
            val rawMsg = e.message ?: ""
            val (extractedMsg, extractedCode) = extractDetailedMessage(rawMsg)
            val effectiveMsg = extractedMsg ?: rawMsg
            val combined = "$rawMsg $effectiveMsg ${extractedCode ?: ""}".lowercase()

            val category = when {
                e is java.net.UnknownHostException || 
                e is java.net.SocketTimeoutException || 
                e is java.net.ConnectException ||
                combined.contains("unable to resolve host") ||
                combined.contains("failed to connect") ||
                combined.contains("timeout") ||
                combined.contains("network") -> ErrorCategory.NETWORK_ERROR

                combined.contains("unauthorized") ||
                combined.contains("jwt") ||
                combined.contains("session expired") ||
                combined.contains("invalid login") ||
                combined.contains("invalid_credentials") ||
                combined.contains("invalid_grant") ||
                combined.contains("email_not_confirmed") ||
                combined.contains("email rate limit") ||
                combined.contains("over_email_send_rate_limit") ||
                combined.contains("email_address_invalid") ||
                combined.contains("user_already_exists") ||
                combined.contains("user already registered") ||
                combined.contains("database error saving new user") ||
                combined.contains("unexpected_failure") ||
                combined.contains("auth") -> ErrorCategory.AUTH_ERROR

                combined.contains("permission denied") ||
                combined.contains("forbidden") ||
                combined.contains("row-level security") ||
                combined.contains("403") ||
                combined.contains("only verified authors") -> ErrorCategory.PERMISSION_ERROR

                combined.contains("storage") ||
                combined.contains("bucket") ||
                combined.contains("upload") -> ErrorCategory.STORAGE_ERROR

                combined.contains("postgrest") ||
                combined.contains("relation") ||
                combined.contains("postgres") ||
                combined.contains("database") ||
                combined.contains("column") -> ErrorCategory.DATABASE_ERROR

                combined.contains("pdf") ||
                combined.contains("invalid") ||
                combined.contains("page count") ||
                combined.contains("validation") -> ErrorCategory.VALIDATION_ERROR

                combined.contains("not found") ||
                combined.contains("404") -> ErrorCategory.NOT_FOUND

                else -> ErrorCategory.UNKNOWN_ERROR
            }

            val friendlyUserMessage = when (category) {
                ErrorCategory.NETWORK_ERROR -> "No internet connection. Please check your network and try again."
                ErrorCategory.AUTH_ERROR -> when {
                    // Database error in auth trigger
                    combined.contains("database error saving new user") || combined.contains("42501") ->
                        "Database error saving new user (permission denied for table profiles). Please ensure handle_new_user trigger is SECURITY DEFINER."

                    combined.contains("jwt") || combined.contains("session expired") || combined.contains("invalid_grant") || combined.contains("expired") ->
                        "Authentication session expired or invalid. Please sign in again."

                    combined.contains("invalid login") || combined.contains("invalid_credentials") ->
                        "Invalid email or password. Please verify your credentials."

                    combined.contains("email not confirmed") || combined.contains("email_not_confirmed") ->
                        "Please confirm your email address before signing in. Check your inbox for the confirmation link."

                    combined.contains("user already registered") || combined.contains("user_already_exists") ->
                        "An account with this email already exists. Please sign in."

                    combined.contains("email_address_invalid") || combined.contains("email address") && combined.contains("invalid") ->
                        extractedMsg ?: "Please enter a valid email address format."

                    combined.contains("password should be at least") ->
                        "Password must be at least 6 characters long."

                    combined.contains("rate limit") || combined.contains("over_email_send_rate_limit") ->
                        "Too many attempts. Email rate limit exceeded. Please wait a few moments and try again."

                    combined.contains("confirmation link sent") || combined.contains("verify your email") ->
                        effectiveMsg

                    // If unexpected_failure has an underlying message or code, present both clearly
                    combined.contains("unexpected_failure") -> {
                        if (!extractedMsg.isNullOrBlank() && !extractedMsg.equals("unexpected_failure", ignoreCase = true)) {
                            "Supabase Auth error: $extractedMsg (unexpected_failure)"
                        } else {
                            "Supabase unexpected failure during authentication. Please check database logs."
                        }
                    }

                    !extractedMsg.isNullOrBlank() ->
                        extractedMsg

                    effectiveMsg.isNotBlank() && !effectiveMsg.contains("{") && !effectiveMsg.contains("Exception") ->
                        effectiveMsg

                    else -> "Authentication failed. Please check your credentials."
                }
                ErrorCategory.PERMISSION_ERROR -> "You do not have permission to perform this action."
                ErrorCategory.STORAGE_ERROR -> "Cloud storage operation failed. Please retry in a moment."
                ErrorCategory.DATABASE_ERROR -> if (!extractedMsg.isNullOrBlank()) extractedMsg else "Database service error. Please retry."
                ErrorCategory.VALIDATION_ERROR -> if (effectiveMsg.isNotBlank()) effectiveMsg else "Invalid publication data or PDF document."
                ErrorCategory.NOT_FOUND -> "The requested book or resource could not be found."
                ErrorCategory.UNKNOWN_ERROR -> if (!extractedMsg.isNullOrBlank()) extractedMsg else if (rawMsg.isNotBlank()) rawMsg else defaultMessage
            }

            return AppError(
                category = category,
                userMessage = friendlyUserMessage,
                technicalMessage = rawMsg,
                canRetry = category != ErrorCategory.PERMISSION_ERROR,
                cause = e
            )
        }
    }
}
