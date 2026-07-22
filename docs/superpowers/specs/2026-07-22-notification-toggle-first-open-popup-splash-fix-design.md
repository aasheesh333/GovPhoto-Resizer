# Notification Toggle + First-Open Popup + Splash Fix — Design

**Date:** 2026-07-22
**Branch:** `feat/pr2-monetization-mega`
**Status:** Approved (pending implementation)

## Goals

1. **Master Notifications toggle in Settings** — replace the existing single clickable "Notifications" row in `SettingsScreen.kt` with a real on/off `Switch`. Moving the toggle ON asks OneSignal/OS for notification permission; turning it OFF stops the app from opting the user into any push sends, without revoking OS-level permission (impossible).
2. **First-app-open notification rationale popup** — show a popup 3 seconds after the app first opens (and after onboarding is complete), explaining why we need notifications (exam application deadlines, government updates). If user denies, **never ask again** (ask-once). Re-enabling later is done via the master Switch in Settings.
3. **Bigger splash icon** — current splash icon appears small on Android 12+ because the bitmap is being stretched via `gravity="fill"`. Switch to `gravity="center"` with explicit `240dp` size to render the bitmap at the largest permitted size within Android's SplashScreen circular mask without distortion. Also fix night-mode splash which currently diverges to a smaller launcher-foreground icon.

## Non-goals

- Removing OneSignal from the project.
- Per-category notification toggles UI (release vs exam-deadlines vs support) — the 3 category booleans stay in storage for segmentation via tags but are not exposed as individual toggles. The master Switch toggles all 3 at once.
- Custom full-screen splash (Option A) — kept the Android 12+ SplashScreen API for compatibility.
- Daily re-prompt (user declined).
- Image-asset redesign — only changes the XML drawable wrapper and night theme reference. The source `ic_splash_photo.jpg` bitmap stays as-is.

## Approach chosen

**Approach B** from the brainstorming session: first-app-open popup tied to onboarding completion. A new composable `FirstOpenNotificationPrompt` replaces the existing 60-second foreground accumulator (`NotificationPermissionGate`). The existing `SettingsItem` "Notifications" row becomes a real Material3 `Switch` wired to a new `PushRepository.setNotificationEnabled(enabled)` master method.

## Architecture

### Data flow (popup)

```
App launches → MainActivity.onCreate installs FirstOpenNotificationPrompt wrapper
  → LaunchedEffect(Unit) { delay(3000); showPopup = true if eligible }
    Eligible = SDK_INT >= TIRAMISU && !settings.notifPromptShown && settings.onboardingComplete
  → AlertDialog shows
    User taps Allow → pushRepo.promptForPermission() + setNotifPromptShown(true)
                         + setNotificationsEnabled(true)
    User taps Not now → setNotifPromptShown(true) + setNotificationsEnabled(false)
                        + pushRepo.setNotificationEnabled(false)
```

### Data flow (master toggle)

```
User taps Switch in Settings
  ON  → settings.notificationsEnabled = true
        pushRepo.promptForPermission() if OS permission not granted
        pushRepo.setCategoryEnabled(RELEASE_NOTES, true)
        pushRepo.setCategoryEnabled(EXAM_DEADLINES, false)  (default)
        pushRepo.setCategoryEnabled(SUPPORT_REPLIES, true)
        pushRepo.refreshTags()
  OFF → settings.notificationsEnabled = false
        pushRepo.setCategoryEnabled(*, false) for all 3 categories
        pushRepo.refreshTags()   // tags all become "false" → OneSignal dashboard stops sending
```

Note: OS-level permission (Android 13+) cannot be revoked programmatically. The OFF state relies on OneSignal tags all being `"false"` so the dashboard segment won't include this user. (OneSignal SDK ≥ 5.x also exposes `OneSignal.Notifications.setEnabled(false)` — we use the tag approach because it's been wired since day 1 and doesn't require SDK migration.)

## Components

### 1. `SettingsRepository` (`app/src/main/java/com/dhanuk/govphoto/data/datastore/SettingsRepository.kt`)

Add to the `Keys` object:
- `NOTIF_PROMPT_SHOWN = booleanPreferencesKey("notif_prompt_shown")` — default `false`
- `NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")` — default `true`

Add to `SettingsState`:
- `val notifPromptShown: Boolean`
- `val notificationsEnabled: Boolean`

Add setters:
- `suspend fun setNotifPromptShown(shown: Boolean)`
- `suspend fun setNotificationsEnabled(enabled: Boolean)`

**Migration block** inside `dataStore.edit { prefs -> ... }` on first read after upgrade:
- If `prefs[Keys.NOTIF_PERMISSION_ASKED] == true` and `prefs[Keys.NOTIF_PROMPT_SHOWN] == null`, set `prefs[Keys.NOTIF_PROMPT_SHOWN] = true` (existing users who already saw the old gate don't get re-prompted by the new one).

**Remove**:
- `NOTIF_PERMISSION_ASKED` key (lines 59, 87) — replaced by `NOTIF_PROMPT_SHOWN`.
- `setNotificationPermissionAsked(asked)` setter (lines 143-145).
- `notificationPermissionAsked` state field (line 38).

### 2. `PushRepository` (`app/src/main/java/com/dhanuk/govphoto/data/push/PushRepository.kt`)

Add new public method:
```kotlin
suspend fun setNotificationEnabled(enabled: Boolean) {
    // Toggles all 3 category boots to |enabled|.
    for (category in PushCategory.values()) {
        setCategoryEnabled(category, enabled)
    }
    refreshTags()
}
```

(Existing `setCategoryEnabled` and `refreshTags` continue to work — `setNotificationEnabled` is a convenience.)

### 3. `FirstOpenNotificationPrompt` (NEW — `app/src/main/java/com/dhanuk/govphoto/ui/components/FirstOpenNotificationPrompt.kt`)

Public composable wrapping content:
```kotlin
@Composable
fun FirstOpenNotificationPrompt(
    settingsViewModel: SettingsViewModel,
    content: @Composable () -> Unit,
) {
    ...
}
```

Internals:
- Resolves `pushRepo` via Hilt EntryPoint (same pattern as the old `NotificationPermissionGate`).
- Collects `settingsViewModel.state` for `onboardingComplete`, `notifPromptShown`.
- `LaunchedEffect(Unit) { delay(3000); if (eligible) showPopup = true }`.
- Renders `content()` always; overlays `AlertDialog` when `showPopup` is true.
- `AlertDialog` with `R.string.notif_rationale_title`, `R.string.notif_rationale_message`, Allow/Not-now buttons.
- Both buttons set `notif_prompt_shown = true` and dismiss the popup.

### 4. `NotificationPermissionGate` (DELETE)

File removed entirely. Imports removed from `MainActivity.kt`.

### 5. `MainActivity` (`app/src/main/java/com/dhanuk/govphoto/MainActivity.kt`)

Line 144 region:
- Remove `import ...NotificationPermissionGate`.
- Add `import ...FirstOpenNotificationPrompt`.
- Replace `NotificationPermissionGate { GovPhotoNavHost() }` with `FirstOpenNotificationPrompt(settingsViewModel) { GovPhotoNavHost() }`.
- `settingsViewModel` instance is already in scope (passed to setContent) — needs verification at edit time.

### 6. `SettingsScreen` (`app/src/main/java/com/dhanuk/govphoto/ui/screens/SettingsScreen.kt`)

Replace `SettingsItem` row (lines 138-149) with a new private composable `NotificationsMasterToggle` showing a Material3 `Switch`.

```kotlin
NotificationsMasterToggle(
    notificationsEnabled = settings.notificationsEnabled,
    onToggle = { enabled ->
        scope.launch {
            viewModel.setNotificationsEnabled(enabled)
            if (enabled) {
                pushRepo?.promptForPermission(fallbackToSettings = true)
            }
            pushRepo?.setNotificationEnabled(enabled)
        }
    },
)
```

(Note: `pushRepo.promptForPermission` is non-suspending; `setNotificationEnabled` and `viewModel.setNotificationsEnabled` are suspending. Single `scope.launch { ... }` block.)

On Android < 13: no OS permission concept exists — notifications are always granted by the OS at install time. The Switch is rendered **off and disabled** (no thumb, grayed out) with the row's subtitle text fixed to `R.string.notifications_subtitle_preal13` (new string value: "Enabled by default on your Android version"). Tapping the row is a no-op. This avoids the pretense of a toggle that does nothing while signaling to the user that they don't need to act.

### 7. `ic_splash_icon.xml` (`app/src/main/res/drawable/ic_splash_icon.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_splash_photo"
    android:gravity="center"
    android:tileMode="disabled"
    android:width="240dp"
    android:height="240dp" />
```

Rationale: Android 12+ SplashScreen API constrains `windowSplashScreenAnimatedIcon` to a 240dp circle. Setting `android:width="240dp"` + `android:height="240dp"` + `gravity="center"` renders the bitmap at exactly that size without stretching distortion.

### 8. `values-night/themes.xml` (`app/src/main/res/values-night/themes.xml`)

Line 6 — change `windowSplashScreenAnimatedIcon` from `@drawable/ic_launcher_foreground` → `@drawable/ic_splash_icon` so night mode uses the same big icon as light mode.

### 9. `strings.xml`

Add 6 strings:
```xml
<string name="notif_rationale_title">Never miss an exam deadline</string>
<string name="notif_rationale_message">We\'ll notify you when new government exam applications open, deadlines approach, and important updates arrive. Tap Allow to enable notifications.</string>
<string name="notif_rationale_allow">Allow</string>
<string name="notif_rationale_not_now">Not now</string>
<string name="notifications_off_subtitle">Tap to allow exam deadline and update notifications</string>
<string name="notifications_subtitle_preal13">Enabled by default on your Android version</string>
```

(Existing `R.string.notif_perm_title`, `notif_perm_message`, `notif_perm_allow`, `notif_perm_later`, `notifications`, `notifications_subtitle` — remove `notif_perm_*` if no other references; reuse `notifications` as-is for the row title.)

## Handling & edge cases

- **Existing installs migration** — `notif_permission_asked = true` → `notif_prompt_shown = true` (one-time edit in SettingsRepository).
- **Android < 13** — no OS permission prompt exists; Switch is locked ON with explanatory subtitle. Popup is gated by `SDK_INT >= TIRAMISU` and never shows on older OSes.
- **User denies popup AND denies OS prompt** — both buttons set `notif_prompt_shown = true`. User can still re-prompt OS-level permission via the Settings toggling ON (tapping `promptForPermission(fallbackToSettings = true)` opens Android App Settings if denied with "don't ask again").
- **User allows popup but denies OS prompt** — `notif_prompt_shown = true`, `notificationsEnabled = true` stays. The app tags don't actually fire until OS-level granted. User can re-prompt from Settings toggle OFF→ON later.
- **Onboarding skipped** — user who taps Skip in OnboardingScreen still completes onboarding (per existing `onComplete` contract), so `onboardingComplete = true` flows; popup will fire 3s after landing on Home.
- **CI verifies via GitHub Actions** (per AGENTS.md) — no local build. Commit + push + `gh run watch`.

## Testing

- Grep `app/src/test/` and `app/src/androidTest/` for:
  - `NotificationPermissionGate`
  - `notificationPermissionAsked`
  - `notif_permission_asked`
  - `promptForPermission`
  - `setNotificationPermissionAsked`
- Any test that touches these symbols — update or remove (depending on whether the existing test was for the deleted gate or for the still-existing `promptForPermission`).
- New unit tests: none required for this iteration (UI-flow feature; repository signature changes are simple setter additions, not new logic to unit-test).

## Verification (per AGENTS.md)

- Branch: `feat/pr2-monetization-mega`
- Bump `versionCode` 3 → 4 in `app/build.gradle.kts`.
- Commit + push.
- `gh run watch <run-id>` → expect `lint` SUCCESS + `build` SUCCESS.
- If lint fires on unused `notif_perm_*` strings → remove dead strings in a follow-up commit. (Per AGENTS.md "do work in small chunks" — keep speculative cleanup out of the primary commit.)

## Out-of-scope follow-ups (do NOT include)

- OneSignal SDK 5.1.38 → 5.9.x migration.
- Kotlin 1.9.24 → 2.0 plugin-compose migration.
- Real AdMob ad unit IDs (user must set GitHub secrets + AdMob UMP config — separate checklist already delivered).
- New splash bitmap asset redesign.
- Per-category notification toggles UI.
