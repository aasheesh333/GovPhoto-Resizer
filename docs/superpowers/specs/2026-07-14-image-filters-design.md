# Image Filters for Signature/Document Presets

**Date:** 2026-07-14
**Status:** Approved

## Problem

Signature, Thumb, and Document presets currently show the BackgroundSelector
(ML Kit selfie segmentation), which is designed for human subjects and is
useless for scanned text/documents. Users need scan-PDF-style filters (like
CamScanner/TapScanner) to enhance written text images.

## Scope

- **Signature / Thumb / Document presets (49):** Background options replaced by
  6 filters.
- **Photo presets (81):** Background options remain AND filters added (default:
  Original/None). Both can be used simultaneously.

## 6 Filters

| # | Name | Algorithm |
|---|------|-----------|
| 1 | Original | No-op (show source as-is) |
| 2 | Grayscale | Luminance weighting: 0.299R + 0.587G + 0.114B |
| 3 | Binarize | Adaptive threshold (Otsu) — pure black/white document-scan look |
| 4 | Enhance | Contrast boost + unsharp mask (3x3 convolution) for faint text |
| 5 | Lighten | +40 brightness, slight gamma correction for dark photos |
| 6 | High Contrast | S-curve contrast for washed-out scans |

## Architecture: Chained Pipeline

### New State

```
_preFilterBitmap  — "base" image: pristine+rotation (or post-bg-removal for PHOTO).
                    Filter reads from this. Never directly displayed.
_displayedBitmap  — "final" image: base + current filter applied.
                    What UI + bakeTransform see.
_selectedFilter   — ImageFilter enum (default ORIGINAL)
isApplyingFilter  — Boolean StateFlow for loading overlay
```

### Flow

1. Source image decoded → pristine → rotation applied → `setBaseBitmap(rotated)`
2. (Optional, PHOTO only) Background removal → `setBaseBitmap(result)`
3. `setBaseBitmap()` stores `_preFilterBitmap` and calls `reapplyFilter()`
4. `reapplyFilter()`:
   - If filter == ORIGINAL → `_displayedBitmap = _preFilterBitmap` (fast, sync)
   - Else → coroutine on Dispatchers.Default applies filter, sets displayed,
     recycles old displayed (respecting pristine/original/preFilter refs)

### Touch Points Updated

Every existing place that sets `_displayedBitmap` changes to call
`setBaseBitmap()` instead:

| Existing code | Change |
|---|---|
| `applyRotationToDisplayed()` | `setBaseBitmap(rotated)` instead of direct assignment |
| `removeBackground()` completion | `setBaseBitmap(result)` |
| `skipBackgroundRemoval()` | Already calls `applyRotationToDisplayed` → covered |
| `restoreFromState()` (undo/redo) | Calls `applyRotationToDisplayed` + optional `removeBackground` → covered; also restores `_selectedFilter` |
| `resetAllEditsAndRefit()` | Calls `applyRotationToDisplayed(it, 0)` → covered; also resets `_selectedFilter = ORIGINAL` |

## New Files

### `data/ml/ImageFilterProcessor.kt`

- `enum class ImageFilter { ORIGINAL, GRAYSCALE, BINARIZE, ENHANCE, LIGHTEN, HIGH_CONTRAST }`
- `suspend fun apply(source: Bitmap, filter: ImageFilter): Bitmap` — dispatches to
  the appropriate pure function. Returns a new ARGB_8888 bitmap (same size as source).
- Pure functions use `getPixels`/`setPixels` pattern (same as `BackgroundRemover`).
- Otsu's method for BINARIZE threshold.
- Unsharp mask for ENHANCE: blur - source, then source + 0.5 * (source - blurred).

## Modified Files

### `SharedPhotoViewModel.kt`

- Add `_preFilterBitmap`, `_selectedFilter`, `_isApplyingFilter` state
- Add `setBaseBitmap()`, `reapplyFilter()`, `applyFilter()` functions
- Update `applyRotationToDisplayed` to call `setBaseBitmap`
- Update `removeBackground` completion to call `setBaseBitmap`
- Update `EditState` to add `filter: ImageFilter = ImageFilter.ORIGINAL`
- Update `restoreFromState` to restore `_selectedFilter`
- Update `resetAllEditsAndRefit` to reset `_selectedFilter`
- Update `clearState` to reset `_selectedFilter` + recycle `_preFilterBitmap`
- Update `recycleBitmaps` to recycle `_preFilterBitmap` (don't double-recycle)
- `bakeTransform()`: No changes (reads `_displayedBitmap` which already has filter)

### `EditPhotoScreen.kt`

- Conditional rendering:
  - PHOTO: show `BackgroundSelector` + `FilterSelector`
  - Non-PHOTO: show only `FilterSelector`
- New `FilterSelector` composable: 2 rows x 3 tiles, mirrors `BackgroundSelector` style
- `isApplyingFilter` loading overlay on preview (reuse spinner pattern)
- History commits include filter via `EditState.filter`

### `strings.xml` + `values-hi/strings.xml`

- Filter labels: Original, Grayscale, Binarize, Enhance, Lighten, High Contrast
- Section title: "Filters"

## Undo/Redo

- `EditState` gets `filter: ImageFilter = ImageFilter.ORIGINAL`
- `pushHistory`/`commitHistory` capture current filter
- `undoEdit`/`redoEdit` → `restoreFromState` sets `_selectedFilter` then
  `setBaseBitmap` → `reapplyFilter` uses restored filter

## Recycling Rules

Follow existing pattern from `removeBackground()`:
- Never recycle `_pristineOriginalBitmap`, `_originalBitmap`, `_preFilterBitmap`
- Only recycle previously-filtered `displayedBitmap` copies
- Check `!==` before recycling to avoid double-recycle shared refs
