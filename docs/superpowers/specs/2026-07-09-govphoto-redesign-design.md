# GovPhoto Resizer v2 — Production Redesign Spec

**Date:** 2026-07-09
**Status:** Approved
**Session scope:** Open Design mockups + full Jetpack Compose port, single session.
**Package change:** `com.govphoto.resizer` → `com.dhanuk.govphoto_resizer` (app name unchanged: "GovPhoto Resizer").

## 1. Goal
Take the existing MVP GovPhoto Resizer Android app and rebuild it for live production:
- **New visual identity** — Material 3 Expressive design system (tokens, palette, typography, shapes, motion), replacing the India-flag Navy/Saffron/Green scheme.
- **More features** — wire currently-stubbed production features (History persistence, Settings persistence, working AI bg-removal, real face-detection guide, light/dark + dynamic color, onboarding, in-app help, batch processing) into the redesign.
- **Easy for everyone** — sensible defaults for general public (large touches, plain copy, EN+Hindi), with "advanced" disclosure for power users.
- **Same app** — same purpose (Indian government-form-style photos), same preset data source, same core flow.

## 2. Reality-checks / non-goals
- OD does not emit Compose code — mockups are a visual spec only.
- OD does not build an `.apk`. Building/signing for Play Store is a later step.
- No remote config. Presets stay in `assets/exam_presets.json`.
- No new preset data. Existing 110 presets / 4 categories reused.
- No backend / accounts / auth.
- No paid ML upgrade. Use only declared ML Kit free tiers.
- No instrumented tests — `androidTest/` stays empty; unit tests for new logic only.

## 3. Feature list (11 locked items)
| # | Feature | End state |
|---|---|---|
| F1 | M3 Expressive redesign | New tokens/types/shapes/motion, applied this time |
| F2 | Room History + Recent | `@Database` + 2 DAOs + Hilt module; History screen shows real saved photos |
| F3 | DataStore Settings | Language EN/HI actually flips strings; accessibility toggles persist; last-used preset |
| F4 | Working AI bg removal | Real mask compositing (5 bg options incl. gradient + transparent=PNG alpha) |
| F5 | Real face-detection guide + validation | Live oval on Edit; real ✓/✗ face check on Preview |
| F6 | Light/Dark + Dynamic Color | Dynamic Color opt-in (Android 12+) backed by DataStore; System/Light/Dark setting |
| F7 | Onboarding | 3-page, skippable, first-launch gated by DataStore flag |
| F8 | In-app Help | FAQ accordion + article detail + per-screen `?` shortcut |
| F9 | Batch processing | One source → multi-select presets → per-preset progress → results list |
| F10 | Save-success screen | Replaces toast — compliance badge, share, save-another |
| F11 | Package rebrand | `com.dhanuk.govphoto_resizer` everywhere |

## 4. Architecture (end state)
~45 Kotlin files (21 today). New packages: `data/local/entity/`, `data/local/dao/`, `data/datastore/`, `data/ml/`, `ui/components/`, `ui/onboarding/`.

## 5. End state of session
1. New design system + 14 HTML mockups produced via OD.
2. Compose port matching mockups.
3. New VMs, repos, Room DB, DataStore, working ML Kit bg-removal + face guide, batch flow, onboarding, help, save-success.
4. App still compiles (`./gradlew :app:assembleDebug`).
5. New unit tests pass.
6. Spec + plan committed to git.

## 6. Order of operations
- **Plan P1 — Design system + rebrand + mockups** (file/byte-level TDD steps)
- **Plan P2 — Persistence + ML features** (expanded at end of P1)
- **Plan P3 — New screens + flows + re-skin** (expanded at end of P2)
