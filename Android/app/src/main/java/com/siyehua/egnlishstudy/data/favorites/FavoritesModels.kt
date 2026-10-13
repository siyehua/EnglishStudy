package com.siyehua.egnlishstudy.data.favorites

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteSyncItem(
    @SerialName("syncId") val syncId: String,
    @SerialName("kind") val kind: String,
    @SerialName("text") val text: String,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("contentId") val contentId: String = "",
    @SerialName("translation") val translation: String = "",
    @SerialName("lessonTitle") val lessonTitle: String = "",
    @SerialName("startTime") val startTime: Double = 0.0,
    @SerialName("endTime") val endTime: Double = 0.0,
    @SerialName("updatedAt") val updatedAt: Long = 0,
    @SerialName("deleted") val deleted: Boolean = false
)

@Serializable
data class FavoritesSyncRequest(
    @SerialName("bucket") val bucket: String,
    @SerialName("items") val items: List<FavoriteSyncItem>
)

@Serializable
data class FavoritesSyncResponse(
    @SerialName("items") val items: List<FavoriteSyncItem> = emptyList(),
    @SerialName("serverTime") val serverTime: Long = 0
)
