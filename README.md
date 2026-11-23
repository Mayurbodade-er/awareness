# Awareness App for Android

> **Mindfulness Heads-Up Display (HUD) for Mindful Social Media Consumption**  
> *Target SDK:* Android 14+ (API 34+) · *Min SDK:* Android 8.0 (API 26)  
> *Architecture:* Unidirectional Pipeline (Sensor → Brain → Translator → Renderer)  
> *UI System:* [StudioBlank Design System](DESIGN.md) (Monochromatic, Flat, 0px Radius)  
> *Operating Blueprint:* [AGENTS.md](AGENTS.md)

---

## Overview

The **Awareness App** interrupts mindless short-form video consumption (Instagram Reels, YouTube Shorts, Facebook Reels, Snapchat Spotlight, and supported browsers) by presenting a non-intrusive floating Heads-Up Display (HUD) over target applications after a user-configured **reveal delay**.

The application is engineered with an uncompromising focus on **privacy**, **ultra-low resource usage (< 30 MB RAM)**, and **battery discipline**.

---

## Core Invariants

- 🛡️ **Zero Content Scraping:** Enforced by custom Android lint checks (`NoTextReadDetector`). The app reads only view IDs and class names—never `node.text`, `node.contentDescription`, or `node.extras`.
- 🔒 **Zero Internet Access:** The manifest declares **no `android.permission.INTERNET`**. All stats and preferences are stored exclusively on device using Room and DataStore.
- 🔋 **Zero-Polling Battery Discipline:** No `AlarmManager`, no `Handler.postDelayed`, no `UsageStatsManager`, and no background config loops. All timing rides a single monotonic 1 Hz ticker.
- ⏱️ **Immutable Session Delay Snapshot:** Reveal delay is snapshotted at `Idle → Active` transition; mid-session configuration changes do not alter an ongoing session.
- 🎨 **StudioBlank Design System:** Monochromatic grayscale palette, strict `0.dp` border radius (`RectangleShape` everywhere), completely flat `0.dp` elevation, and weight contrast using Inter and IBM Plex Mono typography.

---

## Architecture Pipeline

```
┌────────────────────────────────────────────────────────┐
│ 1. SENSOR: AwarenessAccessibilityService               │
│    • System XML packageNames filtering                 │
│    • Depth-bounded tree traversal (MAX_DEPTH = 10)     │
│    • Emits: SessionStarted, SessionEnded, ReelScrolled │
└───────────────────────────┬────────────────────────────┘
                            │ tryEmit (SharedFlow, buffer=16)
                            ▼
┌────────────────────────────────────────────────────────┐
│ 2. BRAIN: SessionOrchestrator (Pure Kotlin, :core:domain)│
│    • State machine: Idle ◄──► Active                   │
│    • Snapshots revealDelayMillisSnapshot at start      │
│    • Increments reelCount independently of HUD display │
│    • Dispatches SessionSummary to Room on session end  │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow<SessionState>
                            ▼
┌────────────────────────────────────────────────────────┐
│ 3. TRANSLATOR: ObserveHudStateUseCase (:core:domain)   │
│    • Combines session state + preferences + 1 Hz tick  │
│    • Computes isVisible = Active && elapsed >= delay   │
│    • Message rotation via MessageRotation.indexFor     │
└───────────────────────────┬────────────────────────────┘
                            │ Flow<HudState>
                            ▼
┌────────────────────────────────────────────────────────┐
│ 4. RENDERER: HudForegroundService (:feature:hud)       │
│    • Android 14+ specialUse FGS (subtype: mindfulness) │
│    • Hosts WindowManager + ComposeView                 │
│    • FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE           │
└────────────────────────────────────────────────────────┘
```

---

## Modular Android Monorepo Structure

```
├── app/                  # Application entry point, DI root, manifest assembly
├── core/
│   ├── domain/           # Pure Kotlin business logic & state machine (zero Android deps)
│   ├── data/             # Room database & DataStore preferences
│   ├── accessibility/    # Event-driven AccessibilityService sensor
│   ├── overlay/          # WindowManager host & ComposeView coordinator
│   ├── presentation/     # Permission coordinator & time/rotation formatters
│   └── design-system/    # StudioBlank design tokens & flat components
├── feature/
│   ├── hud/              # Passive overlay Foreground Service (FGS) renderer
│   ├── settings/         # Companion permissions & HUD personalization UI
│   └── stats/            # Local analytics dashboard UI
└── docs/                 # FRD, ARD, and Vertical Slice specifications
```

---

## Development & Execution Strategy

Development follows the **Vertical Slice Architecture (VSA)** in strict sequence (**VS-01 through VS-09**) as specified in [AGENTS.md](AGENTS.md):

1. **VS-01:** Companion Shell, Permissions, Preferences & Domain Foundation
2. **VS-02:** Target App Foreground Detection & Package Filtering
3. **VS-03:** Session Lifecycle & Delay Snapshotting
4. **VS-04:** HUD Overlay Rendering Shell (Passive FGS)
5. **VS-05:** HUD Content, Monotonic Timer & Message Rotation
6. **VS-06:** Reel/Short Scroll Counting & Traversal Guard
7. **VS-07:** Browser Support & Domain Inference
8. **VS-08:** Room Local Persistence & Stats Dashboard
9. **VS-09:** OEM Survivability, Battery Hardening & Audits

---

## Build & Test

### Local Build
```bash
# Run all unit tests
./gradlew test

# Build debug APK (used on staging branch)
./gradlew assembleDebug

# Install debug APK to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Static Invariant Audits
```bash
# Verify no internet permission
! grep -rn "android.permission.INTERNET" app/ core/ feature/

# Verify no forbidden schedulers
! grep -rnE "(AlarmManager|postDelayed|UsageStatsManager|setRepeating)" app/ core/ feature/

# Verify StudioBlank 0px radius compliance
! grep -rnE "RoundedCornerShape\(([^0]|0\.[1-9])" core/design-system/ feature/ app/
```

---

## License

Local-only, privacy-first open-source utility. See repository for details.
