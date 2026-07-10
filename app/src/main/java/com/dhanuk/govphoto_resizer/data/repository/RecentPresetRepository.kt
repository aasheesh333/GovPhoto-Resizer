package com.dhanuk.govphoto_resizer.data.repository

import com.dhanuk.govphoto_resizer.data.local.dao.RecentPresetDao
import com.dhanuk.govphoto_resizer.data.local.entity.RecentPresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentPresetRepository @Inject constructor(
    private val dao: RecentPresetDao
) {
    fun observeRecent(limit: Int = 10): Flow<List<RecentPresetEntity>> = dao.observeRecent(limit)

    suspend fun recordUse(presetId: String, examName: String, category: String) {
        val inserted = dao.upsert(RecentPresetEntity(presetId = presetId, examName = examName, category = category, usedAt = System.currentTimeMillis(), useCount = 1))
        if (inserted == -1L) {
            dao.bumpUse(presetId, examName, category)
        }
    }

    suspend fun delete(presetId: String) = dao.delete(presetId)
}
