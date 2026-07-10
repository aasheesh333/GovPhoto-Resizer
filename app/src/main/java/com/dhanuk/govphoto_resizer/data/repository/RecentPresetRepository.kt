package com.dhanuk.govphoto_resizer.data.repository

import android.util.Log
import com.dhanuk.govphoto_resizer.data.local.dao.RecentPresetDao
import com.dhanuk.govphoto_resizer.data.local.entity.RecentPresetEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentPresetRepository @Inject constructor(
    private val dao: RecentPresetDao
) {
    private companion object {
        const val TAG = "RecentPresetRepo"
    }

    fun observeRecent(limit: Int = 10): Flow<List<RecentPresetEntity>> = dao.observeRecent(limit)

    suspend fun recordUse(presetId: String, examName: String, category: String) {
        try {
            dao.recordUse(presetId, examName, category, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record recent preset use", e)
        }
    }

    suspend fun delete(presetId: String) = dao.delete(presetId)
}
