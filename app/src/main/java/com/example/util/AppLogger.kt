package com.example.util

import android.util.Log
import com.example.BuildConfig

object AppLogger {

    private const val DEFAULT_TAG = "BookSphere"

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitize(message))
        }
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, sanitize(message))
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        if (throwable != null) {
            Log.w(tag, sanitized, throwable)
        } else {
            Log.w(tag, sanitized)
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        if (throwable != null) {
            Log.e(tag, sanitized, throwable)
        } else {
            Log.e(tag, sanitized)
        }
    }

    private fun sanitize(input: String): String {
        return input
            // Mask Bearer tokens and JWTs
            .replace(Regex("(?i)bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*"), "Bearer [REDACTED_JWT]")
            .replace(Regex("(?i)ey[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]+"), "[REDACTED_JWT]")
            // Mask password arguments
            .replace(Regex("(?i)(password|pass|secret)[\"':= ]+[^,\n&}]+"), "$1=[REDACTED]")
            // Mask API Keys
            .replace(Regex("(?i)(apikey|api_key|service_role|anon_key)[\"':= ]+[^,\n&}]+"), "$1=[REDACTED_KEY]")
    }
}
