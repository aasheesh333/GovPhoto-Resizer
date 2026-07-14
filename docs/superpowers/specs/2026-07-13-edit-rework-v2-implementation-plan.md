# Edit Screen Rework v2 — Final Implementation Plan

Date: 2026-07-13
Branch: `feat/govphoto-redesign-v2`
Last commit: `f8bf8bf` (CI green)

## User's 4 Requirements (confirmed)

1. **Visual-only zoom (no physical crop) in Edit screen** — image select hone ke baad, image **visually zoom** ho ke preset ratio box mein **fill** ho jaye. Image physically crop na ho. Jo portion box ke bahar wo visually cut-off dikhe, lekin bitmap pixels safe rahein.
2. **Continue = bake visible portion → PreviewValidation (current flow)** — Continue dabane par jo image window mein visible hai usko physically crop (bake) karke `_bakedBitmap` mein store karo, Navigate to PreviewValidation. Save button Preview screen par hi rahe.
3. **Original/Processed in PreviewValidation** — Original tab par pristine (untouched) image apne actual aspect ratio mein dikhe; Processed tab par baked (visible portion) dikhe.
4. **Edit screen mein Undo/Redo icons TopAppBar ke "2/3" text ke LEFT mein** — har EditPhoto action (rotate, zoom, pan, bg-change, compression, custom-size) undoable/redoable ho. Intent-based history, ~100 bytes per snapshot.

Plus:

5. **Save crash** — currently "instant app close on Save tap". Defense-in-depth: catch `Throwable` at both VM + UI coroutine boundary; add `CoroutineExceptionHandler` on viewModelScope; pre-flight bitmap-recycled/config-null checks.
6. **Edit screen image window controls** — remove Crop icon, remove Undo-Crop icon; keep `[Reset] [Rotate] [Zoom-] [%] [Zoom+]`. Reset = undo ALL window actions + restore original image + clear history.

## User decisions

- Continue = bake + preview (NOT direct save).
- Undo/Redo = Edit screen only (NOT on Save).
- Original tab box = image's own aspect ratio (NOT preset box).
- History = intent-based (lightweight scalars, not bitmap snapshots).
- Reset behaviour = undo all window actions + restore original from pristine.

## Implementation Details

### 1. `SharedPhotoViewModel.kt`

#### 1.1 Pristine Original Bitmap
- Add `_pristineOriginalBitmap: MutableStateFlow<Bitmap?>` + collector.
- Set ONCE in `decodeUriToOriginalBitmap` after `applyExifOrientation`. Never mutated elsewhere.
- `recycleBitmaps()` does NOT recycle pristine. `clearState()` (full exit) recycles.

#### 1.2 Displayed Bitmap stays FULL original
- `autoFitToPreset()` — REMOVE physical crop. Just sets `_displayedBitmap.value = _pristineOriginalBitmap.value`. Used to signal image loaded.
- `_displayedBitmap` no longer cropped. Always full pristine.

#### 1.3 Baked Bitmap (visible portion after Continue)
- Add `_bakedBitmap: MutableStateFlow<Bitmap?>` + collector `bakedBitmap`.
- `bakeTransform()` writes result to `_bakedBitmap`, NOT `_displayedBitmap`.
- Save flow uses `_bakedBitmap` (instead of `_displayedBitmap`).
- Back button from PreviewValidation → EditPhoto: `_bakedBitmap` stays as-is, but `_displayedBitmap` is full original → user re-edits, re-taps Continue → new `_bakedBitmap` overwrites old.

#### 1.4 Rotate accumulator (quality-preserving)
- Add `_rotationDegrees: MutableStateFlow<Int>` (0/90/180/270).
- `rotate90()`:
  - Increment by 90 mod 360.
  - Create new `_displayedBitmap` from `_pristineOriginalBitmap` with `Matrix.postRotate(rotationDegrees)`.
  - Recycle previous displayed (if !== pristine).
  - Quality preserved across multiple rotations (re-derived from pristine each tap).

#### 1.5 Reset action
- `resetAllEditsAndRefit()`:
  - `_displayedBitmap.value = _pristineOriginalBitmap.value`
  - `_rotationDegrees.value = 0`
  - `_backgroundColor.value = WHITE`
  - `_removalState.value = Idle`
  - `_compressionQuality.value = 0.7f`
  - `_customWidth.value = "350"; _customHeight.value = "450"; _customFormat.value = "jpg"`
  - `historyStack.clear(); historyIdx = -1`
  - updateUndoRedoState()
- EditPhotoScreen also resets `scale=1f, offsetX=0f, offsetY=0f, selectedBackground = BackgroundOption.NONE`.

#### 1.6 Intent-based EditHistory

```kotlin
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
) // ~100 bytes

private val historyStack: MutableList<EditState> = mutableListOf()
private var historyIdx: Int = -1
private val _canUndo = MutableStateFlow(false)
private val _canRedo = MutableStateFlow(false)

fun pushHistory(state: EditState) {
  if (historyStack.isNotEmpty() && historyStack[historyIdx] == state) return  // dedupe
  while (historyStack.size > historyIdx + 1) historyStack.removeAt(historyStack.size - 1)
  historyStack.add(state)
  historyIdx = historyStack.size - 1
  if (historyStack.size > 12) { historyStack.removeAt(0); historyIdx-- }
  updateUndoRedoState()
}

fun undoEdit() {
  if (historyIdx <= 0) return
  historyIdx--
  restoreFromState(historyStack[historyIdx])
  updateUndoRedoState()
}

fun redoEdit() {
  if (historyIdx >= historyStack.size - 1) return
  historyIdx++
  restoreFromState(historyStack[historyIdx])
  updateUndoRedoState()
}

private fun restoreFromState(s: EditState) {
  _rotationDegrees.value = s.rotationDegrees
  val src = _pristineOriginalBitmap.value
  if (src != null && !src.isRecycled) {
    if (s.rotationDegrees == 0) {
      _displayedBitmap.value = src
    } else {
      try {
        val m = Matrix().apply { postRotate(s.rotationDegrees.toFloat()) }
        _displayedBitmap.value = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
      } catch (oom: OutOfMemoryError) { _displayedBitmap.value = src }
    }
  }
  setCompressionQuality(s.compression)
  updateCustomWidth(s.customW); updateCustomHeight(s.customH); updateCustomFormat(s.customFmt)
  // bgOption is UI state — synced via editState (_bgOption value)
}
```

`editState` exposed as StateFlow<EditState> — EditPhotoScreen collects it and applies
scale/offX/offY/bgOption to local state when undo/redo fired (state != current).

#### 1.7 removeBackground
- Continues to set `_displayedBitmap` to result (on Bg option change).
- Does NOT mutate `_pristineOriginalBitmap`.
- Undo past a bg-removal state: restoreFromState recomputes displayedBitmap from pristine + rotation without bg removal → effectively reverts.

#### 1.8 Save crash defense
- Pre-flight: `if (src.config == null || src.isRecycled || src.byteCount <= 0) Result.failure(...)`
- Already `catch (t: Throwable)` — keep.
- Add `CoroutineExceptionHandler` on viewModelScope to log instead of crashing.
- Image source: `_bakedBitmap` (not `_displayedBitmap`).

### 2. `EditPhotoScreen.kt`

#### 2.1 Photo image render (NO stretch)
- Box: `Modifier.aspectRatio(preset.aspectRatio)` — same ratio as preset.
- Image: `_displayedBitmap` (or fallback to `AsyncImage(imageUri)`) with `ContentScale.Crop` inside that box.
- Initial `scale` computed:
  ```
  boxWPx = preset.widthPx, boxHPx = preset.heightPx (or compute from screen density)
  imgWPx = displayedBitmap.width, imgHPx = displayedBitmap.height
  initialScale = max(boxWPx / imgWPx, boxHPx / imgHPx)
  ```
  This makes the image fill the box without stretching (small dimension determines fill, larger is overflow).
- Graphicslayer with `(scaleX, scaleY, translationX, translationY)` — applied via Compose `graphicsLayer`.

#### 2.2 LaunchedEffect on preset change
```kotlin
LaunchedEffect(selectedPreset?.id, originalBitmap) {
  if (originalBitmap != null && !originalBitmap.isRecycled) {
    val ob = originalBitmap
    val bs = preset?.widthPx?.toFloat() ?: 350f
    val bh = preset?.heightPx?.toFloat() ?: 450f
    scale = maxOf(bs / ob.width, bh / ob.height)  // initial fill scale
    offsetX = 0f; offsetY = 0f
    sharedViewModel.pushHistory(currentEditState())
  }
}
```
(Note: when displayedBitmap changes after rotate, the LaunchedEffect keyed on originalBitmap won't fire — but the rotate handler itself pushes history & sets scale appropriately.)

#### 2.3 Top bar Undo/Redo in actions slot
```kotlin
KeyboardActions {  ... }.also {
  // Actually TopAppBar's `actions` slot:
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = { sharedViewModel.undoEdit() }, enabled = canUndo) {
      Icon(Icons.Default.Undo, contentDescription = "Undo")
    }
    IconButton(onClick = { sharedViewModel.redoEdit() }, enabled = canRedo) {
      Icon(Icons.Default.Redo, contentDescription = "Redo")
    }
    Text("2/3", ...)
  }
}
```

#### 2.4 Image window — removed buttons
- Drop `onCrop` parameter + button.
- Drop `onUndoCrop` parameter + button.
- Drop `canUndoCrop` parameter.
- Control row order: `[Reset] [Rotate] [Zoom-] [%] [Zoom+]`.

#### 2.5 Reset button — wired to `sharedViewModel.resetAllEditsAndRefit()`
+ local UI state reset (`scale = initialScale`, offset=0,0, background=NONE).

#### 2.6 All action handlers push history

Rotate tap:
```kotlin
onClick = {
  sharedViewModel.pushHistory(currentEditState())
  sharedViewModel.rotate90()
  // also need to recompute scale based on rotated image dimensions
}
```

Zoom button tap:
```kotlin
onClick = {
  sharedViewModel.pushHistory(currentEditState())
  scale = (scale * 1.1f).coerceIn(0.5f, 5f)
  sharedViewModel.pushHistory(currentEditState())  // after-state push
}
```
(Adjacent dedupe in `pushHistory` ensures duplicates don't pile up.)

Pan gesture end:
```kotlin
detectTransformGestures { _, pan, zoom, _ ->
  onTransform(zoom, pan.x, pan.y)
  sharedViewModel.pushHistory(currentEditState())  // debounced via dedupe
}
```

Bg chip tap:
```kotlin
onClick = {
  sharedViewModel.pushHistory(currentEditState())
  onOptionSelected(it)
}
```

Compression slider:
```kotlin
onValueChange = {
  compressionValue = it
  sharedViewModel.setCompressionQuality(it)
  // debounce via adjacent dedupe
  sharedViewModel.pushHistory(currentEditState())
}
```

Custom size keystroke (`CustomPresetInputs`):
```kotlin
onValueChange = {
  viewModel.updateCustomWidth(it)
  viewModel.applyCustomPreset()
  viewModel.pushHistory(currentEditState())
}
```

#### 2.7 Collect editState
- After Undo/Redo, scale/offX/offY/rotationDegrees need to sync from `editState` to local UI.
- Use `LaunchedEffect(editState)` that emits only when undo/redo fires.
- Trick: track `lastEditState` and compare; if external (undo/redo) caused state change, sync local vars.

### 3. `PreviewValidationScreen.kt`

#### 3.1 Use `_bakedBitmap`
```kotlin
val bakedBitmap by sharedViewModel.bakedBitmap.collectAsState()
val displayed by sharedViewModel.displayedBitmap.collectAsState()
val processedBitmap = bakedBitmap ?: displayed  // fallback if user came without Continue
```

#### 3.2 Use `_pristineOriginalBitmap`
```kotlin
val pristine by sharedViewModel.pristineOriginalBitmap.collectAsState()
val origBmp by sharedViewModel.originalBitmap.collectAsState()
val originalBitmap = pristine ?: origBmp
```

#### 3.3 Save button — full Throwable wrap
```kotlin
onClick = {
  if (!isSaving) {
    isSaving = true
    scope.launch {
      val result = try { sharedViewModel.savePhotoToGallery() }
                   catch (t: Throwable) { Result.failure(t) }
      isSaving = false
      result.fold(
        onSuccess = {
          Toast.makeText(context, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
          onSaveComplete()
        },
        onFailure = {
          Toast.makeText(context, "Failed: ${it.message ?: it::class.simpleName}", Toast.LENGTH_LONG).show()
        }
      )
    }
  }
}
```

### 4. (Optional) `MainActivity.kt`
- Register `Thread.setDefaultUncaughtExceptionHandler` in DEBUG-only for crash logging.

## Files Affected

- `SharedPhotoViewModel.kt` (major): pristine, baked, history, rotate90 rewrite, autoFitToPreset rewrite, resetAllEditsAndRefit, save source switch.
- `EditPhotoScreen.kt` (major): image render with visual-zoom, TopAppBar Undo/Redo, remove Crop/Undo-Crop buttons, Reset wiring, pushHistory call-sites, editState collection.
- `PreviewValidationScreen.kt` (minor): use baked/pristine, save button Throwable wrap.
- `MainActivity.kt` (optional small): UncaughtExceptionHandler in DEBUG.

## QA matrix

1. Aadhaar (3.5×4.5) preset → load 16:9 image → EditScreen: image fills box, no bars, no stretch.
2. Zoom in / pan around → image moves in real time; full data still in memory (visible box overflow clipped only visually).
3. Continue → PreviewValidation: Processed shows baked visible portion (3.5×4.5 ratio); Original shows full 16:9 image.
4. Retake → back to Edit → original 16:9 restored, can re-zoom/re-rotate.
5. Rotate 3×, Undo → 2 rotations (quality preserved since derived from pristine); Redo → 3.
6. Reset button → original position, rotation 0, history cleared.
7. Background chip change, Undo → reverted to NONE.
8. Save → no crash (catch Throwable at UI boundary); Toast shows success or failure-with-reason.
9. Re-edit after Continue → re-bake → re-save, behavior consistent.

## Verification

CI green expected. Manual QA on device per matrix above. Local gradle builds are NOT attempted (host crash issue — all diagnosis via code review + CI).
