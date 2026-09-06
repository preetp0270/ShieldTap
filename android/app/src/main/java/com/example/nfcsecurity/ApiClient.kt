package com.example.nfcsecurity

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CloudFileResult(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val size: Long
)

data class AuthResult(
    val token: String,
    val userId: String,
    val username: String,
    val email: String
)

/**
 * Talks to the Node backend (MongoDB + Cloudinary).
 * Base URL comes from BuildConfig.API_BASE_URL.
 */
class ApiClient(
    private val context: Context,
    private val session: SessionStore,
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private fun authRequest(builder: Request.Builder): Request.Builder {
        session.token?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    fun register(username: String, email: String, password: String): Result<AuthResult> = runCatching {
        val body = JSONObject()
            .put("username", username)
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/api/auth/register")
            .post(body)
            .build()
        parseAuth(client.newCall(req).execute().body?.string() ?: error("Empty response"))
    }

    fun login(usernameOrEmail: String, password: String): Result<AuthResult> = runCatching {
        val body = JSONObject()
            .put("usernameOrEmail", usernameOrEmail)
            .put("password", password)
            .put("deviceLabel", android.os.Build.MODEL)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post(body)
            .build()
        parseAuth(client.newCall(req).execute().body?.string() ?: error("Empty response"))
    }

    fun logout(): Result<Unit> = runCatching {
        val req = authRequest(
            Request.Builder().url("$baseUrl/api/auth/logout").post(ByteArray(0).toRequestBody())
        ).build()
        client.newCall(req).execute().close()
        session.clear()
    }

    /**
     * Upload file bytes to API → Cloudinary; Mongo stores the link.
     * Call after local encrypted save when [SessionStore.cloudBackupEnabled] and logged in.
     */
    fun uploadFile(
        uri: Uri,
        title: String,
        fileName: String,
        mimeType: String?,
        spaceId: String,
        folderId: String,
        itemId: String
    ): Result<CloudFileResult> = runCatching {
        if (session.token.isNullOrBlank()) error("Not logged in")

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read file")
        val media = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaType()

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(media))
            .addFormDataPart("title", title)
            .addFormDataPart("spaceId", spaceId)
            .addFormDataPart("folderId", folderId)
            .addFormDataPart("itemId", itemId)
            .build()

        val req = authRequest(
            Request.Builder()
                .url("$baseUrl/api/files/upload")
                .post(multipart)
        ).build()

        val resp = client.newCall(req).execute()
        val raw = resp.body?.string() ?: error("Empty upload response")
        if (!resp.isSuccessful) {
            val err = runCatching { JSONObject(raw).optString("error") }.getOrNull()
            error(err ?: "Upload failed (${resp.code})")
        }
        val json = JSONObject(raw)
        CloudFileResult(
            id = json.getString("id"),
            url = json.getString("url"),
            fileName = json.optString("fileName", fileName),
            mimeType = json.optString("mimeType", mimeType ?: ""),
            size = json.optLong("size", bytes.size.toLong())
        )
    }

    fun listFiles(spaceId: String? = null): Result<List<CloudFileResult>> = runCatching {
        val url = buildString {
            append("$baseUrl/api/files")
            if (!spaceId.isNullOrBlank()) append("?spaceId=$spaceId")
        }
        val req = authRequest(Request.Builder().url(url).get()).build()
        val raw = client.newCall(req).execute().body?.string() ?: error("Empty")
        val arr = JSONObject(raw).getJSONArray("files")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CloudFileResult(
                        id = o.getString("id"),
                        url = o.getString("url"),
                        fileName = o.optString("fileName"),
                        mimeType = o.optString("mimeType"),
                        size = o.optLong("size")
                    )
                )
            }
        }
    }

    private fun parseAuth(raw: String): AuthResult {
        val json = JSONObject(raw)
        if (json.has("error")) error(json.get("error").toString())
        val user = json.getJSONObject("user")
        return AuthResult(
            token = json.getString("token"),
            userId = user.getString("id"),
            username = user.getString("username"),
            email = user.getString("email")
        )
    }
}
