package com.dhanuk.govphoto_resizer.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
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

    /**
     * Set true at image selection; cleared once autoFitToPreset() runs after
     * face analysis returns. Guarantees the auto-fit crop uses face bounds
     * when available, but still runs (centered crop) if face analysis fails.
     */
    @Volatile private var autoFitPending: Boolean = false

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
        autoFitPending = true
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
        autoFitPending = true
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
        // Apply EXIF orientation so the bitmap matches what the user saw in the camera preview.
        _originalBitmap.value = bmp?.let { applyExifOrientation(uri, it) }
        // autoFitToPreset() is invoked by analyzeFace() once face analysis returns,
        // so the cropped preview can be centered on the detected face.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode URI to originalBitmap", e)
            }
        }
    }

    /**
     * Crops the original (or currently displayed) bitmap to the selected
     * preset's aspect ratio, centered on the detected face if available,
     * otherwise centered on the image. The result is stored as the
     * displayed bitmap so EditPhotoScreen shows the user the preset-framed
     * image immediately; they can drag/zoom/rotate to fine-tune, and the
     * displayed bitmap at Save time IS the saved output.
     */
    fun autoFitToPreset() {
        val source = _displayedBitmap.value ?: _originalBitmap.value ?: return
        if (source.isRecycled) return
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return

        // Selected preset target aspect ratio (w/h). If no preset, default to 0.8 (portrait).
        val targetAR = _selectedPreset.value?.getAspectRatio() ?: 0.78f

        // Crop rectangle dimensions preserving target aspect ratio.
        var cropW: Int
        var cropH: Int
        if (srcW.toFloat() / srcH.toFloat() > targetAR) {
            // Source is wider than target — fit height, crop width.
            cropH = srcH
            cropW = (srcH * targetAR).toInt().coerceIn(1, srcW)
        } else {
            // Source is taller than target — fit width, crop height.
            cropW = srcW
            cropH = (srcW / targetAR).toInt().coerceIn(1, srcH)
        }

        // Try to center the crop on the detected face bounds (if available).
        var centerX = srcW / 2f
        var centerY = srcH / 2f
        val fa = _faceAnalysis.value
        if (fa != null && fa.bounds != null) {
            // Face bounds may be in the source space already (FaceAnalyzer scales back).
            val b = fa.bounds
            centerX = (b.left + b.right) / 2f
            centerY = (b.top + b.bottom) / 2f
            // Bias upward (human faces read better when there is a little more headroom above).
            // Shift center up by 5% of crop height.
            centerY = (centerY - cropH * 0.05f).coerceIn(cropH / 2f, srcH - cropH / 2f)
        }
        // Clamp center so the crop stays inside.
        centerX = centerX.coerceIn(cropW / 2f, srcW - cropW / 2f)
        centerY = centerY.coerceIn(cropH / 2f, srcH - cropH / 2f)

        val left = (centerX - cropW / 2f).toInt().coerceIn(0, srcW - cropW)
        val top = (centerY - cropH / 2f).toInt().coerceIn(0, srcH - cropH)

        try {
            val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
            // Recycle the previous displayed bitmap if it's distinct from the original
            // to avoid retaining intermediate copies from old rotations/removals.
            val prev = _displayedBitmap.value
            _displayedBitmap.value = cropped
            if (prev != null && !prev.isRecycled && prev !== _originalBitmap.value && prev !== source) {
                prev.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "autoFitToPreset: crop failed", e)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "autoFitToPreset: OOM", oom)
        }
    }

    /**
     * Reads the EXIF orientation tag from [uri] and rotates [bitmap] accordingly.
     * Returns the (possibly new) bitmap with EXIF orientation applied. If no
     * rotation is needed, returns [bitmap] unchanged.
     */
    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } ?: bitmap
        } catch (e: Exception) {
            Log.w(TAG, "EXIF orientation read failed", e)
            bitmap
        }
    }

fun analyzeFace() {
    faceAnalysisJob?.cancel()
    faceAnalysisJob = viewModelScope.launch(Dispatchers.Default) {
      try {
        // Wait briefly for the original bitmap to be decoded if a decode is in flight.
        var waitMs = 0
        while (_originalBitmap.value == null && waitMs < 4000) {
          kotlinx.coroutines.delay(100)
          waitMs += 100
        }
        val bitmap = _displayedBitmap.value ?: _originalBitmap.value
        if (bitmap == null || bitmap.isRecycled) {
          _faceAnalysis.value = null
          return@launch
        }
        _faceAnalysis.value = faceAnalyzer.analyze(bitmap)
        // Once face analysis returns, perform the auto-fit crop to the preset ratio
        // — this is when face bounds are now available, so the centered crop can
        // put the face where the user expects it.
        if (autoFitPending) {
          autoFitPending = false
          autoFitToPreset()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Face analysis OOM — photo too large, skipping", e)
        _faceAnalysis.value = null
        // Fallback: still run auto-fit centered on image center
        if (autoFitPending) {
          autoFitPending = false
          autoFitToPreset()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Face analysis failed", e)
        _faceAnalysis.value = null
        if (autoFitPending) {
          autoFitPending = false
          autoFitToPreset()
        }
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
            }?.let { applyExifOrientation(uri, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode image URI for face analysis", e)
            null
        }
    }

    fun setSelectedPreset(presetId: String, presetName: String? = null) {
        // Custom Size preset is a virtual preset — not in the repository DB.
        // Build it from current custom width/height/format so that EditPhotoScreen
        // shows the CustomPresetInputs row (selectedPreset?.id == MANUAL_PRESET_ID).
        if (presetId == PhotoPreset.MANUAL_PRESET_ID) {
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
            _selectedPresetName.value = "Custom ($w × $h)"
            calculateEstimatedFileSize()
            return
        }
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

    /** Skip background removal: keep original/source bitmap and mark state idle. */
    fun skipBackgroundRemoval() {
        _displayedBitmap.value = _originalBitmap.value
        _removalState.value = RemovalState.Idle
        _isRemovingBackground.value = false
        analyzeFace()
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

    /** Rotate the currently displayed (and original) bitmap 90° clockwise. */
    fun rotate90() {
        viewModelScope.launch(Dispatchers.Default) {
            photoMutex.withLock {
                try {
                    val src = _displayedBitmap.value ?: _originalBitmap.value ?: return@withLock
                    if (src.isRecycled) return@withLock
                    val matrix = Matrix().apply { postRotate(90f) }
                    val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
                    _displayedBitmap.value = rotated
                    // Also rotate the original so future bg-removal uses the corrected orientation.
                    _originalBitmap.value?.let { orig ->
                        if (!orig.isRecycled && orig !== src) {
                            val m2 = Matrix().apply { postRotate(90f) }
                            _originalBitmap.value = Bitmap.createBitmap(orig, 0, 0, orig.width, orig.height, m2, true)
                        } else if (orig === src) {
                            _originalBitmap.value = rotated
                        }
                    }
                    analyzeFace()
                } catch (e: Exception) {
                    Log.w(TAG, "rotate90 failed", e)
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "rotate90 OOM", oom)
                }
            }
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
                // Hold a local bitmap reference that won't be recycled underneath us.
                // We deep-copy the displayed bitmap (or fall back to the original / re-decode).
                var workBitmap: Bitmap? = null
                var scaledBitmap: Bitmap? = null
                try {
                    val src = _displayedBitmap.value ?: _originalBitmap.value ?: run {
                        _selectedImageUri.value?.let { uri ->
                            decodeSelectedUri()
                        }
                    }
                    if (src == null || src.isRecycled) {
                        Log.e(TAG, "savePhotoToGallery: No bitmap available")
                        return@withLock Result.failure(Exception("No image to save"))
                    }

                    // Deep copy so concurrent analyzeFace()/rotate90()/autoFit() recycling
                    // the shared bitmap does NOT crash our compress path. ARGB_8888 keeps
                    // transparency for PNG/transparent backgrounds.
                    workBitmap = try {
                        Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also { copy ->
                            android.graphics.Canvas(copy).drawBitmap(src, 0f, 0f, null)
                        }
                    } catch (oom: OutOfMemoryError) {
                        Log.e(TAG, "savePhotoToGallery: deep-copy OOM, using source directly", oom)
                        src
                    } catch (e: Exception) {
                        Log.w(TAG, "savePhotoToGallery: deep-copy failed", e)
                        src
                    }

                    var targetW = targetWidth.coerceAtLeast(50).coerceAtMost(4096)
                    var targetH = targetHeight.coerceAtLeast(50).coerceAtMost(4096)
                    // Cap side by source bounds so we never upscale a tiny image.
                    val maxSrcSide = maxOf(workBitmap!!.width, workBitmap!!.height)
                    val cap = maxSrcSide.coerceAtMost(4096)
                    if (targetW > cap) targetW = (cap * targetWidth.toFloat() / maxOf(targetWidth, targetHeight)).toInt().coerceAtLeast(50)
                    if (targetH > cap) targetH = (cap * targetHeight.toFloat() / maxOf(targetWidth, targetHeight)).toInt().coerceAtLeast(50)

                    val format = _selectedPreset.value?.format?.lowercase() ?: "jpg"
                    val compressFormat = if (format == "png") Bitmap.CompressFormat.PNG
                                         else Bitmap.CompressFormat.JPEG
                    val maxFileSizeBytes = (_selectedPreset.value?.maxFileSizeKb ?: 500) * 1024
                    var quality = (_compressionQuality.value * 100).toInt().coerceIn(10, 100)

                    scaledBitmap = try {
                        Bitmap.createScaledBitmap(workBitmap!!, targetW, targetH, true)
                    } catch (oom: OutOfMemoryError) {
                        Log.e(TAG, "savePhotoToGallery: scale OOM — saving at source size", oom)
                        workBitmap
                    }

                    val outputStream = ByteArrayOutputStream()
                    var attempts = 0
                    val maxAttempts = 15
                    var done = false
                    while (attempts < maxAttempts && !done) {
                        outputStream.reset()
                        val b = scaledBitmap!!
                        if (b.isRecycled) {
                            Log.e(TAG, "savePhotoToGallery: bitmap recycled mid-compress")
                            break
                        }
                        try {
                            b.compress(compressFormat, quality, outputStream)
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, "savePhotoToGallery: compress failed", e)
                            break
                        }
                        if (outputStream.size() <= maxFileSizeBytes) {
                            done = true
                        } else {
                            attempts++
                            if (format == "png") {
                                targetW = ((targetW * 0.9f).toInt()).coerceAtLeast(50)
                                targetH = ((targetH * 0.9f).toInt()).coerceAtLeast(50)
                                if (targetW == b.width && targetH == b.height) break
                                val old = scaledBitmap
                                scaledBitmap = try { Bitmap.createScaledBitmap(workBitmap!!, targetW, targetH, true) }
                                              catch (oom: OutOfMemoryError) { old }
                                if (old != null && old != workBitmap && old != scaledBitmap) old?.recycle()
                            } else {
                                if (quality > 10) quality -= 5
                                else {
                                    targetW = ((targetW * 0.9f).toInt()).coerceAtLeast(50)
                                    targetH = ((targetH * 0.9f).toInt()).coerceAtLeast(50)
                                    if (targetW == b.width && targetH == b.height) break
                                    val old2 = scaledBitmap
                                    scaledBitmap = try { Bitmap.createScaledBitmap(workBitmap!!, targetW, targetH, true) }
                                                  catch (oom: OutOfMemoryError) { old2 }
                                    if (old2 != null && old2 != workBitmap && old2 != scaledBitmap) old2?.recycle()
                                }
                            }
                        }
                    }

                    val imageBytes = outputStream.toByteArray()
                    if (imageBytes.isEmpty()) {
                        return@withLock Result.failure(Exception("Failed to compress image"))
                    }

                    val extension = if (format == "png") "png" else "jpg"
                    val mimeType = if (format == "png") "image/png" else "image/jpeg"
                    val filename = "GovPhoto_${System.currentTimeMillis()}.$extension"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GovPhoto Resizer")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                    val imageUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    ) ?: return@withLock Result.failure(Exception("Failed to create MediaStore entry"))

                    var written = false
                    try {
                        context.contentResolver.openOutputStream(imageUri)?.use { stream ->
                            stream.write(imageBytes)
                            written = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "savePhotoToGallery: openOutputStream/write failed", e)
                    }
                    if (!written) {
                        try { context.contentResolver.delete(imageUri, null, null) } catch (_: Exception) {}
                        return@withLock Result.failure(Exception("Failed to write image to MediaStore"))
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        try { context.contentResolver.update(imageUri, contentValues, null, null) }
                        catch (e: Exception) { Log.w(TAG, "Could not clear IS_PENDING", e) }
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
                } catch (t: Throwable) {
                    // Catch Throwable, not just Exception — covers VirtualMachineError,
                    // native OOM, RuntimeException aggregates, etc. Never crash the app.
                    Log.e(TAG, "savePhotoToGallery: failure", t)
                    Result.failure(t)
                } finally {
                    // Always clean up our private bitmap copies. Never recycle the
                    // shared _displayedBitmap/_originalBitmap from the save path.
                    try {
                        if (scaledBitmap != null && scaledBitmap != workBitmap && !scaledBitmap!!.isRecycled) {
                            scaledBitmap!!.recycle()
                        }
                    } catch (_: Throwable) {}
                    try {
                        // workBitmap is a deep copy made above (unless it equals src).
                        // Distinguish: if workBitmap is NOT the same instance as src
                        // (i.e. deep-copy succeeded), recycle it. If it equals src, leave alone.
                        val srcRef = _displayedBitmap.value ?: _originalBitmap.value
                        if (workBitmap != null && workBitmap !== srcRef && !workBitmap!!.isRecycled) {
                            workBitmap!!.recycle()
                        }
                    } catch (_: Throwable) {}
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
