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
import kotlinx.coroutines.isActive
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
    private var bgRemovalJob: Job? = null

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
        val srcW = options.outWidth.coerceAtLeast(1)
        val srcH = options.outHeight.coerceAtLeast(1)
        val sampleSize = sequenceOf(1, 2, 4, 8).firstOrNull {
            (srcW / it) <= maxDim && (srcH / it) <= maxDim
        } ?: 8

        // Decode preserving aspect ratio. NEVER force square (setTargetSize(max,max)
        // stretches every photo to 1:1 and is the root cause of "image stretched").
        val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
          android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val iw = info.size.width.coerceAtLeast(1)
            val ih = info.size.height.coerceAtLeast(1)
            val longest = maxOf(iw, ih)
            if (longest > maxDim) {
              val s = maxDim.toFloat() / longest.toFloat()
              decoder.setTargetSize(
                (iw * s).toInt().coerceAtLeast(1),
                (ih * s).toInt().coerceAtLeast(1)
              )
            }
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
        if (rot % 360 == 0) {
            val prev = _displayedBitmap.value
            _displayedBitmap.value = pristine
            if (prev != null && !prev.isRecycled &&
                prev !== pristine &&
                prev !== _originalBitmap.value &&
                prev !== _pristineOriginalBitmap.value
            ) {
                prev.recycle()
            }
            return
        }
        try {
            // Full-bitmap rotate — width/height swap for 90/270, no crop.
            val m = Matrix().apply { postRotate((rot % 360).toFloat()) }
            val rotated = Bitmap.createBitmap(
                pristine, 0, 0, pristine.width, pristine.height, m, true
            )
            val prev = _displayedBitmap.value
            _displayedBitmap.value = rotated
            if (prev != null && !prev.isRecycled &&
                prev !== pristine &&
                prev !== _originalBitmap.value &&
                prev !== _pristineOriginalBitmap.value &&
                prev !== rotated
            ) {
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
    faceAnalysisJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
      try {
        // Wait briefly for the original bitmap to be decoded if a decode is in flight.
        var waitMs = 0
        while (_originalBitmap.value == null && waitMs < 4000) {
          kotlinx.coroutines.delay(100)
          waitMs += 100
        }
        // Snapshot a private copy so ML Kit never races with rotate/recycle.
        val snapshot = photoMutex.withLock {
          val src = _displayedBitmap.value ?: _originalBitmap.value ?: _pristineOriginalBitmap.value
          if (src == null || src.isRecycled) null
          else try {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
          } catch (_: OutOfMemoryError) {
            src
          }
        }
        if (snapshot == null) {
          _faceAnalysis.value = null
          return@launch
        }
        val ownsCopy = snapshot !== _displayedBitmap.value &&
            snapshot !== _originalBitmap.value &&
            snapshot !== _pristineOriginalBitmap.value
        try {
          _faceAnalysis.value = faceAnalyzer.analyze(snapshot)
        } finally {
          if (ownsCopy && !snapshot.isRecycled) {
            try { snapshot.recycle() } catch (_: Throwable) {}
          }
        }
        // After first face pass, set displayed = pristine (no physical crop).
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
    // Prefer rotated displayed (or pristine) — never force re-decode mid-edit.
    val bitmap = _displayedBitmap.value
        ?: _pristineOriginalBitmap.value
        ?: _originalBitmap.value
        ?: return
    if (bitmap.isRecycled) {
      Log.w(TAG, "Source bitmap recycled, skipping removal")
      return
    }
    if (_removalState.value is RemovalState.Working) return

    _removalState.value = RemovalState.Working
    _isRemovingBackground.value = true
    faceAnalysisJob?.cancel()
    bgRemovalJob?.cancel()

    bgRemovalJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
      // Snapshot a private copy so ML Kit never holds a shared bitmap ref.
      val localCopy = try {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
      } catch (oom: OutOfMemoryError) {
        _isRemovingBackground.value = false
        _removalState.value = RemovalState.Error("Out of memory")
        return@launch
      }
      try {
        val result = backgroundRemover.remove(localCopy, _backgroundColor.value)
        photoMutex.withLock {
          if (!isActive) {
            if (result !== localCopy && !result.isRecycled) result.recycle()
            return@withLock
          }
          val prev = _displayedBitmap.value
          _displayedBitmap.value = result
          if (prev != null && !prev.isRecycled &&
              prev !== _pristineOriginalBitmap.value &&
              prev !== _originalBitmap.value &&
              prev !== result
          ) {
            prev.recycle()
          }
          _removalState.value = RemovalState.Done
        }
      } catch (e: CancellationException) {
        _removalState.value = RemovalState.Idle
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Background removal failed", e)
        _removalState.value = RemovalState.Error(e.message ?: "Unknown error")
      } catch (oom: OutOfMemoryError) {
        Log.e(TAG, "Background removal OOM", oom)
        _removalState.value = RemovalState.Error("Out of memory")
      } finally {
        _isRemovingBackground.value = false
        if (localCopy !== bitmap && localCopy !== _displayedBitmap.value && !localCopy.isRecycled) {
          try { localCopy.recycle() } catch (_: Throwable) {}
        }
      }
    }
}

    /** Skip background removal: keep original/source bitmap and mark state idle. */
    fun skipBackgroundRemoval() {
        bgRemovalJob?.cancel()
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
     * Called on Continue. Physically crops the portion of [source] that is
     * visible inside the preset-ratio edit window under the current
     * ContentScale.Crop + graphicsLayer(scale, offset) transform.
     *
     * [boxW]/]/boxH] = edit window size in Compose pixels (from onSizeChanged).
     * [userScale] = graphicsLayer scale (1f = cover-fill).
     * [offsetX]/[offsetY] = graphicsLayer translation in Compose pixels.
     *
     * Result is written to [_bakedBitmap] only — displayed stays full image.
     */
    fun bakeTransform(
        userScale: Float,
        offsetX: Float,
        offsetY: Float,
        boxW: Float,
        boxH: Float
    ): Boolean {
        // Stop concurrent ML so it can't recycle source mid-crop (native crash path).
        faceAnalysisJob?.cancel()
        bgRemovalJob?.cancel()
        faceAnalysisJob = null
        bgRemovalJob = null

        val source = _displayedBitmap.value ?: _originalBitmap.value ?: return false
        if (source.isRecycled || userScale <= 0f) return false
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return false
        val bw = boxW.coerceAtLeast(1f)
        val bh = boxH.coerceAtLeast(1f)

        // Cover scale at userScale=1: image fills box, keeps AR, clips overflow.
        val coverScale = maxOf(bw / srcW, bh / srcH)
        val totalScale = coverScale * userScale.coerceAtLeast(0.01f)

        // Compose graphicsLayer: image laid out fillMaxSize then scaled around
        // center, then translated by (offsetX, offsetY). Viewport center maps
        // back to source as: srcCenter - offset / totalScale.
        val halfVW = (bw / totalScale) / 2f
        val halfVH = (bh / totalScale) / 2f
        val centerX = (srcW / 2f - offsetX / totalScale).coerceIn(halfVW, srcW - halfVW)
        val centerY = (srcH / 2f - offsetY / totalScale).coerceIn(halfVH, srcH - halfVH)

        var left = (centerX - halfVW).toInt()
        var top = (centerY - halfVH).toInt()
        var cropW = (halfVW * 2f).toInt().coerceAtLeast(1)
        var cropH = (halfVH * 2f).toInt().coerceAtLeast(1)
        // Clamp crop rect inside source bounds
        if (left < 0) left = 0
        if (top < 0) top = 0
        if (left + cropW > source.width) cropW = source.width - left
        if (top + cropH > source.height) cropH = source.height - top
        if (cropW < 1 || cropH < 1) return false

        return try {
            // Copy crop under isolation — createBitmap(source, ...) is a view into
            // source pixels; use independent ARGB copy for save safety.
            val region = Bitmap.createBitmap(source, left, top, cropW, cropH)
            val cropped = try {
                region.copy(Bitmap.Config.ARGB_8888, false) ?: region
            } catch (_: OutOfMemoryError) {
                region
            }
            if (cropped !== region && !region.isRecycled) {
                try { region.recycle() } catch (_: Throwable) {}
            }
            val prev = _bakedBitmap.value
            _bakedBitmap.value = cropped
            if (prev != null && !prev.isRecycled && prev !== cropped) prev.recycle()
            // Face analysis on a private copy of baked — never pass baked itself
            // into ML Kit (save path needs an unrecycled baked bitmap).
            faceAnalysisJob?.cancel()
            faceAnalysisJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
                val copy = try {
                    cropped.copy(Bitmap.Config.ARGB_8888, false)
                } catch (_: OutOfMemoryError) {
                    null
                }
                if (copy == null) return@launch
                try {
                    _faceAnalysis.value = faceAnalyzer.analyze(copy)
                } catch (fe: CancellationException) {
                    throw fe
                } catch (fe: OutOfMemoryError) {
                    Log.w(TAG, "bakeTransform face analyze OOM", fe)
                } catch (fe: Exception) {
                    Log.w(TAG, "bakeTransform face analyze failed", fe)
                } finally {
                    if (!copy.isRecycled) try { copy.recycle() } catch (_: Throwable) {}
                }
            }
            true
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "bakeTransform OOM", oom)
            writeCrashFile("bakeTransform OOM", oom)
            false
        } catch (e: Exception) {
            Log.w(TAG, "bakeTransform failed", e)
            writeCrashFile("bakeTransform failed", e)
            false
        }
    }

    /** Durable crash breadcrumb — survives process death better than logcat alone. */
    fun writeCrashFile(label: String, t: Throwable? = null) {
        try {
            val f = java.io.File(context.filesDir, "last_crash.txt")
            f.writeText(
                buildString {
                    appendLine(java.util.Date().toString())
                    appendLine(label)
                    if (t != null) {
                        appendLine(t::class.java.name + ": " + t.message)
                        appendLine(t.stackTraceToString().take(4000))
                    }
                }
            )
            Log.e(TAG, "crash file written: $label", t)
        } catch (_: Throwable) {}
    }

    /**
     * Rotate 90° clockwise. Always re-derives FULL image from pristine
     * (no crop). Does not touch scale/pan — UI should reset framing to
     * cover-fill (scale=1, offset=0) after calling this.
     */
    fun rotate90() {
        faceAnalysisJob?.cancel()
        viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            try {
                photoMutex.withLock {
                    val pristine = _pristineOriginalBitmap.value
                    if (pristine == null || pristine.isRecycled) return@withLock
                    val newRot = (_rotationDegrees.value + 90) % 360
                    _rotationDegrees.value = newRot
                    // Full-image rotate from pristine — never crop.
                    applyRotationToDisplayed(pristine, newRot)
                }
                // Face detect AFTER mutex release on the new full displayed bitmap.
                analyzeFace()
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "rotate90 OOM", oom)
            } catch (e: Exception) {
                Log.w(TAG, "rotate90 failed", e)
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
    /**
     * Seed history with the baseline state so the first undo has somewhere
     * to return to. No-op if history already has entries.
     */
    fun ensureHistoryBaseline(state: EditState) {
        if (historyStack.isNotEmpty()) return
        historyStack.add(state)
        historyIdx = 0
        updateUndoRedoState()
    }

    fun pushHistory(state: EditState) {
        if (historyIdx in historyStack.indices) {
            val top = historyStack[historyIdx]
            if (top == state) return
        }
        // Drop any redo entries past historyIdx
        while (historyStack.size > historyIdx + 1) historyStack.removeAt(historyIdx + 1)
        historyStack.add(state)
        // Cap at 20 (after baseline)
        while (historyStack.size > 20) {
            // Keep index 0 as baseline when possible
            if (historyStack.size > 1) historyStack.removeAt(1) else historyStack.removeAt(0)
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
     * Apply an EditState: rotation/bg/compression/custom fields + re-derive
     * displayed bitmap. scale/offX/offY are applied by EditPhotoScreen from
     * the returned EditState. When bg is NONE, restore pristine+rotation;
     * otherwise re-run bg removal for that option.
     */
    private fun restoreFromState(state: EditState) {
        faceAnalysisJob?.cancel()
        bgRemovalJob?.cancel()
        _rotationDegrees.value = state.rotationDegrees
        _bgOption.value = state.bgOption
        _compressionQuality.value = state.compression
        _customWidth.value = state.customW
        _customHeight.value = state.customH
        _customFormat.value = state.customFmt
        _backgroundColor.value = state.bgOption.toBackgroundColor()
        val pristine = _pristineOriginalBitmap.value
        if (pristine != null && !pristine.isRecycled) {
            applyRotationToDisplayed(pristine, state.rotationDegrees)
        }
        if (state.bgOption != BackgroundOption.NONE) {
            removeBackground()
        } else {
            // Restore pristine+rotation without re-pushing history side effects
            _removalState.value = RemovalState.Idle
            _isRemovingBackground.value = false
            analyzeFace()
        }
    }

    private fun updateUndoRedoState() {
        _canUndo.value = historyIdx > 0
        _canRedo.value = historyIdx in 0 until historyStack.size - 1
    }

    /**
     * Reset all edits — restore displayed bitmap from pristine original,
     * clear rotation, reset bg option + custom inputs, re-seed baseline history.
     */
    fun resetAllEditsAndRefit() {
        faceAnalysisJob?.cancel()
        bgRemovalJob?.cancel()
        _rotationDegrees.value = 0
        _bgOption.value = BackgroundOption.NONE
        _backgroundColor.value = BackgroundColor.WHITE
        _compressionQuality.value = 0.7f
        _customWidth.value = "350"
        _customHeight.value = "450"
        _customFormat.value = "jpg"
        _removalState.value = RemovalState.Idle
        _isRemovingBackground.value = false
        _pristineOriginalBitmap.value?.let { if (!it.isRecycled) applyRotationToDisplayed(it, 0) }
        historyStack.clear()
        historyIdx = -1
        // Baseline so Undo is available after next action
        historyStack.add(
            EditState(
                scale = 1f, offX = 0f, offY = 0f,
                rotationDegrees = 0,
                bgOption = BackgroundOption.NONE,
                compression = 0.7f
            )
        )
        historyIdx = 0
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
    bgRemovalJob?.cancel()
    decodeJob?.cancel()
    recycleBitmaps()
  }

    /**
     * Scale [src] into a new ARGB_8888 bitmap of [tw]x[th] using Canvas (safer
     * than some createScaledBitmap native paths on low-RAM devices).
     */
    private fun scaleBitmapSafe(src: Bitmap, tw: Int, th: Int): Bitmap {
        if (src.width == tw && src.height == th) {
            return src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        }
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        val srcRect = android.graphics.Rect(0, 0, src.width, src.height)
        val dstRect = android.graphics.Rect(0, 0, tw, th)
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        return out
    }

    suspend fun savePhotoToGallery(): Result<Uri> {
        Log.d(TAG, "savePhotoToGallery: Starting save process")
        // Cancel ALL concurrent bitmap consumers BEFORE we touch the baked bitmap.
        // Native ML Kit / Skia holding a recycled buffer is the main silent-kill path.
        faceAnalysisJob?.cancel()
        bgRemovalJob?.cancel()
        faceAnalysisJob = null
        bgRemovalJob = null

        return withContext(Dispatchers.IO) {
            var workBitmap: Bitmap? = null
            var scaledBitmap: Bitmap? = null
            try {
                // Snapshot under mutex so no other job can recycle mid-copy.
                val snapshot: Bitmap = photoMutex.withLock {
                    val src = _bakedBitmap.value
                    if (src == null || src.isRecycled) {
                        writeCrashFile("save: no baked bitmap")
                        throw IllegalStateException("No processed image. Go back and tap Continue.")
                    }
                    try {
                        src.copy(Bitmap.Config.ARGB_8888, false)
                            ?: Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also { c ->
                                android.graphics.Canvas(c).drawBitmap(src, 0f, 0f, null)
                            }
                    } catch (oom: OutOfMemoryError) {
                        writeCrashFile("save: deep-copy OOM", oom)
                        throw Exception("Out of memory — try a smaller photo")
                    }
                }
                workBitmap = snapshot
                val wb = snapshot

                // Target preset size, capped. Prefer preset dims so saved file matches exam.
                val presetW = targetWidth.coerceIn(50, 2048)
                val presetH = targetHeight.coerceIn(50, 2048)
                var targetW = presetW
                var targetH = presetH
                // If baked is tiny, don't upscale past 2x source
                targetW = targetW.coerceAtMost(maxOf(wb.width * 2, 50)).coerceAtMost(2048)
                targetH = targetH.coerceAtMost(maxOf(wb.height * 2, 50)).coerceAtMost(2048)

                val format = _selectedPreset.value?.format?.lowercase() ?: "jpg"
                val isPng = format == "png"
                val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val maxFileSizeBytes = (_selectedPreset.value?.maxFileSizeKb ?: 500) * 1024
                var quality = (_compressionQuality.value * 100).toInt().coerceIn(15, 95)

                scaledBitmap = try {
                    if (wb.width == targetW && wb.height == targetH) wb
                    else scaleBitmapSafe(wb, targetW, targetH)
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "savePhotoToGallery: scale OOM — saving at source size", oom)
                    wb
                } catch (e: Exception) {
                    writeCrashFile("save: scale failed", e)
                    wb
                }

                val toCompress = scaledBitmap ?: wb
                if (toCompress.isRecycled) {
                    writeCrashFile("save: compress target recycled")
                    return@withContext Result.failure(Exception("Image was recycled — try again"))
                }

                val outputStream = ByteArrayOutputStream()
                var attempts = 0
                var done = false
                while (attempts < 12 && !done) {
                    outputStream.reset()
                    val ok = try {
                        toCompress.compress(compressFormat, quality, outputStream)
                    } catch (t: Throwable) {
                        writeCrashFile("save: compress Throwable", t)
                        false
                    }
                    if (!ok || outputStream.size() == 0) {
                        // Retry at lower quality / smaller size once
                        quality = (quality - 10).coerceAtLeast(10)
                        attempts++
                        continue
                    }
                    if (outputStream.size() <= maxFileSizeBytes) {
                        done = true
                    } else {
                        attempts++
                        if (quality > 20) {
                            quality = (quality - 10).coerceAtLeast(10)
                        } else {
                            // Shrink and rebuild scaled
                            targetW = ((targetW * 0.85f).toInt()).coerceAtLeast(50)
                            targetH = ((targetH * 0.85f).toInt()).coerceAtLeast(50)
                            try {
                                val old = scaledBitmap
                                val next = scaleBitmapSafe(wb, targetW, targetH)
                                if (old != null && old !== wb && old !== next && !old.isRecycled) {
                                    try { old.recycle() } catch (_: Throwable) {}
                                }
                                scaledBitmap = next
                            } catch (_: Throwable) {
                                break
                            }
                        }
                    }
                }

                val imageBytes = outputStream.toByteArray()
                if (imageBytes.isEmpty()) {
                    writeCrashFile("save: empty compress output")
                    return@withContext Result.failure(Exception("Failed to compress image"))
                }

                val extension = if (isPng) "png" else "jpg"
                val mimeType = if (isPng) "image/png" else "image/jpeg"
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
                ) ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                var written = false
                try {
                    context.contentResolver.openOutputStream(imageUri)?.use { stream ->
                        stream.write(imageBytes)
                        stream.flush()
                        written = true
                    }
                } catch (e: Exception) {
                    writeCrashFile("save: MediaStore write failed", e)
                }
                if (!written) {
                    try { context.contentResolver.delete(imageUri, null, null) } catch (_: Exception) {}
                    return@withContext Result.failure(Exception("Failed to write image to MediaStore"))
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
                Log.e(TAG, "savePhotoToGallery: failure", t)
                writeCrashFile("save: outer Throwable", t)
                Result.failure(t)
            } finally {
                try {
                    if (scaledBitmap != null && scaledBitmap !== workBitmap && !scaledBitmap!!.isRecycled) {
                        scaledBitmap!!.recycle()
                    }
                } catch (_: Throwable) {}
                try {
                    if (workBitmap != null && !workBitmap!!.isRecycled) {
                        workBitmap!!.recycle()
                    }
                } catch (_: Throwable) {}
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
