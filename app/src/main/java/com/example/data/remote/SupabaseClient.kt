package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {

    fun sanitizeSupabaseUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.contains("/rest/v1")) {
            url = url.substringBefore("/rest/v1")
        }
        if (url.contains("/rest")) {
            url = url.substringBefore("/rest")
        }
        if (url.contains("/auth/v1")) {
            url = url.substringBefore("/auth/v1")
        }
        if (url.contains("/storage/v1")) {
            url = url.substringBefore("/storage/v1")
        }
        url = url.trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    val isConfigured: Boolean
        get() = try {
            val url = BuildConfig.SUPABASE_URL
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            !url.isNullOrBlank() && url != "YOUR_SUPABASE_URL" && !key.isNullOrBlank() && key != "YOUR_SUPABASE_PUBLISHABLE_KEY"
        } catch (_: Throwable) {
            false
        }

    val supabaseUrl: String
        get() = try {
            val configUrl = BuildConfig.SUPABASE_URL
            if (!configUrl.isNullOrBlank() && configUrl != "YOUR_SUPABASE_URL") {
                sanitizeSupabaseUrl(configUrl)
            } else {
                "https://placeholder.supabase.co"
            }
        } catch (_: Throwable) {
            "https://placeholder.supabase.co"
        }

    val supabaseKey: String
        get() = try {
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            if (!key.isNullOrBlank() && key != "YOUR_SUPABASE_PUBLISHABLE_KEY") {
                key.trim()
            } else {
                "placeholder-publishable-key"
            }
        } catch (_: Throwable) {
            "placeholder-publishable-key"
        }

    val client: SupabaseClient by lazy {
        val url = supabaseUrl
        val key = supabaseKey
        if (!isConfigured) {
            Log.w("SupabaseConfig", "Supabase publishable credentials not configured. Please supply SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets.")
        } else {
            Log.d("SupabaseConfig", "Initializing SupabaseClient with URL: $url")
        }
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
