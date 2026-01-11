package com.govphoto.resizer.data.repository

import android.content.Context
import com.google.gson.Gson
import com.govphoto.resizer.data.model.PhotoPreset
import com.govphoto.resizer.data.model.PresetData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository to access photo presets from the assets JSON file.
 */
@Singleton
class PresetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private var cachedPresets: List<PhotoPreset>? = null

    /**
     * Get all presets from the JSON file.
     * Caches the result in memory.
     */
    fun getAllPresets(): List<PhotoPreset> {
        if (cachedPresets == null) {
            loadPresets()
        }
        return cachedPresets ?: emptyList()
    }

    /**
     * Get a specific preset by ID.
     */
    fun getPreset(id: String): PhotoPreset? {
        return getAllPresets().find { it.id == id }
    }

    private fun loadPresets() {
        try {
            val jsonString = context.assets.open("exam_presets.json").bufferedReader().use {
                it.readText()
            }
            val presetData = gson.fromJson(jsonString, PresetData::class.java)
            cachedPresets = presetData.presets
        } catch (e: Exception) {
            e.printStackTrace()
            cachedPresets = emptyList()
        }
    }
}
