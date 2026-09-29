package com.shieldtap.vault.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body body: Map<String, String>): ApiMessage

    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): AuthResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("auth/me")
    suspend fun me(): MeResponse

    @GET("folders")
    suspend fun getFolders(@Query("parentId") parentId: String? = null): Map<String, List<FolderDto>>

    @POST("folders")
    suspend fun createFolder(@Body body: Map<String, String?>): Map<String, FolderDto>

    @PATCH("folders/{id}")
    suspend fun updateFolder(@Path("id") id: String, @Body body: Map<String, Any?>): Map<String, FolderDto>

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): ApiMessage

    @GET("vault")
    suspend fun getVaultItems(@Query("folderId") folderId: String): Map<String, List<VaultItemDto>>

    @POST("vault/password")
    suspend fun addPassword(@Body body: Map<String, String>): Map<String, VaultItemDto>

    @Multipart
    @POST("vault/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("folderId") folderId: RequestBody,
        @Part("type") type: RequestBody,
        @Part("title") title: RequestBody?
    ): Map<String, VaultItemDto>

    @DELETE("vault/{id}")
    suspend fun deleteItem(@Path("id") id: String): ApiMessage

    @PATCH("user/profile")
    suspend fun updateProfile(@Body body: Map<String, String>): Map<String, UserDto>

    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Map<String, String>

    @Multipart
    @POST("user/background")
    suspend fun uploadBackground(@Part background: MultipartBody.Part): Map<String, String>

    @GET("user/notifications")
    suspend fun getNotifications(): Map<String, List<NotificationDto>>
}
