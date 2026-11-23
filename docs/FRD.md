# Functional Requirements Document (FRD) — Final

**Project Name:** Awareness App
**Document Version:** 2.0 (Final — aligned to ARD v2.0 and frozen VS-01 … VS-09 contracts)
**Supersedes:** FRD v1.0
**Role:** Veteran Android App System Designer
**Minimum Android Version:** API 26 (Android 8.0) · **Target SDK:** API 34+ (Android 14+)
**Language & Stack:** Kotlin 2.0+, Coroutines, Flow, Jetpack Compose, Room, DataStore, Hilt

---

## 0. What Changed From FRD v1.0

The final vertical slices (VS-01 … VS-09) and ARD v2.0 introduced behaviors not present in FRD v1.0. This revision makes them first-class requirements:

| Area | FRD v1.0 | FRD v2.0 (this document) |
|---|---|---|
| Session state | Implicit | Explicit `Idle` / `Active` sealed state; `Active` carries `revealDelayMillisSnapshot`, `reelCount`, `isBrowser`, `inferredDomain` |
| Reveal delay | Absent | Snapshotted at `Idle → Active`; immutable for the session; HUD visibility derives from it |
| Message selection & duration | Absent | User-configurable via `HudPreferencesRepository`; applies live from next rotation boundary |
| HUD content | Message + timer + count | 6-field `HudState`; hidden state renders nothing visible; browser omits Reel line; optional domain label |
| Reel counting | Accessibility service concern | `SessionOrchestrator` concern; independent of HUD visibility; depth-bounded (10); 80 ms debounce |
| Browser support | Generic HUD | Reveal delay honored, rotation preserved, Reel line omitted, best-effort domain inference via view IDs only |
| Persistence | “Room database” | Explicit `sessions` table with `is_browser` column; DataStore keys for HUD preferences; `inferredDomain` transient |
| Battery | No polling | Hard prohibition: no `AlarmManager`, `Handler.postDelayed`, `UsageStatsManager`, extra `CoroutineWorker` for config; reveal delay rides the existing 1 Hz tick |
| Privacy | No content scraping | Lint-enforced `NoTextReadDetector`; only view IDs / class names read; `inferredDomain` never persisted |
| Test coverage | Unspecified | 120 tests (66 unit / 29 integration / 14 API / 11 E2E) |

---

## 1. Introduction & Purpose

The Awareness App is a lightweight, battery-conscious Android utility that interrupts mindless social media consumption. When a user launches a supported social app or accesses it via a mobile browser, the app overlays a non-intrusive Heads-Up Display (HUD) after a user-configured **reveal delay**. The HUD shows a rotating mindfulness message (user-selected), the total session time, and — for social apps only — a count of Reels/Shorts viewed.

The core engineering challenge is achieving real-time foreground detection and scroll counting while maintaining ultra-low RAM (< 30 MB) and battery consumption (no polling, single 1 Hz tick), without ever reading content.

---

## 2. Scope

### In Scope

- Real-time detection of foreground package names for Facebook, Facebook Lite, Instagram, Instagram Lite, Snapchat, YouTube, and supported browsers (Chrome, Firefox).
- Event-driven detection of vertical scrolls inside confirmed short-form containers (Reels/Shorts) to count engagement.
- Rendering a floating, touch-pass-through HUD after a configurable reveal delay.
- Configurable HUD preferences: reveal delay, message selection, per-message display duration.
- Browser support: reveal delay + message rotation honored, Reel line omitted, best-effort domain inference (view IDs only).
- Local-only Room persistence of session aggregates; local-only DataStore persistence of HUD preferences.
- Foreground Service (`specialUse`) + AccessibilityService + battery-optimization guidance.

### Out of Scope

- Blocking, force-closing, or restricting target apps.
- Reading caption, message, username, or any text content (lint-enforced).
- iOS / cross-platform.
- Network, analytics, cloud sync, or `INTERNET` permission.
- Persisting inferred browser domain (session-transient by design).

---

## 3. Definitions

- **Session:** A contiguous interval in which a Target App is in the foreground. Modeled as `SessionState.Active`; ends on `SessionEnded`.
- **Reveal delay:** User-configured wait time after session start before the HUD becomes visible. **Snapshotted** into `Active.revealDelayMillisSnapshot` at `Idle → Active` and immutable for that session.
- **HUD visibility:** `HudState.isVisible = sessionState is Active && elapsedMillis >= Active.revealDelayMillisSnapshot`. Computed by `ObserveHudStateUseCase`, not by the HUD service.
- **Message rotation:** Selection of the current message from `HudPreferencesRepository.selectedMessages` via `MessageRotation.indexFor`, using elapsed time and message duration. Continues across the reveal boundary and during browser sessions.
- **Reel engagement:** A qualifying `TYPE_VIEW_SCROLLED` inside a confirmed short-form container, within `MAX_TRAVERSAL_DEPTH = 10`, debounced 80 ms.
- **Browser session:** `Active.isBrowser == true`. Contributes time and session count to stats; contributes **zero** to Reel totals; Reel line omitted from HUD; optional domain label rendered above the rotating message.
- **Inferred domain:** Best-effort host (e.g., `instagram.com`) read from a browser URL bar via view IDs. Session-transient; never persisted.

---

## 4. Functional Requirements

### FR-01 — Target App Foreground Detection

The `AccessibilityService` must detect entry/exit of Target Apps via `TYPE_WINDOW_STATE_CHANGED` and match `packageName` against the allowlist (`com.facebook.katana`, `com.facebook.lite`, `com.instagram.android`, `com.instagram.lite`, `com.snapchat.android`, `com.youtube`, `com.android.chrome`, `org.mozilla.firefox`). Detection must be event-driven; **no `UsageStatsManager` polling** is permitted. On match, emit `SessionStarted(packageName, timestamp)` on `AccessibilityEventBus`. If the matched package is a browser, the resulting session must be created with `Active.isBrowser = true`.

### FR-02 — Session Management

The `SessionOrchestrator` must own a `StateFlow<SessionState>` with `Idle` / `Active` variants.

- On `SessionStarted(pkg)`: transition `Idle → Active` with
  - `packageName = pkg`
  - `startElapsed` and `wallClockStart` = now (monotonic + wall clock)
  - `isBrowser = pkg ∈ browser allowlist`
  - `revealDelayMillisSnapshot = hudPreferences.revealDelayMillis` **(snapshot at this instant)**
  - `reelCount = 0`, `inferredDomain = null`
- On `SessionEnded`: transition `Active → Idle` and persist a `SessionSummary` (`packageName`, `wallClockStart`, `end`, `duration`, `reelCount`, `isBrowser`) via `SessionRepository.save` on `Dispatchers.IO`.
- Changes to `revealDelayMillis` after session start must **not** affect the current session; the next session snapshots the new value.
- Changes to message selection and `messageDisplayDurationSeconds` may apply live, effective from the next rotation boundary.

### FR-03 — Reel / Short Counting

- `ScrollContainerMatcher` must identify known short-form containers by view ID and class-name heuristics:
  - Instagram: `clips_viewer`, `reel_viewer`
  - YouTube: `reel_watch_fragment`
  - Facebook: `reel_fragment`, `clips_viewer`
  - Snapchat: `snap_reel_container`
- Traversal must be **depth-bounded to `MAX_TRAVERSAL_DEPTH = 10`** and triggered **only** on `TYPE_VIEW_SCROLLED`.
- Counting must increment **only** on a qualifying scroll inside a confirmed container, with an **80 ms debounce per container** (fast flings count once).
- The matcher must **never** call `node.text`, `node.contentDescription`, or `node.extras` — enforced by the custom lint rule `NoTextReadDetector` plus a code-review checklist.
- `SessionOrchestrator` must increment `Active.reelCount` on each `ReelScrolled` while `Active`. **This is independent of `HudState.isVisible`**: scrolls before the reveal delay still increment the counter (VS-06 AC 8, E6).
- Browser sessions must **not** increment `reelCount` (VS-06 AC 11, VS-07 AC 12).
- If `canRetrieveWindowContent == false`, traversal must not be attempted; matcher returns `false`; no count (graceful degradation).
- HUD count must reflect an increment within **100 ms** of the qualifying scroll (NFR-03).

### FR-04 — HUD Overlay Rendering & Visibility Shell

- `HudForegroundService` must start with `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`.
- Overlay must be added via `WindowManager.addView` with `TYPE_APPLICATION_OVERLAY` and layout params containing `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`.
- An empty `ComposeView` must render within **100 ms** of service start.
- Touch pass-through must be verified: taps on the HUD area reach the underlying app.
- When `HudState.isVisible == false`, the overlay must render **no visible HUD content** (no text, no card, no timer, no count). An empty `ComposeView` may remain attached.
- When `HudState.isVisible` transitions `false → true`, visible content must appear within **200 ms** of the `StateFlow` emission. The same 200 ms budget applies to `true → false`.
- `HudWindowManager` must inject `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, and `ViewTreeViewModelStoreOwner` before `addView` to prevent `ComposeView` crashes.
- `HudWindowManager.add()` must return `HudAddResult.Failure(NO_OVERLAY_PERMISSION)` when `Settings.canDrawOverlays()` is false; the service must stop the overlay and keep the notification.
- The service must be a **passive renderer**: it observes `HudState` through `HudViewModel`; it must never query the accessibility tree and must never read `HudPreferences` directly.

### FR-05 — HUD Content, Reveal Delay & Message Rotation

**FR-05.1 — Reveal Delay**

- HUD must remain hidden while `elapsedMillis < Active.revealDelayMillisSnapshot`.
- If the snapshot is `0`, the HUD is visible from the first active frame.
- At the snapshot boundary, the HUD reveals with:
  - the current selected message,
  - the timer showing **total** session elapsed (e.g., `03:00`),
  - the **total** Reel count for the session, including scrolls that occurred before reveal.

**FR-05.2 — Message Rotation**

- After reveal, the HUD shows exactly three lines: (1) message, (2) `MM:SS` timer, (3) `Reels: N` (social only).
- The timer updates once per second.
- Message rotates every `messageDisplayDurationSeconds` from `HudPreferencesRepository.selectedMessages`. If only one message is selected, it stays static.
- Selection and duration changes apply from the next rotation boundary; the current message stays until its duration ends.
- Empty selection (corrupted data) must fall back to `DefaultMindfulnessMessages.fallback`; never crash.
- Messages beyond 59:59 elapsed must format as `H:MM:SS`.

**FR-05.3 — Message Selection**

- The user must be able to select any subset of default messages to rotate through.
- Selection must persist in DataStore and be read via a single `Flow<HudPreferences>` collected by `ObserveHudStateUseCase` (no additional collectors).

**FR-05.4 — State Production**

- `HudState` must be produced by combining `SessionOrchestrator.sessionState`, `HudPreferencesRepository.preferences`, and a single monotonic 1 Hz tick. The combine logic is frozen (see §10).
- The HUD service must not compute visibility or rotation; it renders the latest `HudState`.

### FR-06 — Foreground Service Lifecycle

- The overlay host must be a Foreground Service declared with `android:foregroundServiceType="specialUse"` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "mindfulness_hud"`.
- Every `startForeground()` call site must be wrapped in `try/catch (ForegroundServiceStartNotAllowedException)`; on catch, `stopSelf()` and post a user notification explaining background-start restriction.
- `onTimeout(startId, fgsType)` must be implemented to stop gracefully and clear the notification.
- The service must observe `HudState` through `HudViewModel` and render only.
- Action constants: `ACTION_START = "com.awareness.app.hud.START"`, `ACTION_STOP = "com.awareness.app.hud.STOP"`.

### FR-07 — Browser Support (Delay-Aware, Rotation-Preserving)

- When the foreground package is in the browser allowlist, `Active.isBrowser = true`.
- Browser sessions must obey the **same reveal delay** as social sessions: visibility uses `elapsedMillis >= revealDelayMillisSnapshot`.
- The browser HUD must **still rotate** the user-selected messages every `messageDisplayDurationSeconds`.
- The Reel line must be **omitted** (`showReelCount = false`), not shown as zero, even if `reelCount > 0` were somehow emitted.
- Best-effort domain inference: `BrowserUrlInferencer.inferDomain(root)` must inspect **only view IDs and class names** (Chrome `url_bar` / `origin`, Firefox `mozac_browser_toolbar_url_view`). It must never read `node.text`, `node.contentDescription`, or `node.extras`, and must never throw — returns `null` on any failure.
- On successful inference, `BrowserDomainInferred(packageName, domain, timestamp)` must be emitted on `AccessibilityEventBus`.
- `SessionOrchestrator` must consume `BrowserDomainInferred` **only while `Active.isBrowser == true` and `Active.packageName == event.packageName`**, setting `inferredDomain = domain` (last-wins within session). Navigating to a non-social site emits an event that clears `inferredDomain` to `null` (no label).
- When `Active.inferredDomain != null`, `HudState.browserDomainLabel` must be populated via `BrowserDomainFormatter.label(domain)` (e.g., `"You're on instagram.com"`) and rendered as a small label **above** the rotating message. Rotation continues unchanged.
- On session end, `inferredDomain` is discarded with the session. It must **never** be persisted (VS-07 AC 13).
- Browser scrolls must not increment `reelCount`.

### FR-08 — Battery Optimization & Survivability

- Companion UI must request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- OEM guidance screen must cover Xiaomi (MIUI), Huawei (EMUI), OnePlus (OxygenOS), Samsung (One UI), Pixel (stock).
- `BatteryOptimizationCard` must refresh from `PowerManager.isIgnoringBatteryOptimizations(packageName)` on each `onResume` — **no polling**.
- `onTimeout` must be implemented; all `startForeground()` calls wrapped in `try/catch`.
- `PermissionRequestCoordinator` must surface a persistent notification when the accessibility service is disabled.
- **Hard prohibition**: no `AlarmManager`, no `Handler.postDelayed`, no `UsageStatsManager`, no additional `CoroutineWorker` for HUD config, no additional Foreground Service, no additional `Flow` collection of `HudPreferences` beyond the single one in `ObserveHudStateUseCase`.
- Reveal delay must ride the existing 1 Hz monotonic tick (from `ObserveHudStateUseCase`) — **no separate scheduler**.
- Survive 8 hours screen-off with battery optimization disabled.
- If the HUD service or accessibility service is killed and restarted, DataStore-backed preferences must survive; the next session re-snapshots the reveal delay.

### FR-09 — HUD Preferences Persistence

- Preferences must persist in DataStore Preferences with keys:
  - `selected_message_ids: Set<String>`
  - `message_display_duration_seconds: Int`
  - `reveal_delay_millis: Int`
- Writes must occur **only on user Save** — no write amplification.
- On read failure at cold start, defaults must be used (reveal delay `0` for the first session); the repository must never crash the app.

### FR-10 — Local Persistence & Stats Dashboard

- Every `SessionSummary` (including `isBrowser` and `reelCount`) must be persisted to Room on session end.
- Room schema (`awareness.db`, version 1) must include:

```sql
CREATE TABLE sessions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name     TEXT    NOT NULL,
    start_wall_clock INTEGER NOT NULL,   -- epoch millis UTC
    end_wall_clock   INTEGER NOT NULL,
    duration_millis  INTEGER NOT NULL,
    reel_count       INTEGER NOT NULL DEFAULT 0,
    is_browser       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_sessions_start   ON sessions(start_wall_clock);
CREATE INDEX idx_sessions_browser ON sessions(is_browser);
```

- `exportSchema = true`; the schema JSON must be committed to VCS.
- `fallbackToDestructiveMigration` must be **disabled**.
- Stats screen must show **Today**: total time, total reels, session count. Browser sessions contribute time and session count only; `reel_count` is guaranteed `0` for browser rows by FR-03, so `SUM(reel_count)` requires no special handling.
- Stats screen must show **Last 7 days** list (date, time, reels), with bars proportional to the max day in the window.
- Empty state: `"No awareness data yet — open a monitored app to begin."`
- Stats screen must render in < 300 ms for 10 000 rows.
- No network calls; no `INTERNET` permission.

---

## 5. Non-Functional Requirements

### NFR-01 — Battery

- `AccessibilityService` must declare `android:packageNames` in its XML config to filter events at the system level.
- No polling loops of any kind (`UsageStatsManager`, `AlarmManager`, `Handler.postDelayed`). Detection and counting are purely event-driven.
- Node traversal is depth-bounded (`MAX_TRAVERSAL_DEPTH = 10`) and triggered only on `TYPE_VIEW_SCROLLED`.
- Reveal delay and message rotation introduce **zero** additional wakeups: they ride the single 1 Hz tick already required for the HUD timer.
- Verified via `dumpsys batterystats` and grep invariants (VS-09 TS-07, TS-09).

### NFR-02 — RAM & Memory Footprint

- Baseline process RAM (FGS + AccessibilityService + ComposeView) must remain **< 30 MB** during active sessions, measured via `adb shell dumpsys meminfo`.
- HUD `ComposeView` hosts a minimal `Text` column: no images, no `LazyColumn`, no animation APIs, no elevation shadow.
- Recomposition must be scoped: only the timer `Text` recomposes per second; message and count are stable until they change.

### NFR-03 — Latency

- Foreground detection → HUD visible content must occur within **200 ms** of the target app entering the foreground (once the reveal delay has already elapsed).
- Reel counter updates must appear on the HUD within **100 ms** of a qualifying scroll.
- `HudState.isVisible` transitions (`false → true`, `true → false`) must render in **≤ 200 ms**.

### NFR-04 — Privacy

- Zero content scraping. `AccessibilityService`, `ScrollContainerMatcher`, and `BrowserUrlInferencer` must read only view IDs and class names. Reading `node.text`, `node.contentDescription`, or `node.extras` is a build failure via lint rule `NoTextReadDetector`.
- No `INTERNET` permission in the manifest.
- Local-only persistence: Room + DataStore. No sync adapter, no WorkManager network job, no export provider.
- `inferredDomain` is session-transient and never persisted.
- Uninstall removes all data.

---

## 6. System Architecture Overview

The system comprises the following cooperating components (full detail in ARD v2.0):

1. **`AwarenessAccessibilityService`** — Event-driven sensor. Filters `TYPE_WINDOW_STATE_CHANGED` and `TYPE_VIEW_SCROLLED` for the allowlist; walks the tree (view IDs only) for scroll containers and browser URL bars; emits `AwarenessEvent` (`SessionStarted`, `SessionEnded`, `ReelScrolled`, `BrowserDomainInferred`) on `AccessibilityEventBus` (in-process `SharedFlow`).
2. **`SessionOrchestrator`** (`:core:domain`) — Pure-Kotlin state machine. Owns `StateFlow<SessionState>`; snapshots reveal delay at `Idle → Active`; increments `reelCount` on `ReelScrolled` (independent of HUD visibility); sets/clears `inferredDomain` on `BrowserDomainInferred`; persists `SessionSummary` on session end.
3. **`ObserveHudStateUseCase`** — Combines `sessionState`, `hudPreferences`, and the single 1 Hz monotonic tick into `Flow<HudState>` using the frozen combine logic (§10).
4. **`HudForegroundService`** — Passive renderer. Hosts a minimal `ComposeView` via `HudWindowManager`. `HudViewModel` observes `HudState`. Never queries the accessibility tree; never reads `HudPreferences` directly.
5. **`feature:stats`** — Reads Room via `GetDailyStatsUseCase`; renders Today and Last 7 Days.
6. **`feature:settings`** — Permission onboarding, OEM guidance, HUD preferences UI (reveal delay, message selection, message duration).

---

## 7. Permissions Matrix

| Permission | Purpose | Request Mechanism |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Foreground detection, scroll counting, browser domain inference | Declared in manifest; user enables in Settings > Accessibility |
| `SYSTEM_ALERT_WINDOW` | Display HUD over target apps | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` |
| `FOREGROUND_SERVICE` | Host the HUD service | Declared in manifest |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Allow `specialUse` type | Manifest + `<property>` |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Survive Doze / OEM killers | `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| `POST_NOTIFICATIONS` | Ongoing FGS notification | Runtime (Android 13+) |
| ~~`INTERNET`~~ | — | **Intentionally absent** |

---

## 8. Edge Cases & Mitigations

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Overlay permission revoked while service running | `HudWindowManager.add()` returns `Failure(NO_OVERLAY_PERMISSION)`; service stops overlay, keeps notification. |
| E2 | `ComposeView` crash on missing owners | `HudWindowManager` injects `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, `ViewTreeViewModelStoreOwner` before `addView`. |
| E3 | `startForeground` throws `ForegroundServiceStartNotAllowedException` | Caught; `stopSelf()`; user notification. |
| E4 | `onTimeout` fires | Graceful stop; notification cleared. |
| E5 | HUD hidden for entire session | Overlay invisible; no touch interception; session + reel counting continue. |
| E6 | Rapid `isVisible` toggles at reveal boundary | Renderer applies latest `HudState`; recomposition scoped. |
| E7 | Session ends while HUD visible | `isVisible = false`; content disappears ≤ 200 ms. |
| E8 | Session > 99 min | Format as `H:MM:SS`. |
| E9 | Message list exhausted | Cycle via `index % messages.size`. |
| E10 | Selected list empty (corrupted) | Fallback to `DefaultMindfulnessMessages.fallback`. |
| E11 | Reveal delay > session duration | HUD never becomes visible; session still persisted. |
| E12 | Reveal delay changed mid-session | No effect on current session (snapshot frozen); next session uses new value. |
| E13 | Message selection / duration changed mid-session | Applied from next rotation boundary. |
| E14 | Pre-reveal Reel scrolls | Count still increments; displayed at reveal as total. |
| E15 | Browser session | `showReelCount = false`; Reel line omitted; rotation runs; optional domain label above message. |
| E16 | Browser URL bar not exposed | `inferDomain` returns `null`; no label; rotation continues. |
| E17 | Domain changes mid-session (instagram → news) | Label updates; clears to `null` on unknown domain. |
| E18 | Non-social site in browser | HUD still shown; no label; no Reel line. |
| E19 | `canRetrieveWindowContent == false` | Matcher returns `false`; no count; inference returns `null`. |
| E20 | Depth-bounded traversal hits limit | Matcher returns `false`; no false positives. |
| E21 | Fast fling (< 80 ms between scrolls) | Debounce per container; counts once per fling. |
| E22 | Browser scroll | Not counted; `reelCount` unchanged. |
| E23 | Accessibility service disabled | Persistent notification + in-app banner. |
| E24 | Doze during reveal countdown | Session ends on screen state change (per FR-02); countdown discarded; next session takes fresh snapshot. |
| E25 | DataStore latency at cold start | Reveal delay defaults to `0` for the first session if repository is not warm. |
| E26 | Room corruption | `fallbackToDestructiveMigration` disabled; user notified to reset stats. |
| E27 | Day boundary during active session | Session bucketed by `startWallClock` date (no split). |
| E28 | OEM force-stop after swipe from Recents | No self-resurrection; guidance UI only. |
| E29 | Device rotation | HUD position is gravity-based; no config dependency. |
| E30 | OEM killer / Battery Saver freezes events | HUD shows “Service may be paused by system” when `PowerManager.isPowerSaveMode` and no accessibility events > 30 s. |

---

## 9. Acceptance Criteria

1. HUD appears over Instagram within 200 ms of opening the app (when reveal delay is 0).
2. With reveal delay = 3 min, no visible HUD appears for the first 3 min after opening Instagram; at 3 min the HUD reveals with the current message, `03:00` timer, and the **total** Reel count including pre-reveal scrolls.
3. Swiping through 5 Reels increments the counter by exactly 5.
4. The HUD does not intercept touches; user can tap, scroll, and interact with the target app normally.
5. App survives 8 hours of idle screen-off (battery optimization disabled).
6. RAM measured via `adb shell dumpsys meminfo` remains < 30 MB during active sessions.
7. Changing message selection or display duration mid-session applies from the next rotation boundary; changing reveal delay mid-session does not affect the current session.
8. Browser session honors reveal delay, rotates messages, omits the Reel line; inferred domain label (if any) appears above the message and updates on navigation.
9. Stats screen shows Today and Last 7 Days; browser sessions contribute time and session count, not Reels.
10. Source scan for `AlarmManager`, `Handler.postDelayed`, `UsageStatsManager`, and `node.text` returns 0 results.
11. `aapt dump permissions` shows no `INTERNET` permission.
12. All 120 tests in the VS-01 … VS-09 matrix pass.

---

## 10. Frozen Contracts (Normative Reference)

The following shapes are frozen across slices. Any change requires coordinated updates to all consumers (see ARD v2.0 §4 for full source).

```kotlin
// AwarenessEvent
sealed interface AwarenessEvent {
    data class SessionStarted(val packageName: String, val timestamp: Long) : AwarenessEvent
    data class SessionEnded(val packageName: String, val timestamp: Long)   : AwarenessEvent
    data class ReelScrolled(val timestamp: Long)                            : AwarenessEvent
    data class BrowserDomainInferred(
        val packageName: String, val domain: String, val timestamp: Long
    ) : AwarenessEvent
}

// SessionState
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

// HudState (6 fields)
data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean,
    val browserDomainLabel: String? = null
)

// HudPreferences
data class HudPreferences(
    val selectedMessages: List<MindfulnessMessage>,
    val messageDisplayDurationSeconds: Int,
    val revealDelayMillis: Long
)
```

**Combine logic (frozen):**

```
combine(sessionState, hudPreferences, timerTicks) { session, prefs, elapsed ->
    when (session) {
        Idle -> HudState(isVisible=false, message="", elapsedMillis=0,
                         reelCount=0, showReelCount=false, browserDomainLabel=null)
        is Active -> {
            val visible = elapsed >= session.revealDelayMillisSnapshot
            val messages = prefs.selectedMessages.ifEmpty { DefaultMindfulnessMessages.fallback }
            val index = if (visible) MessageRotation.indexFor(
                elapsedMillis = elapsed,
                revealDelayMillis = session.revealDelayMillisSnapshot,
                messageDisplayDurationSeconds = prefs.messageDisplayDurationSeconds,
                messageCount = messages.size
            ) else 0
            HudState(
                isVisible = visible,
                message = if (visible) messages[index].text else "",
                elapsedMillis = elapsed,
                reelCount = session.reelCount,
                showReelCount = !session.isBrowser,
                browserDomainLabel =
                    if (session.isBrowser) session.inferredDomain?.let(BrowserDomainFormatter::label)
                    else null
            )
        }
    }
}
```

---

## 11. Test Matrix Summary

| Slice | Unit | Integration | API | E2E | Total |
|---|---:|---:|---:|---:|---:|
| VS-01 | 11 | 5 | 2 | 1 | 19 |
| VS-02 | 4 | 3 | 2 | 1 | 10 |
| VS-03 | 6 | 2 | 1 | 1 | 10 |
| VS-04 | 5 | 3 | 2 | 1 | 11 |
| VS-05 | 10 | 4 | 2 | 1 | 17 |
| VS-06 | 13 | 2 | 1 | 1 | 17 |
| VS-07 | 8 | 3 | 2 | 1 | 14 |
| VS-08 | 5 | 3 | 1 | 1 | 10 |
| VS-09 | 4 | 4 | 1 | 3 | 12 |
| **Total** | **66** | **29** | **14** | **11** | **120** |

Key invariants verified by tests:

- HUD hidden state renders no visible content (VS04-TS-06).
- Reveal transition latency ≤ 200 ms (VS04-TS-07, VS04-TS-08).
- Reveal delay boundary at 179 s / 180 s with 180 s snapshot (VS05-TS-07, TS-08).
- Pre-reveal Reel count surfaced at reveal (VS05-TS-13, VS06-TS-07).
- Counting continues while HUD hidden (VS06-TS-09).
- No text reads (VS06-TS-04, VS07-TS-05).
- Browser hides Reel line and honors reveal delay (VS07-TS-01, TS-04, TS-06).
- Domain label set/cleared on navigation (VS07-TS-02, TS-08, TS-09).
- Browser scrolls excluded from count (VS07-TS-10).
- No polling / no `AlarmManager` / no `postDelayed` / no `UsageStatsManager` (VS09-TS-07, TS-09).
- Reveal delay survives service restart (VS09-TS-10).
- No `INTERNET` permission (VS08-TS-06).

---

## 12. Traceability Matrix

| FR | Primary VS | Supporting VS | Primary ARD Section |
|---|---|---|---|
| FR-01 | VS-02 | VS-01, VS-03 | §2, §6, §8.1 |
| FR-02 | VS-03 | VS-05, VS-06, VS-07 | §5, §6, §7 |
| FR-03 | VS-06 | VS-05, VS-09 | §4.4, §6, §8 |
| FR-04 | VS-04 | VS-09 | §4.5, §5, §8.2 |
| FR-05 | VS-05 | VS-01, VS-07 | §4.1, §4.6, §5.2 |
| FR-06 | VS-04 | VS-09 | §4.7, §8.3 |
| FR-07 | VS-07 | VS-05, VS-06 | §4.4, §5, §9 |
| FR-08 | VS-09 | VS-04 | §8, §10 |
| FR-09 | VS-01 | VS-05 | §4.2, §7.2 |
| FR-10 | VS-08 | VS-06, VS-07 | §7.1, §9 |

---

## Summary

This FRD v2.0 makes the frozen contracts from VS-01 … VS-09 and ARD v2.0 first-class requirements:

- **Session lifecycle** is an explicit `Idle`/`Active` state machine with a reveal-delay snapshot, live reel count, browser flag, and transient inferred domain.
- **HUD visibility** is derived solely from the session snapshot and elapsed time; the hidden state renders nothing; transitions budget 200 ms.
- **Message rotation and selection** are user-configurable and apply from the next rotation boundary; empty selection falls back safely.
- **Reel counting** is owned by the orchestrator, independent of HUD visibility, depth-bounded to 10, debounced 80 ms, and never reads text.
- **Browser support** honors reveal delay, preserves rotation, omits the Reel line, and surfaces an optional transient domain label.
- **Persistence** is explicit: Room `sessions` with `is_browser`; DataStore for HUD preferences; `inferredDomain` never persisted.
- **Battery discipline** is enforced structurally with hard prohibitions on polling, alarms, and extra schedulers; reveal delay rides the single 1 Hz tick.
- **Privacy** is enforced structurally: no `INTERNET`, no text reads (lint-enforced), local-only persistence.
- The 120-test matrix and frozen contracts (§10) are the acceptance gate.