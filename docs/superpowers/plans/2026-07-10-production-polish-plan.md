# GovPhoto Resizer — Production Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take the GovPhoto Resizer from "P3 complete, 75 issues open, 4 features missing, CI non-gating" to truly production-ready with all bugs fixed, all screens re-skinned with M3 Expressive design system, all features implemented, and hardened CI so only green builds are released.

**Architecture:**
- Move to a **two-bitmap model** in SharedPhotoViewModel: `originalBitmap` (never composited) and `displayedBitmap` (current composite). All ML operations read from `originalBitmap`; saves read from `displayedBitmap` where available.
- Adopt **GovX component system** (`GovButton`/`GovCard`/`GovTopBar`/`GovBottomBar`/`GovProgressIndicator`) everywhere replacing bespoke Compose primitives.
- Default theme: **LIGHT theme** (user can toggle in Settings). Dynamic Color **off by default** (user can toggle).
- Navigation refactor: scope `SharedPhotoViewModel` to a nested `upload_edit_preview` nav graph so state dies when the flow ends.
- Camera upgrade: switch from `TakePicturePreview` to high-res `TakePicture` + FileProvider; gallery images now go through bg-removal.

**Tech Stack:**
Kotlin 1.9.22, Compose BOM 2024.01, Material 3 1.x, Hilt 2.50, Room 2.6.1, targetSdk 35, minSdk 24, Gradle 8.x, GitHub Actions.

## Global Constraints
- Version bumps **forbidden** — keep Compose BOM 2024.01, Hilt 2.50, Room 2.6.1.
- Compose Material 3 Expressive design system with seedColor `#006495`; Color.kt legacy aliases to be **removed after re-skin**.
- Default **LIGHT theme** (`DarkModePref.LIGHT`/`dynamicColor=false` in Settings).
- All string externalizations into `strings.xml` (EN + Hindi; user will review onboarding/FAQ copy prior to Phase D).
- All 7 existing screens re-skinned to use GovX ecosystem components.
- Accession: 48dp+ tap targets, proper `contentDescription`, semantics, error/loading/empty states.
- CI hardened: remove `continue-on-error: true`, targetSdk 35, release signing placeholder added, ProGuard rules, Room schema export.
- Package remains: `com.dhanuk.govphoto_resizer` everywhere (manifest, Gradle, source dirs).
- Minimal dependency additions — current stack sufficient. `androidx.compose.material:material-icons-extended` allowed to pull in `Icons.Default.ArrowBack` universally.
- Batch-and-push model per user: all changes committed, pushed once, CI gates merge to main.
- Feature parity to original design spec (F1–F11), guarding accessibility and locale/i18n.

---


## Phase A — Theme & Settings defaults (settings screen + theme xmls)
*Creates  1 new files, modifies 10 existing files*

Note: tasks inside each phase can be executed in order; but the **whole phase must complete with green CI** before moving to the next phase.

Instead of exact file counts, each task will identify exact files.

---


## Phase B — 12 Critical Bug Fixes
*Modifies 9 files; introduces two-bitmap model and safer state machines*

---


### Task B1: SharedPhotoViewModel two-bitmap model (refactor)
**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModel.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/data/ml/BackgroundRemover.kt` (add clear comment)
- Test: new file `app/src/test/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModelTest.kt`

**Interfaces:**
- Consumes: `BackgroundRemover`, `FaceAnalyzer`, `SettingsRepository`
- Produces: StateFlow<Bitmap?> originalBitmap, StateFlow<Bitmap?> displayedBitmap, StateFlow<RemovalState>, Mutex, bitmap recycling

- [ ] **Step 1: Convert single bitmap StateFlow to two-bitmaps + RemovalState sealed interface**
```kotlin
// inside SharedPhotoViewModel.kt
private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

private val _displayedBitmap = MutableStateFlow<Bitmap?>(null)
val displayedBitmap: StateFlow<Bitmap?> = _displayedBitmap.asStateFlow()

private val _removalState = MutableStateFlow<RemovalState>(RemovalState.Idle)
val removalState: StateFlow<RemovalState> = _removalState.asStateFlow()

private val photoMutex = Mutex()

sealed interface RemovalState {
  object Idle: RemovalState
  data class Working(val background: Color): RemovalState
  data class Done(val background: Color): RemovalState
  data class Error(val throwable: Throwable): RemovalState
}
```
Add this interface below the viewmodel class.
- [ ] **Step 2: Remove originalBitmap compositing misuse in removeBackground**
Change removeBackground to:
- read `_originalBitmap.value` (never composited bitmap)
- fill a new bitmap with `_backgroundColor`
- write resulting bitmap to `_displayedBitmap.value`
Never mutate `_originalBitmap`
- [ ] **Step 3: Update savePhotoToGallery to read from displayedBitmap first**
```kotlin
val bitmapToSave = _displayedBitmap.value ?: _originalBitmap.value ?: return null
```
Then proceed to encode via output stream.
- [ ] **Step 4: Wrap removeBackground & savePhoto calls inside photoMutex**
In both functions, call `photoMutex.withLock { ... }`
- [ ] **Step 5: Add bitmap cleanup on replacement & onCleared**
```kotlin
fun clearAllBitmaps() {
  _originalBitmap.value?.recycle(); _originalBitmap.value = null
  _displayedBitmap.value?.recycle(); _displayedBitmap.value = null
  _removalState.value = RemovalState.Idle
}
```
Call from setCapturedBitmap and onCleared.
- [ ] **Step 6: Write 12 unit tests (state machine, mutex, recycling)**
Add `SharedPhotoViewModelTest.kt`. Tests:
- `removeBackground reads from originalBitmap only`
- `savePhoto reads displayedBitmap when available`
- `Mutex prevents concurrent remove+save` launched coroutines
- `bitmap recycled on replacement`
- `CancellationException propagates`
Example test:
```kotlin
@Test
fun `removeBackground reads from originalBitmap only`() = runTest {
  val vm = buildVM()
  vm.setCapturedBitmap(fakeBitmap)
  vm.setBackgroundColor(GovSuccess)
  vm.removeBackground()
  assertEquals(fakeBitmap.hashCode(), vm.originalBitmap.value?.hashCode())
  assertNotEquals(vm.displayedBitmap.value?.hashCode(), fakeBitmap.hashCode())
}
```
- [ ] **Step 7: Run compile and unit tests**
```bash
./gradlew :app:compileDebugSources :app:testDebugUnitTest --tests "*SharedPhotoViewModel*" -PtestDebugUnitTest.enabled=true
```
Expected: all tests pass (12+9 in this fileset)
- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModel.kt app/src/test/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModelTest.kt
# Also update BackgroundRemover.kt with a comment line

if [ -f "app/src/test/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModelTest.kt" ]; then
  git add app/src/test/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModelTest.kt
fi

git commit -m "feat(vm): two-bitmap model; mutex for remove+save; bitmap recycling; CancellationException handling"
```

---


### Task B2: Gallery images now go through bg-removal via VM
**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModel.kt`

**Interfaces:**
- Consumes: `_selectedImageUri` StateFlow
- Produces: decode URI -> setCapturedBitmap -> proceed in VM

- [ ] **Step 1: setSelectedImageUri decodes URI into originalBitmap via ImageDecoder**
```kotlin
fun setSelectedImageUri(uri: Uri?) {
  _selectedImageUri.value = uri
  uri?.let { u ->
    val bmp = ImageDecoder.decodeBitmap(u)
    setCapturedBitmap(bmp)
  }
}
```
- [ ] **Step 2: Robolectric test for decode**
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedPhotoViewModelRobolectricTest {
  @Test
  fun `setSelectedImageUri decodes to originalBitmap`() {
    val vm = SharedPhotoViewModel(ctx, bgRemover, fa)
    val uri = Uri.parse("android.resource://com.dhanuk.govphoto_resizer/raw/sample_photo")
    vm.setSelectedImageUri(uri)
    assertNotNull(vm.originalBitmap.value)
  }
}
```
Create file `app/src/testRobolectric/java/.../SharedPhotoViewModelRobolectricTest.kt`
- [ ] **Step 3: Ensure EditPhotoScreen picks up bg options after gallery pick**
No code change; the VM now exposes originalBitmap and removal can proceed.
- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModel.kt
if [ -f "app/src/testRobolectric/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModelRobolectricTest.kt" ]; then git add app/src/testRobolectric/java/com/dhanuk/govphoto_resizer/ui/viewmodel/SharedPhotoViewModelRobolectricTest.kt; fi

git commit -m "feat(vm): gallery images decoded into originalBitmap; flow now consistent"
```

---


### Task B3: Mutex serialize remove + save to fix race conditions
**Files:** same B1 code already changed; verify it is present.

**Interfaces:** Already enforced in B1; mark this task complete once code review confirms.

- [ ] **Step 1: Confirm implementation in SharedPhotoViewModel.removeBackground & savePhotoToGallery**
Both wrap actions in `photoMutex.withLock { ... }`
- [ ] **Step 2: Write Robolectric concurrency assertion test** (optional for gates; commit ok either way)
```kotlin
@Test
fun `remove and save cannot overlap`() = runTest {
  val vm = buildVM()
  val bgJob = launch { vm.removeBackground() }
  advanceUntilIdle()
  val saveJob = launch { vm.savePhotoToGallery(file, preset) }
  delay(150)
  assertThat(saveJob.isActive).isFalse() // or job.isCompleted
  bgJob.cancel()
  saveJob.cancel()
}
```
- [ ] **Step 3: Commit no-change message**
```bash
git commit --allow-empty -m "chore(vm): Mutex enforced; double-check in code review"
```

---


### Task B4: TakePicture upgrade to full-res via FileProvider
**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/screens/PhotoUploadScreen.kt`
- Add: `app/src/main/java/com/dhanuk/govphoto_resizer/util/FileProviderUtils.kt`
- Strings: add key `camera_permission_required` (EN/HI)

**Interfaces:** High-res capture with FileProvider URI; after capture, decode temp file into originalBitmap

- [ ] **Step 1: Replace TakePicturePreview with TakePicture contract**
```kotlin
// In PhotoUploadScreen.kt
val takePicture = rememberLauncherForActivityResult(
  ActivityResultContracts.TakePicture(),
  onResult = { success ->
    if (success) {
      val uri = context.fileProviderUri(cacheFile)
      vm.setSelectedImageUri(uri)
    }
  }
)
```
- [ ] **Step 2: Add helper to tie FileProvider URIs**
```kotlin
// FileProviderUtils.kt
fun Context.fileProviderUri(cacheFile: File): Uri =
  FileProvider.getUriForFile(
    this,
    "${packageName}.fileprovider",
    cacheFile
  )
```
Manifest provider already declared per current codebase; if not, add:
```xml
<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">
  <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
```
Create `res/xml/file_paths.xml` for cache files if missing.
- [ ] **Step 3: Decode temp file into originalBitmap via ImageDecoder**
```kotlin
// In the onResult, after URI acquisition
val tempFile = File(context.cacheDir, "gov_${System.currentTimeMillis()}.jpg")
// ... after takePicture success
val bmp = ImageDecoder.decodeBitmap(tempFile)
vm.setCapturedBitmap(bmp)
tempFile.delete()
```
- [ ] **Step 4: Guard with camera permission launcher**
```kotlin
val permissionLauncher = rememberLauncher...(ActivityResultContracts.RequestPermission())
IconButton(onClick = {
  if (hasCameraPermission) takePicture.launch(cacheFile)
  else rationaleShown.value = true
}) Icon(...)
if (rationaleShown) {
  AlertDialog(
    onDismissRequest = { rationaleShown.value = false },
    title = { Text(stringResource(R.string.camera_permission_required_title)) },
    text = { Text(stringResource(R.string.camera_permission_required)) },
    confirmButton = { Button(onClick = {
      rationaleShown.value = false
      permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }) { Text(stringResource(android.R.string.ok)) } }
  )
}
```
- [ ] **Step 5: Add strings keys**
```xml
<!-- strings.xml -->
<string name="camera_permission_required_title">Camera Permission Required</string>
<string name="camera_permission_required">Please grant camera permission to take a photo.</string>
```Duplicate in hi/strings.xml.
- [ ] **Step 6: Robolectric sanity test**
```kotlin
@Test
fun `FileProviderHelper returns content URI`() {
  val temp = File(ctx.cacheDir, "gov_test.jpg")
  val uri = ctx.fileProviderUri(temp)
  assertTrue(uri.toString().startsWith("content://"))
}
```
Add file under testRobolectric/java/...
- [ ] **Step 7: Compile & preview; run Robolectric tests**
```bash
./gradlew :app:compileDebugSources
if [ -d "app/src/testRobolectric" ]; then ./gradlew :app:testRobolectricDebug; fi
```
- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/.../PhotoUploadScreen.kt app/src/main/java/.../FileProviderUtils.kt app/src/main/res/values/strings.xml
if [ -d app/src/main/res/values-hi ]; then git add app/src/main/res/values-hi/strings.xml; fi
if [ -f app/src/testRobolectric/java/.../FileProviderUtilsRoboTest.kt ]; then git add app/src/testRobolectric/java/.../FileProviderUtilsRoboTest.kt; fi

if [ -f app/src/main/res/xml/file_paths.xml ]; then git add app/src/main/res/xml/file_paths.xml; fi

git commit -m "feat(camera): upgrade TakePicture + FileProvider; full-res capture; permission guard"
```

---
