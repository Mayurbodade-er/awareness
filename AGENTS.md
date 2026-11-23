# AGENTS.md — Autonomous Agent Operating Blueprint & System Specification

> **System:** Awareness App for Android  
> **Target SDK:** API 34+ (Android 14+) · **Min SDK:** API 26 (Android 8.0)  
> **Role:** Veteran Android System Design Architect  
> **Core Architecture:** Unidirectional Event-Driven Pipeline (Sensor → Brain → Translator → Renderer)  
> **UI System:** StudioBlank Design System (Defined exclusively in [DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md))  
> **Execution Strategy:** **One Vertical Slice at a Time (VS-01 → VS-09)**

---

## 1. Prime Directive & Operating Protocol

This document is the **authoritative operating manual** for any autonomous AI agent or engineer implementing the Awareness App. You must adhere strictly to the architectural constraints, frozen contracts, and execution rules defined herein.

### 1.1 The "One Slice at a Time" Execution Mandate
Development must proceed in **strict sequential vertical slices** (VS-01 through VS-09).

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│  VS-01  │ ──► │  VS-02  │ ──► │  VS-03  │
└─────────┘     └─────────┘     └─────────┘
     │
     ▼
┌─────────┐     ┌─────────┐     ┌─────────┐
│  VS-04  │ ──► │  VS-05  │ ──► │  VS-06  │
└─────────┘     └─────────┘     └─────────┘
     │
     ▼
┌─────────┐     ┌─────────┐     ┌─────────┐
│  VS-07  │ ──► │  VS-08  │ ──► │  VS-09  │
└─────────┘     └─────────┘     └─────────┘
```

#### Protocol for Each Slice:
1. **Scope Intake:** Read the specific slice requirements in `docs/SliceXX-YY.md`, `docs/ARD.md`, `docs/FRD.md`, and this file.
2. **Contract Verification:** Confirm compatibility with frozen cross-slice contracts (§4).
3. **Implementation:**
   - Create/edit only the files allocated to that slice.
   - For all UI design, typography, colors, spacing, borders, shapes, elevation, and component specs, adhere strictly to [DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md) (§3).
   - Never implement features belonging to future slices.
4. **Verification & Testing:**
   - Write and execute corresponding Unit, Integration, and API tests.
   - Run forbidden API grep verification (§6.2).
   - Verify zero lint regressions (`NoTextReadDetector`).
5. **Ledger Update Mandate:**
   - Record every created and modified file/folder in the **Slice Execution & File Modification Ledger (§8)**.
   - Explicitly document the architectural rationale explaining *why* each file or folder was created or modified.
6. **Gate Sign-off:** Confirm every Acceptance Criterion and Edge Case mitigation is met before advancing to the next slice.

### 1.2 GitHub Architecture, Branching Model & Quality Gates

Development and version control on GitHub must follow a **Staged Trunk-Based Workflow** within a **Modular Android Monorepo**:

```
       [ slice/vs-01 ] ──► PR ──┐
                                ▼
(origin) ───────────────────► staging ───────────────────► main (Production / v1.0)
                                ▲            ▲
       [ slice/vs-02 ] ──► PR ──┘            │
                                  (Gate Sign-off & Audit)
```

#### 1. Branch Topology & Responsibilities
- **`main` (Production & Releases):**
  - Protected branch representing verified, production-ready releases (e.g., v1.0 milestone).
  - No direct commits. Merged exclusively from `staging` following complete slice roadmap sign-off.
- **`staging` (Integration Baseline):**
  - Primary working and integration branch for active development.
  - Slices merge into `staging` via Pull Requests once all slice acceptance criteria, tests, and audits pass.
- **`slice/vs-XX` (Vertical Slice Branches):**
  - Ephemeral feature branches cut from `staging` (e.g., `slice/vs-01`).
  - Isolated to the specific files, tests, and documentation of that slice.
  - Merged into `staging` via Pull Request upon reaching Definition of Done (§7).

#### 2. Repository Architecture: Modular Android Monorepo
The codebase is structured into 11 decoupled Gradle modules with unidirectional dependencies:
- `:app`: Application entry point, DI root, manifest assembly.
- `:feature:hud`: Passive overlay Foreground Service (FGS) renderer.
- `:feature:settings`: Companion permissions and HUD personalization UI.
- `:feature:stats`: Local session persistence and stats dashboard UI.
- `:core:accessibility`: Event-driven system accessibility sensor.
- `:core:overlay`: WindowManager host and ComposeView overlay coordinator.
- `:core:presentation`: Permission coordinator, time formatters, and rotation logic.
- `:core:data`: Room database and DataStore preferences implementations.
- `:core:design-system`: StudioBlank design tokens and flat, zero-radius components.
- `:core:domain`: Pure Kotlin business logic and frozen state machine (zero Android framework dependencies).

#### 3. GitHub CI/CD Quality Gates & Automated Audits
Every commit and Pull Request must pass automated validation before merging into `staging`:
- **Automated Test Gate:** All unit and integration tests allocated to merged slices pass (targeting 120 tests total).
- **Privacy Gate:** Custom lint rule `NoTextReadDetector` fails the build if `node.text`, `node.contentDescription`, or `node.extras` is accessed.
- **Security Gate:** CI grep fails if `android.permission.INTERNET` is detected in any manifest.
- **Battery Gate:** CI grep fails if forbidden schedulers (`AlarmManager`, `postDelayed`, `UsageStatsManager`, `setRepeating`) are introduced.
- **Design System Gate:** CI grep fails if rounded corners (`RoundedCornerShape`) or non-zero elevations are introduced.

---

## 2. System Architecture & Invariants

The Awareness App interrupts mindless social media consumption with a floating, non-intrusive Heads-Up Display (HUD) while maintaining ultra-low resource usage and rock-solid privacy.

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. SENSOR: AwarenessAccessibilityService (:core:accessibility)         │
│    • Filter: XML packageNames (Instagram, YouTube, FB, Snapchat, Web)  │
│    • Depth-bounded tree traversal (MAX_DEPTH = 10, view IDs ONLY)      │
│    • Emits: SessionStarted, SessionEnded, ReelScrolled,                │
│             BrowserDomainInferred                                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ tryEmit (SharedFlow, buffer=16)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 2. BRAIN: SessionOrchestrator (:core:domain, Pure Kotlin)              │
│    • State machine: Idle ◄──► Active                                   │
│    • Snapshots revealDelayMillisSnapshot at Idle ──► Active transition │
│    • Increments reelCount on ReelScrolled (independent of HUD display) │
│    • Tracks inferredDomain (session-transient, never persisted)        │
│    • On SessionEnded: persists SessionSummary to Room on IO            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ StateFlow<SessionState>
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 3. TRANSLATOR: ObserveHudStateUseCase (:core:domain)                   │
│    • combine(sessionState, hudPreferences, timerTicks) ──► Flow<HudState│
│    • Computes isVisible = Active && elapsed >= revealDelaySnapshot     │
│    • Computes message rotation via MessageRotation.indexFor            │
│    • Omits Reel line for browsers (showReelCount = !isBrowser)         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Flow<HudState>
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 4. RENDERER: HudForegroundService (:feature:hud)                       │
│    • Android 14+ specialUse FGS (subtype: mindfulness_hud)             │
│    • Hosts WindowManager + ComposeView (passive renderer)              │
│    • FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE (touch pass-through)      │
│    • Zero business logic; never reads DataStore or accessibility tree  │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Non-Negotiable Invariants

| Category | Invariant & Enforcement |
|---|---|
| **Zero Content Scraping** | Lint-enforced (`NoTextReadDetector`). Never call `node.text`, `node.contentDescription`, or `node.extras`. Only view IDs and class names may be read. |
| **No Internet** | The manifest must **never** declare `android.permission.INTERNET`. Zero network libraries or telemetry. |
| **Battery Discipline** | **Hard prohibition:** No `AlarmManager`, no `Handler.postDelayed`, no `UsageStatsManager`, no background polling loops, no extra worker threads for config. Reveal delay and rotation ride the single 1 Hz monotonic tick. |
| **RAM Footprint** | Baseline process RAM < 30 MB. No `LazyColumn` or animation engines inside the overlay `ComposeView`. Only the timer text recomposes every 1 second. |
| **Reveal Delay Immutability** | Snapshotted at `Idle → Active` into `Active.revealDelayMillisSnapshot`. Mid-session preference changes do not affect the active session delay. |
| **Passive HUD Service** | `HudForegroundService` only consumes `HudState`. It never talks to `SessionOrchestrator`, `AccessibilityService`, or `HudPreferencesRepository`. |
| **Transient Browser Domain** | `inferredDomain` is never stored in Room. It lives in memory during the active browser session and is discarded at session end. |
| **Database Migrations** | `fallbackToDestructiveMigration` is strictly disabled. Room schema exported to `schemas/awareness.db/1.json`. |

---

## 3. UI Design System: StudioBlank Reference

All UI implementation across the entire application—including `:core:design-system`, `:core:presentation`, `:feature:*`, and `:app`—must strictly adhere to the **StudioBlank Design System** documented in [DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md).

[DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md) is the **single canonical source of truth** for all visual and interactive specifications:
- **Palette & Colors:** Monochromatic foundation (`#0A0A0A`, `#FAFAFA`, `#D4D4D8`, `#E5E5E5`, `#F4F4F5`) with functional semantic accents (`#16A34A`, `#CA8A04`, `#DC2626`, `#71717A`).
- **Border Radius:** `radius-none` (0.dp) everywhere. Every button, card, input, chip, checkbox, and overlay must use `RectangleShape` (0.dp radius). Rounded corners are strictly prohibited.
- **Elevation & Depth:** `shadow-none` (0.dp elevation). Completely flat UI with zero drop shadows; contrast is achieved exclusively via borders and monochromatic fills.
- **Typography:** `Inter` (Headline and Body) and `IBM Plex Mono` (Numeric and Timer values), utilizing weight contrast (300 Light vs 700 Bold) for hierarchy.
- **Spacing:** 16dp base grid (`space-1` = 4dp through `space-10` = 128dp) with generous whitespace.
- **Components:** Specifications for Buttons (Primary, Secondary, Ghost, Destructive; Small, Medium, Large), Cards, Inputs, Filter/Status Chips, Checkboxes, Radio Buttons, Lists, and Tooltips.
- **Motion:** Duration ≤ 200ms or absent; zero animation in the HUD overlay to conserve battery and eliminate recomposition overhead.

Consult [DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md) directly before creating or styling any Compose UI element.

---

## 4. Frozen Cross-Slice Contracts

These contracts are **permanently frozen**. No slice may modify these signatures without updating all consumers and tests.

### 4.1 Domain Models (`:core:domain`)

```kotlin
// core/domain/src/main/kotlin/com/awareness/core/domain/model/AwarenessEvent.kt
sealed interface AwarenessEvent {
    data class SessionStarted(val packageName: String, val timestamp: Long) : AwarenessEvent
    data class SessionEnded(val packageName: String, val timestamp: Long)   : AwarenessEvent
    data class ReelScrolled(val timestamp: Long)                            : AwarenessEvent
    data class BrowserDomainInferred(
        val packageName: String,
        val domain: String,
        val timestamp: Long
    ) : AwarenessEvent
}

// core/domain/src/main/kotlin/com/awareness/core/domain/model/SessionState.kt
sealed interface SessionState {
    data object Idle : SessionState
    data class Active(
        val packageName: String,
        val startElapsed: Long,
        val wallClockStart: Long,
        val isBrowser: Boolean,
        val revealDelayMillisSnapshot: Long,
        val reelCount: Int,
        val inferredDomain: String? = null
    ) : SessionState
}

// core/domain/src/main/kotlin/com/awareness/core/domain/model/HudState.kt
data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean,
    val browserDomainLabel: String? = null
)

// core/domain/src/main/kotlin/com/awareness/core/domain/model/HudPreferences.kt
data class HudPreferences(
    val selectedMessages: List<MindfulnessMessage>,
    val messageDisplayDurationSeconds: Int,
    val revealDelayMillis: Long
)

// core/domain/src/main/kotlin/com/awareness/core/domain/model/MindfulnessMessage.kt
data class MindfulnessMessage(
    val id: String,
    val text: String
)

// core/domain/src/main/kotlin/com/awareness/core/domain/model/SessionSummary.kt
data class SessionSummary(
    val packageName: String,
    val wallClockStart: Long,
    val wallClockEnd: Long,
    val durationMillis: Long,
    val reelCount: Int,
    val isBrowser: Boolean
)

// core/domain/src/main/kotlin/com/awareness/core/domain/model/DailyAggregate.kt
data class DailyAggregate(
    val day: String, // "YYYY-MM-DD"
    val totalMillis: Long,
    val totalReels: Int,
    val sessionCount: Int
)
```

### 4.2 Repository & Use Case Interfaces

```kotlin
// core/domain/src/main/kotlin/com/awareness/core/domain/repository/SessionRepository.kt
interface SessionRepository {
    suspend fun save(summary: SessionSummary)
    fun observeDailyAggregates(since: Instant): Flow<List<DailyAggregate>>
}

// core/domain/src/main/kotlin/com/awareness/core/domain/repository/HudPreferencesRepository.kt
interface HudPreferencesRepository {
    val preferences: Flow<HudPreferences>
    suspend fun update(prefs: HudPreferences)
}

// core/domain/src/main/kotlin/com/awareness/core/domain/usecase/ObserveHudStateUseCase.kt
interface ObserveHudStateUseCase {
    operator fun invoke(): Flow<HudState>
}

// core/domain/src/main/kotlin/com/awareness/core/domain/usecase/GetDailyStatsUseCase.kt
interface GetDailyStatsUseCase {
    operator fun invoke(days: Int = 7): Flow<List<DailyAggregate>>
}
```

### 4.3 Target App Allowlist & Accessibility Config

```kotlin
// core/accessibility/src/main/kotlin/com/awareness/core/accessibility/TargetAppAllowlist.kt
object TargetAppAllowlist {
    val socialApps = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.lite",
        "com.snapchat.android",
        "com.google.android.youtube"
    )
    val browsers = setOf(
        "com.android.chrome",
        "org.mozilla.firefox"
    )
    val all = socialApps + browsers
}
```

---

## 5. Master Vertical Slice Implementation Roadmap

Execute each slice completely before beginning the next. Do not start VS-(N+1) until VS-N passes all acceptance criteria and test suites.

```
================================================================================
VS-01: Companion Shell, Permissions, HUD Preferences & Domain Foundation
================================================================================
```
- **Scope:** Gradle setup, `:core:domain`, `:core:design-system`, `:core:presentation`, `:core:data` (DataStore), `:feature:settings`.
- **Deliverables:**
  - Multi-module build setup with Kotlin 2.0+ and Version Catalog.
  - StudioBlank Theme, Colors, Typography, Shapes (0dp), and Core Components mapped from [DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md).
  - Settings Screen with 4 permission cards (Accessibility, Overlay, Battery, Notifications) styled per StudioBlank with sharp rectangular cards and zero elevation.
  - HUD Personalization Screen: reveal delay (0–3600s), display duration (5–300s), message selection list with StudioBlankCheckboxes.
  - `HudPreferencesRepositoryImpl` backed by DataStore Preferences. Clamping & defaults fallback.
- **Tests (19):** 11 unit (validation, clamping, preferences roundtrip), 5 integration (DataStore read/write, permission coordinator), 2 API, 1 manual E2E.

```
================================================================================
VS-02: Target App Foreground Detection
================================================================================
```
- **Scope:** `:core:accessibility` service skeleton, package filtering, `AccessibilityEventBus`.
- **Deliverables:**
  - `AwarenessAccessibilityService` declared with `typeWindowStateChanged` and XML `android:packageNames` filter.
  - `AccessibilityEventBus` backed by `MutableSharedFlow<AwarenessEvent>` (`extraBufferCapacity = 16`, `DROP_OLDEST`).
  - Detection of enter/exit for target apps and browsers. Emits `SessionStarted(pkg)` and `SessionEnded(pkg)`.
- **Invariants Enforced:** Zero polling; system XML package filtering only.
- **Tests (10):** 4 unit, 3 integration (event emission on window change), 2 API, 1 manual E2E.

```
================================================================================
VS-03: Session Lifecycle & Config Snapshot
================================================================================
```
- **Scope:** `SessionOrchestrator` in `:core:domain`, `ObserveHudStateUseCase`.
- **Deliverables:**
  - Pure Kotlin `SessionOrchestrator` maintaining `StateFlow<SessionState>`.
  - Snapshotting `revealDelayMillisSnapshot` from `HudPreferencesRepository` at the instant of `Idle → Active`.
  - `isBrowser` flag set based on package name allowlist.
  - `ObserveHudStateUseCase` combining `sessionState`, `preferences`, and 1 Hz `timerTicks`.
  - Invariant: `HudState.isVisible = Active && elapsedMillis >= revealDelayMillisSnapshot`.
- **Tests (10):** 6 unit (snapshot immutability, state transitions, combine logic), 2 integration, 1 API, 1 manual E2E.

```
================================================================================
VS-04: HUD Overlay Rendering & Visibility Shell
================================================================================
```
- **Scope:** `:core:overlay`, `:feature:hud` service setup.
- **Deliverables:**
  - `HudForegroundService` started as `specialUse` (subtype: `mindfulness_hud`).
  - Wrapped `startForeground()` in `try/catch (ForegroundServiceStartNotAllowedException)`.
  - `HudWindowManager` managing `WindowManager` overlay with `TYPE_APPLICATION_OVERLAY` and `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`.
  - Injects `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, `ViewTreeViewModelStoreOwner` before `addView`.
  - Empty `ComposeView` renders in < 100 ms; handles `isVisible = false` by rendering no visible child nodes.
- **Tests (11):** 5 unit, 3 integration, 2 API, 1 manual E2E.

```
================================================================================
VS-05: HUD Content, Reveal Delay & Message Rotation
================================================================================
```
- **Scope:** `HudComposable`, `HudViewModel`, presentation formatters.
- **Deliverables:**
  - `HudComposable` styled with StudioBlank: SurfaceInverse `#0A0A0A` background (alpha 0.85f), `1.dp #D4D4D8` border, strictly `RectangleShape` (0dp radius), flat.
  - 3-line layout: (1) Mindfulness message, (2) `MM:SS` monotonic timer (`IBM Plex Mono Bold`), (3) `Reels: N`.
  - Rotating messages every `messageDisplayDurationSeconds` using `MessageRotation.indexFor`.
  - Recomposition scoping: only the timer text recomposes every 1 second.
- **Tests (17):** 10 unit (rotation index, time formatting, recomposition count), 4 integration, 2 API, 1 manual E2E.

```
================================================================================
VS-06: Short-Form Reel / Short Counting
================================================================================
```
- **Scope:** `ScrollContainerMatcher` in `:core:accessibility`, custom lint rule.
- **Deliverables:**
  - `ScrollContainerMatcher` identifying Instagram (`clips_viewer`), YouTube (`reel_watch_fragment`), FB (`reel_fragment`), Snapchat (`snap_reel_container`).
  - Depth-bounded traversal: `MAX_TRAVERSAL_DEPTH = 10`.
  - Scroll debounce: 80 ms per container.
  - Emits `ReelScrolled` event. `SessionOrchestrator` increments `Active.reelCount`.
  - **Critical:** Counting occurs even while HUD is hidden before reveal delay!
  - Custom lint check: `NoTextReadDetector` enforcing zero text/contentDescription reads.
- **Tests (17):** 13 unit (tree depth boundaries, debounce timing, lint detector), 2 integration, 1 API, 1 manual E2E.

```
================================================================================
VS-07: Browser Support (Delay-Aware, Message-Rotation-Preserving)
================================================================================
```
- **Scope:** `BrowserUrlInferencer` in `:core:accessibility`, browser HUD layout.
- **Deliverables:**
  - Browser sessions set `Active.isBrowser = true`.
  - Reveal delay and message rotation honored identically to native apps.
  - Reel line hidden: `showReelCount = false` (never rendered for browsers; browser scrolls not counted).
  - Best-effort domain inference inspecting Chrome (`url_bar`/`origin`) and Firefox (`mozac_browser_toolbar_url_view`) view IDs only. Never reads text.
  - On match, emits `BrowserDomainInferred`. Renders small domain label (e.g. `"You're on instagram.com"`) above rotating message.
  - `inferredDomain` is transient and discarded on session end.
- **Tests (14):** 8 unit, 3 integration, 2 API, 1 manual E2E.

```
================================================================================
VS-08: Local Persistence & Stats Dashboard
================================================================================
```
- **Scope:** Room DB in `:core:data`, `:feature:stats` screen.
- **Deliverables:**
  - Room DB (`awareness.db`, version 1) with `sessions` table (including `is_browser`). `exportSchema = true`.
  - `SessionRepositoryImpl` saving `SessionSummary` on session end.
  - Aggregation query calculating daily total time, reel count, and session count.
  - Stats Screen styled with StudioBlank:
    - Today summary card (`#FFFFFF` bg, `1.dp #E5E5E5` border, `0dp` radius).
    - Last 7 days vertical bar breakdown (bars proportional to max day, pure `#0A0A0A` rectangles, `0dp` radius).
    - Empty state: `"No awareness data yet — open a monitored app to begin."`
- **Tests (10):** 5 unit, 3 integration (Room in-memory roundtrip), 1 API, 1 manual E2E.

```
================================================================================
VS-09: Battery Optimization, OEM Survivability & System Hardening
================================================================================
```
- **Scope:** OEM guidance screen, battery intent coordinators, static hardening invariants.
- **Deliverables:**
  - `OemGuidanceScreen` providing specific steps for Xiaomi, Huawei, OnePlus, Samsung, Pixel.
  - `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow.
  - Implementation of `onTimeout(startId, fgsType)` in `HudForegroundService`.
  - Notification prompt if accessibility service is killed by OS.
  - Static grep audit verifying zero forbidden calls across the entire codebase.
- **Tests (12):** 4 unit, 4 integration, 1 API, 3 manual E2E.

---

## 6. Verification, Testing & Static Invariants

### 6.1 Master Test Matrix (Total: 120 Tests)

| Slice | Unit Tests | Integration Tests | API Tests | Manual E2E | Total |
|---|---:|---:|---:|---:|---:|
| **VS-01** | 11 | 5 | 2 | 1 | **19** |
| **VS-02** | 4 | 3 | 2 | 1 | **10** |
| **VS-03** | 6 | 2 | 1 | 1 | **10** |
| **VS-04** | 5 | 3 | 2 | 1 | **11** |
| **VS-05** | 10 | 4 | 2 | 1 | **17** |
| **VS-06** | 13 | 2 | 1 | 1 | **17** |
| **VS-07** | 8 | 3 | 2 | 1 | **14** |
| **VS-08** | 5 | 3 | 1 | 1 | **10** |
| **VS-09** | 4 | 4 | 1 | 3 | **12** |
| **Total** | **66** | **29** | **14** | **11** | **120** |

### 6.2 Forbidden API & Static Analysis Verification

Before closing any slice, the following bash checks must be run. Any non-zero output constitutes a **build-blocking failure**:

```bash
# 1. Verify zero calls to text/contentDescription on AccessibilityNodeInfo
grep -rnE "\.(text|contentDescription|extras)\b" \
  core/accessibility/ feature/hud/ \
  && echo "FAILED: Forbidden accessibility text access detected" || echo "PASSED: No text access"

# 2. Verify no background polling or forbidden schedulers
grep -rnE "(AlarmManager|postDelayed|UsageStatsManager|setRepeating|Handler\(Looper)" \
  core/ feature/ app/ \
  && echo "FAILED: Forbidden scheduler/polling detected" || echo "PASSED: No forbidden schedulers"

# 3. Verify no INTERNET permission declared in AndroidManifest
grep -rn "android.permission.INTERNET" app/ core/ feature/ \
  && echo "FAILED: INTERNET permission detected" || echo "PASSED: No INTERNET permission"

# 4. Verify StudioBlank Design: ZERO Rounded Corners
grep -rnE "RoundedCornerShape\(([^0]|0\.[1-9])" \
  core/design-system/ feature/ app/ \
  && echo "FAILED: Rounded corners detected! StudioBlank requires 0.dp (RectangleShape)" || echo "PASSED: Strict 0px radius"

# 5. Verify StudioBlank Design: ZERO Shadows / Elevation
grep -rnE "elevation = [1-9]" \
  core/design-system/ feature/ app/ \
  && echo "FAILED: Elevation detected! StudioBlank requires flat 0.dp" || echo "PASSED: Flat 0dp elevation"
```

---

## 7. Definition of Done (DoD) per Slice

A slice is considered **Done** and ready for sign-off only when:
1. All allocated code files are implemented and follow the unidirectional architecture.
2. All UI components strictly adhere to [DESIGN.md](file:///home/shunya/Projects/awareness/DESIGN.md) (monochrome, 0dp radius, flat, Inter/IBM Plex Mono).
3. All unit, integration, and API tests for that slice pass with 100% green status.
4. Static grep verification checks pass with zero violations.
5. No cross-slice frozen contracts (§4) have been modified or breached.
6. The **Slice Execution & File Modification Ledger (§8)** is fully updated with all created/modified files, folders, and explicit architectural rationale.
7. The next slice is unblocked and clean to begin.

---

## 8. Slice Execution & File Modification Ledger

> **Mandatory Rule:** Upon completing or modifying any slice, the agent/developer MUST log all created and modified folders and files below with the architectural reason for each change.

### VS-01: Companion Shell, Permissions, HUD Preferences & Domain Foundation
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-02: Target App Foreground Detection
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-03: Session Lifecycle & Config Snapshot
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-04: HUD Overlay Rendering & Visibility Shell
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-05: HUD Content, Reveal Delay & Message Rotation
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-06: Short-Form Reel / Short Counting
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-07: Browser Support (Delay-Aware, Message-Rotation-Preserving)
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-08: Local Persistence & Stats Dashboard
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*

---

### VS-09: Battery Optimization, OEM Survivability & System Hardening
- **Status:** *Pending Execution*
- **Folders Created / Modified:**
  - *(Log entries populated upon slice execution)*
- **Files Created / Modified:**
  - *(Log entries populated upon slice execution, specifying path and architectural rationale)*
