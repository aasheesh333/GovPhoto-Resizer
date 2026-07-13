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
import kotlinx.coroutines.CoroutineExceptionHandler
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

    /**
     * Last-resort safety net for coroutine failures in viewModelScope. If a
     * coroutine throws an uncaught Throwable (e.g. OutOfMemoryError in a
     * bg-removal path we forgot to wrap), we log instead of crashing the app.
     * CancellationException is re-thrown automatically by coroutines, so it
     * won't reach here.
     */
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "viewModelScope uncaught coroutine exception", throwable)
    }

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    /**
     * The TRUE original decoded bitmap — written ONCE at decode time, after EXIF
     * orientation is applied, and never mutated by any edit operation (rotate, bg
     * removal, auto-fit, etc.). Used by the Original tab on PreviewValidationScreen
     * and as the source-of-truth for Reset and Undo. Recycled only by clearState()
     * (full screen-exit cleanup), never by intermediate operations.
     */
    private val _pristineOriginalBitmap = MutableStateFlow<Bitmap?>(null)
    val pristineOriginalBitmap: StateFlow<Bitmap?> = _pristineOriginalBitmap.asStateFlow()

    private val _displayedBitmap = MutableStateFlow<Bitmap?>(null)
    val displayedBitmap: StateFlow<Bitmap?> = _displayedBitmap.asStateFlow()

    /**
     * The baked (physical-cropped) output of `bakeTransform()`. Set when the user
     * taps Continue on EditPhotoScreen — contains ONLY the portion of the image
     * visible inside the preset-box at that moment, cropped to the preset's
     * aspect ratio. Read by PreviewValidationScreen's Processed tab and by
     * `savePhotoToGallery()` as the source of bytes to compress to disk.
     */
    private val _bakedBitmap = MutableStateFlow<Bitmap?>(null)
    val bakedBitmap: StateFlow<Bitmap?> = _bakedBitmap.asStateFlow()

    /**
     * Current rotation accumulator in degrees (0, 90, 180, 270). Incremented by
     * `rotate90()` and used to re-derive the displayed bitmap from the pristine
     * original on each rotation tap (preserves image quality across many
     * rotations — no accumulative sampling loss).
     */
    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees: StateFlow<Int> = _rotationDegrees.asStateFlow()

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

    /**
     * Selected background option as chosen in the Edit screen (NONE/WHITE/
     * STUDIO_BLUE/LIGHT_GREY/GRADIENT/TRANSPARENT). This is UI-owned state but
     * tracked by the history stack so Undo/Redo can revert BackgroundOption changes.
     * The underlying `_backgroundColor` enum is set from this on bg-removal.
     */
    private val _bgOption = MutableStateFlow<BackgroundOption>(BackgroundOption.NONE)
    val bgOption: StateFlow<BackgroundOption> = _bgOption.asStateFlow()

    // ----- Undo/Redo (intent-based) -----
    private val historyStack: MutableList<EditState> = mutableListOf()
    private var historyIdx: Int = -1
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

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
        // reset pristine + rotation — will be re-populated inside decodeUriToOriginalBitmap
        _pristineOriginalBitmap.value?.let { if (!it.isRecycled) it.recycle() }
        _pristineOriginalBitmap.value = null
        _bakedBitmap.value?.let { if (!it.isRecycled) it.recycle() }
        _bakedBitmap.value = null
        _rotationDegrees.value = 0
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
        _pristineOriginalBitmap.value = bitmap
        _rotationDegrees.value = 0
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
        val withExif = bmp?.let { applyExifOrientation(uri, it) }
        _originalBitmap.value = withExif
        // Pristine original — write ONCE here, never mutated by edits. Used by
        // the Original tab on PreviewValidationScreen and as the source for rotating
        // `_displayedBitmap` from a clean baseline (quality-preserving rotations).
        _pristineOriginalBitmap.value = withExif
        // Reset rotation history on fresh image load
        _rotationDegrees.value = 0
        // autoFitToPreset() is invoked by analyzeFace() once face analysis returns,
        // so the displayed bitmap is set to the (rotated-0) pristine version.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode URI to originalBitmap", e)
            }
        }
    }

    /**
     * Sets up the displayed bitmap from the pristine original. NO physical
     * crop is performed here — the displayed bitmap IS the full original
     * image (rotated to current [_rotationDegrees], default 0). The
     * EditPhotoScreen applies a visual `graphicsLayer` zoom/pan so the image
     * fills the preset-aspect-ratio box without stretching. Physical crop to
     * the visible portion happens only when the user taps Continue, via
     * [bakeTransform].
     *
     * This function must be safe to call repeatedly (e.g. on preset change,
     * on Reset, on Undo/Redo). It must not mutate [_pristineOriginalBitmap].
     */
    fun autoFitToPreset() {
        val pristine = _pristineOriginalBitmap.value ?: return
        if (pristine.isRecycled) return
        val rot = _rotationDegrees.value
        applyRotationToDisplayed(pristine, rot)
    }

    /**
     * Sets [_displayedBitmap] to [pristine] rotated by [rot] degrees (0/90/
     * 180/270). Recycles previous displayed if it's not the pristine itself.
     */
    private fun applyRotationToDisplayed(pristine: Bitmap, rot: Int) {
        if (rot == 0) {
            _displayedBitmap.value = pristine
            return
        }
        try {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val rotated = Bitmap.createBitmap(pristine, 0, 0, pristine.width, pristine.height, m, true)
            val prev = _displayedBitmap.value
            _displayedBitmap.value = rotated
            if (prev != null && !prev.isRecycled && prev !== pristine && prev !== _originalBitmap.value) {
                prev.recycle()
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "applyRotationToDisplayed: OOM", oom)
            _displayedBitmap.value = pristine
        } catch (e: Exception) {
            Log.w(TAG, "applyRotationToDisplayed failed", e)
            _displayedBitmap.value = pristine
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

    viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
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
        // Restore displayed to the rotation-correct pristine baseline (no bg removal applied)
        val pristine = _pristineOriginalBitmap.value ?: _originalBitmap.value
        if (pristine != null && !pristine.isRecycled) {
            applyRotationToDisplayed(pristine, _rotationDegrees.value)
        }
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
    /**
     * Called on Continue button in EditPhotoScreen. Physically crops the
     * portion of the displayed bitmap visible inside the preset-ratio box at
     * the current zoom/pan transform, and stores the result in
     * [_bakedBitmap] (NOT `_displayedBitmap` — the latter must remain the
     * full original so back-navigation from PreviewValidation shows the user
     * a fresh editable state). The baked bitmap is later read by
     * PreviewValidation's Processed tab and by `savePhotoToGallery()`.
     */
    fun bakeTransform(scale: Float, offsetX: Float, offsetY: Float): Boolean {
        val source = _displayedBitmap.value ?: _originalBitmap.value ?: return false
        if (source.isRecycled || scale <= 0f) return false
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return false
        val targetAR = aspectRatio
        // The crop rect preserves the preset aspect ratio. Its dimensions are
        // exactly what is visually represented by the preset-ratio box (the
        // "viewport" in the rendered image).
        val cropW: Int
        val cropH: Int
        if (targetAR >= 1f) {
            // Landscape-ish preset box — its width is limited by the smaller
            // source dimension, height is the box-pixel height = width / ratio.
            cropW = minOf(srcW, srcH)
            cropH = (cropW / targetAR).toInt().coerceIn(1, srcH)
        } else {
            // Portrait-ish preset box — analogous.
            cropH = minOf(srcW, srcH)
            cropW = (cropH * targetAR).toInt().coerceIn(1, srcW)
        }
        // At scale=1, the viewport half-width is cropW/2 in source pixels.
        // When user zooms in, the visible area shrinks in source pixels.
        val halfVW = (cropW / 2f / scale).coerceAtMost(srcW / 2f)
        val halfVH = (cropH / 2f / scale).coerceAtMost(srcH / 2f)
        val centerX = (srcW / 2f - offsetX / scale).coerceIn(halfVW, srcW - halfVW)
        val centerY = (srcH / 2f - offsetY / scale).coerceIn(halfVH, srcH - halfVH)
        val left = (centerX - halfVW).toInt().coerceIn(0, srcW - cropW)
        val top = (centerY - halfVH).toInt().coerceIn(0, srcH - cropH)
        val cropPxW = (halfVW * 2).toInt().coerceIn(1, srcW - left)
        val cropPxH = (halfVH * 2).toInt().coerceIn(1, srcH - top)
        return try {
            val cropped = Bitmap.createBitmap(source, left, top, cropPxW, cropPxH)
            val prev = _bakedBitmap.value
            _bakedBitmap.value = cropped
            if (prev != null && !prev.isRecycled && prev !== cropped) prev.recycle()
            // Fresh face analysis on the baked bitmap (for preview-screen checklist).
            // analyze{} is suspend — kick it off in the viewModelScope.
            viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
                try {
                    _faceAnalysis.value = faceAnalyzer.analyze(cropped)
                } catch (fe: CancellationException) {
                    throw fe
                } catch (fe: OutOfMemoryError) {
                    Log.w(TAG, "bakeTransform face analyze OOM", fe)
                } catch (fe: Exception) {
                    Log.w(TAG, "bakeTransform face analyze failed", fe)
                }
            }
            true
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "bakeTransform OOM", oom)
            false
        } catch (e: Exception) {
            Log.w(TAG, "bakeTransform failed", e)
            false
        }
    }

    /** Rotate the currently displayed (and original) bitmap 90° clockwise. */
    fun rotate90() {
        viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            photoMutex.withLock {
                try {
                    val pristine = _pristineOriginalBitmap.value
                    if (pristine == null || pristine.isRecycled) return@withLock
                    // Increment rotation accumulator (mod 360) and re-derive the
                    // displayed bitmap from the pristine source. Quality is
                    // preserved across many rotations — no accumulative sampling
                    // loss because we always rotate from the original.
                    val newRot = (_rotationDegrees.value + 90) % 360
                    _rotationDegrees.value = newRot
                    applyRotationToDisplayed(pristine, newRot)
                    analyzeFace()
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "rotate90 OOM", oom)
                } catch (e: Exception) {
                    Log.w(TAG, "rotate90 failed", e)
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

    // ----------------------------- Undo / Redo -----------------------------
    // History captures the *intent* of each Edit-screen action (zoom, pan,
    // rotate, bg-change, custom-size-change). It does NOT touch the displayed
    // bitmap directly — restoreFromState re-derives the displayed bitmap from
    // the pristine source + rotationDegrees, just like autoFitToPreset.

    /**
     * Push the current edit-window state onto the history stack, discarding
     * any "redo" entries that come after the current index. Cap at 12 entries
     * to keep memory bounded; adjacent dedupe — if the new state equals the
     * topmost, no-op.
     */
    fun pushHistory(state: EditState) {
        if (historyIdx in historyStack.indices) {
            val top = historyStack[historyIdx]
            if (top == state) return
        }
        // Drop any redo entries past historyIdx
        while (historyStack.size > historyIdx + 1) historyStack.removeAt(historyIdx + 1)
        historyStack.add(state)
        // Cap at 12
        while (historyStack.size > 12) {
            historyStack.removeAt(0)
        }
        historyIdx = historyStack.size - 1
        updateUndoRedoState()
    }

    fun undoEdit(): EditState? {
        if (historyIdx <= 0) return null
        historyIdx--
        updateUndoRedoState()
        val state = historyStack[historyIdx]
        restoreFromState(state)
        return state
    }

    fun redoEdit(): EditState? {
        if (historyIdx >= historyStack.size - 1) return null
        historyIdx++
        updateUndoRedoState()
        val state = historyStack[historyIdx]
        restoreFromState(state)
        return state
    }

    /**
     * Apply an EditState to the ViewModel: set _rotationDegrees, _bgOption,
     * _compressionQuality, _customWidth/_customHeight/_customFormat, then
     * re-derive _displayedBitmap by rotating the pristine original to the
     * new rotationDegrees. The scale/offX/offY fields are returned via
     * StateFlow but consumed by EditPhotoScreen's local UI directly (the
     * ViewModel has no concept of "window pixel scale" — only source-pixel
     * rotation matters).
     */
    private fun restoreFromState(state: EditState) {
        _rotationDegrees.value = state.rotationDegrees
        _bgOption.value = state.bgOption
        _compressionQuality.value = state.compression
        _customWidth.value = state.customW
        _customHeight.value = state.customH
        _customFormat.value = state.customFmt
        // Re-derive displayed bitmap from pristine (applyRotationToDisplayed handles 0/90/180/270)
        val pristine = _pristineOriginalBitmap.value
        if (pristine != null && !pristine.isRecycled) {
            applyRotationToDisplayed(pristine, state.rotationDegrees)
        }
    }

    private fun updateUndoRedoState() {
        _canUndo.value = historyIdx > 0
        _canRedo.value = historyIdx in 0 until historyStack.size - 1
    }

    /**
     * Reset all edits — restore displayed bitmap from pristine original,
     * clear rotation, reset bg option + custom inputs, re-trigger auto-fit.
     * Clears the history stack too.
     */
    fun resetAllEditsAndRefit() {
        _rotationDegrees.value = 0
        _bgOption.value = BackgroundOption.NONE
        _compressionQuality.value = 0.7f
        _customWidth.value = "350"
        _customHeight.value = "450"
        _customFormat.value = "jpg"
        _pristineOriginalBitmap.value?.let { if (!it.isRecycled) applyRotationToDisplayed(it, 0) }
        historyStack.clear()
        historyIdx = -1
        updateUndoRedoState()
    }

    fun clearState() {
        _selectedImageUri.value = null
        recycleBitmaps()
        _displayedBitmap.value = null
        _selectedPreset.value = null
        _selectedPresetName.value = null
        _backgroundColor.value = BackgroundColor.WHITE
        _bgOption.value = BackgroundOption.NONE
        _compressionQuality.value = 0.7f
        _rotationDegrees.value = 0
        _processedImageUri.value = null
        _fileSizeKb.value = 0
        _isRemovingBackground.value = false
        _removalState.value = RemovalState.Idle
        _faceAnalysis.value = null
        preCropBitmap?.let { if (!it.isRecycled) it.recycle() }
        preCropBitmap = null
        // Clear undo/redo history
        historyStack.clear()
        historyIdx = -1
        updateUndoRedoState()
    }

private fun recycleBitmaps() {
    _originalBitmap.value?.let { if (!it.isRecycled) it.recycle() }
    _originalBitmap.value = null
    _displayedBitmap.value?.let { if (!it.isRecycled && it != _originalBitmap.value) it.recycle() }
    _displayedBitmap.value = null
    // Pristine is shared with `_originalBitmap` initially — same reference. Don't double-recycle.
    _pristineOriginalBitmap.value?.let { if (!it.isRecycled && it !== _originalBitmap.value) it.recycle() }
    _pristineOriginalBitmap.value = null
    _bakedBitmap.value?.let { if (!it.isRecycled) it.recycle() }
    _bakedBitmap.value = null
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
                // We deep-copy the baked bitmap (or fall back to displayed / original / re-decode).
                var workBitmap: Bitmap? = null
                var scaledBitmap: Bitmap? = null
                try {
                    // Prefer the baked bitmap (output of Continue/bakeTransform).
                    // Fall back to displayed (pre-Continue case, e.g. test or legacy path).
                    val src = _bakedBitmap.value
                        ?: _displayedBitmap.value
                        ?: _originalBitmap.value
                        ?: run {
                            _selectedImageUri.value?.let { uri ->
                                decodeSelectedUri()
                            }
                        }
                    if (src == null || src.isRecycled) {
                        Log.e(TAG, "savePhotoToGallery: No bitmap available")
                        return@withLock Result.failure(Exception("No image to save"))
                    }
                    // Pre-flight: source must be a non-recycled ARGB_8888 (or compatible) bitmap.
                    // Reconfigure to ARGB_8888 if needed (some ImageDecoder paths yield RGB_565
                    // which fails PNG compress with transparent pixels).
                    val srcSafe = if (src.config == Bitmap.Config.ARGB_8888 || src.config == Bitmap.Config.RGBA_F16) {
                        src
                    } else {
                        try { src.copy(Bitmap.Config.ARGB_8888, true) ?: src }
                        catch (oom: OutOfMemoryError) { src }
                    }

                    // Deep copy so concurrent analyzeFace()/rotate90()/autoFit() recycling
                    // the shared bitmap does NOT crash our compress path. ARGB_8888 keeps
                    // transparency for PNG/transparent backgrounds.
                    workBitmap = try {
                        Bitmap.createBitmap(srcSafe.width, srcSafe.height, Bitmap.Config.ARGB_8888).also { copy ->
                            android.graphics.Canvas(copy).drawBitmap(srcSafe, 0f, 0f, null)
                        }
                    } catch (oom: OutOfMemoryError) {
                        Log.e(TAG, "savePhotoToGallery: deep-copy OOM, using source directly", oom)
                        srcSafe
                    } catch (e: Exception) {
                        Log.w(TAG, "savePhotoToGallery: deep-copy failed", e)
                        srcSafe
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
                        val srcRef = _bakedBitmap.value ?: _displayedBitmap.value ?: _originalBitmap.value
                        if (workBitmap != null && workBitmap !== srcRef && !workBitmap!!.isRecycled) {
                            workBitmap!!.recycle()
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}

/**
 * Background-option entry — distinct from `BackgroundColor` enum (which is the
 * underlying pick used by the bg-remover). `NONE` means "skip bg removal"; the
 * other options map to `BackgroundColor` values directly.
 */
enum class BackgroundOption {
    NONE, WHITE, STUDIO_BLUE, LIGHT_GREY, GRADIENT, TRANSPARENT;
    fun toBackgroundColor(): BackgroundColor = when (this) {
        NONE, WHITE -> BackgroundColor.WHITE
        STUDIO_BLUE -> BackgroundColor.STUDIO_BLUE
        LIGHT_GREY -> BackgroundColor.LIGHT_GREY
        GRADIENT -> BackgroundColor.GRADIENT
        TRANSPARENT -> BackgroundColor.TRANSPARENT
    }
}

/**
 * Snapshot of all edit-screen state. ~100 bytes per snapshot. The history
 * stack keeps up to 12 of these; Undo/Redo restores them via
 * [SharedPhotoViewModel.restoreFromState].
 *
 * Note: scale/offX/offY here are the *post-fit* scale (what the UI renders
 * inside the preset box), not the source-bitmap pixel dim. EditPhotoScreen
 * applies them locally after undo/redo.
 */
data class EditState(
    val scale: Float = 1f,
    val offX: Float = 0f,
    val offY: Float = 0f,
    val rotationDegrees: Int = 0,
    val bgOption: BackgroundOption = BackgroundOption.NONE,
    val compression: Float = 0.7f,
    val customW: String = "350",
    val customH: String = "450",
    val customFmt: String = "jpg"
)

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
