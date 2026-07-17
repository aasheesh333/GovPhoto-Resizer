package com.dhanuk.govphoto.data.push

interface PushCategoryStore {
    suspend fun isEnabled(category: PushCategory): Boolean
    suspend fun setEnabled(category: PushCategory, enabled: Boolean)
}
