package com.govphoto.resizer.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govphoto.resizer.data.model.PhotoPreset
import com.govphoto.resizer.data.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Shared ViewModel for managing photo state across screens.
 * Handles photo selection, editing, compression, and saving.
 */
@HiltViewModel
class SharedPhotoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetRepository: PresetRepository
) : ViewModel() {
    
    // Selected image state
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()
    
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()
    
    // Preset configuration
    private val _selectedPreset = MutableStateFlow<PhotoPreset?>(null)
    val selectedPreset: StateFlow<PhotoPreset?> = _selectedPreset.asStateFlow()
    
    private val _selectedPresetName = MutableStateFlow<String?>(null)
    val selectedPresetName: StateFlow<String?> = _selectedPresetName.asStateFlow()
    
    // Editing parameters
    private val _backgroundColor = MutableStateFlow(BackgroundColor.WHITE)
    val backgroundColor: StateFlow<BackgroundColor> = _backgroundColor.asStateFlow()
    
    private val _compressionQuality = MutableStateFlow(0.7f)
    val compressionQuality: StateFlow<Float> = _compressionQuality.asStateFlow()
    
    // Result state
    private val _processedImageUri = MutableStateFlow<Uri?>(null)
    val processedImageUri: StateFlow<Uri?> = _processedImageUri.asStateFlow()
    
    private val _fileSizeKb = MutableStateFlow(0)
    val fileSizeKb: StateFlow<Int> = _fileSizeKb.asStateFlow()
    
    // Derived properties
    val aspectRatio: Float
        get() = _selectedPreset.value?.getAspectRatio() ?: 0.8f // Default to 4:5 if no preset
        
    val targetWidth: Int
        get() = _selectedPreset.value?.widthPx ?: 600
        
    val targetHeight: Int
        get() = _selectedPreset.value?.heightPx ?: 750

    /**
     * Set the selected image URI from gallery
     */
    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
        _capturedBitmap.value = null
        calculateEstimatedFileSize()
    }
    
    /**
     * Set the captured bitmap from camera
     */
    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
        _selectedImageUri.value = null
        calculateEstimatedFileSize()
    }
    
    /**
     * Set the selected preset by ID
     */
    fun setSelectedPreset(presetId: String, presetName: String? = null) {
        viewModelScope.launch {
            val preset = withContext(Dispatchers.IO) {
                presetRepository.getPreset(presetId)
            }
            _selectedPreset.value = preset
            _selectedPresetName.value = preset?.examName ?: presetName
            
            // Set default background from preset if available
            preset?.backgroundColor?.let { colorCode ->
                // Simple logic for now - can be expanded to parse hex codes
                if (colorCode.equals("#FFFFFF", ignoreCase = true)) {
                    _backgroundColor.value = BackgroundColor.WHITE
                }
            }
            
            calculateEstimatedFileSize()
        }
    }
    
    fun setBackgroundColor(color: BackgroundColor) {
        _backgroundColor.value = color
    }
    
    fun setCompressionQuality(quality: Float) {
        _compressionQuality.value = quality.coerceIn(0.1f, 1f)
        calculateEstimatedFileSize()
    }
    
    /**
     * Calculate estimated file size based on dimensions and compression
     */
    private fun calculateEstimatedFileSize() {
        val width = targetWidth
        val height = targetHeight
        val quality = _compressionQuality.value
        
        // Approximation: Size = (Pixels * 3 bytes * Quality_Factor) / 1024
        // JPEG compression curve is non-linear, this is a rough estimate
        // quality 1.0 -> ~0.3 bytes per pixel for complex images
        // quality 0.5 -> ~0.05 bytes per pixel
        
        // Improved estimation
        val pixels = width * height
        val bytesPerPixel = when {
            quality > 0.9 -> 0.4
            quality > 0.8 -> 0.25
            quality > 0.6 -> 0.15
            quality > 0.4 -> 0.10
            else -> 0.05
        }
        
        val estimatedBytes = (pixels * bytesPerPixel).toInt()
        _fileSizeKb.value = (estimatedBytes / 1024).coerceAtLeast(10) // Min 10KB
    }
    
    /**
     * Clear all state
     */
    fun clearState() {
        _selectedImageUri.value = null
        _capturedBitmap.value = null
        _selectedPreset.value = null
        _selectedPresetName.value = null
        _backgroundColor.value = BackgroundColor.WHITE
        _compressionQuality.value = 0.7f
        _processedImageUri.value = null
        _fileSizeKb.value = 0
    }
    
    /**
     * Save the processed photo to Gallery using MediaStore
     */
    suspend fun savePhotoToGallery(): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                // Get the bitmap to save (either captured or loaded from URI)
                val originalBitmap = _capturedBitmap.value ?: run {
                    _selectedImageUri.value?.let { uri ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                    }
                }
                
                if (originalBitmap == null) {
                    return@withContext Result.failure(Exception("No image to save"))
                }

                // 1. Resize/Crop (Simplified: Just scaling to target dimensions for now)
                // Real app would implement actual cropping based on UI coordinates
                val scaledBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    targetWidth,
                    targetHeight,
                    true
                )
                
                // 2. Compress
                val outputStream = ByteArrayOutputStream()
                val qualityInt = (_compressionQuality.value * 100).toInt()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, qualityInt, outputStream)
                val imageBytes = outputStream.toByteArray()
                
                // 3. Save to MediaStore
                val filename = "GovPhoto_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.WIDTH, targetWidth)
                    put(MediaStore.Images.Media.HEIGHT, targetHeight)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GovPhoto Resizer")
                }
                
                val imageUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))
                
                context.contentResolver.openOutputStream(imageUri)?.use { stream ->
                    stream.write(imageBytes)
                }
                
                _processedImageUri.value = imageUri
                Result.success(imageUri)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}

enum class BackgroundColor {
    WHITE,
    LIGHT_BLUE,
    TRANSPARENT
}
