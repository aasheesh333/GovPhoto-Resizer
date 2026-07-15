package com.dhanuk.govphoto.data.repository

import com.dhanuk.govphoto.data.local.dao.PhotoHistoryDao
import com.dhanuk.govphoto.data.local.entity.PhotoHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: PhotoHistoryDao
) {
    data class HistorySave(
        val presetId: String,
        val examName: String,
        val originalImagePath: String,
        val processedImagePath: String,
        val fileSizeKb: Int,
        val widthPx: Int,
        val heightPx: Int
    )

    fun observeRecent(limit: Int = 50): Flow<List<PhotoHistoryEntity>> = dao.observeRecent(limit)
    fun search(q: String): Flow<List<PhotoHistoryEntity>> = dao.search(q)
    suspend fun getById(id: Long): PhotoHistoryEntity? = dao.getById(id)
    suspend fun delete(item: PhotoHistoryEntity) = dao.delete(item)
    suspend fun count(): Int = dao.count()

    suspend fun recordSave(save: HistorySave): Long = dao.insert(
        PhotoHistoryEntity(
            presetId = save.presetId,
            examName = save.examName,
            originalImagePath = save.originalImagePath,
            processedImagePath = save.processedImagePath,
            fileSizeKb = save.fileSizeKb,
            widthPx = save.widthPx,
            heightPx = save.heightPx,
            createdAt = System.currentTimeMillis()
        )
    )
}
