package com.dhanuk.govphoto_resizer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a processed photo stored in the history.
 */
@Entity(
    tableName = "photo_history",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["presetId"]),
        Index(value = ["examName"])
    ]
)
data class PhotoHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val presetId: String,
    val examName: String,
    val originalImagePath: String,
    val processedImagePath: String,
    val thumbnailPath: String? = null,
    val fileSizeKb: Int,
    val widthPx: Int,
    val heightPx: Int,
    val createdAt: Long = System.currentTimeMillis()
)
