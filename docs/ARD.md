# Architecture Requirements Document (ARD) — Final

**Project:** Awareness App
**Document Version:** 2.0 (Final — derived from frozen VS-01 … VS-09 contracts)
**Supersedes:** ARD v1.0
**Derived From:** FRD v1.0 + final vertical slice contracts (VS-04–VS-06, VS-07–VS-09; VS-01–VS-03 contracts treated as frozen)
**Minimum Android Version:** API 26 (Android 8.0)
**Target SDK:** API 34+ (Android 14+) — required for `specialUse` FGS
**Language & Stack:** Kotlin 2.0+, Coroutines, Flow, Jetpack Compose, Room, DataStore, Hilt

---

## 0. What Changed From ARD v1.0

The final vertical slices changed several architectural facts. This ARD corrects them:

| Area | ARD v1.0 | Final (this document) |
|---|---|---|
| `HudState` shape | 4 fields | 6 fields: `isVisible`, `message`, `elapsedMillis`, `reelCount`, `showReelCount`, `browserDomainLabel` |
| Visibility rule | Derived from session state only | Derived from `sessionState is Active && elapsed >= Active.revealDelayMillisSnapshot` |
| Reveal delay | Not modeled | Snapshotted into `Active.revealDelayMillisSnapshot` at `Idle → Active`; immutable for the session |
| Message rotation | Not modeled | Driven by `HudPreferencesRepository` via `MessageRotation.indexFor`; live from next rotation boundary |
| Reel counting | Owned by `AccessibilityService` | Owned by `SessionOrchestrator`; independent of HUD visibility |
| Browser support | Not in v1.0 | Reintroduced with reveal delay honored, rotation preserved, Reel line hidden, optional domain label |
| `AwarenessEvent` | 4 variants | + `BrowserDomainInferred(packageName, domain, timestamp)` |
| `SessionState` | Implicit | Explicit sealed `Idle` / `Active` with `revealDelayMillisSnapshot`, `reelCount`, `isBrowser`, `inferredDomain` |
| Persistence | `SessionSummary` | Room `sessions` table including `is_browser` column; aggregates include browser rows with `reel_count = 0` |
| Error handling | “retry via `AlarmManager`” | No `AlarmManager`, no `Handler.postDelayed`, no `UsageStatsManager`. Hard prohibition (VS-09). |
| `canRetrieveWindowContent` | Universal `false` | Enabled where reel container detection requires traversal; **text/contentDescription/extras are never read** (lint-enforced) |
| Test count | Unspecified | 120 tests across VS-01 … VS-09 |

---

## 1. Architectural Drivers

Unchanged in priority, updated in consequence:

| Driver | Architectural Consequence |
|---|---|
| **Ultra-low battery & RAM** (< 30 MB baseline, no polling) | Event-driven `AccessibilityService` with XML package filtering; depth-bounded traversal (`MAX_TRAVERSAL_DEPTH = 10`); **no** `UsageStatsManager`, `AlarmManager`, `Handler.postDelayed`, or extra `CoroutineWorker` for HUD preferences. Reveal delay rides the existing 1 Hz ticker. |
| **Real-time detection & scroll counting** (≤ 200 ms detect, ≤ 100 ms count) | `SharedFlow` in-process pipeline from `AccessibilityService` → `SessionOrchestrator` → `ObserveHudStateUseCase` → `HudForegroundService`. No disk / network in the hot path. |
| **Touch-pass-through HUD** | `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE \| FLAG_NOT_FOCUSABLE`. |
| **Survivability across OEMs / Doze** | `specialUse` FGS with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; OEM guidance screen; `onTimeout` graceful. |
| **Privacy-first, local-only** | No `INTERNET` permission. Accessibility layer reads **only** view IDs / class names — never `text`, `contentDescription`, or `extras`. Room + DataStore never leave the device. `inferredDomain` is session-transient (never persisted). |
| **Config fidelity** | Reveal delay snapshotted per session; message selection and display duration can apply live from the next rotation boundary without re-snapshotting the session. |

---

## 2. Architecture Overview

Three Android components cooperate through a strictly unidirectional pipeline. **Decision-making lives in `:core:domain`; Android components are shells.**

```
┌──────────────────────────────────────────────────────────────┐
│  AwarenessAccessibilityService                                │
│  • Listens: TYPE_WINDOW_STATE_CHANGED, TYPE_VIEW_SCROLLED     │
│  • Filter: XML packageNames (target apps + browsers)          │
│  • Emits: SessionStarted, SessionEnded, ReelScrolled,         │
│           BrowserDomainInferred                               │
│  • → SharedFlow<AwarenessEvent>  (AccessibilityEventBus)      │
└─────────────────────────────┬────────────────────────────────┘
                              │ (in-process)
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  SessionOrchestrator (pure Kotlin, :core:domain)              │
│  • Owns StateFlow<SessionState>                               │
│  • Idle → Active: snapshots revealDelayMillisSnapshot         │
│  • ReelScrolled while Active → Active.reelCount + 1           │
│     (independent of HudState.isVisible)                       │
│  • BrowserDomainInferred while Active && isBrowser →          │
│     Active.inferredDomain = domain                            │
│  • Active → Idle: persists SessionSummary via                 │
│     SessionRepository                                         │
└─────────────────────────────┬────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  ObserveHudStateUseCase (combine)                             │
│  combine(sessionState, hudPreferences, timerTicks) →          │
│      Flow<HudState>                                           │
│  • isVisible  = Active && elapsed >= revealDelaySnapshot      │
│  • message     = rotation (or "" when hidden)                 │
│  • reelCount   = Active.reelCount                             │
│  • showReelCount = !Active.isBrowser                          │
│  • browserDomainLabel = formatted inferredDomain (or null)    │
└─────────────────────────────┬────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌──────────────────────────┐   ┌──────────────────────────────┐
│  HudForegroundService    │   │  feature:stats               │
│  (specialUse FGS)        │   │  • Reads Room DAO            │
│  • HudViewModel observes │   │  • Daily aggregates          │
│    HudState              │   │  • Local-only                │
│  • HudWindowManager      │   └──────────────────────────────┘
│    hosts ComposeView     │
│  • Passive renderer      │
└──────────────────────────┘
```

**Separation of concerns:**

- `AccessibilityService` = **sensor**: package filter, event types, node lookup (view IDs only), emission.
- `SessionOrchestrator` = **brain**: session lifecycle, reveal-delay snapshot, reel count, inferred domain, persistence.
- `ObserveHudStateUseCase` = **translator**: combines session + preferences + tick into a single immutable `HudState`.
- `HudForegroundService` = **renderer**: hosts `ComposeView`, observes `HudState`, never queries the accessibility tree and never reads `HudPreferences` directly (VS-04 AC 10).

---

## 3. Module Structure

### 3.1 Gradle Graph

```
:app
:build-logic
:core:domain
:core:data
:core:accessibility
:core:overlay
:core:presentation
:core:design-system
:feature:hud
:feature:settings
:feature:stats
```

Dependency rules (unchanged pattern, enforced by `:build-logic`):

| Module | May depend on |
|---|---|
| `:app` | everything (DI assembly) |
| `:feature:*` | `:core:domain`, `:core:presentation`, `:core:design-system`, own data |
| `:core:data` | `:core:domain` |
| `:core:accessibility` | `:core:domain` |
| `:core:overlay` | `:core:domain` |
| `:core:presentation` | `:core:domain`, `:core:design-system` |
| `:core:domain` | nothing (pure Kotlin) |

### 3.2 Source Tree (final)

```
com.awareness.app/
├── AppApplication.kt
├── MainActivity.kt
├── di/  { AppModule, AccessibilityModule, ServiceModule }

core/
├── domain/src/main/kotlin/com/awareness/core/domain/
│   ├── model/
│   │   ├── TargetApp.kt
│   │   ├── AwarenessEvent.kt          (SessionStarted, SessionEnded,
│   │   │                                ReelScrolled, BrowserDomainInferred)
│   │   ├── SessionState.kt            (sealed: Idle, Active)
│   │   ├── HudState.kt                (6 fields — frozen)
│   │   ├── HudPreferences.kt
│   │   ├── MindfulnessMessage.kt
│   │   ├── DefaultMindfulnessMessages.kt
│   │   ├── SessionSummary.kt
│   │   └── DailyAggregate.kt
│   ├── repository/
│   │   ├── SessionRepository.kt
│   │   └── HudPreferencesRepository.kt
│   ├── usecase/
│   │   ├── ObserveHudStateUseCase.kt
│   │   ├── ObserveHudPreferencesUseCase.kt
│   │   └── GetDailyStatsUseCase.kt
│   └── SessionOrchestrator.kt

├── data/src/main/kotlin/com/awareness/core/data/
│   ├── database/
│   │   ├── AwarenessDatabase.kt
│   │   ├── SessionEntity.kt
│   │   ├── SessionDao.kt
│   │   └── Converters.kt
│   ├── repository/
│   │   ├── SessionRepositoryImpl.kt
│   │   └── HudPreferencesRepositoryImpl.kt
│   └── datastore/
│       └── SettingsDataStore.kt

├── accessibility/src/main/kotlin/com/awareness/core/accessibility/
│   ├── AwarenessAccessibilityService.kt
│   ├── AccessibilityEventBus.kt
│   ├── TargetAppAllowlist.kt
│   ├── ScrollContainerMatcher.kt
│   ├── ScrollContainerConfig.kt
│   ├── BrowserUrlInferencer.kt
│   ├── BrowserUrlConfig.kt
│   └── res/xml/accessibility_service_config.xml

├── overlay/src/main/kotlin/com/awareness/core/overlay/
│   ├── HudWindowManager.kt
│   ├── HudLayoutParamsFactory.kt
│   ├── OverlayPermissionChecker.kt
│   └── HudAddResult.kt

├── presentation/src/main/kotlin/com/awareness/core/presentation/
│   ├── permission/
│   │   ├── PermissionState.kt
│   │   └── PermissionRequestCoordinator.kt
│   └── util/
│       ├── ObserveAsEvents.kt
│       ├── TimeFormatter.kt           (mmss, hms)
│       ├── MessageRotation.kt
│       └── BrowserDomainFormatter.kt

└── design-system/src/main/kotlin/com/awareness/core/designsystem/
    ├── theme/{Color.kt, Type.kt, AwarenessTheme.kt}
    └── component/HudText.kt

feature/
├── hud/src/main/kotlin/com/awareness/feature/hud/
│   ├── HudForegroundService.kt
│   ├── HudComposable.kt
│   ├── HudViewModel.kt
│   ├── MindfulnessMessages.kt
│   └── di/HudModule.kt
├── settings/src/main/kotlin/com/awareness/feature/settings/
│   ├── SettingsActivity.kt
│   ├── SettingsScreen.kt
│   ├── SettingsViewModel.kt
│   ├── permission/{AccessibilityPermissionCard.kt,
│   │               OverlayPermissionCard.kt,
│   │               BatteryOptimizationCard.kt,
│   │               NotificationPermissionCard.kt}
│   ├── oem/{OemGuidanceProvider.kt, OemGuidanceScreen.kt}
│   └── di/SettingsModule.kt
└── stats/src/main/kotlin/com/awareness/feature/stats/
    ├── StatsScreen.kt
    ├── StatsViewModel.kt
    └── di/StatsModule.kt

app/
├── schemas/awareness.db/1.json       (committed — exportSchema = true)
├── lint/NoTextReadDetector.kt
└── res/
    ├── xml/accessibility_service_config.xml
    ├── xml/backup_rules.xml
    ├── values/strings.xml
    └── drawable/ic_notification.xml
```

---

## 4. Frozen Cross-Slice Contracts

The following contracts are **frozen** across slices and are the only inter-module coupling points. Any change requires a coordinated update to every consumer.

### 4.1 Domain Models

```kotlin
// core/domain/model/AwarenessEvent.kt
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

// core/domain/model/SessionState.kt
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

// core/domain/model/HudState.kt
data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean,
    val browserDomainLabel: String? = null
)

// core/domain/model/HudPreferences.kt
data class HudPreferences(
    val selectedMessages: List<MindfulnessMessage>,
    val messageDisplayDurationSeconds: Int,
    val revealDelayMillis: Long
)
```

### 4.2 Repository Contracts

```kotlin
// core/domain/repository/SessionRepository.kt
interface SessionRepository {
    suspend fun save(summary: SessionSummary)
    fun observeDailyAggregates(since: Instant): Flow<List<DailyAggregate>>
}

// core/domain/repository/HudPreferencesRepository.kt
interface HudPreferencesRepository {
    val preferences: Flow<HudPreferences>
    suspend fun update(prefs: HudPreferences)
}
```

### 4.3 Use Cases

```kotlin
// core/domain/usecase/ObserveHudStateUseCase.kt
interface ObserveHudStateUseCase {
    operator fun invoke(): Flow<HudState>
}

// core/domain/usecase/GetDailyStatsUseCase.kt
interface GetDailyStatsUseCase {
    operator fun invoke(days: Int = 7): Flow<List<DailyAggregate>>
}
```

### 4.4 Accessibility Contracts

```kotlin
// core/accessibility/ScrollContainerMatcher.kt
interface ScrollContainerMatcher {
    /** @return true iff node is inside a known short-form container within
     *  MAX_TRAVERSAL_DEPTH. Never reads text / contentDescription / extras. */
    fun isReelContainer(node: AccessibilityNodeInfo): Boolean
}

// core/accessibility/ScrollContainerConfig.kt
object ScrollContainerConfig {
    const val MAX_TRAVERSAL_DEPTH = 10
    const val SCROLL_DEBOUNCE_MS   = 80L
    val instagramViewIds = setOf("clips_viewer", "reel_viewer")
    val facebookViewIds  = setOf("reel_fragment", "clips_viewer")
    val snapchatViewIds  = setOf("snap_reel_container")
    val youtubeViewIds   = setOf("reel_watch_fragment")
}

// core/accessibility/BrowserUrlInferencer.kt
interface BrowserUrlInferencer {
    /** Best-effort; reads only view IDs and class names. Returns null on any failure. */
    fun inferDomain(root: AccessibilityNodeInfo?): String?
}

// core/accessibility/BrowserUrlConfig.kt
object BrowserUrlConfig {
    val chromeUrlViewIds  = setOf("url_bar", "origin")
    val firefoxUrlViewIds = setOf("mozac_browser_toolbar_url_view")
    val knownSocialHosts  = setOf(
        "instagram.com", "www.instagram.com", "m.instagram.com",
        "facebook.com",  "www.facebook.com",  "m.facebook.com",
        "snapchat.com",  "www.snapchat.com",
        "youtube.com",   "www.youtube.com",   "m.youtube.com",
        "tiktok.com",    "www.tiktok.com"
    )
}
```

### 4.5 Overlay Contracts

```kotlin
// core/overlay/HudWindowManager.kt
interface HudWindowManager {
    fun add(params: HudWindowParams): HudAddResult
    fun update(state: HudState)
    fun remove()
}

data class HudWindowParams(
    val gravity: Int = Gravity.TOP or Gravity.END,
    val xOffsetDp: Int = 16,
    val yOffsetDp: Int = 16
)

sealed interface HudAddResult {
    data object Success : HudAddResult
    data class Failure(val reason: FailureReason) : HudAddResult
}
enum class FailureReason { NO_OVERLAY_PERMISSION, FGS_REFUSED, UNKNOWN }
```

### 4.6 Presentation Utilities

```kotlin
object TimeFormatter {
    fun mmss(elapsedMillis: Long): String   // "12:34"
    fun hms(elapsedMillis: Long): String    // "1:02:34"
}

object MessageRotation {
    fun indexFor(
        elapsedMillis: Long,
        revealDelayMillis: Long,
        messageDisplayDurationSeconds: Int,
        messageCount: Int
    ): Int
}

object BrowserDomainFormatter {
    fun label(domain: String): String       // "You're on instagram.com"
}

interface PermissionRequestCoordinator {
    fun requestAccessibility(context: Activity)
    fun requestOverlay(context: Activity)
    fun requestBatteryOptimization(context: Activity)
    fun requestNotifications(context: Activity)
    fun isAccessibilityEnabled(context: Context): Boolean
    fun showAccessibilityDisabledNotification(context: Context)
}
```

### 4.7 FGS Intent Actions

```kotlin
// feature/hud/HudForegroundService.kt
const val ACTION_START = "com.awareness.app.hud.START"
const val ACTION_STOP  = "com.awareness.app.hud.STOP"
```

---

## 5. Runtime Data Flow & Session Lifecycle

### 5.1 Session Lifecycle

```
Idle
 │  ← SessionStarted(pkg) from AccessibilityService
 │    (fires on TYPE_WINDOW_STATE_CHANGED entering a target app)
 ▼
Active(
  packageName = pkg,
  startElapsed = now,
  wallClockStart = now,
  isBrowser = pkg ∈ TargetAppAllowlist.browsers,
  revealDelayMillisSnapshot = hudPrefs.revealDelayMillis  ← snapshot at this instant
  reelCount = 0,
  inferredDomain = null
)
 │
 │  During Active:
 │   • ReelScrolled        → reelCount++      (regardless of HUD visibility)
 │   • BrowserDomainInferred(pkg, d) with isBrowser && pkg matches
 │                          → inferredDomain = d  (or null on unknown)
 │   • HudPreferences changes are observed live by ObserveHudStateUseCase:
 │       – message selection / duration → applied at next rotation boundary
 │       – reveal delay                 → NOT applied (snapshot is frozen)
 │
 ▼
Idle
    ← SessionEnded(pkg)
    Persist SessionSummary(wallClockStart, end, duration, reelCount, isBrowser)
```

### 5.2 `ObserveHudStateUseCase` combine logic (frozen)

```kotlin
combine(sessionState, hudPreferences, timerTicks) { session, prefs, elapsed ->
    when (session) {
        Idle -> HudState(
            isVisible = false, message = "", elapsedMillis = 0,
            reelCount = 0, showReelCount = false, browserDomainLabel = null
        )
        is Active -> {
            val visible = elapsed >= session.revealDelayMillisSnapshot
            val messages = prefs.selectedMessages
                .ifEmpty { DefaultMindfulnessMessages.fallback }
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

**Contract invariant:** `HudState.isVisible == (sessionState is Active && elapsed >= revealDelayMillisSnapshot)`. The HUD service never computes this — it only renders (VS-04 AC 11).

### 5.3 Time source

- **`timerTicks`:** a single `flow { while(true) { emit(now); delay(1000) } }` in `SessionOrchestrator` / `ObserveHudStateUseCase`, using a monotonic `SystemClock.elapsedRealtime()`. **This is the only periodic loop in the codebase.**
- Reveal delay comparison uses this same tick. **No separate scheduler.** (VS-09 AC 10, E6.)
- `Active.startElapsed` is captured from the same monotonic clock at session start.

---

## 6. Concurrency & Threading

| Component | Dispatcher | Notes |
|---|---|---|
| `AccessibilityService.onAccessibilityEvent` | Main (system) | Must not block. `tryEmit` to `SharedFlow`. Node traversal (scroll matcher, URL inferencer) executed on `Dispatchers.Default` **before** emitting `ReelScrolled` / `BrowserDomainInferred`. |
| `SessionOrchestrator` | `Dispatchers.Default` | Collects `SharedFlow`, updates `StateFlow<SessionState>`, invokes `SessionRepository.save` on `Dispatchers.IO`. |
| `ObserveHudStateUseCase` | `Dispatchers.Default` | Pure combine; `timerTicks` emits on `Dispatchers.Default` with `delay(1000)`. |
| `HudForegroundService` | `Dispatchers.Main.immediate` | Collects `HudState` via `HudViewModel`; `WindowManager` calls on main. |
| `HudPreferencesRepositoryImpl` | `Dispatchers.IO` | DataStore reads/writes. **Single collector** inside `ObserveHudStateUseCase` — no additional collectors. |
| Room writes / reads | `Dispatchers.IO` | Session persistence and stats queries. |

**Backpressure:** `SharedFlow` configured with `extraBufferCapacity = 16`, `onBufferOverflow = DROP_OLDEST`. Session start/end are cheap; scroll events are monotonic counter increments so dropping an intermediate event cannot corrupt the count.

**No polling:** verified by VS-09 TS-07 and TS-09 (`grep` for `postDelayed`, `UsageStatsManager`, `AlarmManager`, `setRepeating`, `Handler(Looper` returns 0 new occurrences vs baseline).

---

## 7. Persistence

### 7.1 Room (stats)

```sql
-- awareness.db, version 1
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

- `exportSchema = true`; `schemas/awareness.db/1.json` committed.
- Browser rows contribute `duration_millis` and `session_count` to aggregates; `reel_count` is guaranteed `0` by VS-06 AC 11 (browser scrolls are not counted), so `SUM(reel_count)` needs no special handling.
- `fallbackToDestructiveMigration` **disabled** (VS-08 E4).

Aggregation query:

```sql
SELECT date(start_wall_clock / 1000, 'unixepoch', 'localtime') AS day,
       SUM(duration_millis) AS totalMillis,
       SUM(reel_count)      AS totalReels,
       COUNT(*)             AS sessionCount
FROM sessions
WHERE start_wall_clock >= :since
GROUP BY day
ORDER BY day DESC;
```

### 7.2 DataStore (preferences)

- Keys: `selected_message_ids: Set<String>`, `message_display_duration_seconds: Int`, `reveal_delay_millis: Int`.
- Written **only on user Save** — no write amplification (VS-09 AC 13).
- Read via a single `Flow<HudPreferences>` collected once inside `ObserveHudStateUseCase`. The HUD service **never** reads `HudPreferences` directly (VS-04 AC 10).

### 7.3 `inferredDomain` — not persisted

Browser domain is session-transient. Persisting it would create a privacy surface. Discarded with the session (VS-07 AC 13, E11).

---

## 8. Battery & Performance Architecture

### 8.1 `AccessibilityService` configuration

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeViewScrolled"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:packageNames="com.facebook.katana,com.facebook.lite,com.instagram.android,com.instagram.lite,com.snapchat.android,com.android.chrome,org.mozilla.firefox"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_description" />
```

- `packageNames` filtering → the system never delivers events from other apps.
- `canRetrieveWindowContent="true"` is **required** for `ScrollContainerMatcher` / `BrowserUrlInferencer` to walk the tree. **Never** call `node.text`, `node.contentDescription`, or `node.extras` — enforced by custom lint rule `NoTextReadDetector` + code review checklist (VS-06 AC 4, VS-07 AC 5).
- If `canRetrieveWindowContent=false` at runtime (OEM / user), matcher returns `false`, no count (VS-06 AC 10).

### 8.2 HUD rendering budget

- RAM baseline < 30 MB. `ComposeView` hosts a single small `Text` column. No images, no `LazyColumn`, no animation APIs, no elevation shadow.
- Recomposition scoped: `HudComposable` reads `HudState` via a single `collectAsStateWithLifecycle()`; only the timer `Text` recomposes per second. Message and count are stable until changed (VS-05 AC 11, TS-05).
- `FLAG_NOT_TOUCHABLE` ensures the overlay never intercepts touch dispatch.

### 8.3 Survivability

- `specialUse` FGS with:
  ```xml
  <service android:name=".feature.hud.HudForegroundService"
           android:foregroundServiceType="specialUse">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="mindfulness_hud" />
  </service>
  ```
- `startForeground()` wrapped in `try/catch (ForegroundServiceStartNotAllowedException)` at **every** call site (VS-04 AC 7, VS-09 AC 5).
- `onTimeout(startId, fgsType)` implemented → graceful stop + notification clear (VS-04 AC 6, VS-09 AC 4).
- Battery optimization exemption via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; `BatteryOptimizationCard` refreshes on `onResume` from `PowerManager.isIgnoringBatteryOptimizations` (no polling).
- `OemGuidanceScreen` covers Xiaomi (MIUI), Huawei (EMUI), OnePlus (OxygenOS), Samsung (One UI), Pixel (stock).
- **No self-resurrection** — Play policy compliant.

---

## 9. Privacy & Security

| Requirement | Enforcement |
|---|---|
| Zero content scraping | `NoTextReadDetector` lint rule + code review checklist (VS-06 TS-04, VS-07 TS-05). Only view IDs and class names are read. |
| No `INTERNET` permission | Manifest declares none (VS-08 TS-06: `aapt dump permissions` → 0). |
| Local-only persistence | Room + DataStore; no sync adapter, no WorkManager network job, no export provider. Uninstall removes all data. |
| Accessibility scope | `packageNames` restricts to the target-app allowlist. |
| Overlay touch pass-through | `FLAG_NOT_TOUCHABLE` prevents credential / gesture interception. |
| Session-transient browser domain | `inferredDomain` never persisted (VS-07 AC 13). |

---

## 10. Error Handling & Resilience

| Scenario | Mitigation |
|---|---|
| Overlay permission revoked while running | `HudWindowManager.add()` returns `Failure(NO_OVERLAY_PERMISSION)`; service stops overlay, keeps notification (VS-04 E1). |
| `ComposeView` crash on missing owners | `HudWindowManager` injects `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, `ViewTreeViewModelStoreOwner` before `addView` (VS-04 E2). |
| `startForeground` throws `ForegroundServiceStartNotAllowedException` | Caught; `stopSelf()`; user notification explaining background-start restriction (VS-04 E3, VS-09 E4). |
| `onTimeout` | Graceful stop, notification cleared (VS-04 AC 6, VS-09 AC 4). |
| Accessibility service disabled by system | `PermissionRequestCoordinator.showAccessibilityDisabledNotification` — persistent notification (VS-09 AC 6). |
| `canRetrieveWindowContent=false` | Matcher returns `false`, no count (VS-06 AC 10). URL inference returns `null` (VS-07 E10). |
| Browser inference failure / malformed nodes | `BrowserUrlInferencer` never throws — returns `null` (VS-07 AC 11, TS-12). |
| OEM force-stop after swipe from Recents | No self-resurrection; companion UI guides the user (VS-09 E1). |
| Doze during reveal countdown | Session ends on screen state change (VS-03 E2); countdown is discarded with the session; next session takes a fresh snapshot (VS-09 E8, TS-12). |
| DataStore latency on cold start | Reveal delay defaults to `0` for the first session if the repository is not warm; warm state is stable (VS-09 E9). |
| Room corruption | `fallbackToDestructiveMigration` **disabled**; user is notified to reset stats (VS-08 E4). |
| **No `AlarmManager` retry** | Removed from ARD v1.0 — conflicts with VS-09 AC 8 / TS-09. |

---

## 11. Technology Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.0+ |
| Asynchrony | Kotlin Coroutines |
| Reactive streams | Flow / SharedFlow / StateFlow |
| Overlay UI | Jetpack Compose (`ComposeView`) |
| Companion UI | Jetpack Compose |
| DI | Hilt (`@Singleton` bus; `@ServiceScoped` for HUD service) |
| Persistence | Room (stats) + DataStore Preferences (settings) |
| Build | Kotlin DSL + Version Catalog + `:build-logic` convention plugins |
| Testing | JUnit 5, Turbine, Robolectric, Compose UI Test, Macrobenchmark |

---

## 12. Testing Strategy (aligned to final matrix)

| Slice | Unit | Integration | API | E2E Manual | Total |
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

**Coverage intent:**

- **100%** of `SessionOrchestrator` transitions (Idle↔Active, reel increment, inferred-domain set/clear, ignore for non-browser / mismatched package).
- **100%** of `ObserveHudStateUseCase` branches (hidden / visible, browser / social, empty selection fallback, delay boundary).
- `ScrollContainerMatcher`: 20+ cases including depth 10 boundary and depth 11 rejection; mock that throws on `.text` to prove no text reads.
- `BrowserUrlInferencer`: null root, no URL bar, matching / non-matching domain, malformed nodes, never-throws contract.
- `HudWindowManager`: add / update / remove idempotency; `add` returns `Failure(NO_OVERLAY_PERMISSION)` when permission missing.
- `SessionDao` (Room in-memory): mixed social/browser inserts across 3 days; aggregation correctness; `is_browser` round-trip.
- E2E manual: delay 3 min → no HUD for 3 min → HUD appears with pre-reveal reel count → tap-through works → leave app → HUD disappears. Browser session honors delay and hides Reel line. Stats persist after process kill.
- Grep-based invariants: `\.text\b`, `postDelayed`, `UsageStatsManager`, `AlarmManager`, `setRepeating`, `Handler(Looper` → all 0.

---

## 13. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Play policy rejection for `specialUse` | Distribution | Clear `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification string; no `dataSync` misuse; alternative-store fallback. |
| OEM disables `canRetrieveWindowContent` | Reel counting degrades | Silent degradation (matcher returns `false`); no crash; UI still renders message + timer. |
| Instagram / Chrome view-ID drift | Missed counts / labels | View-ID sets are `Set<String>` with class-name heuristic fallback; unknown containers no-op (VS-06 E1, VS-07 E1). |
| `ComposeView` overlay lifecycle crash | HUD unavailable | Inject lifecycle / savedstate / viewmodel owners before `addView`. |
| OEM force-stop after Recents swipe | Service dies | No architectural defense; guidance UI only. |
| Doze kills FGS despite exemption | Missing HUD | `specialUse` + exemption; no self-resurrection; user notified on next launch. |
| DataStore latency at cold start | Reveal delay momentarily 0 | `HudPreferencesRepository` returns defaults; session snapshot uses whatever is available; warm state is stable. |
| Config drift between VS contracts | Integration failures | Contract table §4 is the source of truth; any change requires coordinated updates. |

---

## 14. Acceptance Criteria Mapping (FRD → Architecture)

| FRD Criterion | Architectural Mechanism |
|---|---|
| HUD appears within 200 ms of Instagram opening | `AccessibilityService` → `SharedFlow` → `SessionOrchestrator` → `ObserveHudStateUseCase` → `HudForegroundService` (no disk / network). |
| Reveal delay honored exactly per session | Snapshot captured at `Idle → Active`; frozen for the session; HUD visibility derives from `elapsed >= snapshot`. |
| Message rotation from user selection | `HudPreferencesRepository` + `MessageRotation.indexFor`; live from next boundary. |
| 5 Reels increments counter by exactly 5 | `ScrollContainerMatcher` emits one `ReelScrolled` per qualifying scroll within a confirmed container (debounced 80 ms); orchestrator increments monotonically. |
| Counting independent of HUD visibility | `SessionOrchestrator` increments `Active.reelCount` regardless of `HudState.isVisible`. |
| HUD does not intercept touches | `FLAG_NOT_TOUCHABLE \| FLAG_NOT_FOCUSABLE`; no interactive modifiers. |
| Browser session — no Reel line | `showReelCount = !isBrowser`; line omitted not zero-shown. |
| Browser — reveal delay honored, rotation preserved | Same combine logic; only the Reel line and optional domain label differ. |
| Browser — optional domain label | `BrowserDomainInferred` → `Active.inferredDomain` → `HudState.browserDomainLabel` → rendered above message. |
| Stats reflect browser honestly | Room `is_browser` column; aggregates include browser time and sessions; reel sums stay `0` for browser rows. |
| Survives 8 h idle screen-off | `specialUse` FGS + exemption + no polling + XML package filtering. |
| RAM < 30 MB | Minimal `ComposeView`, no images/lists/animations. |
| No new battery cost from config | Reveal delay rides existing 1 Hz tick; single DataStore collector; no `AlarmManager` / `postDelayed` / `UsageStatsManager` / extra `CoroutineWorker`. |
| Privacy — never reads content | `NoTextReadDetector` lint + review checklist; only view IDs / class names. |
| Privacy — local only | No `INTERNET`; Room + DataStore only; `inferredDomain` transient. |

---

## 15. Summary

The Awareness App’s final architecture is a **Kotlin-first, feature-modularized, event-driven system** where:

- The **`AccessibilityService`** is a thin, privacy-constrained sensor that emits `AwarenessEvent`s through a `SharedFlow`.
- The **`SessionOrchestrator`** is a pure-Kotlin state machine that owns session lifecycle, reveal-delay snapshotting, reel counting (independent of HUD visibility), and inferred browser domain.
- **`ObserveHudStateUseCase`** combines session state, live preferences, and a single 1 Hz monotonic tick into an immutable, 6-field `HudState`.
- The **`HudForegroundService`** is a passive renderer that never queries the accessibility tree and never reads preferences directly.
- **Room + DataStore** persist stats and settings locally; `inferredDomain` is session-transient.
- **Battery discipline** is enforced structurally: one periodic loop (the HUD tick), zero `AlarmManager` / `postDelayed` / `UsageStatsManager` / extra `CoroutineWorker` for config, and XML package filtering at the accessibility layer.
- **Privacy** is enforced structurally: no `INTERNET` permission, no text reads (lint-checked), and browser domain is never persisted.

All frozen contracts in §4 are the single source of truth for cross-slice coupling. The test matrix (§12) totals **120 tests**, matching the final vertical-slice documents exactly.
