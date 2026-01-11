package com.govphoto.resizer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a processed photo stored in the history.
 */
@Entity(tableName = "photo_history")
data class PhotoHistory(
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

/**
 * Entity for tracking recently used presets.
 */
@Entity(tableName = "recent_presets")
data class RecentPreset(
    @PrimaryKey
    val presetId: String,
    val examName: String,
    val category: String,
    val usedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 1
)
