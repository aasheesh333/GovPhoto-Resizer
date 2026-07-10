package com.dhanuk.govphoto_resizer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dhanuk.govphoto_resizer.data.local.dao.PhotoHistoryDao
import com.dhanuk.govphoto_resizer.data.local.dao.RecentPresetDao
import com.dhanuk.govphoto_resizer.data.local.entity.PhotoHistoryEntity
import com.dhanuk.govphoto_resizer.data.local.entity.RecentPresetEntity

@Database(
    entities = [PhotoHistoryEntity::class, RecentPresetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GovPhotoDatabase : RoomDatabase() {
    abstract fun photoHistoryDao(): PhotoHistoryDao
    abstract fun recentPresetDao(): RecentPresetDao
}
