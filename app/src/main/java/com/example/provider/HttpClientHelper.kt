package com.example.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpClientHelper {
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

class AiProviderException(
    val statusCode: Int,
    val providerName: String,
    message: String,
    val isQuotaExhausted: Boolean = statusCode == 429,
    val isAuthError: Boolean = statusCode == 401 || statusCode == 403
) : IOException("[$providerName Error $statusCode]: $message")
