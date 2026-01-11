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
    /**
     * Save the processed photo to Gallery using MediaStore
     * STRICTLY enforces file size limit and format.
     */
    suspend fun savePhotoToGallery(): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                // Get the bitmap to save
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

                // Target dimensions
                var targetW = targetWidth
                var targetH = targetHeight
                
                // Target Format
                val format = _selectedPreset.value?.format?.lowercase() ?: "jpg"
                val compressFormat = if (format == "png") {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                
                // Target File Size
                val maxFileSizeBytes = (_selectedPreset.value?.maxFileSizeKb ?: 500) * 1024
                
                // Iterative Compression & Resizing Loop
                var quality = (_compressionQuality.value * 100).toInt()
                val outputStream = ByteArrayOutputStream()
                var attempts = 0
                val maxAttempts = 15 // Prevent infinite loops
                
                var currentBitmap = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                
                while (attempts < maxAttempts) {
                    outputStream.reset()
                    currentBitmap.compress(compressFormat, quality, outputStream)
                    
                    val currentSize = outputStream.size()
                    
                    if (currentSize <= maxFileSizeBytes) {
                        break // Success!
                    }
                    
                    // Size exceeded, need to reduce
                    attempts++
                    
                    if (format == "png") {
                        // PNG is lossless, quality param deals with filter/compression level but usually doesn't reduce size much.
                        // We must reduce dimensions for PNG if size is too big.
                        targetW = (targetW * 0.9f).toInt()
                        targetH = (targetH * 0.9f).toInt()
                        currentBitmap = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                    } else {
                        // JPG: Reduce quality first, then dimensions if quality gets too low
                        if (quality > 10) {
                            quality -= 5 // Reduce quality by 5%
                        } else {
                            // Quality already very low, start reducing dimensions
                            targetW = (targetW * 0.9f).toInt()
                            targetH = (targetH * 0.9f).toInt()
                            currentBitmap = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                        }
                    }
                }
                
                val imageBytes = outputStream.toByteArray()
                
                // Save to MediaStore
                val extension = if (format == "png") "png" else "jpg"
                val mimeType = if (format == "png") "image/png" else "image/jpeg"
                val filename = "GovPhoto_${System.currentTimeMillis()}.$extension"
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.WIDTH, targetW)
                    put(MediaStore.Images.Media.HEIGHT, targetH)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GovPhoto Resizer")
                }
                
                val imageUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))
                
                context.contentResolver.openOutputStream(imageUri)?.use { stream ->
                    stream.write(imageBytes)
                }
                
                // Update final size
                _fileSizeKb.value = imageBytes.size / 1024
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
