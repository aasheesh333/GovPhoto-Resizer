package com.dhanuk.govphoto_resizer.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto_resizer.data.ml.BackgroundRemover
import com.dhanuk.govphoto_resizer.data.model.PhotoPreset
import com.dhanuk.govphoto_resizer.data.repository.HistoryRepository
import com.dhanuk.govphoto_resizer.data.repository.PresetRepository
import com.dhanuk.govphoto_resizer.data.repository.RecentPresetRepository
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

private const val TAG = "SharedPhotoViewModel"

/**
 * Shared ViewModel for managing photo state across screens.
 * Handles photo selection, editing, compression, and saving.
 */
@HiltViewModel
class SharedPhotoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetRepository: PresetRepository,
    private val historyRepo: HistoryRepository,
    private val recentPresetRepo: RecentPresetRepository,
    private val backgroundRemover: BackgroundRemover,
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

    // Background Removal State
    private val _isRemovingBackground = MutableStateFlow(false)
    val isRemovingBackground: StateFlow<Boolean> = _isRemovingBackground.asStateFlow()

    private val _removalState = MutableStateFlow<RemovalState>(RemovalState.Idle)
    val removalState: StateFlow<RemovalState> = _removalState.asStateFlow()

    // Custom/Manual Preset State
    private val _customWidth = MutableStateFlow("350")
    val customWidth: StateFlow<String> = _customWidth.asStateFlow()

    private val _customHeight = MutableStateFlow("450")
    val customHeight: StateFlow<String> = _customHeight.asStateFlow()

    private val _customFormat = MutableStateFlow("jpg")
    val customFormat: StateFlow<String> = _customFormat.asStateFlow()

    
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
     * Remove background using ML Kit Selfie Segmentation via [BackgroundRemover].
     */
    fun removeBackground() {
        val bitmap = _capturedBitmap.value ?: return
        if (_removalState.value is RemovalState.Working) return

        _removalState.value = RemovalState.Working
        _isRemovingBackground.value = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = backgroundRemover.remove(bitmap, _backgroundColor.value)
                _capturedBitmap.value = result
                _removalState.value = RemovalState.Done
            } catch (e: Exception) {
                Log.w(TAG, "Background removal failed", e)
                _removalState.value = RemovalState.Error(e.message ?: "Unknown error")
            } finally {
                _isRemovingBackground.value = false
            }
        }
    }

    // Setters for custom manual preset
    fun updateCustomWidth(w: String) { _customWidth.value = w }
    fun updateCustomHeight(h: String) { _customHeight.value = h }
    fun updateCustomFormat(f: String) { _customFormat.value = f }

    fun applyCustomPreset() {
        val w = _customWidth.value.toIntOrNull() ?: 350
        val h = _customHeight.value.toIntOrNull() ?: 450
        val fmt = _customFormat.value.lowercase()
        
        // Create a transient preset for manual mode
        val manualPreset = PhotoPreset(
            id = PhotoPreset.MANUAL_PRESET_ID,
            examName = "Custom Size",
            examNameHi = "मैन्युअल साइज",
            authority = "Manual",
            category = com.dhanuk.govphoto_resizer.data.model.PresetCategory.CUSTOM,
            widthPx = w,
            heightPx = h,
            maxFileSizeKb = 500, // Default max for custom
            format = fmt,
            lastUpdated = System.currentTimeMillis().toString()
        )
        
        _selectedPreset.value = manualPreset
        _selectedPresetName.value = "Custom ($w x $h)"
        calculateEstimatedFileSize()
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
        _isRemovingBackground.value = false
        _removalState.value = RemovalState.Idle
    }
    
    /**
     * Save the processed photo to Gallery using MediaStore
     */
    /**
     * Save the processed photo to Gallery using MediaStore
     * STRICTLY enforces file size limit and format.
     */
    suspend fun savePhotoToGallery(): Result<Uri> {
        Log.d(TAG, "savePhotoToGallery: Starting save process")
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "savePhotoToGallery: URI=${_selectedImageUri.value}, Bitmap=${_capturedBitmap.value != null}")
                
                // Get the bitmap to save
                val originalBitmap = _capturedBitmap.value ?: run {
                    _selectedImageUri.value?.let { uri ->
                        Log.d(TAG, "savePhotoToGallery: Loading bitmap from URI")
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
                    Log.e(TAG, "savePhotoToGallery: No bitmap available!")
                    return@withContext Result.failure(Exception("No image to save"))
                }
                
                Log.d(TAG, "savePhotoToGallery: Bitmap loaded: ${originalBitmap.width}x${originalBitmap.height}")
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

                try {
                    historyRepo.recordSave(
                        HistoryRepository.HistorySave(
                            presetId = _selectedPreset.value?.id ?: "unknown",
                            examName = _selectedPreset.value?.examName ?: "Custom",
                            originalImagePath = _selectedImageUri.value?.toString() ?: "",
                            processedImagePath = imageUri.toString(),
                            fileSizeKb = imageBytes.size / 1024,
                            widthPx = targetW,
                            heightPx = targetH
                        )
                    )
                    recentPresetRepo.recordUse(
                        presetId = _selectedPreset.value?.id ?: "unknown",
                        examName = _selectedPreset.value?.examName ?: "Custom",
                        category = _selectedPreset.value?.category?.name ?: "CUSTOM"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record history/recent preset", e)
                }

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
    STUDIO_BLUE,
    LIGHT_GREY,
    GRADIENT,
    TRANSPARENT,
}

sealed class RemovalState {
    data object Idle : RemovalState()
    data object Working : RemovalState()
    data object Done : RemovalState()
    data class Error(val message: String) : RemovalState()
}
