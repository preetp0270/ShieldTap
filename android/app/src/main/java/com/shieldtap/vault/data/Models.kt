package com.shieldtap.vault.data

data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val phone: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val notifications: List<NotificationDto>? = null
)

data class NotificationDto(
    val type: String,
    val message: String,
    val createdAt: String? = null,
    val read: Boolean = false
)

data class AuthResponse(
    val token: String,
    val expiresAt: String,
    val user: UserDto
)

data class MeResponse(
    val user: UserDto,
    val expiresAt: String
)

data class FolderDto(
    val _id: String,
    val name: String,
    val parentId: String? = null,
    val isLocked: Boolean = false,
    val color: String? = null
)

data class UpdateFolderRequest(
    val name: String? = null,
    val isLocked: Boolean? = null,
    val color: String? = null
)

data class VaultItemDto(
    val _id: String,
    val type: String,          // password | file | image
    val key: String? = null,
    val value: String? = null,
    val title: String? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val size: Long? = null
)

data class ApiMessage(val message: String? = null, val error: String? = null)
