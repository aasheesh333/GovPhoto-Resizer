# Photo Edit Rework — 4 Fixes (Design)

Date: 2026-07-13
Branch: `feat/govphoto-redesign-v2`
Last commit: `d0c43c6` (CI green)

## Problem Statements (user-reported)

1. **Save Photo → instant app crash** on tapping Save in PreviewValidationScreen.
   User clarified: app closes immediately, not a Toast. Current `savePhotoToGallery()`
   has Java `try/catch` for `Exception` and `OutOfMemoryError` but the crash still
   reaches the system. Likely root cause — the bitmap fed to save is in an
   unsafe state (a reference that gets recycled concurrently, or native OOM
   on a very large bitmap that doesn't bubble as `OutOfMemoryError` but as a
   native `Error`/`Throwable`).

2. **Image auto-fill at selection time** — when user picks 4:3 preset and image
   is 16:9, the current `ContentScale.Fit` shows the whole 16:9 image inside the
   4:3 box, leaving empty bars. User wants the image to **auto-fill the 4:3 box**
   at selection time (face-detection-guided best framing), then drag to adjust.

3. **Preview-window-is-final guarantee** — whatever the edit-screen preview box
   shows the user (zoom, pan, rotate, the framed area inside the preset ratio)
   is exactly what should be saved. Currently `bakeTransform()` does its own
   crop on Continue using math; any mismatch between displayed transform and
   the baked output is a bug. The design must ensure the saved file equals the
   pixels visible inside the preview box at Save time.

4. **Custom Size width/height inputs not appearing** — `EditPhotoScreen` shows
   `CustomPresetInputs` only when `selectedPreset?.id == MANUAL_PRESET_ID`.
   But `setSelectedPreset(MANUAL_PRESET_ID)` calls `presetRepository
   .getPreset(MANUAL_PRESET_ID)`, which returns `null` (the manual preset is
   not in the DB), so `selectedPreset` stays `null` and the inputs never show.

## User Decisions

- **Auto-fit timing**: at EditScreen entry (selection time), not on Continue.
- **Custom Size UI**: same slot as other presets — when user taps "Custom Size"
  in AllFormsScreen, EditPhotoScreen should show the existing width/height
  input row at the top so they can fine-tune. Existing `CustomPresetInputs`
  composable will be reused.

## Design

### A. Save crash hardening (Task 1)

Replace the bitmap save path with a **safe re-decode + immutable copy**
strategy:

1. On `savePhotoToGallery()` start, capture the **source uri**
   (`_selectedImageUri.value`) and a snapshot of the target dims.
2. **Re-decode the URI with the target dimensions** directly using
   `ImageDecoder.setTargetSize(targetW, targetH)` — this returns a fresh
   immutable bitmap sized exactly to the preset. No `createScaledBitmap`
   call on the displayed/original (which may be recycled or shared).
3. Apply EXIF orientation to the re-decoded bitmap (existing
   `applyExifOrientation()` helper).
4. **Composite over background** (if bg-removal was applied): take the
   re-decoded bitmap, run `BackgroundRemover.remove(...)` only if
   `_removalState.value == RemovalState.Done`, otherwise just compress the
   re-decoded bitmap directly. **Skip removal during save** — the displayed
   bitmap is already bg-removed; instead, **deep-copy the displayed bitmap**
   if non-null and not recycled, else re-decode.
5. **Wrap entire body in `try { } catch (e: Throwable)`** so *any* error
   (including `VirtualMachineError`, `OutOfOfMemoryError`, native alloc
   failures) becomes a `Result.failure(Throwable)` — no crash path remains.
6. **Cap the bitmap dimensions to 4096px max per side** during re-decode to
   prevent OOM on huge camera photos.

Net effect: save never touches the live `_displayedBitmap`/`_originalBitmap`
— it builds its own private bitmap, compresses it, recycles it before
returning. No race with concurrent `analyzeFace()` / `rotate90()` /
`removeBackground()`.

### B. Auto-fit at selection time (Task 2)

New `autoFitToPreset()` in `SharedPhotoViewModel`, called once after
`decodeUriToOriginalBitmap` completes:

1. Get source bitmap `_originalBitmap.value`.
2. Compute **target crop rectangle** with preset's aspect ratio:
   - If image aspect ratio > preset aspect ratio → crop width
   - If image aspect ratio < preset aspect ratio → crop height
   - Center the crop rect on the **face bounding box** if
     `_faceAnalysis.value` is available, else center on image.
3. `Bitmap.createBitmap(source, left, top, w, h)` → set
   `_displayedBitmap.value = cropped`.
4. Reset scale=1, offset=0.

`EditPhotoScreen` shows this cropped displayed bitmap with `ContentScale.Fit`
inside a box of `aspectRatio(aspectRatio)`.

### C. Preview-window-is-final guarantee (Task 3)

On Continue button click (`EditPhotoScreen` → PreviewValidation), keep
current `bakeTransform()` behavior but **also call `autoFitToPreset()` once
on selection**. Result: the displayed bitmap at save time is already framed
to the preset ratio. `bakeTransform()` only further crops when user zoomed
or panned. No rescale happens at save time — the displayed bitmap is
**already the output**.

For save, instead of upscaling a 1MP image to 600×800, we **preserve aspect
ratio and let the displayed bitmap be saved at its native crop size**. If
the preset specifies exact width/height in px, perform `createScaledBitmap`
on the **cropped displayed bitmap copy** (deep-copied first to avoid
recycle race) — this is unavoidable for exact-pixel output.

So Task A's "re-decode at target size" is replaced by **"deep-copy the
displayed cropped bitmap at target size, then compress"**. This combines
Tasks A and C — the displayed bitmap IS the source of truth, and save
makes a private copy without touching the shared mutable state.

### D. Custom Size width/height inputs (Task 4)

Fix `setSelectedPreset(MANUAL_PRESET_ID, ...)`:

- If `presetId == PhotoPreset.MANUAL_PRESET_ID`, build the PhotoPreset
  from current `_customWidth`/`_customHeight`/`_customFormat` values
  (default 350×450 jpg) and set `_selectedPreset.value = manualPreset`
  directly — **do not call `presetRepository.getPreset()`**.
- Then `calculateEstimatedFileSize()`.

Net effect: tapping "Custom Size" in AllFormsScreen → EditPhotoScreen
shows the `CustomPresetInputs` row, the aspect ratio box updates live as
user types width/height, and `applyCustomPreset()` (already called on
every keystroke) refreshes `_selectedPreset.value`.

### E. Indian exam presets refresh (Task 5, mentioned by user)

User said: "abhi mujhe lag raha hai abhi bhi kai saare sizes galat ya
valide nahi hai, aap firse india ke jitne exams hai unko research karke
json ya jiss nhi format me set hai usko karo". Out of scope for this
spec — needs a separate research + data-file-update pass. Flag in
implementation plan as a follow-up TODO; no code change in this cycle.

## Scope

In-scope (this spec):
- Tasks 1, 2, 3, 4 above

Out-of-scope (separate spec/PR):
- Full Indian exam database refresh (user's point E)
- Persistent custom-size presets across sessions

## Verification

CI green = compile + unit tests + lint passing. Manual QA matrix:
- 16:9 photo, 4:3 preset → preview shows auto-framed, no stretching
- Tap Rotate 90° 4 times → back to original orientation
- Tap Save → no crash, file in `Pictures/GovPhoto Resizer/`
- Tap "Custom Size" in AllForms → EditPhoto shows width/height fields
- Type 600x800 → preview box ratio updates, save produces 600x800 jpg

## Files Affected

- `SharedPhotoViewModel.kt`:
  - `decodeUriToOriginalBitmap()` → call `autoFitToPreset()` after decode
  - New `autoFitToPreset()` — uses face analysis for centered crop
  - `setSelectedPreset()` — MANUAL_PRESET_ID shortcut
  - `savePhotoToGallery()` — re-decode path replaced with deep-copy +
    createScaledBitmap with full Throwable catch
- `EditPhotoScreen.kt` — no new code, maybe re-position `CustomPresetInputs`
  above the preview box for visibility (currently below it)
- No DB migrations needed (no preset table change)

## Risks

- Face-detection-based framing may pick wrong face in group photos.
  Mitigation: use first/largest face; if none, center on image. User can
  still drag.
- Deep-copy of a displayed bitmap for save doubles memory transiently.
  Mitigation: cap source bitmap at MAX_DECODE_DIM=2048; copy is at most
  ~16MB ARGB, well within Android heap.
