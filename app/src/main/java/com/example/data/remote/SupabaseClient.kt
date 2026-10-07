package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    const val DEFAULT_URL = "https://mrkxwbuhzwfnyrcjdxac.supabase.co"
    const val DEFAULT_KEY = "sb_publishable_3fjoW1A-2MbMjywOxDenzQ_864474a0"

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

    val supabaseUrl: String
        get() = try {
            val configUrl = BuildConfig.SUPABASE_URL
            val raw = if (!configUrl.isNullOrBlank() && configUrl != "YOUR_SUPABASE_URL") configUrl else DEFAULT_URL
            sanitizeSupabaseUrl(raw)
        } catch (_: Throwable) {
            DEFAULT_URL
        }

    val supabaseKey: String
        get() = try {
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            if (!key.isNullOrBlank() && key != "YOUR_SUPABASE_PUBLISHABLE_KEY") key.trim() else DEFAULT_KEY
        } catch (_: Throwable) {
            DEFAULT_KEY
        }

    val client: SupabaseClient by lazy {
        val url = supabaseUrl
        val key = supabaseKey
        Log.d("SupabaseConfig", "Initializing SupabaseClient with sanitized URL: $url")
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
