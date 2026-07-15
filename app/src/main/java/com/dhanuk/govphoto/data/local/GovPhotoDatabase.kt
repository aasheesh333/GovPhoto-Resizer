package com.dhanuk.govphoto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhanuk.govphoto.data.local.dao.PhotoHistoryDao
import com.dhanuk.govphoto.data.local.dao.RecentPresetDao
import com.dhanuk.govphoto.data.local.entity.PhotoHistoryEntity
import com.dhanuk.govphoto.data.local.entity.RecentPresetEntity

@Database(
    entities = [PhotoHistoryEntity::class, RecentPresetEntity::class],
    version = 1,
    exportSchema = true
)
abstract class GovPhotoDatabase : RoomDatabase() {
    abstract fun photoHistoryDao(): PhotoHistoryDao
    abstract fun recentPresetDao(): RecentPresetDao
}
