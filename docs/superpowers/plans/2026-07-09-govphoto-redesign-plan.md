# GovPhoto Resizer v2 — Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement task-by-task. Steps use `- [ ]` syntax.

## Global Constraints
- Package: `com.dhanuk.govphoto_resizer` everywhere — Gradle `namespace` + `applicationId`, manifest, all Kotlin source dirs.
- App display name: "GovPhoto Resizer" (unchanged `R.string.app_name`).
- Compose BOM 2024.01.00, Material3 1.x — no version bumps this plan.
- minSdk 24 / targetSdk 34 — unchanged.
- New M3 Expressive palette uses a single seedColor; light + dark + Dynamic Color fallback all keyed from same seed.
- OD agent must be `opencode` (only installed agent).
- Each OD run gets exactly 1 retry on failure; on 2nd failure, hand-port that screen using neighbouring tokens; commit comment carries the note.
- No new feature behaviour introduced in P1. After P1, the app does exactly what it did before, with new package name + new theme colours/type/shapes; screens visually unchanged (re-skin happens in P3).
- Legacy color constants are re-added as aliases in P1.5 so existing screens compile untouched; removed in P3.8.

---

# PLAN P1 — Design System, Rebrand & Open Design Mockups

**Goal:** Generate M3 Expressive design system + 14 OD screen mockups, rebrand package to `com.dhanuk.govphoto_resizer`, and land new Compose theme tokens + shared components package so P2/P3 can build screens against it — no behaviour changes.

**Architecture:** OD runs sequentially in one project `govphoto-redesign-v2` per mockup; each produces a phone-framed HTML mockup; we port tokens/components into Compose. Package rename first (mechanical, lowest-risk-when-early).

**Tech Stack:** Open Design (skills `design-consultation`, `artifacts-builder`, agent `opencode`); Android Kotlin 17, Jetpack Compose (M3 BOM 2024.01), Hilt 2.50, AGP compile SDK 34.

---

## Task P1.0 — Scaffold test source set, verify baseline build

**Files:**
- Create: `app/src/test/java/com/dhanuk/govphoto_resizer/.gitkeep`
- (No test written in this task — scaffolding only.)

**Interfaces:** Produces `./gradlew :app:test` infrastructure for the rest of P1.

- [ ] **Step 1: Verify clean baseline build.** Run `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 2: Create the test directory.** `mkdir -p app/src/test/java/com/dhanuk/govphoto_resizer/ui/theme` then `touch app/src/test/java/.gitkeep`.
- [ ] **Step 3: Commit scaffold.** `git add app/src/test && git commit -m "chore: scaffold test source set for P1 redesign"`.

---

## Task P1.1 — Package rename to `com.dhanuk.govphoto_resizer`

**Files:**
- Modify: `app/build.gradle.kts:9` (namespace), `:13` (applicationId)
- Move: `app/src/main/java/com/govphoto/resizer/**` → `app/src/main/java/com/dhanuk/govphoto_resizer/**` (21 Kotlin files)
- Modify: every moved Kotlin file's `package` + `import` lines
- Modify: `proguard-rules.pro` if it references old package
- Test: `app/src/test/java/com/dhanuk/govphoto_resizer/PackageRenameTest.kt`

**Interfaces:** After this task, R class is `com.dhanuk.govphoto_resizer.R` and Hilt-generated code is rooted in `com.dhanuk.govphoto_resizer.*`.

- [ ] **Step 1: Create the new package dir tree.** `mkdir -p app/src/main/java/com/dhanuk/govphoto_resizer/{di,data/model,data/repository,ui/theme,ui/screens,ui/viewmodel,ui/navigation}`
- [ ] **Step 2: Move 21 Kotlin files with git mv.** Use individual `git mv` for each .kt under `app/src/main/java/com/govphoto/resizer/` to its mirror path under `app/src/main/java/com/dhanuk/govphoto_resizer/`. Then `rmdir` the now-empty old package dirs.
- [ ] **Step 3: Rewrite `package` + `import`.** For each file, sed `s/^package com\.govphoto\.resizer/package com.dhanuk.govphoto_resizer/g; s/import com\.govphoto\.resizer/import com.dhanuk.govphoto_resizer/g`.
- [ ] **Step 4: Update `app/build.gradle.kts`** lines 9 + 13 to `namespace = "com.dhanuk.govphoto_resizer"` and `applicationId = "com.dhanuk.govphoto_resizer"`.
- [ ] **Step 5: Update `proguard-rules.pro`.** `grep -n govphoto app/proguard-rules.pro`, replace `com.govphoto.resizer` → `com.dhanuk.govphoto_resizer`.
- [ ] **Step 6: Clean + build.** `./gradlew clean && ./gradlew :app:assembleDebug` — expected `BUILD SUCCESSFUL`. If Hilt/KSP caches stale, `rm -rf app/build ~/.gradle/caches/transforms-3` and re-run.
- [ ] **Step 7: Write `PackageRenameTest.kt`.** Verify `javaClass.`package`?.name` starts with `com.dhanuk.govphoto_resizer`.
- [ ] **Step 8: Run tests.** `./gradlew :app:test` — expected PASS.
- [ ] **Step 9: Commit.** `git add -A && git commit -m "refactor(rename): rebrand package to com.dhanuk.govphoto_resizer"`.

---

## Task P1.2 — OD project & run #1 (design system tokens)

**Files:** none in repo directly (output is OD artifact). Sidecar memo `app/src/main/assets/_od_tokens_notes.md` (gitignored).

**Interfaces:** Produces OD project `govphoto-redesign-v2` with run #1 at `design-system/tokens.html` + `design-system/components.html`. The tokens captured drive P1.4–P1.5.

- [ ] **Step 1: Create OD project.** Tool call `open-design_create_project(name="GovPhoto Resizer v2", id="govphoto-redesign-v2")`.
- [ ] **Step 2: Kick off run #1 with `design-consultation` skill.** Prompt asks for M3 Expressive tokens reference board (seedColor, full tonal palette, light/dark colorSchemes, photo-bg swatches incl. white/studio blue/light grey/gradient/transparent, status colors, M3 Expressive type scale 15 styles, shape scale 4/8/12/16/28/full, motion tokens, components.html). Real gov-photo copy only ("Choose your document", "Compliant with Aadhaar — 4.5cm × 5.6cm • 78 KB"). Phone-framed where phone-native. Output path `design-system/tokens.html`.
- [ ] **Step 3: Poll `get_run` until `succeeded`.** On 1st `failed`: retry once. On 2nd `failed`: proceed to P1.4 using fallback seedColor `#006495` and log "OD design system failed; using fallback seed" in commit.
- [ ] **Step 4: Pull artifact bundle** — `open-design_get_artifact(project="govphoto-redesign-v2", entry="design-system/tokens.html", include="all")`.
- [ ] **Step 5: Save hex/sp values to local memo** `app/src/main/assets/_od_tokens_notes.md` (paste swatches/scales). Append `app/src/main/assets/_od_tokens_notes.md` to `.gitignore`.
- [ ] **Step 6: Commit.** `git add .gitignore && git commit -m "feat(design): OD design system run #1 — M3 Expressive tokens captured"`.

---

## Task P1.3 — OD runs #2–#14 (13 screen mockups, sequential)

**Files:** none in repo. `_od_failures.txt` (gitignored) if any fail.

**Interfaces:** 13 artifacts under `screens/<slug>.html` inside project `govphoto-redesign-v2`. Used by P3 to drive per-screen Compose re-skinning.

**Per-screen content summary (the on-screen sections each mockup must render):**
- 2 `onboarding` — 3-page pager. Pages: "Choose your document — 110+ presets" / "Snap or upload — we crop & validate" / "Auto background removal & compliant export". Skip + Next + Get started + page dots.
- 3 `home` — Header w/ app name + `?` + EN/HI chip. Hero "Quick Upload" (gradient). Recent presets row (3 chips). 2×2 grid: Passport/Aadhaar/PAN/Custom. "Browse all forms" outline button. Bottom nav: Home/Batch/History/Settings.
- 4 `allforms` — Topbar w/ back + search + count. Category chips (All/Identity/Travel/Exams/Banking/Defence/Railways/Education/Jobs/Custom). LazyColumn grouped by category — each row: category icon + exam name + small meta + chevron. Empty-state when no matches.
- 5 `upload` — Topbar "Upload Photo • Step 1/3". Big camera card (green), big gallery card (blue), "Recent preset shortcut" chip row. Guidelines card: face forward / plain background / good lighting — 3 bullets w/ icons.
- 6 `edit` — Topbar "Edit Photo • Step 2/3". Preview w/ face-guide oval (green), rule-of-thirds grid, zoom %, reset+crop FABs. Background selector chips: White/Studio Blue/Light Grey/Gradient/Remove. Compression slider w/ live "~78 KB" pill + target "≤ 100 KB". Advanced disclosure (collapsed): width/height/DPI/format FilterChips. "Continue to Save" CTA bottom.
- 7 `preview` — Topbar "Preview & Validation • Step 3/3" + share. 3-seg progress. Original/Processed tab toggle. Preview card sized to dims w/ "VALID" badge + dim info. Validation checklist: Face detected ✓ / Correct dimensions ✓ / File size bar 78/100 KB green. Save (saffron-equivalent in new palette) CTA + "Retake/Edit" outlined.
- 8 `save-success` — Celebration. Big preview image. "✓ Compliant with Aadhaar" headline. "3.5cm × 4.5cm • 78 KB • JPG" sub. Three CTAs: Share / Save another / View in gallery. Animation note.
- 9 `batch` — Topbar "Batch • Pick variants". Source photo thumbnail (sticky top). Multi-select list of presets (leading checkbox). Footer sticky: "Process N variants" + total est. size.
- 10 `batch-results` — Topbar "Batch complete". List of N rows: thumb + preset name + ✓/✗ compliance + size + Share. "Save all to gallery" CTA. Failures have "Retry" per row.
- 11 `history` — Topbar "History" + search. Timeline grouped by day. Each row: 48dp thumb + preset name + size + time + chevron. Swipe-to-delete state shown. Empty state: icon + "Photos you resize will appear here" + "Try it now" CTA.
- 12 `settings` — Topbar "Settings". Sections in GovCards: Language (EN/HI radio), Appearance (Dynamic Color Switch + Dark mode selector chips System/Light/Dark), Accessibility (Large Buttons Switch + High Contrast Switch), About (Version 1.0.0 + Privacy Policy + Replay onboarding + Help).
- 13 `help` — Topbar "Help". Search. Category tiles grid (Getting started/Your photos/Background & cropping/File sizes/Troubleshooting). FAQ accordion — 6 sample Q/A w/ real gov-photo copy. "Was this helpful?" sticky bottom.
- 14 `help-article` — Topbar back + "How does auto background removal work?". Body: hero image, paragraphs, callout "Tip: use a plain background for best results", numbered steps 1-2-3, related-articles row, "Was this helpful?" thumbs up/down.

**Reusable prompt header (use verbatim in every run):**
`TARGET APP: "GovPhoto Resizer" — Material 3 EXPRESSIVE Android app for Indian citizens to produce government-form-compliant photos. Audience: mix of general public + power users. Languages: English/Hindi. Phone-framed mockup, vertical 9:19.5, ~390×844 viewport. Use the design system at design-system/tokens.html and design-system/components.html — every colour, shape, type, motion value must come from there. NO lorem ipsum — use the exact copy in the SCREEN section below. Must include EN string with HI placeholder in parentheses (e.g. "Choose your document (अपना दस्तावेज़ चुनें)"). Accessibility: 48dp min touch targets, body text 16sp+.\n\nSCREEN:`

- [ ] **Step 1: Run each of 13 mockups sequentially.** For run `i` in `2..14`: `open-design_start_run(project="govphoto-redesign-v2", skill="artifacts-builder", agent="opencode", prompt=<header> + "<row i body>\n\nOutput path: 'screens/<slug i>.html'")`. Then poll `get_run` ~45s cadence until `succeeded` or `failed`. On 1st `failed`: retry once. On 2nd `failed`: append slug to `app/src/main/assets/_od_failures.txt` and continue.
- [ ] **Step 2: Pull artifact for each successful run.** `open-design_get_artifact(project="govphoto-redesign-v2", entry="screens/<slug i>.html", include="auto")`.
- [ ] **Step 3: Commit run log.** `git add app/src/main/assets/_od_failures.txt 2>/dev/null; git commit --allow-empty -m "feat(design): OD mockups #2-#14 generated"`.

---

## Task P1.4 — New Compose Shape + Motion tokens

**Files:**
- Create: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/theme/Motion.kt`
- Test: `app/src/test/java/com/dhanuk/govphoto_resizer/ui/theme/ShapeTest.kt`

**Interfaces:**
- Produces `govShapes: Shapes` (read by Theme.kt in P1.5)
- Produces `govShapeFull: Shape`, `govShapeRectangle: Shape`
- Produces `MotionEasing.Standard`, `MotionEasing.Emphasized`
- Produces `MotionDurations.Short/Medium/Long: Int`
- Produces `GovSpringSpecs.button<T>(): Spring<T>`

- [ ] **Step 1: Write failing `ShapeTest.kt`.** Asserts `govShapes.extraSmall/small/medium/large/extraLarge` are all `RoundedCornerShape` and `govShapeFull == CircleShape`.
- [ ] **Step 2: Run test — expect FAIL.** `./gradlew :app:testDebugUnitTest --tests "*ShapeTest*"` — unresolvable.
- [ ] **Step 3: Create `Shape.kt`.** `govShapes = Shapes(extraSmall=4.dp, small=8.dp, medium=12.dp, large=16.dp, extraLarge=28.dp)`; `val govShapeFull: Shape = CircleShape`; `val govShapeRectangle: Shape = RectangleShape`.
- [ ] **Step 4: Run test — expect PASS.**
- [ ] **Step 5: Create `Motion.kt`.** Standard = `FastOutSlowInEasing`, Emphasized = `CubicBezierEasing(0.2f, 0f, 0f, 1f)`. Durations Short=150, Medium=250, Long=400 (Int consts). `GovSpringSpecs.button<T>() = spring(dampingRatio=DampingRatioMediumBouncy, stiffness=StiffnessMedium)`.
- [ ] **Step 6: Verify build.** `./gradlew :app:assembleDebug` — `BUILD SUCCESSFUL`.
- [ ] **Step 7: Commit.** `feat(theme): add M3 Expressive shape + motion tokens`.

---

## Task P1.5 — New Color tokens + Type applied + Theme Dynamic Color

**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/theme/Color.kt` (replace whole file)
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/theme/Type.kt` (replace whole file)
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/ui/theme/Theme.kt` (replace whole file)
- Modify: `app/src/main/java/com/dhanuk/govphoto_resizer/MainActivity.kt` (theme call site)
- Test: `app/src/test/java/com/dhanuk/govphoto_resizer/ui/theme/M3ExpressiveColorTest.kt`

**Interfaces:**
- Produces `GovSeedColor: Color` (default `Color(0xFF006495)`)
- Produces `govLightColorScheme: ColorScheme`, `govDarkColorScheme: ColorScheme`
- Produces `GovPhotoBgWhite/StudioBlue/LightGrey/GradientA/GradientB/Transparent` for Edit screen
- Produces `AppTypography: Typography` — actually applied via Theme.kt
- Produces `GovPhotoTheme(darkTheme, dynamicColor, content)` — Dynamic Color opt-in on Android 12+
- Adds legacy color aliases (Primary, IndiaGreen, Saffron, etc.) — REMOVED in P3.8

- [ ] **Step 1: Write failing `M3ExpressiveColorTest.kt`.** Asserts `GovSeedColor` is a `Color`, `govLightColorScheme.primary == GovPrimary`, `govLightColorScheme.onPrimary == GovOnPrimary`, and dark-scheme background luminance < 0.3.
- [ ] **Step 2: Run test — expect FAIL** (unresolvable identifiers).
- [ ] **Step 3: Replace `Color.kt`.** Define GovSeedColor + tonal palette (primary/secondary/tertiary/error + containers/on, light and dark) + status (success/warning expressive tones) + photo-bg swatches + `govLightColorScheme` + `govDarkColorScheme`. If `_od_tokens_notes.md` has different hex, prefer those; otherwise use seed `#006495`.
- [ ] **Step 4: Run test — expect PASS.**
- [ ] **Step 5: Replace `Type.kt`** with M3 Expressive 15-style scale (`AppTypography: Typography`).
- [ ] **Step 6: Replace `Theme.kt`** — Dynamic Color opt-in via `dynamicLightColorScheme`/`dynamicDarkColorScheme` on `Build.VERSION.SDK_INT >= S`; otherwise seeded schemes; apply `typography = AppTypography`, `shapes = govShapes`.
- [ ] **Step 7: Verify `MainActivity.kt` gov theme call uses no explicit args** (defaults opt-in Dynamic Color).
- [ ] **Step 8: Build + test — expected to FAIL first time** because existing screens reference legacy `Primary`/`IndiaGreen`/`Saffron`. Fix by appending legacy aliases at the bottom of `Color.kt` (Primary=GovPrimary, IndiaGreen=GovSuccess, Saffron=0xFFFF9933, etc.) mapping every old name used by screens. Re-run `./gradlew clean :app:assembleDebug :app:test` until green.
- [ ] **Step 9: Commit.** `feat(theme): port M3 Expressive tokens + dynamic color + typography wiring`.

**Legacy alias block (add at bottom of new `Color.kt` so existing screens still compile):**
```kotlin
// === Legacy aliases (DEPRECATED — removed in P3 once screens re-skinned)
val Primary = GovPrimary
val PrimaryDark = GovPrimaryDark
val PrimaryLight = GovPrimaryDark
val PrimaryContainer = GovPrimaryContainer
val IndiaGreen = GovSuccess
val Saffron = Color(0xFFFF9933)
val IndiaWhite = GovPhotoBgWhite
val BackgroundLight = GovBackground
val BackgroundDark = GovBackgroundDark
val SurfaceLight = GovSurface
val SurfaceDark = GovSurfaceDark
val TextMainLight = GovOnBackground
val TextMainDark = GovOnBackgroundDark
val TextSecondaryLight = GovOnSurfaceVariant
val TextSecondaryDark = GovOnSurfaceVariantDark
val Success = GovSuccess
val SuccessLight = GovSuccessContainer
val Warning = GovWarning
val WarningLight = GovWarningContainer
val Error = GovError
val ErrorLight = GovErrorContainer
val CategoryBlue = Color(0xFF49A6CC)
val CategoryTeal = Color(0xFF49A69E)
val CategoryOrange = Color(0xFFE07A3F)
val CategoryPurple = GovTertiary
val PhotoBgWhite = GovPhotoBgWhite
val PhotoBgLightBlue = GovPhotoBgStudioBlue
val PhotoBgTransparent = GovPhotoBgTransparent
val BorderLight = GovOutlineVariant
val BorderDark = GovOutlineVariantDark
val DividerLight = GovOutlineVariant
val DividerDark = GovOutlineVariantDark
val CardDark = GovSurfaceDark
```

---

## Task P1.6 — Add `ui/components/` shared components

**Files:**
- Create 5 component files under `app/src/main/java/com/dhanuk/govphoto_resizer/ui/components/`: `GovButton.kt`, `GovCard.kt`, `GovTopBar.kt`, `GovBottomBar.kt`, `GovProgressIndicator.kt`
- Test: `app/src/test/java/com/dhanuk/govphoto_resizer/ui/components/GovButtonTest.kt` (compile-presence smoke)

**Interfaces:**
- `GovButton(text, onClick, modifier, enabled, large)` — Button w/ min-height 48dp (56dp if large=true)
- `GovOutlinedButton(text, onClick, modifier, enabled, large)`
- `GovTextButton(text, onClick, modifier)`
- `GovCard(modifier, content)`, `GovOutlinedCard(modifier, content)`
- `GovTopBar(title, onBack?, actions)` — CenterAlignedTopAppBar, AutoMirrored ArrowBack
- `GovBottomBar(items: List<GovNavItem>, currentRoute, onNavigate)` + `data class GovNavItem(label, icon, route)`
- `GovLinearProgress(progress, modifier)`, `GovCircularProgress(modifier, size=24)`

Complete specifications of each composable (public signatures, default arg values) are in the plan body. Subagent reads the task brief containing full signatures.

- [ ] **Step 1: Write `GovButton.kt`, `GovCard.kt`, `GovTopBar.kt`, `GovBottomBar.kt`, `GovProgressIndicator.kt`** per the plan's full code listings.
- [ ] **Step 2: Write `GovButtonTest.kt`** — compile-presence smoke (no Compose UI test).
- [ ] **Step 3: Build + test.** `./gradlew :app:assembleDebug :app:test` — `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit.** `feat(ui): add GovX shared components package (M3 Expressive, 48dp+ tap targets)`.

---

## Task P1.7 — Final verification of P1

**Files:** none; verification-only.

- [ ] **Step 1: Full clean build + tests.** `./gradlew clean :app:assembleDebug :app:test` — `BUILD SUCCESSFUL`, all tests green.
- [ ] **Step 2: Static scan no Kotlin file references old package.** `grep -rn "com\.govphoto\.resizer" app/src/` — empty.
- [ ] **Step 3: Confirm OD mockups exist.** `open-design_list_files(project="govphoto-redesign-v2")` — ≥14 files under `design-system/` + `screens/`.
- [ ] **Step 4: Tag.** `git tag -a p1-complete -m "P1 complete: design system + rebrand + 14 OD mockups delivered"`.

---

# PLAN P2 — Persistence + ML Features (outline — fully expanded at end of P1)

After P1, package rename is done; P2 layers new behaviour (no visual changes yet). Goal: wire Room persistence + DataStore persistence + working ML Kit bg-removal + working face detection.

**Tasks (to be expanded to full TDD steps at the end of P1):**
- **P2.1** Room: `GovPhotoDatabase` `@Database(entities=[PhotoHistory, RecentPreset], version=1)`. Move entities to `data/local/entity/`. Add `PhotoHistoryDao`, `RecentPresetDao` impl (insert with OnConflictPolicy.REPLACE, query ordered by createdAt DESC LIMIT n, delete, count). Add `DatabaseModule` Hilt module for `@Singleton RoomDatabase` + each DAO. Unit-tests via in-memory Room.
- **P2.2** `HistoryRepository` + `RecentPresetRepository` expose `Flow<List<PhotoHistory>>` + `Flow<List<RecentPreset>>` + add `recordSave(PhotoHistory)`, `bumpRecent(presetId, examName, category)`. Update `SharedPhotoViewModel.savePhotoToGallery` to call these. Unit-test repos with in-memory Room + fake DAO.
- **P2.3** `SettingsRepository(context)` over `preferencesDataStore` w/ keys: language (`en`/`hi`), dynamicColor (bool, default true on Android 12+), darkMode (`system`/`light`/`dark`), largeButtons, highContrast, onboardingComplete, lastPresetId. Expose `Flow<SettingsState>`. Add `SettingsViewModel`. Wire `MainActivity` to read Dynamic Color / Dark mode from Flow → `GovPhotoTheme` real-time. Unit-test with temp DataStore.
- **P2.4** Language toggle wiring (`HomeScreen`, `SettingsScreen`). `MainActivity.createConfigurationContext` overrides `Locale` so `R.string.*` resolve to selected locale. Tests.
- **P2.5** `data/ml/BackgroundRemover.kt`. Extract from `SharedPhotoViewModel.removeBackground`. Real ML Kit compositing: `Segmentation.getClient(SelfieSegmenterOptions SINGLE_IMAGE_MODE)` → `process(InputImage.fromBitmap)` → mask `FloatBuffer` width×height → threshold 0.5 keep subject, else bg colour → 3px Box blur feather at edges (cap radius at width/200) → compose subject over target bg (white/studio blue/light grey/gradient/PNG-alpha for transparent). Add interface `SegmenterClient` so unit-test can fake the SDK (real ML Kit can't run in unit tests). Update `BackgroundColor` enum to `WHITE/STUDIO_BLUE/LIGHT_GREY/GRADIENT/TRANSPARENT` (5 options). Update `SharedPhotoViewModel.removeBackground` to delegate; surface `RemovalState` Flow (Idle/Working/Done/Error). Unit-test `BackgroundRemover` with fake `SegmenterClient` returning a deterministic mask, and pure `composeOver(bitmap, mask, bgColor)` function tested RGB-by-RGB.
- **P2.6** `data/ml/FaceAnalyzer.kt`. Real `FaceDetection.getClient(FaceDetectorOptions ... setPerformanceMode(FAST) setLandmarkMode(ALL) setMinFaceSize(0.15f) ...)`. Returns `data class FaceAnalysisResult(faceCount:Int, bounds:RectF, ovalGuide:RectF, eyesLevel:Float, sizeRatio:Float, issues:List<String>, isWithinMargin:Boolean)`. Define interface `FaceDetectorClient` so ML runtime is fakeable. Unit-test the pure `withinOval(faceBounds, ovalGuide): Boolean` rule with hand-derived bounds. (defer live-detection runtime check to androidTest.)
- **P2.7** Edit screen overlay: face-guide Canvas drawing `FaceAnalyzer.ovalGuide`, stroke green when `isWithinMargin==true`, amber else. Replace Preview's `face detected = always success` with the actual check from `FaceAnalysisResult`.

# PLAN P3 — New Screens + Re-skin + Flows (outline — fully expanded at end of P2)

Goal: Port every screen from its OD mockup into Compose; add onboarding, batch, help+articles, save-success; swap to GovX components; expand nav graph. Build green, tests green.

**Tasks (to be expanded):**
- **P3.1** Onboarding (`OnboardingScreen.kt` + 3 pages in HorizontalPager). Gate on `Settings.onboardingComplete`. New `OnboardingViewModel`.
- **P3.2** Save-success (`SaveSuccessScreen.kt`); replaces toast. Route target of `PreviewValidationScreen.onSaveComplete`.
- **P3.3** Batch flow: `BatchScreen.kt` (preset multi-select) + `BatchViewModel` (sequential, `Flow<BatchProgress>`). Route from Home + Edit "save multiple variants" CTA.
- **P3.4** History re-skin: `HistoryViewModel` reads `HistoryRepository`; shows real timeline; re-uses OD mockup.
- **P3.5** Help: `HelpScreen.kt` (FAQ accordion + category chips) + `HelpArticleScreen.kt` (rich text + thumbs rating). `HelpViewModel` (static content).
- **P3.6** Settings full re-skin against OD mockup; toggles persist via `SettingsViewModel`.
- **P3.7** Re-skin core screens (Home/AllForms/PhotoUpload/Edit/Preview) per OD mockups; swap duplicated TopBar/BottomNav → `GovTopBar`/`GovBottomBar`. Expand `Screen.kt` sealed class + `NavHost.kt` graph with new routes.
- **P3.8** Remove legacy color aliases (bottom of `Color.kt`) once no screen references them.
- **P3.9** Final build + tests + `p3-complete` tag.

---

## Execution
Per subagent-driven-development skill: controller reviews each task, dispatch fixer on Critical/Important findings, final whole-branch review at the end of P3.
