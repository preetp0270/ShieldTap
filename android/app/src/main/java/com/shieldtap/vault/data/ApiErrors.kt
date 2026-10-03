package com.shieldtap.vault.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Extracts a human-readable error message from any Throwable
 * thrown by Retrofit / OkHttp.
 */
fun Throwable.toUserMessage(): String {
    return when (this) {
        is HttpException -> {
            val body = response()?.errorBody()?.string()
            parseBackendError(body) ?: "Request failed (${code()})"
        }
        is UnknownHostException ->
            "Cannot reach server. Check BASE_URL and that the backend is running."
        is ConnectException ->
            "Connection refused. Is the backend running on port 5000?"
        is SocketTimeoutException ->
            "Server took too long to respond. Backend may be down or MongoDB is not connected."
        is IOException -> {
            val m = message?.lowercase() ?: ""
            when {
                m.contains("failed to connect") || m.contains("connection refused") ->
                    "Connection refused. Is the backend running on port 5000?"
                m.contains("timeout") ->
                    "Server took too long to respond. Backend may be down or MongoDB is not connected."
                else -> message?.takeIf { it.isNotBlank() } ?: "Network error"
            }
        }
        else -> message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") }
            ?: "Something went wrong"
    }
}

private fun parseBackendError(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        val json = Gson().fromJson(body, JsonObject::class.java)
        when {
            json.has("error") && json.get("error").isJsonPrimitive ->
                json.get("error").asString
            json.has("message") && json.get("message").isJsonPrimitive ->
                json.get("message").asString
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
