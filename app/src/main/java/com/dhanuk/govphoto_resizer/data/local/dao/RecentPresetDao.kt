package com.dhanuk.govphoto_resizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhanuk.govphoto_resizer.data.local.entity.RecentPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPresetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsert(item: RecentPresetEntity): Long

    @Query("SELECT * FROM recent_presets ORDER BY usedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<RecentPresetEntity>>

    @Query("UPDATE recent_presets SET useCount = useCount + 1, usedAt = :now, examName = :examName, category = :category WHERE presetId = :presetId")
    suspend fun bumpUse(presetId: String, examName: String, category: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM recent_presets WHERE presetId = :presetId")
    suspend fun delete(presetId: String)
}
