package com.dhanuk.govphoto_resizer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking recently used presets.
 */
@Entity(tableName = "recent_presets")
data class RecentPresetEntity(
    @PrimaryKey
    val presetId: String,
    val examName: String,
    val category: String,
    val usedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 1
)
