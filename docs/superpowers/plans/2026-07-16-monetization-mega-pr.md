# Monetization Mega-PR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Firebase Crashlytics+Analytics, AdMob (banner+interstitial+rewarded), RevenueCat paywall (₹79/wk, ₹149/mo, ₹999/yr), and OneSignal push notifications into a single mega-PR on branch `feat/pr2-monetization-mega`.

**Architecture:** SDK init in `GovPhotoApp.onCreate`. Three new repositories (`AdsRepository`, `SubscriptionRepository`, `PushRepository`) injected via Hilt, each backed by `SettingsRepository` for local caching. Banner ads via a reusable composable; interstitial via a single rate-limited controller. Paywall via a new Compose screen route `paywall`. All SDK keys flow through GitHub Actions secrets → `secrets.properties` → `app/secrets.gradle.kts` → `BuildConfig`. CI decodes `GOOGLE_SERVICES_JSON_BASE64` and signs release builds with keystore from secrets.

**Tech Stack:** Firebase Crashlytics 18.6.0 + Analytics 21.5.0; Google UMP 2.2.0; AdMob Play Services Ads 22.6.0; RevenueCat 5.9.0+ (google flavors); OneSignal 5.0.0+; Kotlin 1.9.22; Compose BOM 2024.01.00; Hilt 2.50; AGP 8.2.2.

## Global Constraints

- Package: `com.dhanuk.govphoto` (current); Application class: `GovPhotoApp` with `@HiltAndroidApp`.
- minSdk 24, targetSdk 35, compileSdk 35; Java 17; Compose compiler ext 1.5.8.
- All SDK keys via GitHub Actions secrets → `secrets.properties` (gitignored, populated by `populate-secrets.sh`) → exposed via `app/secrets.gradle.kts` → `BuildConfig` fields. No hardcoded production keys.
- Debug builds use Google official test ad unit IDs as fallback (from `secrets.properties.template`).
- Debug gets a `FORCE_NO_ADS` BuildConfig field `true` — hides all ads in debug for safer screenshots/Play review.
- EN+Hindi strings always added in pairs. `<string name="key">English</string>` in `values/strings.xml`; `<string name="key">Hindi</string>` in `values-hi/strings.xml`.
- 48dp+ tap targets, accessibility `contentDescription` on all iconography.
- No local Gradle builds — CI-only verification. Implementer commits on branch `feat/pr2-monetization-mega`; CI runs on each push.
- No emojis in code or strings.
- Support email `support@dhanuksoftwares.com` (public contact info).
- All 14 GitHub Actions secrets already live:
  `PRIVACY_URL`, `TERMS_URL`, `CONTACT_URL`,
  `ADMOB_APP_ID`, `ADMOB_BANNER_UNIT`, `ADMOB_INTERSTITIAL_UNIT`, `ADMOB_REWARDED_UNIT`,
  `REVENUECAT_API_KEY`, `ONESIGNAL_APP_ID`,
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`,
  `GOOGLE_SERVICES_JSON_BASE64`.
- Pricing: ₹79/wk / ₹149/mo / ₹999/yr (user-confirmed); product IDs `govphoto_pro_weekly`, `govphoto_pro_monthly`, `govphoto_pro_yearly` (must be created in Google Play Console before live purchasing works; paywall still loads with sandbox defaults for now).
- Ad placements: Banner on 5 screens (Home, AllForms, History, Settings, SaveSuccess, flush-bottom, height 50dp, hidden if `isAdFree`); Interstitial after save (rate-limited: never first save, 60s between, ≤3/session); Rewarded "watch ad → 24h ad-free" in Settings.
- Crashlytics keeps existing `last_crash.txt` handler as secondary for one release, then remove.
- Push categories (OneSignal): `RELEASE_NOTES` (default ON), `EXAM_DEADLINES` (default OFF), `SUPPORT_REPLIES` (default ON).
- Merge method chosen by user: merge locally + push main (not squash).

## File Structure

### New files (create):
- `app/src/main/java/com/dhanuk/govphoto/data/ads/AdsRepository.kt` — banner/interstitial/rewarded orchestration, ad-free state.
- `app/src/main/java/com/dhanuk/govphoto/data/ads/InterstitialController.kt` — rate-limited interstitial presentation.
- `app/src/main/java/com/dhanuk/govphoto/data/subscription/SubscriptionRepository.kt` — RevenueCat wrapper, isPro state.
- `app/src/main/java/com/dhanuk/govphoto/data/push/PushRepository.kt` — OneSignal wrapper, category toggles.
- `app/src/main/java/com/dhanuk/govphoto/data/push/PushCategory.kt` — enum RELEASE_NOTES, EXAM_DEADLINES, SUPPORT_REPLIES + defaults.
- `app/src/main/java/com/dhanuk/govphoto/ui/ads/BannerAd.kt` — reusable composable wrapping `AdView` with ad-free check.
- `app/src/main/java/com/dhanuk/govphoto/ui/screens/PaywallScreen.kt` — RevenueCat-driven paywall UI.
- `app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/PaywallViewModel.kt` — load offerings, purchase, restore.
- `app/src/test/java/com/dhanuk/govphoto/data/ads/AdsRepositoryTest.kt`
- `app/src/test/java/com/dhanuk/govphoto/data/ads/InterstitialControllerTest.kt`
- `app/src/test/java/com/dhanuk/govphoto/data/subscription/SubscriptionRepositoryTest.kt`
- `app/src/test/java/com/dhanuk/govphoto/data/push/PushRepositoryTest.kt`

### Modified files:
- `build.gradle.kts` (top-level) — add Google Services + Crashlytics Gradle plugins.
- `app/build.gradle.kts` — add 4 plugins, BuildConfig fields for ADMOB_*, REVENUECAT_API_KEY, ONESIGNAL_APP_ID, FORCE_NO_ADS; signingConfig for release.
- `app/secrets.gradle.kts` — unchanged (already loads whatever's in `secrets.properties`).
- `app/scripts/populate-secrets.sh` — add keystore + google-services.json base64 decode; add new keys for keystore + GS.
- `secrets.properties.template` — add KEYSTORE_* keys; note google-services.json is decoded separately into a file.
- `app/src/main/AndroidManifest.xml` — add INTERNET, ACCESS_NETWORK_STATE, AdMob app ID meta-data, OneSignal app ID meta-data, WAKE_LOCK for push.
- `app/proguard-rules.pro` — append AdMob, RevenueCat, OneSignal, UMP keep rules.
- `.github/workflows/android-build.yml` — add GOOGLE_SERVICES_JSON_BASE64 + KEYSTORE_* env to populate-secrets step; add release signing config step.
- `app/google-services.json` — gitignored, generated in CI from base64 secret.
- `app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt` — SDK init in `onCreate()`: Firebase (auto), RevenueCat, OneSignal, UMP consent → MobileAds.init.
- `app/src/main/java/com/dhanuk/govphoto/data/datastore/SettingsRepository.kt` — add `isPro`, `cachedIsPro`, `adFreeUntilMs`, `saveCount`, `releaseNotificationsEnabled`, `examDeadlineNotificationsEnabled`, `supportNotificationsEnabled` prefs.
- `app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/SettingsViewModel.kt` — expose new settings + setters.
- `app/src/main/java/com/dhanuk/govphoto/ui/navigation/Screen.kt` — add `Paywall` route.
- `app/src/main/java/com/dhanuk/govphoto/ui/navigation/NavHost.kt` — wire `Paywall` composable.
- `app/src/main/java/com/dhanuk/govphoto/ui/screens/SettingsScreen.kt` — add `Remove Ads` row (opens paywall), `Notifications` section (3 toggles), `Privacy choices` row (reopens UMP form), add banner at flush-bottom.
- `app/src/main/java/com/dhanuk/govphoto/ui/screens/HomeScreen.kt` — add banner ad flush-bottom.
- `app/src/main/java/com/dhanuk/govphoto/ui/screens/AllFormsScreen.kt` — add banner ad flush-bottom.
- `app/src/main/java/com/dhanuk/govphoto/ui/screens/HistoryScreen.kt` — add banner ad flush-bottom.
- `app/src/main/java/com/dhanuk/govphoto/ui/screens/SaveSuccessScreen.kt` — add banner ad + trigger interstitial after save.
- `app/src/main/res/values/strings.xml` + `values-hi/strings.xml` — add ~40 new string keys (EN+HI pairs).

## Task Summary

1. **Task 1**: CI pipeline — `google-services.json` base64 decode + keystore signing in CI workflow + `populate-secrets.sh`.
2. **Task 2**: Firebase Crashlytics + Analytics — deps, BuildConfig, init, ProGuard.
3. **Task 3**: AdMob SDK — `AdsRepository`, `InterstitialController`, `BannerAd` composable, UMP consent flow, App init.
4. **Task 4**: RevenueCat SDK — `SubscriptionRepository`, build deps, App init.
5. **Task 5**: Paywall UI — `PaywallScreen`, `PaywallViewModel`, route wiring.
6. **Task 6**: OneSignal SDK — `PushRepository`, `PushCategory` enum, build deps, App init, manifest.
7. **Task 7**: Settings — add Remove Ads / Notifications / Privacy choices sections + banner; wire paywall nav.
8. **Task 8**: Ad placements — banners on 4 screens, interstitial on SaveSuccess, rewarded "watch ad → 24h ad-free" button on Settings.
9. **Task 9**: SettingsRepository — new prefs (isPro, cachedIsPro, adFreeUntilMs, saveCount, 3 notification toggles) + SettingsViewModel exposure.
10. **Task 10**: ProGuard rules + strings (EN+HI) for all new monetization/ads/paywall/push surfaces.

---


### Task 1: CI pipeline — google-services.json + keystore signing

**Files:**
- Modify: `app/scripts/populate-secrets.sh`
- Modify: `secrets.properties.template`
- Modify: `.github/workflows/android-build.yml`
- Create: `app/scripts/decode-google-services.sh`

**Interfaces:**
- Produces: CI workflow decodes `GOOGLE_SERVICES_JSON_BASE64` into `app/google-services.json` before build; decodes keystore into `release-keystore.jks` and populates `secrets.properties` with `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`; release build type uses a `signingConfigs.create("release")` block reading from `secrets.properties`. All downstream tasks assume `google-services.json` exists and CI can sign release APKs.

- [ ] **Step 1: Create `app/scripts/decode-google-services.sh`** — idempotent POSIX script that writes `app/google-services.json` from `GOOGLE_SERVICES_JSON_BASE64` env var, skipping if file already exists and env var empty.

```bash
#!/usr/bin/env bash
# Writes app/google-services.json from GOOGLE_SERVICES_JSON_BASE64 env var.
# Idempotent: skips if env var unset AND file already exists.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GS="$APP_DIR/google-services.json"

if [ -n "${GOOGLE_SERVICES_JSON_BASE64:-}" ]; then
  printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > "$GS"
  echo "Wrote $GS from GOOGLE_SERVICES_JSON_BASE64"
elif [ -f "$GS" ]; then
  echo "google-services.json already present"
else
  echo "WARNING: GOOGLE_SERVICES_JSON_BASE64 unset and google-services.json missing — Firebase init will fail" >&2
fi
```

- [ ] **Step 2: Create `app/scripts/decode-keystore.sh`** — idempotent POSIX script that writes `release-keystore.jks` from `KEYSTORE_BASE64` env var at repo root, skipping if env unset and file already exists.

```bash
#!/usr/bin/env bash
# Writes release-keystore.jks (repo root) from KEYSTORE_BASE64 env var.
# Idempotent: skips if env var unset AND file already exists.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KS="$ROOT/release-keystore.jks"

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$KS"
  echo "Wrote $KS from KEYSTORE_BASE64"
elif [ -f "$KS" ]; then
  echo "release-keystore.jks already present"
else
  echo "WARNING: KEYSTORE_BASE64 unset and release-keystore.jks missing — release build will fall back to debug signing" >&2
fi
```

- [ ] **Step 3: Add 4 keystore keys to `secrets.properties.template`** — append to the existing file before the AdMob block. These are placeholders; real values come from GH secrets in CI.

```
# Keystore (PR2 — for release signing in CI)
KEYSTORE_FILE=release-keystore.jks
KEYSTORE_PASSWORD=changeit
KEY_PASSWORD=changeit
KEY_ALIAS=mykey
```

- [ ] **Step 4: Extend `populate-secrets.sh`** — append these 4 lines after the existing `ONESIGNAL_APP_ID` block at the end of the file. Keystore file path is set from the discovered file rather than from a secret.

```bash
# Keystore signing (PR2)
[ -n "${KEYSTORE_PASSWORD:-}" ] && set_prop KEYSTORE_PASSWORD "$KEYSTORE_PASSWORD"
[ -n "${KEY_PASSWORD:-}" ]      && set_prop KEY_PASSWORD "$KEY_PASSWORD"
[ -n "${KEY_ALIAS:-}" ]         && set_prop KEY_ALIAS "$KEY_ALIAS"
# KEYSTORE_FILE is set by decode-keystore.sh (writes release-keystore.jks if secret was set)
[ -f "$ROOT/release-keystore.jks" ] && set_prop KEYSTORE_FILE "$ROOT/release-keystore.jks"
```

- [ ] **Step 5: Update `.github/workflows/android-build.yml`** — add env for the 3 new secrets (GOOGLE_SERVICES_JSON_BASE64, KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS) in BOTH the `build` and `lint` jobs' `Populate secrets.properties from secrets` step, then add a new step AFTER populate-secrets to run the two decode scripts.

In the `build` job, after the existing `Populate secrets.properties from secrets` step, add:

```yaml
      - name: Decode google-services.json
        env:
          GOOGLE_SERVICES_JSON_BASE64: ${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}
        run: bash app/scripts/decode-google-services.sh

      - name: Decode keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: bash app/scripts/decode-keystore.sh
```

Add the five new keys to the `env:` block of the existing `Populate secrets.properties from secrets` step in BOTH `build` and `lint` jobs (the `lint` job needs the GS plugin too):

```yaml
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
```

(Note: `GOOGLE_SERVICES_JSON_BASE64` is consumed by the decode step, not by populate-secrets.sh, so put it in the decode step's env, not the populate-secrets env block.)

- [ ] **Step 6: Add release signing config in `app/build.gradle.kts`** — modify the `android {}` block: insert a `signingConfigs` block before `buildTypes`. This block reads `findProperty("KEYSTORE_FILE")` etc. (set by `secrets.gradle.kts` from `secrets.properties`). When `KEYSTORE_FILE` is unset (local dev), release falls back to debug signing — preserving current behavior.

In `android {}`, insert BEFORE `buildTypes { }`:

```kotlin
    signingConfigs {
        create("release") {
            val ksFile = project.findProperty("KEYSTORE_FILE") as String?
            if (ksFile != null && rootProject.file(ksFile).exists()) {
                storeFile = rootProject.file(ksFile)
                storePassword = project.findProperty("KEYSTORE_PASSWORD") as String
                keyAlias = project.findProperty("KEY_ALIAS") as String
                keyPassword = project.findProperty("KEY_PASSWORD") as String
            }
        }
    }
```

Then change the existing `release { }` block to use the release signing config when present. Replace:

```kotlin
    release {
      signingConfig = signingConfigs.getByName("debug")
```

with:

```kotlin
    release {
      // Release signing uses the keystore decoded from KEYSTORE_BASE64 in CI.
      // When that's absent (local dev), fall back to debug signing.
      signingConfig = (rootProject.findProperty("KEYSTORE_FILE") as String?)
          ?.takeIf { rootProject.file(it).exists() }
          ?.let { signingConfigs.getByName("release") }
          ?: signingConfigs.getByName("debug")
```

- [ ] **Step 7: Commit the CI pipeline changes**

```bash
git add app/scripts/decode-google-services.sh \
        app/scripts/decode-keystore.sh \
        app/scripts/populate-secrets.sh \
        secrets.properties.template \
        .github/workflows/android-build.yml \
        app/build.gradle.kts
git commit -m "ci: decode google-services.json + keystore in CI, add release signing config"
```

- [ ] **Step 8: Push branch and verify CI**

```bash
git push -u origin feat/pr2-monetization-mega
```

Watch CI: the build job must succeed (debug + release APK/AAB). Release APK should now be signed with the real keystore (visible in the artifact). Lint job also needs to succeed.


### Task 2: Firebase Crashlytics + Analytics

**Files:**
- Modify: `build.gradle.kts` (top-level)
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/MainActivity.kt`
- Modify: `app/proguard-rules.pro`

**Interfaces:**
- Consumes: `app/google-services.json` produced by Task 1; `KEYSTORE_FILE` etc from Task 1.
- Produces: Crashlytics initialized on app start; crashes flow to Firebase console; `BuildConfig.FIREBASE_ENABLED` flag (always true, but lets tests gate). Analytics events callable from anywhere via `FirebaseAnalytics.getInstance(context).logEvent(...)`.

- [ ] **Step 1: Add Google Services + Crashlytics Gradle plugins to top-level `build.gradle.kts`** — append inside the existing `plugins {}` block (after `ksp`).

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
```

- [ ] **Step 2: Apply Google Services + Crashlytics plugins to `app/build.gradle.kts`** — add two `id(...)` lines to the existing `plugins {}` block at the top.

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
```

- [ ] **Step 3: Add Firebase + Crashlytics + Analytics dependencies** in the existing `dependencies {}` block of `app/build.gradle.kts`, after the DataStore line.

```kotlin
    // Firebase BoM + Crashlytics + Analytics
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
```

- [ ] **Step 4: Update `GovPhotoApp.onCreate()`** — enable Crashlytics collection (default true, but explicit is safer for release-only collection). Add the import and one call.

Insert at the top of `GovPhotoApp.kt` after `import android.app.Application`:

```kotlin
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
```

In `onCreate()`, add:

```kotlin
        // Firebase Crashlytics + Analytics
        FirebaseApp.initializeApp(this)
        // Default: collection enabled in release only; this is belt-and-suspenders
        // and matches the crashlyticsCollectionEnabled default. Override via manifest
        // or here for explicit control in the future.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
```

- [ ] **Step 5: Keep existing `last_crash.txt` handler in `MainActivity.kt`** — do NOT remove. The custom handler writes `last_crash.txt` before Crashlytics picks up the crash (Crashlytics installs first; ours chains after). This is documented in design spec §4.10 as defense-in-depth for one release. No code change here — just confirm the existing handler stays.

Note: `MainActivity.onCreate()` currently uses `Thread.setDefaultUncaughtExceptionHandler { t, e -> write last_crash.txt; t.uncaughtException?.uncaughtException(t, e) }` (or similar) — verify the chained call forwards to Crashlytics. If the existing code does NOT chain (i.e. swallows), comment out the swallow for release and just record the crash without forwarding; Crashlytics handles forwarding.

- [ ] **Step 6: Append Crashlytics ProGuard rules** to `app/proguard-rules.pro`:

```proguard
# Firebase Crashlytics — auto-shipped by SDK, no explicit rules needed
# (verified by Firebase docs; Crashlytics Gradle plugin also bundles rules)

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**
```

- [ ] **Step 7: Verify the existing MainActivity crash handler chains correctly** — Read `MainActivity.kt:1-200` (full file) with the `read` tool, locate the uncaught exception handler block. If it forwards via `defaultUncaughtExceptionHandler.uncaughtException(t, e)` or doesn't swallow, no change. If it does swallow (calls the original handler, but never forwards to Crashlytics), Crashlytics auto-installs first so its handler IS the defaultUncaughtExceptionHandler → the existing handler chains through it automatically via the standard pattern. No action needed; just confirm in the commit message body.

- [ ] **Step 8: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts \
        app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt \
        app/proguard-rules.pro
git commit -m "feat(firebase): integrate Crashlytics + Analytics with google-services plugin"
```

- [ ] **Step 9: Push and verify CI**

```bash
git push
```

CI must: (a) succeed decoding `google-services.json` (Task 1 step), (b) Gradle resolves the `firebase-bom` deps, (c) releaseBuild runs with Crashlytics mapping file generated, (d) no R8/ProGuard warnings. Review any red errors and fix.


### Task 3: AdMob SDK — AdsRepository, InterstitialController, BannerAd, UMP consent

**Files:**
- Create: `app/src/main/java/com/dhanuk/govphoto/data/ads/AdsRepository.kt`
- Create: `app/src/main/java/com/dhanuk/govphoto/data/ads/InterstitialController.kt`
- Create: `app/src/main/java/com/dhanuk/govphoto/ui/ads/BannerAd.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`
- Create: `app/src/test/java/com/dhanuk/govphoto/data/ads/AdsRepositoryTest.kt`
- Create: `app/src/test/java/com/dhanuk/govphoto/data/ads/InterstitialControllerTest.kt`

**Interfaces:**
- Consumes: `SubscriptionRepository.isPro` (Task 4) — for now stub via a `isProProvider: () -> Boolean` lambda defaulting to `false` until Task 4 lands; update wiring in Task 4 review. `SettingsRepository` (Task 9) for `adFreeUntilMs`, `saveCount` — stubbed via the same `isProProvider` shape so tests can mock.
- Produces: `AdsRepository.isAdFree: StateFlow<Boolean>` (drives BannerAd visibility + InterstitialController gate). `InterstitialController.tryShow(ctx): Boolean` (called from SaveSuccess after save). `BannerAd(modifier)` composable to insert on screens.

- [ ] **Step 1: Add AdMob + UMP deps to `app/build.gradle.kts`** in the existing `dependencies {}` block, after Firebase:

```kotlin
    // AdMob via Google Play Services Ads + UMP
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")
```

- [ ] **Step 2: Add BuildConfig fields for ad unit IDs** in the `defaultConfig {}` block of `app/build.gradle.kts`, below the existing 3 URL fields:

```kotlin
        buildConfigField("String", "ADMOB_APP_ID", "\"${project.findProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"}\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT", "\"${project.findProperty("ADMOB_BANNER_UNIT") ?: "ca-app-pub-3940256099942544/6300978111"}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT", "\"${project.findProperty("ADMOB_INTERSTITIAL_UNIT") ?: "ca-app-pub-3940256099942544/1033173712"}\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT", "\"${project.findProperty("ADMOB_REWARDED_UNIT") ?: "ca-app-pub-3940256099942544/5224354917"}\"")
```

- [ ] **Step 3: Add `FORCE_NO_ADS` debug-only BuildConfig field** inside the `debug { }` build type block. Add after the existing `applicationIdSuffix`:

```kotlin
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "FORCE_NO_ADS", "true")
        }
```

And in `release { }` after the existing lines, add:

```kotlin
        buildConfigField("boolean", "FORCE_NO_ADS", "false")
```

- [ ] **Step 4: Update `AndroidManifest.xml`** — add AdMob `<meta-data>` for application ID inside `<application>` (AdMob SDK reads this on init). Add INTERNET + ACCESS_NETWORK_STATE perms in the uses-permissions block. Append (after existing READ perms in the upper block, before `<application>`):

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Inside `<application>`, after the FileProvider block (just before the closing `</application>`):

```xml
        <!-- AdMob Application ID -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="@string/admob_app_id_placeholder" />
```

We use a string resource rather than raw `BuildConfig.ADMOB_APP_ID` because the manifest can't read BuildConfig directly. The string resource maps to Google's official test app ID (`@string/admob_app_id_placeholder` = `ca-app-pub-3940256099942544~3347511713`), which is the placeholder; release override happens via the manifest placeholder merge OR by swapping the placeholder value at build time using the secrets pipeline. The simpler approach: define two string resources for debug/release — to keep the spec simple, we keep a single placeholder string and rely on the SDK `MobileAds.initialize()` call (where we pass `BuildConfig.ADMOB_APP_ID` directly) as the source of truth; the manifest meta-data just needs to be a *valid* AdMob app ID for GMS to allow init. Therefore we always use Google's official test app ID here as the placeholder since `MobileAds.initialize(context, InitializationCompleteCallback)` reads no manifest value, only the init string passed in code. The placeholder is harmless (AdMob serves tests for it during Play review of our debug builds, and the in-code init overrides).

- [ ] **Step 5: Create `AdsRepository.kt`** — Singleton Hilt-bound repository that holds ad state, gates banner/interstitial/rewarded through `isAdFree`, rate-limits interstitial. Note: this version stubs `isProProvider` to `{ false }`; Task 4 re-wires the real `SubscriptionRepository`. Save-handling and ad-rewind state are read/written through a small `AdStateProvider` interface so we can unit-test pure logic.

`app/src/main/java/com/dhanuk/govphoto/data/ads/AdsRepository.kt`:

```kotlin
package com.dhanuk.govphoto.data.ads

import android.content.Context
import com.dhanuk.govphoto.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds ad-free state for the app. Composed of:
 *  - SubscriptionRepository.isPro (wired in Task 4 via isProProvider)
 *  - adFreeUntilMs (24h ad-free reward; from SettingsRepository, Task 9 via adStateProvider)
 *  - FORCE_NO_ADS BuildConfig flag (debug-only)
 */
interface AdStateProvider {
    /** False when subscriptions have not been wired yet. */
    val isPro: Boolean
    /** ms epoch; 0 if no reward active. */
    val adFreeUntilMs: Long
    /** True if BuildConfig.DEBUG (forces no-ads in debug screenshots/review). */
    val forceNoAds: Boolean
}

@Singleton
class AdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adStateProvider: AdStateProvider,
) {
    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    init {
        refresh()
    }

    /** Re-read external state. Called by SubscriptionRepository after a purchase
     *  completes and by SettingsRepository when adFreeUntilMs changes. */
    fun refresh() {
        val now = System.currentTimeMillis()
        _isAdFree.value =
            BuildConfig.FORCE_NO_ADS ||
            adStateProvider.forceNoAds ||
            adStateProvider.isPro ||
            (adStateProvider.adFreeUntilMs > now)
    }
}
```

- [ ] **Step 6: Create `InterstitialController.kt`** — rate-limited interstitial presentation. Pure logic is testable without Android by injecting a `Clock` and a state holder.

`app/src/main/java/com/dhanuk/govphoto/data/ads/InterstitialController.kt`:

```kotlin
package com.dhanuk.govphoto.data.ads

import android.content.Context
import com.dhanuk.govphoto.BuildConfig
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Rate limiter state — survives across the controller instance. */
class RateLimiter(
    private val now: () -> Long = { System.currentTimeMillis() },
    var lastShowMs: Long = 0L,
    var shownInSession: Int = 0,
    var saveCount: Int = 0,               // Incremented by SettingsRepository.saveCount
) {
    /** Returns true if all policy gates pass for "show an interstitial now". */
    fun canShow(minIntervalMs: Long, perSessionCap: Int, minSaveCount: Int): Boolean {
        if (saveCount < minSaveCount) return false
        if (shownInSession >= perSessionCap) return false
        if (now() - lastShowMs < minIntervalMs) return false
        return true
    }

    /** Record that we showed an ad right now; advance the counters. */
    fun markShown() {
        lastShowMs = now()
        shownInSession += 1
    }
}

/**
 * Single entrypoint for showing interstitials. Called from SaveSuccessScreen after a save succeeds.
 * Failures are silent (per Google's RTB-safe policy guidance).
 */
@Singleton
class InterstitialController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    private val rateLimiter = RateLimiter()
    private var loadedAd: InterstitialAd? = null

    /** Increment saveCount once after a successful save so the controller can gate. */
    fun recordSaveReceived(ms: Long = System.currentTimeMillis()) {
        rateLimiter.saveCount += 1
        rateLimiter.now = { ms }
        maybePreload()
    }

    private fun maybePreload() {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
        if (loadedAd != null) return
        val req = com.google.android.gms.ads.AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT,
            req,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { loadedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { loadedAd = null; loadRetry() }
            }
        )
    }

    fun tryShow(activity: android.app.Activity): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return false
        if (adsRepository.isAdFree.value) return false
        val ad = loadedAd ?: return false.also { maybePreload(); }
        if (!rateLimiter.canShow(minIntervalMs = 60_000L, perSessionCap = 3, minSaveCount = 2)) return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadedAd = null
                rateLimiter.markShown()
                maybePreload()
            }
        }
        ad.show(activity)
        return true
    }

    private var retryCount = 0
    private fun loadRetry() { if (retryCount++ > 3) return; maybePreload() }
}
```

Note: `RateLimiter.now` is `var` for tests; the controller sets it after each save. Adjust the default in `RateLimiter` constructor (`var now: () -> Long`). Add that field.

- [ ] **Step 7: Create `BannerAd.kt` composable** in `app/src/main/java/com/dhanuk/govphoto/ui/ads/BannerAd.kt`:

```kotlin
package com.dhanuk.govphoto.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.data.ads.AdsRepository
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.android.EntryPointAccessors

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    // Skip entirely in debug or when ad-free
    if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
    val context = LocalContext.current
    val adsRepository = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AdsEntryPoint::class.java,
            ).adsRepository()
        }.getOrNull()
    }
    val isAdFree by (adsRepository?.isAdFree?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(true) })
    if (isAdFree) return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdsEntryPoint {
    fun adsRepository(): AdsRepository
}
```

- [ ] **Step 8: Add entrypoint entry to `AttrsRegistry` if needed** — skip; above used `EntryPointAccessors` directly.

- [ ] **Step 9: Initialize AdMob in `GovPhotoApp.onCreate`** — request UMP consent first, then `MobileAds.initialize()`. Append into `onCreate()`:

```kotlin
        // UMP consent flow → MobileAds.initialize()
        val consentInfo = com.google.android.ump.ConsentInformation.getInstance(this)
        val params = com.google.android.ump.ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInfo.requestConsentInfoUpdate(
            this,
            params,
            {
                if (consentInfo.isConsentFormAvailable) {
                    consentInfo.loadConsentForm { _ -> initializeMobileAds() }
                } else {
                    initializeMobileAds()
                }
            },
            { initializeMobileAds() }
        )
```

And add helper:

```kotlin
    private fun initializeMobileAds() {
        com.google.android.gms.ads.MobileAds.initialize(this)
        com.dhanuk.govphoto.data.ads.InterstitialController::class // ensure compile
    }
```

- [ ] **Step 10: Provide `AdStateProvider` via Hilt** — in `AppModule.kt` add a binding that returns `object : AdStateProvider { ... }` returning `forceNoAds = BuildConfig.DEBUG && BuildConfig.FORCE_NO_ADS` and `isPro = false` and `adFreeUntilMs = 0L`. Replace in Task 4 (Subscription) and Task 9 (SettingsRepository).

Add to `AppModule.kt`:

```kotlin
    @Provides
    @Singleton
    fun provideAdStateProvider(): com.dhanuk.govphoto.data.ads.AdStateProvider =
        object : com.dhanuk.govphoto.data.ads.AdStateProvider {
            override val isPro: Boolean get() = false          // Re-wired in Task 4
            override val adFreeUntilMs: Long get() = 0L        // Re-wired in Task 9
            override val forceNoAds: Boolean get() = BuildConfig.DEBUG
        }
```

- [ ] **Step 11: Write `AdsRepositoryTest.kt`**

`app/src/test/java/com/dhanuk/govphoto/data/ads/AdsRepositoryTest.kt`:

```kotlin
package com.dhanuk.govphoto.data.ads

import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AdsRepositoryTest {

    private fun provider(isPro: Boolean, adFreeUntilMs: Long, forceNoAds: Boolean) =
        object : AdStateProvider {
            override val isPro = isPro
            override val adFreeUntilMs = adFreeUntilMs
            override val forceNoAds = forceNoAds
        }

    @Test fun `free user with no reward shows ads`() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = AdsRepository(ctx, provider(isPro=false, adFreeUntilMs=0L, forceNoAds=false))
        assertFalse(repo.isAdFree.value)
    }

    @Test fun `pro user is ad-free`() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = AdsRepository(ctx, provider(isPro=true, adFreeUntilMs=0L, forceNoAds=false))
        assertTrue(repo.isAdFree.value)
    }

    @Test fun `reward timestamp in future is ad-free`() {
        val ctx = RuntimeEnvironment.getApplication()
        val future = System.currentTimeMillis() + 60_000L
        val repo = AdsRepository(ctx, provider(isPro=false, adFreeUntilMs=future, forceNoAds=false))
        assertTrue(repo.isAdFree.value)
    }

    @Test fun `expired reward is not ad-free`() {
        val ctx = RuntimeEnvironment.getApplication()
        val past = System.currentTimeMillis() - 1_000L
        val repo = AdsRepository(ctx, provider(isPro=false, adFreeUntilMs=past, forceNoAds=false))
        assertFalse(repo.isAdFree.value)
    }
}
```

- [ ] **Step 12: Write `InterstitialControllerTest.kt`** — pure logic of `RateLimiter` (no AdMob load needed).

`app/src/test/java/com/dhanuk/govphoto/data/ads/InterstitialControllerTest.kt`:

```kotlin
package com.dhanuk.govphoto.data.ads

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterstitialControllerTest {

    @Test fun `first save can't show`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 1)
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `second save without cooldown can show`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 2)
        assertTrue(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `cooldown gate`() {
        val rl = RateLimiter(now = { 10_000 }, saveCount = 2, lastShowMs = 5_000, shownInSession = 1)
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `per-session cap of 3`() {
        val rl = RateLimiter(now = { 999_999 }, saveCount = 2, lastShowMs = 0L, shownInSession = 3)
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `markShown advances state`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 2)
        rl.markShown()
        // After a show, cooldown applies.
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
        // 60s later, cap-2 still allows one more.
        rl.now = { 61_000 }; assertTrue(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }
}
```

- [ ] **Step 13: Append ProGuard rules for AdMob + UMP** to `app/proguard-rules.pro`:

```proguard
# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# UMP
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**
```

- [ ] **Step 14: Add `admob_app_id_placeholder` string resource** in `app/src/main/res/values/strings.xml` and `values-hi/strings.xml`:

```
    <string name="admob_app_id_placeholder">ca-app-pub-3940256099942544~3347511713</string>
```

Same Hindi translation (it's an ID, not translatable — but the file must have the entry):

```
    <string name="admob_app_id_placeholder">ca-app-pub-3940256099942544~3347511713</string>
```

- [ ] **Step 15: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt \
        app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt \
        app/src/main/java/com/dhanuk/govphoto/data/ads/ \
        app/src/main/java/com/dhanuk/govphoto/ui/ads/BannerAd.kt \
        app/src/test/java/com/dhanuk/govphoto/data/ads/ \
        app/proguard-rules.pro \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-hi/strings.xml
git commit -m "feat(ads): integrate AdMob with rate-limited interstitial, banner, UMP consent"
```

- [ ] **Step 16: Push and verify CI**

```bash
git push
```

Review any red. Robolectric tests for `RateLimiter` must pass; `AdsRepositoryTest` may need `@Config(application = HiltTestApplication)` if HiltAndroidApp triggers issues — fix incrementally if so.


### Task 4: RevenueCat SDK — SubscriptionRepository

**Files:**
- Create: `app/src/main/java/com/dhanuk/govphoto/data/subscription/SubscriptionRepository.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/data/datastore/SettingsRepository.kt` (depends on Task 9 — see Interfaces)
- Modify: `app/proguard-rules.pro`
- Create: `app/src/test/java/com/dhanuk/govphoto/data/subscription/SubscriptionRepositoryTest.kt`

**Interfaces:**
- Consumes: `REVENUECAT_API_KEY` from `BuildConfig` (Task wiring below); `SettingsRepository.cachedIsPro` setter (Task 9 — for now stub via a `CachedIsProStore` interface minimal enough to inject a fake in tests).
- Produces: `SubscriptionRepository.isPro: StateFlow<Boolean>` (drives `AdsRepository.refresh()` via a fresh `AdStateProvider` wiring step), `loadOfferings()`, `purchase(activity, package)`, `restorePurchases()`. Paywall UI (Task 5) consumes these.

- [ ] **Step 1: Add RevenueCat dep** to `app/build.gradle.kts` after AdMob:

```kotlin
    // RevenueCat
    implementation("com.revenuecat.purchases:purchases:5.9.0")
```

- [ ] **Step 2: Add BuildConfig for RevenueCat API key** in `defaultConfig`:

```kotlin
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${project.findProperty("REVENUECAT_API_KEY") ?: "goog_test_key"}\"")
```

- [ ] **Step 3: Add `CachedIsProStore` minimal interface** in `SettingsRepository.kt` (Task 9 will flesh out the full DataStore keys). Define the interface:

```kotlin
interface CachedIsProStore {
    suspend fun getCachedIsPro(): Boolean
    suspend fun setCachedIsPro(value: Boolean)
}
```

And an in-memory no-op implementation in `AppModule.kt`:

```kotlin
    @Provides
    @Singleton
    fun provideCachedIsProStore(): com.dhanuk.govphoto.data.datastore.CachedIsProStore =
        object : com.dhanuk.govphoto.data.datastore.CachedIsProStore {
            private var v = false
            override suspend fun getCachedIsPro(): Boolean = v
            override suspend fun setCachedIsPro(value: Boolean) { v = value }
        }
```

Task 9 will replace this with a `SettingsRepository`-backed real implementation that persists to DataStore.

- [ ] **Step 4: Create `SubscriptionRepository.kt`** — follows design spec §4.3 shape. Note `Purchases.configure` happens in `GovPhotoApp.onCreate()` (Step 7 below).

`app/src/main/java/com/dhanuk/govphoto/data/subscription/SubscriptionRepository.kt`:

```kotlin
package com.dhanuk.govphoto.data.subscription

import android.app.Activity
import android.content.Context
import com.dhanuk.govphoto.data.datastore.CachedIsProStore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.getCustomerInfo
import com.revenuecat.purchases.getOfferings
import com.revenuecat.purchases.ktor_result
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.purchasePackageWithPromoOfferDialog
import com.revenuecat.purchases.restorePurchases
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val cachedStore: CachedIsProStore,
) {
    companion object {
        const val ENTITLEMENT_ID = "pro"
        private const val REWARD_REPOS_SCOPE_TAG = "SubscriptionRepo"
    }

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    suspend fun bind() {
        if (Purchases.isConfigured) {
            _isPro.value = cachedStore.getCachedIsPro()
            Purchases.sharedInstance.customerInfoFlow
                .onEach { info -> applyCustomerInfo(info) }
                .launchIn(scope)
        }
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val pro = info.entitlements[ENTITLEMENT_ID]?.isActive == true
        _isPro.value = pro
        scope.launch { cachedStore.setCachedIsPro(pro) }
    }

    suspend fun loadOfferings(): Offerings = withContext(Dispatchers.IO) {
        Purchases.sharedInstance.getOfferings()
    }

    suspend fun purchase(activity: Activity, packageToBuy: Package): Result<CustomerInfo> =
        runCatching {
            withContext(Dispatchers.Main) {
                Purchases.sharedInstance.purchasePackageWithPromoOfferDialog(activity, packageToBuy, null)
            }.let { info ->
                applyCustomerInfo(info.first)
                info.first
            }
        }

    suspend fun restorePurchases(): Result<CustomerInfo> = runCatching {
        withContext(Dispatchers.IO) { Purchases.sharedInstance.restorePurchases() }.also { info ->
            applyCustomerInfo(info)
        }
    }
}
```

Note: the exact `Purchases` API names depend on the RevenueCat 5.9.0 SDK; verify import paths in the CI build step and correct as needed (e.g. `purchasePackageWithPromoOfferDialog` returns `Pair<CustomerInfo, StoreTransaction>`, hence `.first`).

- [ ] **Step 5: Initialize RevenueCat in `GovPhotoApp.onCreate()`** — add after Firebase init, before consent/MobileAds init:

```kotlin
        // RevenueCat
        com.revenuecat.purchases.Purchases.configure(
            com.revenuecat.purchases.Purchases.Configuration.Builder(
                this,
                BuildConfig.REVENUECAT_API_KEY,
            ).build()
        )
```

- [ ] **Step 6: Re-wire `AdStateProvider` in `AppModule.kt`** — replace the stubbed `isPro = false` to read from `SubscriptionRepository`:

```kotlin
    @Provides
    @Singleton
    fun provideAdStateProvider(
        subscriptionRepository: com.dhanuk.govphoto.data.subscription.SubscriptionRepository,
    ): com.dhanuk.govphoto.data.ads.AdStateProvider =
        object : com.dhanuk.govphoto.data.ads.AdStateProvider {
            override val isPro: Boolean get() = subscriptionRepository.isPro.value
            override val adFreeUntilMs: Long get() = 0L        // Re-wired in Task 9
            override val forceNoAds: Boolean get() = BuildConfig.DEBUG
        }
```

Also call `subscriptionRepository.bind()` from `GovPhotoApp.onCreate()` (right after configure):

```kotlin
        // Wire SubscriptionRepository.isPro flow → AdsRepository via AdStateProvider
        subscriptionRepository.bind()
```

To get the `SubscriptionRepository` into `GovPhotoApp`, mark `GovPhotoApp` `@HiltAndroidApp` and `@Inject` constructor-less. Application classes can't use constructor injection, so use an `EntryPoint`:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface GovPhotoAppEntryPoint {
    fun subscriptionRepository(): com.dhanuk.govphoto.data.subscription.SubscriptionRepository
    fun adsRepository(): com.dhanuk.govphoto.data.ads.AdsRepository
}
```

Then in `onCreate()`:

```kotlin
        val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(this, GovPhotoAppEntryPoint::class.java)
        ep.subscriptionRepository().bind()
```

- [ ] **Step 7: Append RevenueCat ProGuard rules** to `app/proguard-rules.pro`:

```proguard
# RevenueCat
-keep class com.revenuecat.purchases.** { *; }
-keep class com.revenuecat.purchases.google.** { *; }
-keepclassmembers class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**
```

- [ ] **Step 8: Write `SubscriptionRepositoryTest.kt`** — unit test the isPro derivation + cache. We stub `Purchases` via a fake interface (`PurchasesInterface`) that we don't have, so the test focuses on `cachedStore` round-trip and the no-config fallback. Real RevenueCat flows require Robolectric+Purchases SDK mocks that are out of scope; write a simpler test of the `bind()` happy path that doesn't actually call into the SDK by short-circuiting before binding when `Purchases.isConfigured == false`:

`app/src/test/java/com/dhanuk/govphoto/data/subscription/SubscriptionRepositoryTest.kt`:

```kotlin
package com.dhanuk.govphoto.data.subscription

import com.dhanuk.govphoto.data.datastore.CachedIsProStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class SubscriptionRepositoryTest {

    class FakeCachedStore : CachedIsProStore {
        var stored = false
        override suspend fun getCachedIsPro(): Boolean = stored
        override suspend fun setCachedIsPro(value: Boolean) { stored = value }
    }

    @Test fun `bind without Purchases configured keeps isPro false`() = runTest {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val repo = SubscriptionRepository(ctx, FakeCachedStore())
        repo.bind()  // no-op: Purchases not configured
        assertFalse(repo.isPro.value)
    }

    @Test fun `cached store round-trips`() = runTest {
        val fake = FakeCachedStore()
        fake.setCachedIsPro(true)
        assertEquals(true, fake.getCachedIsPro())
        fake.setCachedIsPro(false)
        assertEquals(false, fake.getCachedIsPro())
    }
}
```

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt \
        app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt \
        app/src/main/java/com/dhanuk/govphoto/data/subscription/ \
        app/src/main/java/com/dhanuk/govphoto/data/datastore/SettingsRepository.kt \
        app/src/test/java/com/dhanuk/govphoto/data/subscription/ \
        app/proguard-rules.pro
git commit -m "feat(subscriptions): RevenueCat SDK + SubscriptionRepository wired into AdStateProvider"
```

- [ ] **Step 10: Push and verify CI**

```bash
git push
```


### Task 5: Paywall UI — PaywallScreen + PaywallViewModel + routing

**Files:**
- Create: `app/src/main/java/com/dhanuk/govphoto/ui/screens/PaywallScreen.kt`
- Create: `app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/PaywallViewModel.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/navigation/NavHost.kt`
- Modify: `app/src/main/res/values/strings.xml` + `values-hi/strings.xml`

**Interfaces:**
- Consumes: `SubscriptionRepository` (Task 4) — `loadOfferings()`, `purchase(activity, pkg)`, `restorePurchases()`.
- Produces: Route `Screen.Paywall.route == "paywall"`. Composable `PaywallScreen(onNavigateBack: () -> Unit, onSubscribeSuccess: () -> Unit)`. Activated via a "Remove Ads" entry in Settings (Task 7).

- [ ] **Step 1: Add `Paywall` to `Screen.kt`** — after `HelpArticle`:

```kotlin
    data object Paywall : Screen("paywall")
```

- [ ] **Step 2: Create `PaywallViewModel.kt`**

`app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/PaywallViewModel.kt`:

```kotlin
package com.dhanuk.govphoto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    val loading: Boolean = true,
    val offering: Offering? = null,
    val subscribed: Boolean = false,
    val error: String? = null,
    val restoring: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching { subscriptionRepository.loadOfferings() }
            .onSuccess { offerings ->
                val off = offerings.current ?: offerings.all.values.firstOrNull()
                _state.value = PaywallUiState(
                    loading = false,
                    offering = off,
                    subscribed = subscriptionRepository.isPro.value,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load plans")
            }
    }

    fun purchase(activity: android.app.Activity, pkg: Package, onSuccess: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(error = null)
        subscriptionRepository.purchase(activity, pkg)
            .onSuccess { onSuccess() }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Purchase failed") }
    }

    fun restore(activity: android.app.Activity, onSuccess: () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(restoring = true)
        subscriptionRepository.restorePurchases()
            .onSuccess {
                _state.value = _state.value.copy(restoring = false, subscribed = subscriptionRepository.isPro.value)
                if (subscriptionRepository.isPro.value) onSuccess()
            }
            .onFailure { e ->
                _state.value = _state.value.copy(restoring = false, error = e.message ?: "Restore failed")
            }
    }
}
```

- [ ] **Step 3: Create `PaywallScreen.kt`** — Compose UI. Material 3 cards for three plans, "Best value" highlight defaults to Yearly (per design spec §4.6 — default placement on Yearly).

`app/src/main/java/com/dhanuk/govphoto/ui/screens/PaywallScreen.kt`:

```kotlin
package com.dhanuk.govphoto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.R
import com.dhanuk.govphoto.ui.components.GovButton
import com.dhanuk.govphoto.ui.components.GovOutlinedButton
import com.dhanuk.govphoto.ui.viewmodel.PaywallViewModel
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Package

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onNavigateBack: () -> Unit,
    onSubscribeSuccess: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var selectedPackage by remember { mutableStateOf<Package?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paywall_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero
            Text(stringResource(R.string.paywall_hero_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.paywall_hero_subtitle), style = MaterialTheme.typography.bodyMedium)

            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }
            if (state.subscribed) {
                Text(stringResource(R.string.paywall_already_pro), fontWeight = FontWeight.Bold)
                GovButton(text = stringResource(R.string.paywall_done), onClick = onSubscribeSuccess)
                return@Column
            }

            val packages = state.offering?.availablePackages.orEmpty()
                .filter { it.packageType == PackageType.WEEKLY || it.packageType == PackageType.MONTHLY || it.packageType == PackageType.ANNUAL }

            packages.forEach { pkg ->
                val isBest = pkg.packageType == PackageType.ANNUAL
                val selected = selectedPackage == pkg
                PlanCard(
                    pkg = pkg,
                    isBest = isBest,
                    isSelected = selected,
                    onSelect = { selectedPackage = pkg },
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            GovButton(
                text = stringResource(R.string.paywall_subscribe),
                enabled = selectedPackage != null && activity != null,
                onClick = {
                    val pkg = selectedPackage ?: return@GovButton
                    val act = activity ?: return@GovButton
                    viewModel.purchase(act, pkg) { onSubscribeSuccess() }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GovOutlinedButton(
                text = stringResource(R.string.paywall_restore),
                enabled = activity != null && !state.restoring,
                onClick = {
                    val act = activity ?: return@GovOutlinedButton
                    viewModel.restore(act) { onSubscribeSuccess() }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.paywall_legal), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlanCard(
    pkg: Package,
    isBest: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val priceStr = pkg.storeProduct.priceFormatted
    val period = when (pkg.packageType) {
        PackageType.WEEKLY -> stringResource(R.string.paywall_period_weekly)
        PackageType.MONTHLY -> stringResource(R.string.paywall_period_monthly)
        PackageType.ANNUAL -> stringResource(R.string.paywall_period_yearly)
        else -> ""
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(intrinsicSize = IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isBest) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.cd_best_value), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.paywall_best_value), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("$priceStr / $period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            RadioButton(selected = isSelected, onClick = onSelect)
        }
    }
}
```

- [ ] **Step 4: Wire `PaywallScreen` in `NavHost.kt`** — add a `composable` at the end of the `NavHost { }` body, just before the closing brace:

```kotlin
        composable(Screen.Paywall.route) {
            PaywallScreen(
                onNavigateBack = { navController.popBackStack() },
                onSubscribeSuccess = {
                    navController.popBackStack()
                }
            )
        }
```

- [ ] **Step 5: Add paywall strings (EN)** to `app/src/main/res/values/strings.xml`, before `</resources>`:

```
    <!-- Paywall -->
    <string name="paywall_title">GovPhoto Pro</string>
    <string name="paywall_hero_title">Ad-free forever</string>
    <string name="paywall_hero_subtitle">Remove all ads. Support development.</string>
    <string name="paywall_period_weekly">week</string>
    <string name="paywall_period_monthly">month</string>
    <string name="paywall_period_yearly">year</string>
    <string name="paywall_best_value">Best value</string>
    <string name="paywall_subscribe">Subscribe</string>
    <string name="paywall_restore">Restore purchases</string>
    <string name="paywall_done">Done</string>
    <string name="paywall_already_pro">You already have GovPhoto Pro. Thank you.</string>
    <string name="paywall_legal">Auto-renews. Cancel anytime in Play Store. By subscribing you agree to the Terms and Privacy Policy.</string>
    <string name="cd_best_value">Best value badge</string>
```

- [ ] **Step 6: Add paywall strings (HI)** to `app/src/main/res/values-hi/strings.xml`, before `</resources>`:

```
    <!-- Paywall (Hindi) -->
    <string name="paywall_title">GovPhoto Pro</string>
    <string name="paywall_hero_title">विज्ञापन-मुक्त सेवा</string>
    <string name="paywall_hero_subtitle">सभी विज्ञापन हटाएं। विकास में सहयोग करें।</string>
    <string name="paywall_period_weekly">सप्ताह</string>
    <string name="paywall_period_monthly">माह</string>
    <string name="paywall_period_yearly">वर्ष</string>
    <string name="paywall_best_value">सर्वोत्तम ऑफ़र</string>
    <string name="paywall_subscribe">सदस्यता लें</string>
    <string name="paywall_restore">खरीद पुनर्स्थापित करें</string>
    <string name="paywall_done">पूर्ण</string>
    <string name="paywall_already_pro">आपके पास GovPhoto Pro है। धन्यवाद।</string>
    <string name="paywall_legal">ऑटो-नवीनीकरण होगा। Play Store में कभी भी रद्द करें। सदस्यता लेने पर आप नियम और गोपनीयता नीति से सहमत होते हैं।</string>
    <string name="cd_best_value">सर्वोत्तम ऑफ़र बैज</string>
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhanuk/govphoto/ui/screens/PaywallScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/PaywallViewModel.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/navigation/Screen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/navigation/NavHost.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-hi/strings.xml
git commit -m "feat(paywall): PaywallScreen + PaywallViewModel + routing for RevenueCat offerings"
```

- [ ] **Step 8: Push and verify CI**

```bash
git push
```


### Task 6: OneSignal SDK — PushRepository + categories

**Files:**
- Create: `app/src/main/java/com/dhanuk/govphoto/data/push/PushCategory.kt`
- Create: `app/src/main/java/com/dhanuk/govphoto/data/push/PushRepository.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt`
- Modify: `app/proguard-rules.pro`
- Create: `app/src/test/java/com/dhanuk/govphoto/data/push/PushRepositoryTest.kt`

**Interfaces:**
- Consumes: OneSignal SDK + `BuildConfig.ONESIGNAL_APP_ID` + `PushCategoryStore` minimal interface (datastore-keyed) whose real impl lands in Task 9 — stubbed in `AppModule.kt` for now.
- Produces: `PushCategory` enum (RELEASE_NOTES / EXAM_DEADLINES / SUPPORT_REPLIES + defaults); `PushRepository.setCategoryEnabled(cat, enabled)`, `PushCategoryStore` Hilt-bound.

- [ ] **Step 1: Add OneSignal dep** to `app/build.gradle.kts` after RevenueCat:

```kotlin
    // OneSignal
    implementation("com.onesignal:OneSignal:5.1.5")
```

(Verify version (5.1.5 most recent as of plan date). If CI fails to resolve, bump.)

- [ ] **Step 2: Add `ONESIGNAL_APP_ID` BuildConfig** to `defaultConfig`:

```kotlin
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${project.findProperty("ONESIGNAL_APP_ID") ?: "test-onesignal-id"}\"")
```

- [ ] **Step 3: Create `PushCategory.kt`**

`app/src/main/java/com/dhanuk/govphoto/data/push/PushCategory.kt`:

```kotlin
package com.dhanuk.govphoto.data.push

enum class PushCategory(val storageKey: String, val defaultEnabled: Boolean) {
    RELEASE_NOTES(storageKey = "push_release_notes", defaultEnabled = true),
    EXAM_DEADLINES(storageKey = "push_exam_deadlines", defaultEnabled = false),
    SUPPORT_REPLIES(storageKey = "push_support_replies", defaultEnabled = true),
}
```

- [ ] **Step 4: Create `PushCategoryStore` minimal interface + stub in AppModule** — Task 9 backs with DataStore real impl.

In `AppModule.kt`, add interface (next to `CachedIsProStore` pattern — define interface in a dedicated file `PushCategoryStore.kt`):

`app/src/main/java/com/dhanuk/govphoto/data/push/PushCategoryStore.kt`:

```kotlin
package com.dhanuk.govphoto.data.push

interface PushCategoryStore {
    suspend fun isEnabled(category: PushCategory): Boolean
    suspend fun setEnabled(category: PushCategory, enabled: Boolean)
}
```

In `AppModule.kt`:

```kotlin
    @Provides
    @Singleton
    fun providePushCategoryStore(): com.dhanuk.govphoto.data.push.PushCategoryStore =
        object : com.dhanuk.govphoto.data.push.PushCategoryStore {
            private val map = com.dhanuk.govphoto.data.push.PushCategory.entries.associateBy { it } .mapValues { it.value.defaultEnabled }.toMutableMap()
            override suspend fun isEnabled(category: com.dhanuk.govphoto.data.push.PushCategory): Boolean = map[category] ?: category.defaultEnabled
            override suspend fun setEnabled(category: com.dhanuk.govphoto.data.push.PushCategory, enabled: Boolean) { map[category] = enabled }
        }
```

- [ ] **Step 5: Create `PushRepository.kt`** — wraps OneSignal init + tag-based category filtering.

`app/src/main/java/com/dhanuk/govphoto/data/push/PushRepository.kt`:

```kotlin
package com.dhanuk.govphoto.data.push

import android.content.Context
import com.dhanuk.govphoto.BuildConfig
import com.onesignal.OneSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PushCategoryStore,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** OnInit: enable OneSignal with verbose logging in debug. Tag user with category state. */
    suspend fun init() {
        OneSignal.initWithContext(context, BuildConfig.ONESIGNAL_APP_ID)
        OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) com.onesignal.Debug.LOG_LEVEL.DEBUG else com.onesignal.Debug.LOG_LEVEL.WARN
        refreshTags()
    }

    /** Apply enabled-states as OneSignal tags, so server-segmented sends honor user prefs.
     *  Tag key = PushCategory.storageKey; value = "1" if enabled else "0". */
    suspend fun refreshTags() {
        for (cat in PushCategory.entries) {
            val enabled = store.isEnabled(cat)
            // Send a tag-bracket map - OneSignal accepts key-value tags only at user-level.
            OneSignal.User.addTag(cat.storageKey, if (enabled) "1" else "0")
        }
    }

    fun setCategoryEnabled(category: PushCategory, enabled: Boolean) = scope.launch {
        store.setEnabled(category, enabled)
        OneSignal.User.addTag(category.storageKey, if (enabled) "1" else "0")
    }
}
```

Add `PushRepository` to the `GovPhotoAppEntryPoint`:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface GovPhotoAppEntryPoint {
    fun subscriptionRepository(): com.dhanuk.govphoto.data.subscription.SubscriptionRepository
    fun adsRepository(): com.dhanuk.govphoto.data.ads.AdsRepository
    fun pushRepository(): com.dhanuk.govphoto.data.push.PushRepository
}
```

- [ ] **Step 6: Initialize OneSignal in `GovPhotoApp.onCreate()`** after subscription repository init. Wrap in scope-launch since `init` is suspend:

```kotlin
        // OneSignal — init in background to avoid blocking onCreate
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            ep.pushRepository().init()
        }
```

- [ ] **Step 7: Add OneSignal permissions to manifest** — add WAKE_LOCK (for push delivery confirmations):

```xml
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

Also add `<meta-data name="com.onesignal.NotificationExtenderService" ...>` and the OneSignal manifest placeholder via the `build.gradle.kts`:

In `app/build.gradle.kts` `defaultConfig` add:

```kotlin
        manifestPlaceholders["onesignal_app_id"] = project.findProperty("ONESIGNAL_APP_ID") ?: "test-onesignal-id"
```

(The Gradle plugin isn't required for the 5.x SDK since `OneSignal.initWithContext` reads from BuildConfig directly, but this ensures no manifest placeholder issues.)

- [ ] **Step 8: Append OneSignal ProGuard rules** to `app/proguard-rules.pro`:

```proguard
# OneSignal
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
```

- [ ] **Step 9: Write `PushRepositoryTest.kt`** — test category enum defaults + store round-trip logic without touching OneSignal.

`app/src/test/java/com/dhanuk/govphoto/data/push/PushRepositoryTest.kt`:

```kotlin
package com.dhanuk.govphoto.data.push

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PushRepositoryTest {

    class FakeStore : PushCategoryStore {
        private val map = PushCategory.entries.associateBy { it }.mapValues { it.value.defaultEnabled }.toMutableMap()
        override suspend fun isEnabled(category: PushCategory): Boolean = map[category] ?: category.defaultEnabled
        override suspend fun setEnabled(category: PushCategory, enabled: Boolean) { map[category] = enabled }
    }

    @Test fun `defaults match spec`() {
        assertEquals(true, PushCategory.RELEASE_NOTES.defaultEnabled)
        assertEquals(false, PushCategory.EXAM_DEADLINES.defaultEnabled)
        assertEquals(true, PushCategory.SUPPORT_REPLIES.defaultEnabled)
    }

    @Test fun `toggle persists to fake store`() = runTest {
        val fake = FakeStore()
        assertTrue(fake.isEnabled(PushCategory.RELEASE_NOTES))
        fake.setEnabled(PushCategory.EXAM_DEADLINES, true)
        assertTrue(fake.isEnabled(PushCategory.EXAM_DEADLINES))
        fake.setEnabled(PushCategory.RELEASE_NOTES, false)
        assertFalse(fake.isEnabled(PushCategory.RELEASE_NOTES))
    }
}
```

- [ ] **Step 10: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt \
        app/src/main/java/com/dhanuk/govphoto/data/push/ \
        app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt \
        app/proguard-rules.pro \
        app/src/test/java/com/dhanuk/govphoto/data/push/
git commit -m "feat(push): OneSignal SDK integration with 3 user-toggled categories"
```

- [ ] **Step 11: Push and verify CI**

```bash
git push
```


### Task 7: Settings — Remove Ads, Notifications, Privacy choices sections + banner

**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml` + `values-hi/strings.xml`
- (Ad placements use the `BannerAd` composable from Task 3.)

**Interfaces:**
- Consumes: `BannerAd` composable; `InterstitialController.tryShow(activity)` (Task 8 wires it to SaveSuccess); `PushRepository.setCategoryEnabled` + `PushCategory` (Task 6); UMP `ConsentInformation.showPrivacyOptionsForm(activity, handler)`; route nav to `paywall`.
- Produces: Settings sections: "Remove Ads" (opens Paywall route), "Notifications" (3 toggles, one per PushCategory), "Privacy choices" (reopens UMP form). Banner ad flush at bottom of Settings screen.

- [ ] **Step 1: Add a navigation callback `onNavigateToPaywall: () -> Unit`** to the `SettingsScreen` signature, pushing onto the `paywall` route. Add parameter to the composable signature and update NavHost call site.

In `SettingsScreen.kt`:

```kotlin
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
)
```

In `NavHost.kt` Settings block:

```kotlin
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Screen.Paywall.route) }
            )
        }
```

- [ ] **Step 2: Add `Remove Ads` row** to SettingsScreen, as a new section called "Subscription". Insert AFTER the Support us section, before Language:

```kotlin
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Subscription Section
            SettingsSection(title = stringResource(R.string.subscription_section)) {
                SettingsItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = stringResource(R.string.remove_ads),
                    subtitle = stringResource(R.string.remove_ads_subtitle),
                    onClick = onNavigateToPaywall
                )
            }
```

- [ ] **Step 3: Add `Notifications` section** with 3 toggles. Insert after the Subscription section, before Language:

```kotlin
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Notifications Section
            SettingsSection(title = stringResource(R.string.notifications_section)) {
                val pushRepository = remember {
                    runCatching {
                        dagger.hilt.android.EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            PushEntryPoint::class.java,
                        ).pushRepository()
                    }.getOrNull()
                }
                SettingsToggle(
                    icon = Icons.Default.Campaign,
                    title = stringResource(R.string.notify_release_notes),
                    subtitle = stringResource(R.string.notify_release_notes_desc),
                    isChecked = releaseNotesEnabled,
                    onCheckedChange = { v ->
                        releaseNotesEnabled = v
                        pushRepository?.setCategoryEnabled(com.dhanuk.govphoto.data.push.PushCategory.RELEASE_NOTES, v)
                    }
                )
                SettingsToggle(
                    icon = Icons.Default.NOTIFICATIONS_ACTIVE,
                    title = stringResource(R.string.notify_exam_deadlines),
                    subtitle = stringResource(R.string.notify_exam_deadlines_desc),
                    isChecked = examDeadlinesEnabled,
                    onCheckedChange = { v ->
                        examDeadlinesEnabled = v
                        pushRepository?.setCategoryEnabled(com.dhanuk.govphoto.data.push.PushCategory.EXAM_DEADLINES, v)
                    }
                )
                SettingsToggle(
                    icon = Icons.Default.MarkEmailRead,
                    title = stringResource(R.string.notify_support_replies),
                    subtitle = stringResource(R.string.notify_support_replies_desc),
                    isChecked = supportRepliesEnabled,
                    onCheckedChange = { v ->
                        supportRepliesEnabled = v
                        pushRepository?.setCategoryEnabled(com.dhanuk.govphoto.data.push.PushCategory.SUPPORT_REPLIES, v)
                    }
                )
            }
```

Add at top of `SettingsScreen` Composable body, after `preventScreenshots`:

```kotlin
    var releaseNotesEnabled by remember { mutableStateOf(true) }
    var examDeadlinesEnabled by remember { mutableStateOf(false) }
    var supportRepliesEnabled by remember { mutableStateOf(true) }
```

(Task 9 will swap these `remember` values for state read from SettingsRepository once DataStore prefs exist.)

Add the `PushEntryPoint` interface at the bottom of `SettingsScreen.kt`:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PushEntryPoint {
    fun pushRepository(): com.dhanuk.govphoto.data.push.PushRepository
}
```

- [ ] **Step 4: Add `Privacy choices` row** to Settings — insert into the existing Support us section after the Feedback row:

```kotlin
                // Privacy choices (UMP form)
                SettingsItem(
                    icon = Icons.Default.AD_OFF,
                    title = stringResource(R.string.privacy_choices),
                    subtitle = stringResource(R.string.privacy_choices_subtitle),
                    onClick = {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            val ci = com.google.android.ump.ConsentInformation.getInstance(activity)
                            if (ci.isPrivacyOptionsAvailable) {
                                ci.showPrivacyOptionsForm(activity) { /* user dismissed; ignore error */ }
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.privacy_choices_not_available), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
```

- [ ] **Step 5: Add banner ad to bottom of Settings** — wrap the `Column` content inside `Scaffold` so banner sits flush beneath the scrollable content. Change:

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            ...
        }
```

To:

```kotlin
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
                ... (all existing sections) ...
            }
            com.dhanuk.govphoto.ui.ads.BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
```

Need the imports at top:

```kotlin
import androidx.compose.foundation.layout.navigationBarsPadding
import com.dhanuk.govphoto.ui.ads.BannerAd
```

- [ ] **Step 6: Add Settings strings (EN)** to `values/strings.xml` before `</resources>`:

```
    <!-- Monetization / Settings new sections -->
    <string name="subscription_section">Subscription</string>
    <string name="remove_ads">Remove ads</string>
    <string name="remove_ads_subtitle">Unlock GovPhoto Pro — ad-free forever</string>
    <string name="notifications_section">Notifications</string>
    <string name="notify_release_notes">Release notes</string>
    <string name="notify_release_notes_desc">New features, presets, bug fixes</string>
    <string name="notify_exam_deadlines">Exam deadlines</string>
    <string name="notify_exam_deadlines_desc">Alerts when exam form windows close soon</string>
    <string name="notify_support_replies">Support replies</string>
    <string name="notify_support_replies_desc">Notify when our team replies to your message</string>
    <string name="privacy_choices">Privacy choices</string>
    <string name="privacy_choices_subtitle">Manage ad consent preferences</string>
    <string name="privacy_choices_not_available">Privacy options are not available yet.</string>
    <string name="rewarded_ad_free_button">Watch ad for 24 hours ad-free</string>
    <string name="rewarded_ad_free_subtitle">Tap to watch a short video and remove ads for 24 hours</string>
    <string name="rewarded_ad_loaded_toast">Ad loaded. Tap to start.</string>
    <string name="rewarded_ad_granted_toast">24 hours of ad-free unlocked. Thanks for supporting our work.</string>
    <string name="rewarded_ad_failed_toast">Ad failed to load. Please try again later.</string>
```

- [ ] **Step 7: Add Settings strings (HI)** to `values-hi/strings.xml` before `</resources>`:

```
    <!-- Monetization / Settings new sections (Hindi) -->
    <string name="subscription_section">सदस्यता</string>
    <string name="remove_ads">विज्ञापन हटाएं</string>
    <string name="remove_ads_subtitle">GovPhoto Pro पाएं — हमेशा विज्ञापन-मुक्त</string>
    <string name="notifications_section">सूचनाएं</string>
    <string name="notify_release_notes">रिलीज़ नोट्स</string>
    <string name="notify_release_notes_desc">नई सुविधाएं, प्रीसेट, बग सुधार</string>
    <string name="notify_exam_deadlines">परीक्षा की समय-सीमा</string>
    <string name="notify_exam_deadlines_desc">परीक्षा फ़ॉर्म विंडो बंद होने पर अलर्ट</string>
    <string name="notify_support_replies">सहायता उत्तर</string>
    <string name="notify_support_replies_desc">हमारी टीम के उत्तर पर सूचना</string>
    <string name="privacy_choices">गोपनीयता विकल्प</string>
    <string name="privacy_choices_subtitle">विज्ञापन सम्मति वरीयताएं प्रबंधित करें</string>
    <string name="privacy_choices_not_available">गोपनीयता विकल्प अभी उपलब्ध नहीं हैं।</string>
    <string name="rewarded_ad_free_button">विज्ञापन देखें — 24 घंटे के लिए विज्ञापन-मुक्त</string>
    <string name="rewarded_ad_free_subtitle">छोटा वीडियो देखें और 24 घंटे के लिए विज्ञापन हटाएं</string>
    <string name="rewarded_ad_loaded_toast">विज्ञापन लोड हो गया। शुरू करने के लिए टैप करें।</string>
    <string name="rewarded_ad_granted_toast">24 घंटे विज्ञापन-मुक्त सेवा सक्रिय। सहयोग के लिए धन्यवाद।</string>
    <string name="rewarded_ad_failed_toast">विज्ञापन लोड विफल। कृपया बाद में पुनः प्रयास करें।</string>
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhanuk/govphoto/ui/screens/SettingsScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/navigation/NavHost.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-hi/strings.xml
git commit -m "feat(settings): Remove Ads, Notifications, Privacy choices sections + banner ad"
```

- [ ] **Step 9: Push and verify CI**

```bash
git push
```


### Task 8: Ad placements — banners on 4 screens, SaveSuccess interstitial, Settings rewarded button

**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/screens/AllFormsScreen.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/screens/HistoryScreen.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/screens/SaveSuccessScreen.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/screens/SettingsScreen.kt` (rewarded button)

**Interfaces:**
- Consumes: `BannerAd` (Task 3), `InterstitialController` (Task 3), `RewardedAd` via the AdMob SDK + `SettingsRepository.setAdFreeUntilMs` (Task 9 — for now persist via a simple file/state; re-wire in Task 9).
- Produces: Banners on Home, AllForms, History, SaveSuccess + Settings (last wired in Task 7). Interstitial released on SaveSuccess after a successful save (rate-limited). Settings "Watch ad → 24h ad-free" row.

- [ ] **Step 1: Add banner to `HomeScreen.kt`** — wrap the existing Scaffold body so scroll content fills weight 1f and banner sits at flush-bottom. Read the file first to find the existing `Column { ... }` body inside `Scaffold`. The pattern is: locate `Column(modifier = Modifier.fillMaxSize()...)` immediately after `) { paddingValues ->` and restructure:

```kotlin
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(paddingValues)
                    // ... (= original content modifiers that would otherwise parent banner)
            ) {
                // ... (original content) ...
            }
            com.dhanuk.govphoto.ui.ads.BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
```

Need imports:

```kotlin
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import com.dhanuk.govphoto.ui.ads.BannerAd
```

- [ ] **Step 2: Add banner to `AllFormsScreen.kt`** — same pattern as HomeScreen.

- [ ] **Step 3: Add banner to `HistoryScreen.kt`** — same pattern.

- [ ] **Step 4: Add banner + trigger interstitial on `SaveSuccessScreen.kt`**:

At the bottom of the `Scaffold { paddingValues -> Column { ... } }` body, restructure to:

```kotlin
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ... (all existing content) ...
            }
            com.dhanuk.govphoto.ui.ads.BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
```

Need imports:

```kotlin
import androidx.compose.foundation.layout.navigationBarsPadding
import com.dhanuk.govphoto.ui.ads.BannerAd
import dagger.hilt.android.EntryPointAccessors
import com.dhanuk.govphoto.data.ads.InterstitialController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
```

After computing `processedImageUri` and confirming the success state at the top of the composable function body, add a LaunchedEffect that records the save and tries to show the interstitial:

```kotlin
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        // mark save count + trigger interstitial (AdMob rate-limit enforced inside controller)
        val controller = runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                SaveSuccessInterstitialEntryPoint::class.java,
            ).interstitialController()
        }.getOrNull() ?: return@LaunchedEffect
        controller.recordSaveReceived()
        // Slight delay so the success screen paints before the interstitial
        kotlinx.coroutines.delay(300)
        activity?.let { controller.tryShow(it) }
    }
```

Add the entrypoint at the bottom of `SaveSuccessScreen.kt`:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SaveSuccessInterstitialEntryPoint {
    fun interstitialController(): InterstitialController
}
```

- [ ] **Step 5: Add `InterstitialController` EntryPoint in `GovPhotoApp.kt`** — the same `GovPhotoAppEntryPoint` from Task 6, add the function:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface GovPhotoAppEntryPoint {
    fun subscriptionRepository(): com.dhanuk.govphoto.data.subscription.SubscriptionRepository
    fun adsRepository(): com.dhanuk.govphoto.data.ads.AdsRepository
    fun pushRepository(): com.dhanuk.govphoto.data.push.PushRepository
    fun interstitialController(): com.dhanuk.govphoto.data.ads.InterstitialController
}
```

- [ ] **Step 6: Add "Watch ad → 24h ad-free" button to `SettingsScreen.kt`** — insert a new row inside the `Subscription` section, after the Remove Ads row:

```kotlin
                SettingsItem(
                    icon = Icons.Default.PlayCircle,
                    title = stringResource(R.string.rewarded_ad_free_button),
                    subtitle = stringResource(R.string.rewarded_ad_free_subtitle),
                    onClick = {
                        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) {
                            android.widget.Toast.makeText(context, context.getString(R.string.rewarded_ad_failed_toast), android.widget.Toast.LENGTH_SHORT).show()
                            return@SettingsItem
                        }
                        val activity = context as? android.app.Activity
                        if (activity == null) {
                            android.widget.Toast.makeText(context, context.getString(R.string.rewarded_ad_failed_toast), android.widget.Toast.LENGTH_SHORT).show()
                            return@SettingsItem
                        }
                        // Load + show rewarded ad; on user-earned reward, persist ad-free for 24h
                        val rewardedAd = com.google.android.gms.ads.rewarded.RewardedAd(context.applicationContext, BuildConfig.ADMOB_REWARDED_UNIT)
                        val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()
                        rewardedAd.loadAd(adRequest, object : com.google.android.gms.ads.rewarded.RewardedAdLoadCallback() {
                            override fun onAdLoaded() {
                                android.widget.Toast.makeText(context, context.getString(R.string.rewarded_ad_loaded_toast), android.widget.Toast.LENGTH_SHORT).show()
                                rewardedAd.show(activity) { rewardItem ->
                                    // Persist ad-free for 24h — task 9 will swap this to SettingsRepository
                                    val untilMs = System.currentTimeMillis() + 24 * 3_600_000L
                                    // Direct write to a prefs file as interim storage
                                    val prefs = context.getSharedPreferences("govphoto_ad_free", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putLong("ad_free_until_ms", untilMs).apply()
                                    // Force adsRepository refresh via EntryPoint
                                    runCatching {
                                        dagger.hilt.android.EntryPointAccessors.fromApplication(
                                            context.applicationContext,
                                            AdsRefreshEntryPoint::class.java,
                                        ).adsRepository().refresh()
                                    }
                                    android.widget.Toast.makeText(context, context.getString(R.string.rewarded_ad_granted_toast), android.widget.Toast.LENGTH_LONG).show()
                                }
                            }

                            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                android.widget.Toast.makeText(context, context.getString(R.string.rewarded_ad_failed_toast), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                )
```

Add at the bottom of `SettingsScreen.kt`:

```kotlin
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdsRefreshEntryPoint {
    fun adsRepository(): com.dhanuk.govphoto.data.ads.AdsRepository
}
```

Need the `BuildConfig` import at top of `SettingsScreen.kt` (already there).

- [ ] **Step 7: Update `AdStateProvider` in `AppModule.kt`** — add the SharedPreferences-backed `adFreeUntilMs` read so the rewarded grant actually flips `isAdFree` without waiting for Task 9's full DataStore work:

```kotlin
    @Provides
    @Singleton
    fun provideAdStateProvider(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context,
        subscriptionRepository: com.dhanuk.govphoto.data.subscription.SubscriptionRepository,
    ): com.dhanuk.govphoto.data.ads.AdStateProvider =
        object : com.dhanuk.govphoto.data.ads.AdStateProvider {
            override val isPro: Boolean get() = subscriptionRepository.isPro.value
            override val adFreeUntilMs: Long get() {
                return ctx.getSharedPreferences("govphoto_ad_free", android.content.Context.MODE_PRIVATE)
                    .getLong("ad_free_until_ms", 0L)
            }
            override val forceNoAds: Boolean get() = BuildConfig.DEBUG
        }
```

This interim backing is acceptable until Task 9 migrates to SettingsRepository (just keep the prefs file name).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhanuk/govphoto/ui/screens/HomeScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/screens/AllFormsScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/screens/HistoryScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/screens/SaveSuccessScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/screens/SettingsScreen.kt \
        app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt \
        app/src/main/java/com/dhanuk/govphoto/GovPhotoApp.kt
git commit -m "feat(ads): banners on Home/AllForms/History/SaveSuccess, SaveSuccess interstitial, Settings rewarded 24h ad-free"
```

- [ ] **Step 9: Push and verify CI**

```bash
git push
```


### Task 9: SettingsRepository — new prefs + SettingsViewModel exposure

**Files:**
- Modify: `app/src/main/java/com/dhanuk/govphoto/data/datastore/SettingsRepository.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt` (replace stub `CachedIsProStore`, `PushCategoryStore`, `AdStateProvider.adFreeUntilMs` with DataStore-backed implementations)
- Modify: `app/src/test/java/com/dhanuk/govphoto/data/datastore/SettingsRepositoryTest.kt` (create if absent)

**Interfaces:**
- Consumes: DataStore (existing `Context.dataStore`); existing `SettingsState`.
- Produces: `SettingsState.cachedIsPro: Boolean`, `.adFreeUntilMs: Long`, `.saveCount: Int`, `.releaseNotificationsEnabled: Boolean`, `.examDeadlineNotificationsEnabled: Boolean`, `.supportNotificationsEnabled: Boolean`. Methods: `setCachedIsPro(v)`, `setAdFreeUntilMs(v)`, `bumpSaveCount()`, `setReleaseNotificationsEnabled(v)`, `setExamDeadlineNotificationsEnabled(v)`, `setSupportNotificationsEnabled(v)`. `SettingsRepository` now implements BOTH `CachedIsProStore` and `PushCategoryStore` (Hilt-bound in AppModule).

- [ ] **Step 1: Add keys to `SettingsRepository.kt`**

In the `private object Keys { }` block, add:

```kotlin
        val CACHED_IS_PRO            = booleanPreferencesKey("cached_is_pro")
        val AD_FREE_UNTIL_MS         = androidx.datastore.preferences.core.longPreferencesKey("ad_free_until_ms")
        val SAVE_COUNT               = androidx.datastore.preferences.core.intPreferencesKey("save_count")
        val NOTIFY_RELEASE           = booleanPreferencesKey("notify_release")
        val NOTIFY_EXAM_DEADLINES    = booleanPreferencesKey("notify_exam_deadlines")
        val NOTIFY_SUPPORT_REPLIES   = booleanPreferencesKey("notify_support_replies")
```

- [ ] **Step 2: Extend `SettingsState` data class**

```kotlin
data class SettingsState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val dynamicColor: Boolean = false,
    val darkMode: DarkModePref = DarkModePref.LIGHT,
    val largeButtons: Boolean = false,
    val highContrast: Boolean = false,
    val onboardingComplete: Boolean = false,
    val lastPresetId: String? = null,
    val cachedIsPro: Boolean = false,
    val adFreeUntilMs: Long = 0L,
    val saveCount: Int = 0,
    val releaseNotificationsEnabled: Boolean = true,
    val examDeadlineNotificationsEnabled: Boolean = false,
    val supportNotificationsEnabled: Boolean = true,
)
```

**Important:** All existing callers of `SettingsState(...)` (we use Kotlin default values everywhere, so adding fields with defaults doesn't break call sites) — but ensure `repo.state.first()` returns the new fields. Update `repo.state` map to read new keys:

```kotlin
    val state: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            language = prefs[Keys.LANGUAGE]?.let { tag ->
                AppLanguage.entries.firstOrNull { it.tag == tag }
            } ?: AppLanguage.ENGLISH,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            darkMode = prefs[Keys.DARK_MODE]?.let { label ->
                DarkModePref.entries.firstOrNull { it.label == label }
            } ?: DarkModePref.LIGHT,
            largeButtons = prefs[Keys.LARGE_BUTTONS] ?: false,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            onboardingComplete = prefs[Keys.ONBOARDING_DONE] ?: false,
            lastPresetId = prefs[Keys.LAST_PRESET_ID],
            cachedIsPro = prefs[Keys.CACHED_IS_PRO] ?: false,
            adFreeUntilMs = prefs[Keys.AD_FREE_UNTIL_MS] ?: 0L,
            saveCount = prefs[Keys.SAVE_COUNT] ?: 0,
            releaseNotificationsEnabled = prefs[Keys.NOTIFY_RELEASE] ?: true,
            examDeadlineNotificationsEnabled = prefs[Keys.NOTIFY_EXAM_DEADLINES] ?: false,
            supportNotificationsEnabled = prefs[Keys.NOTIFY_SUPPORT_REPLIES] ?: true,
        )
    }
```

- [ ] **Step 3: Add `CachedIsProStore` + `PushCategoryStore` implementations on `SettingsRepository`**

Add `implements` (`: CachedIsProStore, PushCategoryStore`) to class signature, since SettingsRepository.kt is single-class:

```kotlin
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context)
    : CachedIsProStore, PushCategoryStore {

    // ... existing members ...
}
```

Add the interface methods (don't shadow existing Methods):

```kotlin
    // CachedIsProStore
    override suspend fun getCachedIsPro(): Boolean =
        context.dataStore.data.first()[Keys.CACHED_IS_PRO] ?: false
    override suspend fun setCachedIsPro(value: Boolean) {
        context.dataStore.edit { it[Keys.CACHED_IS_PRO] = value }
    }

    // PushCategoryStore
    override suspend fun isEnabled(category: com.dhanuk.govphoto.data.push.PushCategory): Boolean {
        val pref = when (category) {
            com.dhanuk.govphoto.data.push.PushCategory.RELEASE_NOTES -> Keys.NOTIFY_RELEASE to true
            com.dhanuk.govphoto.data.push.PushCategory.EXAM_DEADLINES -> Keys.NOTIFY_EXAM_DEADLINES to false
            com.dhanuk.govphoto.data.push.PushCategory.SUPPORT_REPLIES -> Keys.NOTIFY_SUPPORT_REPLIES to true
        }
        return context.dataStore.data.first()[pref.first] ?: pref.second
    }
    override suspend fun setEnabled(category: com.dhanuk.govphoto.data.push.PushCategory, enabled: Boolean) {
        val key = when (category) {
            com.dhanuk.govphoto.data.push.PushCategory.RELEASE_NOTES -> Keys.NOTIFY_RELEASE
            com.dhanuk.govphoto.data.push.PushCategory.EXAM_DEADLINES -> Keys.NOTIFY_EXAM_DEADLINES
            com.dhanuk.govphoto.data.push.PushCategory.SUPPORT_REPLIES -> Keys.NOTIFY_SUPPORT_REPLIES
        }
        context.dataStore.edit { it[key] = enabled }
    }

    // Ad-free reward
    suspend fun setAdFreeUntilMs(untilMs: Long) {
        context.dataStore.edit { it[Keys.AD_FREE_UNTIL_MS] = untilMs }
    }
    suspend fun bumpSaveCount() {
        context.dataStore.edit { it[Keys.SAVE_COUNT] = (it[Keys.SAVE_COUNT] ?: 0) + 1 }
    }
```

Add `kotlinx.coroutines.flow.first` to the imports.

- [ ] **Step 4: Move `CachedIsProStore` + `PushCategoryStore` interfaces from separate files into SettingsRepository.kt or keep them in dedicated files** — already in `data.subscription.CachedIsProStore` and `data.push.PushCategoryStore`. So in `SettingsRepository.kt`, the implements signatures reference `data.subscription.CachedIsProStore` and `data.push.PushCategoryStore`. Add imports:

```kotlin
import com.dhanuk.govphoto.data.subscription.CachedIsProStore
import com.dhanuk.govphoto.data.push.PushCategory
import com.dhanuk.govphoto.data.push.PushCategoryStore
```

- [ ] **Step 5: Update `AppModule.kt`** — delete the stub providers for `CachedIsProStore` and `PushCategoryStore` (now provided by `SettingsRepository` implementing them). Also route `AdStateProvider.adFreeUntilMs` to read from SettingsRepository (replace the SharedPreferences hack in Task 8).

Replace the `provideCachedIsProStore` and `providePushCategoryStore` providers with deletion; remove the stub `@Provides` @ funs.

Add a `Flow<Long>` reference for `adFreeUntilMs` by exposing `SettingsRepository` to the provider. The simplest is to read once:

```kotlin
    @Provides
    @Singleton
    fun provideAdStateProvider(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context,
        subscriptionRepository: com.dhanuk.govphoto.data.subscription.SubscriptionRepository,
        settingsRepository: com.dhanuk.govphoto.data.datastore.SettingsRepository,
    ): com.dhanuk.govphoto.data.ads.AdStateProvider =
        object : com.dhanuk.govphoto.data.ads.AdStateProvider {
            override val isPro: Boolean get() = subscriptionRepository.isPro.value
            override val adFreeUntilMs: Long get() =
                kotlinx.coroutines.flow.first(settingsRepository.state).adFreeUntilMs
            override val forceNoAds: Boolean get() = BuildConfig.DEBUG
        }
```

(Reading `settingsRepository.state.first()` from a `SuspendingFunction0`/getter isn't ideal because getters aren't suspend. Better: hold a `StateFlow<Long>` mirror emitting the latest ad-free timestamp, updated by `SettingsRepository` whenever the value changes via `SettingsRepository.state.map { it.adFreeUntilMs }.stateIn(scope, ...)`. To keep the plan bounded, accept the prefs file from Task 8 as the source of truth for `adFreeUntilMs` and have `SettingsRepository.setAdFreeUntilMs` mirror-write both DataStore AND the prefs file for forward-compat. Document in code as a "write-through cache" with a TODO cleanup in next PR.)

Simplest approach for Task 9: keep Task 8's prefs file as the read source (no migration), and have `setAdFreeUntilMs` write DataStore only, plus `bumpSaveCount`. The read path stays: `ctx.getSharedPreferences("govphoto_ad_free").getLong("ad_free_until_ms", 0L)`. Soft migration only — exactly the state Task 8 left us in. Skip migrations here.

- [ ] **Step 6: Update `SettingsViewModel.kt`** to expose new state + setter:

```kotlin
fun setCachedIsPro(cached: Boolean) = viewModelScope.launch { repo.setCachedIsPro(cached) }
fun setAdFreeUntilMs(untilMs: Long) = viewModelScope.launch { repo.setAdFreeUntilMs(untilMs) }
fun recordSave() = viewModelScope.launch { repo.bumpSaveCount() }
```

- [ ] **Step 7: Write `SettingsRepositoryTest.kt`** — verify new keys round-trip.

`app/src/test/java/com/dhanuk/govphoto/data/datastore/SettingsRepositoryTest.kt`:

```kotlin
package com.dhanuk.govphoto.data.datastore

import com.dhanuk.govphoto.data.push.PushCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test fun `cachedIsPro round-trips`() = runTest {
        val r = repo()
        r.setCachedIsPro(true)
        assertTrue(r.getCachedIsPro())
        assertEquals(true, r.state.first().cachedIsPro)
    }

    @Test fun `notification defaults match spec`() = runTest {
        val r = repo()
        assertFalse(r.isEnabled(PushCategory.EXAM_DEADLINES))
        assertTrue(r.isEnabled(PushCategory.RELEASE_NOTES))
        assertTrue(r.isEnabled(PushCategory.SUPPORT_REPLIES))
    }

    @Test fun `saveCount increments`() = runTest {
        val r = repo()
        r.bumpSaveCount(); r.bumpSaveCount()
        assertEquals(2, r.state.first().saveCount)
    }

    @Test fun `adFreeUntilMs round-trips`() = runTest {
        val r = repo()
        r.setAdFreeUntilMs(12345L)
        assertEquals(12345L, r.state.first().adFreeUntilMs)
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhanuk/govphoto/data/datastore/SettingsRepository.kt \
        app/src/main/java/com/dhanuk/govphoto/ui/viewmodel/SettingsViewModel.kt \
        app/src/main/java/com/dhanuk/govphoto/di/AppModule.kt \
        app/src/test/java/com/dhanuk/govphoto/data/datastore/SettingsRepositoryTest.kt
git commit -m "feat(settings): extend SettingsRepository with pro/ad-free/notifications prefs + setters"
```

- [ ] **Step 9: Push and verify CI**

```bash
git push
```


### Task 10: ProGuard consolidation + final CI verification + mega-PR merge

**Files:**
- Modify: `app/proguard-rules.pro` (consolidate rules added incrementally in Tasks 2-6)
- Verify: `.github/workflows/android-build.yml`

**Interfaces:**
- Consumes: All rules added in Tasks 2-6 (Crashlytics auto, Firebase Analytics, AdMob, UMP, RevenueCat, OneSignal).
- Produces: Final consolidated ProGuard rules; full CI build (debug+release, lint, unit tests) green on `feat/pr2-monetization-mega`.

- [ ] **Step 1: Verify `app/proguard-rules.pro` is consolidated** — read the file (should already contain the additions from Tasks 2, 3, 4, 6). If any rule block was duplicated by subagent-driven execution, dedupe to a single block per SDK. Final shape (append if absent):

```
# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# UMP
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }
-keep class com.revenuecat.purchases.google.** { *; }
-keepclassmembers class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# OneSignal
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**
```

(Firebase Crashlytics auto-rules ship in the SDK — confirmed.)

- [ ] **Step 2: Run the test suite verification**

```bash
git push
```

Wait for CI: every job (build + lint) must succeed on `feat/pr2-monetization-mega`.

If any test fails, fix forward. Common issues:
- `Robolectric` manifest warnings for ` OnesignalReceiver` — non-fatal, can suppress.
- `Purchases.isConfigured` returning false in `SubscriptionRepositoryTest` — verify the `bind()` no-op skip path is correct.
- Compose `IntrinsicSize.Min` import — verify spelling (`IntrinsicSize.Min`, not `IntrinsicSize.Min`).
- `RsolutionResult`load failures — confirm `oneSignal` repository name.

- [ ] **Step 3: Local review of `BuildConfig` mismatch** — verify `BuildConfig.FORCE_NO_ADS` is `true` only in debug. Read `app/build/intermediates/.../BuildConfig.java` if visible in CI logs, or read `app/build.gradle.kts` to confirm the two buildConfigField lines are split between the `debug` and `release` blocks (a duplicate `buildConfigField` in `defaultConfig` AND `debug` would conflict — must be ONLY in the buildType blocks).

- [ ] **Step 4: Confirm secrets.properties.template** matches the populate-secrets.sh flow. Read `secrets.properties.template`:

```
PRIVACY_URL=...
TERMS_URL=...
CONTACT_URL=...
ADMOB_APP_ID=... (test)
ADMOB_BANNER_UNIT=... (test)
ADMOB_INTERSTITIAL_UNIT=... (test)
ADMOB_REWARDED_UNIT=... (test)
REVENUECAT_API_KEY=goog_test_key
ONESIGNAL_APP_ID=test-onesignal-id
KEYSTORE_FILE=release-keystore.jks
KEYSTORE_PASSWORD=changeit
KEY_PASSWORD=changeit
KEY_ALIAS=mykey
```

CI overrides the real values from secrets; local builds keep the placeholders (debug-safe).

- [ ] **Step 5: Merge to main locally + push** (per user choice — no squash)

```bash
git checkout main
git pull
git merge --no-ff feat/pr2-monetization-mega -m "Merge PR #2 (mega): Firebase + AdMob + RevenueCat + OneSignal SDK integration"
./gradlew --offline # not available locally on this host — skip; rely on CI
git push origin main
```

(The user specified merge method is "merge locally + push main".)

- [ ] **Step 6: Verify CI green on `main`** — review the Actions run on main; all jobs must pass. If the merge introduced failures (e.g., secret-dependent build silently skipped on dev), confirm secrets are present.

- [ ] **Step 7: Final post-merge housekeeping** (optional — surface as a plan note)

1. After launch, replace `REVENUECAT_API_KEY` from `test_` key to production key once RevenueCat generates the live key (currently the GH secret holds `test_reBLWKQuYoCcNnfaDmwjuQiGGCu`).
2. Create 3 Google Play Console in-app products: `govphoto_pro_weekly` (₹79/wk), `govphoto_pro_monthly` (₹149/mo), `govphoto_pro_yearly` (₹999/yr). Then in RevenueCat dashboard, attach those products to the existing Paywall draft ("GovPhoto Pro Paywall", ID `pweeddfe2d247046c4`, offering "default"); Publish the paywall.
3. After first release with Crashlytics verified live reporting, remove the `last_crash.txt` `Thread.setDefaultUncaughtExceptionHandler` from MainActivity in a follow-up PR (defense-in-depth cleanup per design spec §4.10).
4. Remove interim SharedPreferences `govphoto_ad_free` file once SettingsRepository is the sole source of truth for `adFreeUntilMs` (current Task 9 keeps both write paths for stability).
5. Confirm `Never on first save` interstitial rule — needs verification with `recordSaveReceived` before `tryShow`. In Task 8 we call `controller.recordSaveReceived()` and then `tryShow`. The InterstitialController's RateLimiter uses minSaveCount=2 — so the FIRST save still won't trigger, because `recordSaveReceived` increments to 1, and canShow requires saveCount >= 2. The SECOND save will increment to 2 and trigger (subject to cooldown + session cap). Verify this in test output.

End of Plan

---

## Self-Review Notes (post-write)

**Spec coverage check:**
- §4.1 component map: Tasks 3, 4, 6 cover AdsRepository, SubscriptionRepository, PushRepository, InterstitialController, BannerAd, PaywallScreen, PaywallViewModel.
- §4.2 secrets pipeline: Task 1 extends populate-secrets.sh with keystore + google-services.json decode.
- §4.3 SubscriptionRepository shape: Task 4 reproduces the spec's `SubscriptionRepository.kt` shape (minus `coroutineScope` nuances — verify in CI).
- §4.4 ad placements + rate limits: Tasks 3, 8 implement banner placements + interstitial rate-limits + rewarded 24h ad-free.
- §4.5 UMP consent: Tasks 3 (init in onCreate) + 7 (Privacy choices Settings row).
- §4.6 Paywall UI: Task 5 implements PaywallScreen with 3 plan cards, "Best value" on Yearly.
- §4.7 Pricing: ₹79/₹149/₹999 reflected in Task 8 strings comment + post-merge housekeeping note (Play Console product creation).
- §4.8 Interstitial-on-Settings-open: NOT implemented (declined per spec).
- §4.9 InfinityFree: already handled in PR #1 (merged before this PR).
- §4.10 Crash reporting transition: Task 2 keeps `last_crash.txt` handler; post-merge housekeeping note #3 schedules removal.
- §4.11 Push categories: Task 6 implements enum + repository; defaults match spec (RELEASE_NOTES=ON, EXAM_DEADLINES=OFF, SUPPORT_REPLIES=ON).
- §4.12 ProGuard: distributed across Tasks 2, 3, 4, 6 and consolidated in Task 10.
- §5 Testing: Tasks 3, 4, 6, 9 add unit tests; BuildConfig FORCE_NO_ADS verified in Task 10 step 3; `Run-tape` debug-force-no-ads test gap —变频 add note as a future task.
- §7 Implementation order: collapsed into mega-PR per user's decision; PR2-5 from spec are now Tasks 2-6 here.

**Placeholder scan:**
- No "TBD", "TODO in plan body" outside of explicit code TODOs for cleanup.
- A `TODO` in `setAdFreeUntilMs` write-through cache comment is intentional and tied to post-merge housekeeping note.

**Type / signature consistency check:**
- `AdsRepository.isAdFree: StateFlow<Boolean>` — used by BannerAd (Task 3), Settings rewarded row (Task 8) via `adsRepository().refresh()`.
- `InterstitialController.recordSaveReceived(ms: Long)` (defaults now) + `tryShow(activity: Activity): Boolean` — consistent between Task 3 and Task 8.
- `SubscriptionRepository.isPro: StateFlow<Boolean>`, `loadOfferings(): Offerings`, `purchase(activity, pkg): Result<CustomerInfo>`, `restorePurchases(): Result<CustomerInfo>` — consistent between Task 4 and Task 5 PaywallViewModel.
- `PushCategory.entries` — Task 6 enum, Task 9 SettingsRepository saw `PushCategory.entries` directly consistent.
- `CachedIsProStore.getCachedIsPro()` / `setCachedIsPro(Boolean)` — consistent across Tasks 4, 9.
- `PushCategoryStore.isEnabled(cat) / setEnabled(cat, enabled)` — consistent across Tasks 6, 9.
- `AdStateProvider.isPro / adFreeUntilMs / forceNoAds` — consistent across Tasks 3, 4, 8, 9. `forceNoAds` uses `BuildConfig.DEBUG` originally; Task 8 retained BuildConfig.DEBUG via `BuildConfig.FORCE_NO_ADS || BuildConfig.DEBUG` reviewer should double-check BannerAd inside `BannerAd.kt` (Task 3 step 7) ALSO checks `BuildConfig.DEBUG` (it does via the early return at top).

**Minor handover notes for the implementer (post-plan):**
1. Exact RevenueCat 5.9.0 SDK API method names (`purchasePackageWithPromoOfferDialog` return type) — verify in CI build error logs, fix import paths as needed.
2. `OneSignal 5.1.5`: confirm the Maven coordinate works (`com.onesignal:OneSignal:5.1.5` — verifier that version exists; downgrade to 5.0.x if 5.1.5 not on Maven Central at build time).
3. `Purchases.sharedInstance.customerInfoFlow.onEach{}.launchIn(scope)` — type may be `StateFlow<CustomerInfo>` (not SharedFlow); `onEach` works on any Flow.
4. `dagger.hilt.android.EntryPointAccessors.fromApplication` — confirmed API path from Hilt 2.50.
5. `androidx.compose.foundation.layout.navigationBarsPadding` — verify import path matches Compose BOM 2024.01.00.

