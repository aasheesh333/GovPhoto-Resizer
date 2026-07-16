package com.dhanuk.govphoto.data.model

import com.google.gson.annotations.SerializedName

/**
 * Type of preset, used to decide whether ML Kit face detection should run.
 * Non-PHOTO presets (signatures, thumb impressions, documents) skip face detection.
 */
enum class PresetType {
    PHOTO,
    SIGNATURE,
    THUMB,
    DOCUMENT
}

/**
 * Data class representing a photo preset for a specific exam or document.
 * Contains all specifications required to resize and validate a photo.
 */
data class PhotoPreset(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("exam_name")
    val examName: String,
    
    @SerializedName("exam_name_hi")
    val examNameHi: String? = null,
    
    @SerializedName("authority")
    val authority: String,
    
    @SerializedName("category")
    val category: PresetCategory,
    
    @SerializedName("width_px")
    val widthPx: Int,
    
    @SerializedName("height_px")
    val heightPx: Int,
    
    @SerializedName("width_cm")
    val widthCm: Float? = null,
    
    @SerializedName("height_cm")
    val heightCm: Float? = null,
    
    @SerializedName("max_file_size_kb")
    val maxFileSizeKb: Int,
    
    @SerializedName("min_file_size_kb")
    val minFileSizeKb: Int? = null,

    @SerializedName("format")
    val format: String = "jpg", // "jpg" or "png"

    @SerializedName("preset_type")
    val presetType: PresetType = PresetType.PHOTO,

    @SerializedName("background_color")
    val backgroundColor: String = "#FFFFFF",
    
    @SerializedName("allowed_backgrounds")
    val allowedBackgrounds: List<String>? = null,
    
    @SerializedName("dpi")
    val dpi: Int = 300,
    
    @SerializedName("face_margin_rules")
    val faceMarginRules: FaceMarginRules? = null,
    
    @SerializedName("state")
    val state: String? = null,
    
    @SerializedName("is_active")
    val isActive: Boolean = true,
    
    @SerializedName("last_updated")
    val lastUpdated: String,
    
    @SerializedName("notes")
    val notes: String? = null,

    @SerializedName("spec_source")
    val specSource: String? = null,

    @SerializedName("spec_verified_date")
    val specVerifiedDate: String? = null
) {
    /**
     * Returns a formatted dimension string like "3.5cm x 4.5cm" or "600x600px"
     */
    fun getFormattedDimensions(): String {
        return if (widthCm != null && heightCm != null) {
            "${widthCm}cm x ${heightCm}cm"
        } else {
            "${widthPx}x${heightPx}px"
        }
    }
    
    /**
     * Returns aspect ratio of the photo
     */
    fun getAspectRatio(): Float {
        if (heightPx == 0) return 1f
        return widthPx.toFloat() / heightPx.toFloat()
    }

    companion object {
        const val MANUAL_PRESET_ID = "manual_custom_preset"
    }
}

/**
 * Face margin rules for positioning the face within the photo.
 */
data class FaceMarginRules(
    @SerializedName("top_margin_percent")
    val topMarginPercent: Float = 10f,
    
    @SerializedName("face_height_min_percent")
    val faceHeightMinPercent: Float = 50f,
    
    @SerializedName("face_height_max_percent")
    val faceHeightMaxPercent: Float = 70f
)

/**
 * Wrapper for the JSON preset file structure.
 */
data class PresetData(
    @SerializedName("version")
    val version: String,
    
    @SerializedName("last_updated")
    val lastUpdated: String,
    
    @SerializedName("presets")
    val presets: List<PhotoPreset>
)
