package com.govphoto.resizer.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Shared ViewModel for managing photo state across screens.
 * This ViewModel survives configuration changes and is shared between
 * PhotoUploadScreen, EditPhotoScreen, and PreviewValidationScreen.
 */
@HiltViewModel
class SharedPhotoViewModel @Inject constructor() : ViewModel() {
    
    // Selected image URI from gallery
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()
    
    // Captured bitmap from camera
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()
    
    // Selected preset ID
    private val _selectedPresetId = MutableStateFlow<String?>(null)
    val selectedPresetId: StateFlow<String?> = _selectedPresetId.asStateFlow()
    
    // Selected preset name for display
    private val _selectedPresetName = MutableStateFlow<String?>(null)
    val selectedPresetName: StateFlow<String?> = _selectedPresetName.asStateFlow()
    
    // Background option
    private val _backgroundColor = MutableStateFlow(BackgroundColor.WHITE)
    val backgroundColor: StateFlow<BackgroundColor> = _backgroundColor.asStateFlow()
    
    // Compression quality (0.0 to 1.0)
    private val _compressionQuality = MutableStateFlow(0.7f)
    val compressionQuality: StateFlow<Float> = _compressionQuality.asStateFlow()
    
    // Processed image (after editing)
    private val _processedImageUri = MutableStateFlow<Uri?>(null)
    val processedImageUri: StateFlow<Uri?> = _processedImageUri.asStateFlow()
    
    // File size in KB
    private val _fileSizeKb = MutableStateFlow(0)
    val fileSizeKb: StateFlow<Int> = _fileSizeKb.asStateFlow()
    
    /**
     * Set the selected image URI from gallery picker
     */
    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
        _capturedBitmap.value = null // Clear bitmap if gallery image selected
    }
    
    /**
     * Set the captured bitmap from camera
     */
    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
        _selectedImageUri.value = null // Clear URI if camera image captured
    }
    
    /**
     * Set the selected preset
     */
    fun setSelectedPreset(presetId: String, presetName: String? = null) {
        _selectedPresetId.value = presetId
        _selectedPresetName.value = presetName
    }
    
    /**
     * Update background color
     */
    fun setBackgroundColor(color: BackgroundColor) {
        _backgroundColor.value = color
    }
    
    /**
     * Update compression quality
     */
    fun setCompressionQuality(quality: Float) {
        _compressionQuality.value = quality.coerceIn(0f, 1f)
    }
    
    /**
     * Set processed image URI after editing
     */
    fun setProcessedImageUri(uri: Uri?) {
        _processedImageUri.value = uri
    }
    
    /**
     * Update file size
     */
    fun setFileSizeKb(sizeKb: Int) {
        _fileSizeKb.value = sizeKb
    }
    
    /**
     * Check if an image is selected (either from gallery or camera)
     */
    fun hasImage(): Boolean {
        return _selectedImageUri.value != null || _capturedBitmap.value != null
    }
    
    /**
     * Clear all photo state (reset for new session)
     */
    fun clearState() {
        _selectedImageUri.value = null
        _capturedBitmap.value = null
        _selectedPresetId.value = null
        _selectedPresetName.value = null
        _backgroundColor.value = BackgroundColor.WHITE
        _compressionQuality.value = 0.7f
        _processedImageUri.value = null
        _fileSizeKb.value = 0
    }
}

enum class BackgroundColor {
    WHITE,
    LIGHT_BLUE,
    TRANSPARENT
}
