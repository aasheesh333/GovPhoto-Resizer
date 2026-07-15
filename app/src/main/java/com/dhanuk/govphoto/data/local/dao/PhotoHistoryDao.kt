package com.dhanuk.govphoto.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dhanuk.govphoto.data.local.entity.PhotoHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PhotoHistoryEntity): Long

    @Query("SELECT * FROM photo_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<PhotoHistoryEntity>>

    @Query("SELECT * FROM photo_history WHERE presetId LIKE '%' || :q || '%' OR examName LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<PhotoHistoryEntity>>

    @Query("SELECT * FROM photo_history WHERE id = :id")
    suspend fun getById(id: Long): PhotoHistoryEntity?

    @Delete
    suspend fun delete(item: PhotoHistoryEntity)

    @Query("SELECT COUNT(*) FROM photo_history")
    suspend fun count(): Int
}
