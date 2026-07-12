package com.dhanuk.govphoto_resizer.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto_resizer.data.ml.BackgroundRemover
import com.dhanuk.govphoto_resizer.data.ml.FaceAnalysisResult
import com.dhanuk.govphoto_resizer.data.ml.FaceAnalyzer
import com.dhanuk.govphoto_resizer.data.model.PhotoPreset
import com.dhanuk.govphoto_resizer.data.repository.HistoryRepository
import com.dhanuk.govphoto_resizer.data.repository.PresetRepository
import com.dhanuk.govphoto_resizer.data.repository.RecentPresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val TAG = "SharedPhotoViewModel"
private const val MAX_DECODE_DIM = 2048

@HiltViewModel
class SharedPhotoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetRepository: PresetRepository,
    private val historyRepo: HistoryRepository,
    private val recentPresetRepo: RecentPresetRepository,
    private val backgroundRemover: BackgroundRemover,
    private val faceAnalyzer: FaceAnalyzer,
) : ViewModel() {

    private val photoMutex = Mutex()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _displayedBitmap = MutableStateFlow<Bitmap?>(null)
    val displayedBitmap: StateFlow<Bitmap?> = _displayedBitmap.asStateFlow()

    private val _selectedPreset = MutableStateFlow<PhotoPreset?>(null)
    val selectedPreset: StateFlow<PhotoPreset?> = _selectedPreset.asStateFlow()

    private val _selectedPresetName = MutableStateFlow<String?>(null)
    val selectedPresetName: StateFlow<String?> = _selectedPresetName.asStateFlow()

    private val _backgroundColor = MutableStateFlow(BackgroundColor.WHITE)
    val backgroundColor: StateFlow<BackgroundColor> = _backgroundColor.asStateFlow()

    private val _compressionQuality = MutableStateFlow(0.7f)
    val compressionQuality: StateFlow<Float> = _compressionQuality.asStateFlow()

    private val _processedImageUri = MutableStateFlow<Uri?>(null)
    val processedImageUri: StateFlow<Uri?> = _processedImageUri.asStateFlow()

    private val _fileSizeKb = MutableStateFlow(0)
    val fileSizeKb: StateFlow<Int> = _fileSizeKb.asStateFlow()

    private val _isRemovingBackground = MutableStateFlow(false)
    val isRemovingBackground: StateFlow<Boolean> = _isRemovingBackground.asStateFlow()

    private val _removalState = MutableStateFlow<RemovalState>(RemovalState.Idle)
    val removalState: StateFlow<RemovalState> = _removalState.asStateFlow()

    private var faceAnalysisJob: Job? = null
    private var decodeJob: Job? = null

    /** Snapshot of displayedBitmap before the most recent crop, for undo. */
    private var preCropBitmap: Bitmap? = null

    private val _faceAnalysis = MutableStateFlow<FaceAnalysisResult?>(null)
    val faceAnalysis: StateFlow<FaceAnalysisResult?> = _faceAnalysis.asStateFlow()

    private val _customWidth = MutableStateFlow("350")
    val customWidth: StateFlow<String> = _customWidth.asStateFlow()

    private val _customHeight = MutableStateFlow("450")
    val customHeight: StateFlow<String> = _customHeight.asStateFlow()

    private val _customFormat = MutableStateFlow("jpg")
    val customFormat: StateFlow<String> = _customFormat.asStateFlow()

    val aspectRatio: Float
        get() = _selectedPreset.value?.getAspectRatio() ?: 0.8f

    val targetWidth: Int
        get() = _selectedPreset.value?.widthPx ?: 600

    val targetHeight: Int
        get() = _selectedPreset.value?.heightPx ?: 750

    /**
     * The current bitmap to display in UI: composited if available, else original.
     */
    val displayBitmap: StateFlow<Bitmap?>
        get() = _displayedBitmap

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
        recycleBitmaps()
        _displayedBitmap.value = null
        _faceAnalysis.value = null
        _removalState.value = RemovalState.Idle
        uri?.let { decodeUriToOriginalBitmap(it) }
        calculateEstimatedFileSize()
        analyzeFace()
    }

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _selectedImageUri.value = null
        recycleBitmaps()
        _originalBitmap.value = bitmap
        _displayedBitmap.value = null
        _faceAnalysis.value = null
        _removalState.value = RemovalState.Idle
        calculateEstimatedFileSize()
        analyzeFace()
    }

private fun decodeUriToOriginalBitmap(uri: Uri) {
    decodeJob?.cancel()
    decodeJob = viewModelScope.launch(Dispatchers.IO) {
      try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        val maxDim = MAX_DECODE_DIM
        val sampleSize = sequenceOf(1, 2, 4, 8).firstOrNull {
            (options.outWidth / it) <= maxDim && (options.outHeight / it) <= maxDim
        } ?: 8

        val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
          android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(maxDim, maxDim)
            decoder.isMutableRequired = true
          }
        } else {
          val decodeOptions = BitmapFactory.Options().apply {
              inSampleSize = sampleSize
              inMutable = true
          }
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
          }
        }
        _originalBitmap.value = bmp
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode URI to originalBitmap", e)
            }
        }
    }

fun analyzeFace() {
    faceAnalysisJob?.cancel()
    faceAnalysisJob = viewModelScope.launch(Dispatchers.Default) {
      try {
        val bitmap = _displayedBitmap.value ?: _originalBitmap.value
        if (bitmap == null || bitmap.isRecycled) {
          _faceAnalysis.value = null
          return@launch
        }
        _faceAnalysis.value = faceAnalyzer.analyze(bitmap)
      } catch (e: CancellationException) {
        throw e
      } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Face analysis OOM — photo too large, skipping", e)
        _faceAnalysis.value = null
      } catch (e: Exception) {
        Log.w(TAG, "Face analysis failed", e)
        _faceAnalysis.value = null
      }
    }
  }

    private fun decodeSelectedUri(): Bitmap? {
        val uri = _selectedImageUri.value ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val maxDim = MAX_DECODE_DIM
            val sample = sequenceOf(1, 2, 4, 8, 16).firstOrNull {
                (bounds.outWidth / it) <= maxDim && (bounds.outHeight / it) <= maxDim
            } ?: 16
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode image URI for face analysis", e)
            null
        }
    }

    fun setSelectedPreset(presetId: String, presetName: String? = null) {
        viewModelScope.launch {
            val preset = withContext(Dispatchers.IO) {
                presetRepository.getPreset(presetId)
            }
            _selectedPreset.value = preset
            _selectedPresetName.value = preset?.examName ?: presetName

            preset?.backgroundColor?.let { colorCode ->
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

    private fun calculateEstimatedFileSize() {
        val width = targetWidth
        val height = targetHeight
        val quality = _compressionQuality.value

        val pixels = width * height
        val bytesPerPixel = when {
            quality > 0.9 -> 0.4
            quality > 0.8 -> 0.25
            quality > 0.6 -> 0.15
            quality > 0.4 -> 0.10
            else -> 0.05
        }

        val estimatedBytes = (pixels * bytesPerPixel).toInt()
        _fileSizeKb.value = (estimatedBytes / 1024).coerceAtLeast(10)
    }

fun removeBackground() {
    val bitmap = _originalBitmap.value ?: run {
      _selectedImageUri.value?.let { uri ->
        decodeSelectedUri()
      }
    } ?: return
    if (bitmap.isRecycled) {
      Log.w(TAG, "Original bitmap recycled, skipping removal")
      return
    }
    if (_removalState.value is RemovalState.Working) return

    _removalState.value = RemovalState.Working
    _isRemovingBackground.value = true

    viewModelScope.launch(Dispatchers.Default) {
      photoMutex.withLock {
        try {
          val result = backgroundRemover.remove(bitmap, _backgroundColor.value)
          _displayedBitmap.value = result
          _removalState.value = RemovalState.Done
          try {
            _faceAnalysis.value = faceAnalyzer.analyze(result)
          } catch (fe: CancellationException) {
            throw fe
          } catch (fe: OutOfMemoryError) {
            Log.w(TAG, "Face analysis after bg removal OOM", fe)
          } catch (fe: Exception) {
            Log.w(TAG, "Face analysis after bg removal failed", fe)
          }
        } catch (e: CancellationException) {
          _removalState.value = RemovalState.Idle
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Background removal failed", e)
          _removalState.value = RemovalState.Error(e.message ?: "Unknown error")
        } finally {
          _isRemovingBackground.value = false
        }
      }
    }
    }

    fun updateCustomWidth(w: String) { _customWidth.value = w }
    fun updateCustomHeight(h: String) { _customHeight.value = h }
    fun updateCustomFormat(f: String) { _customFormat.value = f }

    /**
     * Crop [source] to the visible region implied by the current zoom/pan,
     * preserving the preset target aspect ratio so the crop is not stretched.
     * Returns the cropped bitmap and replaces [displayedBitmap]; null on failure.
     */
    fun applyCrop(source: Bitmap, scale: Float, offsetX: Float, offsetY: Float): Bitmap? {
        if (source.isRecycled || scale <= 0f) return null
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return null

        val targetAR = aspectRatio
        val minSrcDim = minOf(srcW, srcH)
        val cropW: Int
        val cropH: Int
        if (targetAR >= 1f) {
            cropW = minSrcDim
            cropH = (cropW / targetAR).toInt().coerceIn(1, srcH)
        } else {
            cropH = minSrcDim
            cropW = (cropH * targetAR).toInt().coerceIn(1, srcW)
        }
        val visibleSize = (minSrcDim / scale).toInt().coerceIn(cropW, srcW)
        val halfV = visibleSize / 2
        val centerX = (srcW / 2 - offsetX / scale).toInt().coerceIn(halfV, srcW - halfV)
        val centerY = (srcH / 2 - offsetY / scale).toInt().coerceIn(halfV, srcH - halfV)
        val left = (centerX - cropW / 2).coerceIn(0, srcW - cropW)
        val top = (centerY - cropH / 2).coerceIn(0, srcH - cropH)
        return try {
            val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
            preCropBitmap?.let { if (!it.isRecycled && it !== source) it.recycle() }
            preCropBitmap = source
            _displayedBitmap.value = cropped
            cropped
        } catch (e: Exception) {
            Log.w(TAG, "applyCrop failed", e)
            null
        }
    }

    /** Undo the last crop — restores the bitmap that was visible before [applyCrop]. */
    fun undoCrop(): Boolean {
        val prev = preCropBitmap ?: return false
        if (prev.isRecycled) {
            preCropBitmap = null
            return false
        }
        preCropBitmap = null
        _displayedBitmap.value = prev
        analyzeFace()
        return true
    }

    /**
     * Bake current zoom/pan transform into a new [displayedBitmap] that represents
     * exactly what the user sees on screen (the visible region scaled back up).
     * Use this before navigating to the processed preview so the preview reflects
     * the user's adjustments. Returns true on success.
     */
    fun bakeTransform(scale: Float, offsetX: Float, offsetY: Float): Boolean {
        val source = _displayedBitmap.value ?: _originalBitmap.value ?: return false
        if (source.isRecycled || scale <= 0f) return false
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return false
        val targetAR = aspectRatio
        val cropW: Int
        val cropH: Int
        if (targetAR >= 1f) {
            cropW = minOf(srcW, srcH)
            cropH = (cropW / targetAR).toInt().coerceIn(1, srcH)
        } else {
            cropH = minOf(srcW, srcH)
            cropW = (cropH * targetAR).toInt().coerceIn(1, srcW)
        }
        val halfVW = (cropW / 2f / scale).coerceAtMost(srcW / 2f)
        val halfVH = (cropH / 2f / scale).coerceAtMost(srcH / 2f)
        val centerX = (srcW / 2f - offsetX / scale).coerceIn(halfVW, srcW - halfVW)
        val centerY = (srcH / 2f - offsetY / scale).coerceIn(halfVH, srcH - halfVH)
        val left = (centerX - halfVW).toInt().coerceIn(0, srcW - cropW)
        val top = (centerY - halfVH).toInt().coerceIn(0, srcH - cropH)
        return try {
            val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
            _displayedBitmap.value = cropped
            analyzeFace()
            true
        } catch (e: Exception) {
            Log.w(TAG, "bakeTransform failed", e)
            false
        }
    }

    fun applyCustomPreset() {
        val w = _customWidth.value.toIntOrNull() ?: 350
        val h = _customHeight.value.toIntOrNull() ?: 450
        val fmt = _customFormat.value.lowercase()

        val manualPreset = PhotoPreset(
            id = PhotoPreset.MANUAL_PRESET_ID,
            examName = "Custom Size",
            examNameHi = "मैन्युअल साइज",
            authority = "Manual",
            category = com.dhanuk.govphoto_resizer.data.model.PresetCategory.CUSTOM,
            widthPx = w,
            heightPx = h,
            maxFileSizeKb = 500,
            format = fmt,
            lastUpdated = System.currentTimeMillis().toString()
        )

        _selectedPreset.value = manualPreset
        _selectedPresetName.value = "Custom ($w x $h)"
        calculateEstimatedFileSize()
    }

    fun clearState() {
        _selectedImageUri.value = null
        recycleBitmaps()
        _displayedBitmap.value = null
        _selectedPreset.value = null
        _selectedPresetName.value = null
        _backgroundColor.value = BackgroundColor.WHITE
        _compressionQuality.value = 0.7f
        _processedImageUri.value = null
        _fileSizeKb.value = 0
        _isRemovingBackground.value = false
        _removalState.value = RemovalState.Idle
        _faceAnalysis.value = null
        preCropBitmap?.let { if (!it.isRecycled) it.recycle() }
        preCropBitmap = null
    }

private fun recycleBitmaps() {
    _originalBitmap.value?.let { if (!it.isRecycled) it.recycle() }
    _originalBitmap.value = null
    _displayedBitmap.value?.let { if (!it.isRecycled && it != _originalBitmap.value) it.recycle() }
    _displayedBitmap.value = null
    preCropBitmap?.let { if (!it.isRecycled) it.recycle() }
    preCropBitmap = null
  }

override fun onCleared() {
    super.onCleared()
    faceAnalysisJob?.cancel()
    decodeJob?.cancel()
    recycleBitmaps()
  }

    suspend fun savePhotoToGallery(): Result<Uri> {
        Log.d(TAG, "savePhotoToGallery: Starting save process")
        return withContext(Dispatchers.IO) {
            photoMutex.withLock {
                try {
                    val bitmapToSave = _displayedBitmap.value ?: _originalBitmap.value ?: run {
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

if (bitmapToSave == null || bitmapToSave.isRecycled) {
        Log.e(TAG, "savePhotoToGallery: No bitmap available!")
        return@withLock Result.failure(Exception("No image to save"))
      }

                    Log.d(TAG, "savePhotoToGallery: Bitmap loaded: ${bitmapToSave.width}x${bitmapToSave.height}")
                    var targetW = targetWidth
                    var targetH = targetHeight

                    val format = _selectedPreset.value?.format?.lowercase() ?: "jpg"
                    val compressFormat = if (format == "png") {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }

                    val maxFileSizeBytes = (_selectedPreset.value?.maxFileSizeKb ?: 500) * 1024

                    var quality = (_compressionQuality.value * 100).toInt()
                    val outputStream = ByteArrayOutputStream()
                    var attempts = 0
                    val maxAttempts = 15

                    var currentBitmap = Bitmap.createScaledBitmap(bitmapToSave, targetW, targetH, true)

                    while (attempts < maxAttempts) {
                        outputStream.reset()
                        currentBitmap.compress(compressFormat, quality, outputStream)

                        val currentSize = outputStream.size()

                        if (currentSize <= maxFileSizeBytes) {
                            break
                        }

                        attempts++

if (format == "png") {
          targetW = (targetW * 0.9f).toInt()
          targetH = (targetH * 0.9f).toInt()
          val oldBitmap = currentBitmap
          currentBitmap = Bitmap.createScaledBitmap(bitmapToSave, targetW, targetH, true)
          if (oldBitmap != bitmapToSave) oldBitmap.recycle()
        } else {
          if (quality > 10) {
            quality -= 5
          } else {
            targetW = (targetW * 0.9f).toInt()
            targetH = (targetH * 0.9f).toInt()
            val oldBitmap2 = currentBitmap
            currentBitmap = Bitmap.createScaledBitmap(bitmapToSave, targetW, targetH, true)
            if (oldBitmap2 != bitmapToSave) oldBitmap2.recycle()
          }
        }
                    }

                    val imageBytes = outputStream.toByteArray()

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
                    ) ?: return@withLock Result.failure(Exception("Failed to create MediaStore entry"))

                    val written = context.contentResolver.openOutputStream(imageUri)?.use { stream ->
                        stream.write(imageBytes)
                        true
                    } ?: run {
                        context.contentResolver.delete(imageUri, null, null)
                        false
                    }

                    if (!written) {
                        return@withLock Result.failure(Exception("Failed to write image to MediaStore"))
                    }

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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to record history/recent preset", e)
                    }

                    Result.success(imageUri)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    Result.failure(e)
                }
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
