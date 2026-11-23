Below are **VS-07, VS-08, and VS-09 rewritten from scratch**, aligned with the revised contracts from VS-01–VS-06:

- `SessionState.Active` carries `revealDelayMillisSnapshot`, `reelCount`, `isBrowser`, and (new) `inferredDomain`.
- `HudState` gains (new) `browserDomainLabel: String? = null` — additive, non-breaking default.
- Reveal delay snapshotted at session start; message rotation from `HudPreferencesRepository`.
- Browser sessions hide the Reel line and surface an optional domain label **above** the rotating message (rotation is preserved, not replaced).

---

# VS-07 — Browser Support (Delay-Aware, Message-Rotation-Preserving)

**Dependencies:** VS-02, VS-05, VS-01 (`HudPreferencesRepository` contract)  
**Parallel With:** VS-06, VS-08  
**FRD Coverage:** FR-07, FR-05.1 (browser reveal delay), FR-05.2 (browser message rotation), NFR-04 (no text read)

### User / Actor Story
> **As a user**, when I browse social media in Chrome or Firefox, I still want an awareness HUD that **respects my configured reveal delay and rotates my chosen mindfulness messages**, shows me **which social domain I’m on** if the browser exposes it, and **never** shows a Reel count (since browser scrolls are unreliable to classify), **so that** the tool covers browser-based consumption without lying to me about short-form video counts.

### Acceptance Criteria

1. When the foreground package ∈ `TargetAppAllowlist.browsers`, the session starts with `Active.isBrowser = true` (emitted by VS-02).
2. HUD for a browser session obeys the **same reveal delay** as social sessions: `isVisible = Active && elapsedMillis >= Active.revealDelayMillisSnapshot`.
3. HUD for a browser session **omits the Reel line** (`showReelCount = false`); the line is not rendered even if `reelCount > 0`.
4. HUD for a browser session **still rotates the user’s selected messages** every `messageDisplayDurationSeconds` from `HudPreferencesRepository`.
5. Best-effort domain inference: `BrowserUrlInferencer.inferDomain(root)` inspects **only view IDs and node class names** (e.g. Chrome’s `url_bar` / `origin`, Firefox’s `mozac_browser_toolbar_url_view`). It **never** reads `node.text`, `node.contentDescription`, or `node.extras`.
6. When inference succeeds and the host matches a known social domain (`instagram.com`, `facebook.com`, `snapchat.com`, `youtube.com`, `tiktok.com`, and their `m.` / `www.` variants), a `BrowserDomainInferred(packageName, domain, timestamp)` event is emitted on `AccessibilityEventBus`.
7. `SessionOrchestrator` consumes `BrowserDomainInferred` **only while `Active.isBrowser == true`** and sets `Active.inferredDomain = domain` (last-wins within a session).
8. When `Active.inferredDomain != null`, `HudState.browserDomainLabel` is populated with the human-readable form (e.g. `"You're on instagram.com"`) and the HUD renders it as a small label **above** the rotating message. Rotation continues unchanged.
9. When inference fails, `Active.inferredDomain == null` and the HUD renders no domain label. No crash, no count.
10. Domain re-infers on subsequent `TYPE_WINDOW_STATE_CHANGED` events within the browser (navigating within Chrome updates the label). If a new inference returns `null`, the label clears.
11. `BrowserUrlInferencer` never throws — returns `null` on any failure.
12. `ReelScrolled` events during a browser session are **not** counted by `SessionOrchestrator` (see VS-06 AC 11); the count stays `0` and is hidden.
13. When a browser session ends, `Active.inferredDomain` is discarded with the session.
14. No `INTERNET` permission; all inference is local, from the accessibility tree.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Browser URL bar not exposed (custom tab, WebView, OEM skin) | Inference returns `null`; HUD shows rotation only; log at DEBUG level. |
| E2 | Non-social site in browser | HUD still shown (FR-07 accepts false positives); no domain label. |
| E3 | Chrome Custom Tab from another app | Package is `com.android.chrome` → treated as browser; domain inferred from URL if available. |
| E4 | Incognito mode | Same behavior; no differentiability required. |
| E5 | Inference succeeds but domain is not in known set | Do **not** set `inferredDomain`; no label; rotation continues. |
| E6 | Domain inferred before reveal delay | `Active.inferredDomain` set immediately; label appears at reveal, not before (HUD hidden until reveal). |
| E7 | Domain changes mid-session (instagram → facebook → news) | Label updates on each inference; clears when domain becomes unknown. |
| E8 | Empty rotation list + inferred domain | Domain label still rendered; message line falls back to defaults (VS-05 mapper). |
| E9 | Browser session shorter than reveal delay | HUD never visible; no label ever rendered; session still persisted with `isBrowser=true`. |
| E10 | `canRetrieveWindowContent=false` on the service | Inference always returns `null`; HUD rotation runs normally. |

### UI/UX Specification — Browser HUD (revealed, domain inferred)

```
┌─────────────────────────────────────┐
│  (Chrome: instagram.com)            │
│              ┌────────────────────┐ │
│              │ You’re on          │ │   ← domain label (12 sp, gray)
│              │ instagram.com      │ │
│              │ Is this serving    │ │   ← rotating message (12 sp, white)
│              │ you?               │ │
│              │ ⏱ 04:12            │ │   ← total session elapsed
│              └────────────────────┘ │
│  (Chrome: instagram.com)            │
└─────────────────────────────────────┘
```

### UI/UX Specification — Browser HUD (revealed, no domain)

```
┌─────────────────────────────────────┐
│  (Chrome)                           │
│              ┌────────────────────┐ │
│              │ Is this serving    │ │
│              │ you?               │ │
│              │ ⏱ 04:12            │ │
│              └────────────────────┘ │
│  (Chrome)                           │
└─────────────────────────────────────┘
```

- **Domain label:** 11 sp, `Color.White.copy(alpha = 0.72f)`, ellipsized to 1 line, max 200 dp.
- **Rotation** is not paused by the presence of the domain label.
- **Reel line** is **absent**, not zero-shown.
- **No animation**, no new gesture handling.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS07‑TS‑01 | Browser triggers HUD after delay | Delay 180 s, open Chrome, advance 180 s | HUD reveals with rotation, no Reel line, no domain (inference may fail on emulator) |
| VS07‑TS‑02 | Domain inferred | Fake tree with URL view = `instagram.com`; open Chrome | `BrowserDomainInferred("com.android.chrome", "instagram.com")`; label = `You're on instagram.com` |
| VS07‑TS‑03 | Inference failure | Fake tree with no URL view | `inferredDomain = null`; no label; rotation runs |
| VS07‑TS‑04 | No Reel line | Inspect HUD during browser session | Line 3 absent, even after `ReelScrolled` is emitted |
| VS07‑TS‑05 | No text read | `grep -r "\.text\b" accessibility/browser/` | 0 results |
| VS07‑TS‑06 | Reveal delay honored | Delay 60 s, open Chrome, advance 59 s / 60 s | HUD hidden at 59 s; visible at 60 s |
| VS07‑TS‑07 | Rotation continues with domain label | Domain inferred, duration 30 s, advance 30 s | Message index increments; domain label stays |
| VS07‑TS‑08 | Domain change | Navigate instagram → facebook (fake events) | Label updates to `facebook.com` |
| VS07‑TS‑09 | Domain cleared | Navigate to non-social site | Label disappears; rotation continues |
| VS07‑TS‑10 | Browser scroll ignored | Emit `ReelScrolled` in browser session | `reelCount` remains 0; hidden line unchanged |
| VS07‑TS‑11 | Browser session ends | End session | `inferredDomain` discarded; new session starts with `null` |
| VS07‑TS‑12 | Inference never throws | Feed null / malformed nodes | Returns `null`; no crash |

### Database Schema

None new. `isBrowser` is persisted via VS-08. `inferredDomain` is **session-transient** and not persisted — it describes the current URL, which is ephemeral; persisting it would create a privacy surface.

### API Contracts

```kotlin
// core/accessibility/BrowserUrlInferencer.kt
interface BrowserUrlInferencer {
    /**
     * Best-effort. Reads only view IDs and node class names.
     * Never reads node.text, node.contentDescription, or node.extras.
     * @return normalised host (e.g. "instagram.com") or null if not inferable.
     */
    fun inferDomain(root: AccessibilityNodeInfo?): String?
}

// core/accessibility/BrowserUrlConfig.kt
object BrowserUrlConfig {
    val chromeUrlViewIds = setOf("url_bar", "origin")
    val firefoxUrlViewIds = setOf("mozac_browser_toolbar_url_view")
    val knownSocialHosts = setOf(
        "instagram.com", "www.instagram.com", "m.instagram.com",
        "facebook.com", "www.facebook.com", "m.facebook.com",
        "snapchat.com", "www.snapchat.com",
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "tiktok.com", "www.tiktok.com"
    )
}

// core/domain/model/AwarenessEvent.kt (additive)
sealed interface AwarenessEvent {
    // ... existing variants
    data class BrowserDomainInferred(
        val packageName: String,
        val domain: String,
        val timestamp: Long
    ) : AwarenessEvent
}

// core/domain/model/SessionState.kt (VS-07 delta, additive default)
sealed interface SessionState {
    data object Idle : SessionState
    data class Active(
        val packageName: String,
        val startElapsed: Long,
        val wallClockStart: Long,
        val isBrowser: Boolean,
        val revealDelayMillisSnapshot: Long,
        val reelCount: Int,
        val inferredDomain: String? = null     // <- VS-07
    ) : SessionState
}

// core/domain/model/HudState.kt (VS-07 delta, additive default)
data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean,
    val browserDomainLabel: String? = null     // <- VS-07
)

// core/presentation/util/BrowserDomainFormatter.kt
object BrowserDomainFormatter {
    fun label(domain: String): String   // "You're on instagram.com"
}
```

**Orchestrator behavior (delta to VS-03):**

```text
On BrowserDomainInferred(pkg, domain, ts):
  if (sessionState is Active && sessionState.isBrowser && sessionState.packageName == pkg) {
    sessionState = sessionState.copy(inferredDomain = domain)
  }
  // else: ignored

On SessionStarted:
  // Active(... inferredDomain = null)
```

**ObserveHudStateUseCase mapping (delta to VS-05):**

```text
val browserDomainLabel =
    if (session is Active && session.isBrowser) {
        session.inferredDomain?.let(BrowserDomainFormatter::label)
    } else null
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `BrowserUrlInferencerTest` — null root, no URL bar, matching domain, non-matching domain, malformed nodes, no text access (mock throws on `.text`). `BrowserDomainFormatterTest`. `SessionOrchestratorBrowserDomainTest` — set on Active, cleared when event domain becomes unknown, ignored for non-browser sessions, ignored for mismatched package. |
| **Integration** | `AwarenessAccessibilityService → orchestrator → ObserveHudStateUseCase` with fake browser events; assert `HudState.browserDomainLabel` transitions (`null → "You're on …" → null`). |
| **API** | `BrowserUrlInferencerContractTest` — returns `null` on any failure; never throws. `HudStateBrowserContractTest` — `browserDomainLabel == null` when `isBrowser == false`. |
| **E2E Manual** | Open Chrome, navigate to instagram.com → HUD reveals at configured delay → domain label visible above rotating message → navigate to news site → label disappears, rotation continues. |

### File & Folder Mapping

```
:core:accessibility  BrowserUrlInferencer.kt
                     BrowserUrlConfig.kt
                     AwarenessAccessibilityService.kt (dispatch BrowserDomainInferred)
:core:domain         model/AwarenessEvent.kt (BrowserDomainInferred)
                     model/SessionState.kt (inferredDomain)
                     model/HudState.kt (browserDomainLabel)
                     SessionOrchestrator.kt (handle BrowserDomainInferred)
:core:presentation   util/BrowserDomainFormatter.kt
:feature:hud         HudComposable.kt (render optional domain label line)
                     HudViewModel.kt (unchanged — ObserveHudStateUseCase already maps)
:app                 lint/NoTextReadDetector.kt (extended to browser package)
```

---

# VS-08 — Local Persistence & Stats Dashboard

**Dependencies:** VS-03  
**Parallel With:** VS-06, VS-07  
**FRD Coverage:** §6.3 (aggregate daily awareness stats, local Room), NFR-04 (privacy — local-only)

### User / Actor Story
> **As a user**, I want to see my daily totals (time, reels, sessions) **so that** I can reflect on my habits, and I want that data to **never leave my device**, and I want browser sessions to appear as sessions (with time and count of sessions) but with **zero Reels**, **so that** my stats are honest.

### Acceptance Criteria

1. Every `SessionSummary` (including `isBrowser` and `reelCount`) is persisted to Room on session end.
2. Stats screen shows **Today**: total time, total reels, session count. Browser sessions contribute time and session count, not Reels.
3. Stats screen shows **Last 7 days** list (date, time, reels).
4. No network call exists; `INTERNET` permission absent from manifest.
5. Database is `exportSchema = true`; schema JSON committed to VCS.
6. Stats screen renders in < 300 ms for 10 000 rows.
7. Room schema includes `is_browser` column from version 1 (new column integrated pre-release; no migration needed).
8. Aggregates include both social and browser sessions in time and session counts; Reel sums treat browser sessions as `0`.
9. Stats are read-only from the user’s perspective — no editing, no exporting.
10. Uninstalling the app removes all data (no backup provider, no export, no sync).
11. Reveal delay, message selection, message duration are **not** part of stats; they live in DataStore (VS-01).
12. Stats screen is reachable from Settings (`[ View Stats ]`) and from any future home surface; no deep-link requirement.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Zero sessions | “No awareness data yet” empty state. |
| E2 | Day boundary while session active | Session bucketed by `startWallClock` date; no split. |
| E3 | Timezone change | Aggregation uses stored `startWallClock` (UTC millis) + device zone at query time. |
| E4 | DB corruption | Room `fallbackToDestructiveMigration` **disabled**; corruption triggers `RoomDatabase.Callback.onDestructiveMigration` → user notification to reset stats. |
| E5 | Browser-only day | Today shows time and sessions, `Reels: 0`; no error. |
| E6 | Reveal delay longer than session | Session still persisted with `durationMillis` and `reelCount = 0`. |
| E7 | All-selected-messages-change mid-session | No impact — stats are session-scoped, not message-scoped. |
| E8 | Long sessions (>1 h) | `durationMillis` stored as `Long`; formatting handled in UI (`H:MM`). |

### UI/UX Specification — Stats Screen

```
┌─────────────────────────────────────┐
│  ← Awareness Stats                  │
│  ─────────────────────────────────  │
│  Today                              │
│  ┌───────────────────────────────┐  │
│  │  Total Time    1h 23m         │  │
│  │  Total Reels   47             │  │
│  │  Sessions      5              │  │
│  └───────────────────────────────┘  │
│                                     │
│  Last 7 Days                        │
│  ┌───────────────────────────────┐  │
│  │ Mon   ████        1h 23m      │  │
│  │ Tue   ██████      2h 05m      │  │
│  │ Wed   ██          0h 45m      │  │
│  │ Thu   ███         1h 10m      │  │
│  │ Fri   █████       1h 55m      │  │
│  │ Sat   █           0h 20m      │  │
│  │ Sun   ██          0h 50m      │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

- Bars are proportional to max day in 7-day window.
- Empty state: centered text `"No awareness data yet — open a monitored app to begin."`
- No animation. No export button. No “clear data” button on this screen (OS-level “Clear storage” is the reset path).
- Browser sessions are visually indistinguishable from social sessions at the day level (this is by design — the daily summary is about total consumption, not per-app).

### Database Schema

```sql
-- awareness.db, version 1
CREATE TABLE sessions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name     TEXT    NOT NULL,
    start_wall_clock INTEGER NOT NULL,   -- epoch millis UTC
    end_wall_clock   INTEGER NOT NULL,
    duration_millis  INTEGER NOT NULL,
    reel_count       INTEGER NOT NULL DEFAULT 0,
    is_browser       INTEGER NOT NULL DEFAULT 0   -- 0 = social, 1 = browser
);
CREATE INDEX idx_sessions_start ON sessions(start_wall_clock);
CREATE INDEX idx_sessions_browser ON sessions(is_browser);
```

Room entities: `SessionEntity`, DAO `SessionDao`, converters for `Instant` / `LocalDate`.

Aggregation query (DAO):

```sql
SELECT
  date(start_wall_clock / 1000, 'unixepoch', 'localtime') AS day,
  SUM(duration_millis) AS totalMillis,
  SUM(reel_count)      AS totalReels,   -- browser rows contribute 0 by construction
  COUNT(*)             AS sessionCount
FROM sessions
WHERE start_wall_clock >= :since
GROUP BY day
ORDER BY day DESC;
```

Optional second query for future per-app views (not exposed in UI yet):

```sql
SELECT package_name, is_browser,
       SUM(duration_millis) AS totalMillis,
       SUM(reel_count)      AS totalReels
FROM sessions
WHERE start_wall_clock >= :since
GROUP BY package_name, is_browser
ORDER BY totalMillis DESC;
```

### API Contracts

```kotlin
// core/domain/repository/SessionRepository.kt
interface SessionRepository {
    suspend fun save(summary: SessionSummary)
    fun observeDailyAggregates(since: Instant): Flow<List<DailyAggregate>>
}

// core/domain/model/DailyAggregate.kt
data class DailyAggregate(
    val date: LocalDate,
    val totalMillis: Long,
    val totalReels: Int,
    val sessionCount: Int
)

// core/domain/usecase/GetDailyStatsUseCase.kt
interface GetDailyStatsUseCase {
    operator fun invoke(days: Int = 7): Flow<List<DailyAggregate>>
}
```

Mapping note: `SessionSummary.reelCount` for browser sessions is `0` (guaranteed by VS-06 AC 11), so `SUM(reel_count)` needs no special handling. `is_browser` is stored for future per-app views and auditability but is not surfaced in the current Stats screen.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS08‑TS‑01 | Persist session | Save `SessionSummary(duration=5s, reelCount=3, isBrowser=false)` | Row present; fields match |
| VS08‑TS‑02 | Persist browser session | Save `SessionSummary(..., isBrowser=true, reelCount=0)` | Row present; `is_browser=1` |
| VS08‑TS‑03 | Empty state | Open Stats with 0 rows | “No awareness data yet…” shown |
| VS08‑TS‑04 | Daily aggregation | Insert 5 sessions across 2 days | Totals correct per day |
| VS08‑TS‑05 | Browser contributes time only | Insert 1 social (3 reels) + 1 browser (0 reels) | Today totals = 2 sessions, 3 reels |
| VS08‑TS‑06 | No network | `aapt dump permissions` | No `INTERNET` |
| VS08‑TS‑07 | Schema export | Inspect `schemas/awareness.db/1.json` | Contains `is_browser` column |
| VS08‑TS‑08 | Large query perf | Insert 10 000 rows across 30 days, open Stats | Renders < 300 ms |
| VS08‑TS‑09 | Zero-reel day | Day with only browser sessions | Today `Reels: 0`, no error |
| VS08‑TS‑10 | Day boundary | Session starts 23:59, ends 00:05 next day | Bucketed to start day |

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `SessionSummaryMapperTest` — domain ↔ entity, `isBrowser` round-trip. `DailyAggregateMapperTest`. `GetDailyStatsUseCaseTest` (Turbine, fake DAO). |
| **Integration** | `SessionDaoTest` (Room in-memory): insert mixed social/browser sessions across 3 days → assert aggregation and `is_browser` storage. |
| **API** | `SessionRepositoryContractTest` — `save` then `observe` emits correct totals; `save` for browser session does not corrupt Reel sums. |
| **E2E Manual** | Open Instagram 2 min, swipe 3 Reels → leave. Open Chrome 1 min → leave. Stats shows Today: 3 min, 3 Reels, 2 sessions. Kill app → relaunch → data persists. |

### File & Folder Mapping

```
:core:data       database/AwarenessDatabase.kt
                 database/SessionEntity.kt
                 database/SessionDao.kt
                 database/Converters.kt
                 repository/SessionRepositoryImpl.kt
:core:domain     repository/SessionRepository.kt
                 usecase/GetDailyStatsUseCase.kt
                 model/DailyAggregate.kt
:feature:stats   StatsScreen.kt
                 StatsViewModel.kt
                 di/StatsModule.kt
:app             schemas/awareness.db/1.json   (committed to VCS)
```

---

# VS-09 — Battery Optimization & Survivability

**Dependencies:** VS-02 … VS-08  
**FRD Coverage:** FR-08, NFR-01, NFR-02, Edge Cases §8

### User / Actor Story
> **As a user**, I want the app to keep running reliably all day despite Doze, OEM killers, and system restrictions, **so that** my awareness prompts are always there when I open Instagram or Chrome, and **the reveal delay and message settings I chose are honored from the very first session of the day**, without adding any battery cost from the new configuration features.

### Acceptance Criteria

1. Settings screen requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent.
2. OEM Guidance screen provides device-specific steps for **Xiaomi (MIUI), Huawei (EMUI), OnePlus (OxygenOS), Samsung (One UI), Pixel (stock)**.
3. App survives **8 hours screen-off** with battery optimization disabled (verified via instrumented soak test + `dumpsys batterystats`).
4. `onTimeout` implemented on `HudForegroundService` (graceful stop + notification).
5. `startForeground()` all call sites wrapped in `try/catch (ForegroundServiceStartNotAllowedException)`.
6. `AccessibilityService` disabled detection: `PermissionRequestCoordinator` surfaces a persistent notification.
7. RAM baseline < 30 MB after 1 hour of active use (`dumpsys meminfo`).
8. No `UsageStatsManager` or `Handler.postDelayed` polling loops in the entire codebase.
9. **No new battery cost is introduced by HUD Personalization**: reveal delay, message rotation, and message selection must not add:
   - no periodic alarm (`AlarmManager`),
   - no additional `CoroutineWorker`,
   - no polling of `HudPreferencesRepository` (single `Flow` collection inside `HudViewModel`),
   - no extra foreground service.
10. Reveal delay is honored **exactly once per session** via `revealDelayMillisSnapshot`; message rotation uses a single monotonic timer ticker already running for the HUD (VS-05). No separate scheduler is added.
11. If the accessibility service or HUD service is killed and restarted, `HudPreferencesRepository` state survives (DataStore), and reveal delay snapshot semantics are re-applied on the next session.
12. `BatteryOptimizationCard` reflects current state from `PowerManager.isIgnoringBatteryOptimizations(packageName)` on each `onResume` (no polling).
13. The persisted HUD preferences (VS-01) are small (`Int`, `Int`, `Set<String>`), bounded, and written only on user Save — no write amplification.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | OEM force-stops app after swipe from Recents | Companion UI guides user to lock app in Recents. No self-resurrection (Play policy). |
| E2 | Battery Saver defers usage events | HUD shows “Service may be paused by system” when `PowerManager.isPowerSaveMode` and no accessibility events > 30 s. |
| E3 | Accessibility service turned off by system | Notification + in-app banner. |
| E4 | `startForeground` refused on API 31+ | Caught; `stopSelf()`; notification explains background restriction. |
| E5 | Doze kills FGS despite exemption | `specialUse` type + exemption should prevent; if killed, no `AlarmManager` restart is used (Play policy); user is notified on next launch. |
| E6 | Reveal delay chosen as 3600 s (max) | No scheduler added; the same once-per-second timer tick already checks `elapsed >= snapshot`; no per-second wakeups added beyond HUD tick. |
| E7 | User changes HUD preferences repeatedly | Only DataStore writes on Save; no data-flow side effects; single collector in `HudViewModel`. |
| E8 | Doze kicks in before reveal delay elapses | Session ends when screen state changes (per VS-03 E2), so reveal-delay countdown stops with the session — Doze does not corrupt the countdown. |
| E9 | DataStore latency on cold start | Reveal delay defaults to `0` for the *first* session if the repository is not warm — VS-03 E6 covers read failure. Once warm, values are stable. |

### UI/UX Specification — OEM Guidance Screen

```
┌─────────────────────────────────────┐
│  ← OEM Setup Guide                  │
│  ─────────────────────────────────  │
│  Select your device:                │
│  [ Samsung ] [ Xiaomi ] [ OnePlus ] │
│  [ Huawei  ] [ Pixel  ] [ Other   ] │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Xiaomi / MIUI                 │  │
│  │ 1. Settings → Apps → Manage   │  │
│  │    apps → Awareness App       │  │
│  │ 2. Enable “Autostart”         │  │
│  │ 3. Battery saver →            │  │
│  │    “No restrictions”          │  │
│  │ 4. Lock app in Recents        │  │
│  │ 5. Settings → Battery →       │  │
│  │    disable “Battery saver”    │  │
│  └───────────────────────────────┘  │
│                                     │
│  [ Open Battery Settings ]          │
└─────────────────────────────────────┘
```

No changes from v1.0. The companion UI already covers all OEM guidance.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS09‑TS‑01 | Battery exemption intent | Tap card | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` launched with package URI |
| VS09‑TS‑02 | OEM guidance | Select Xiaomi | MIUI steps shown |
| VS09‑TS‑03 | 8-hour soak | Disable battery opt, screen off 8 h | Service alive on wake, HUD works |
| VS09‑TS‑04 | `onTimeout` | Simulate timeout | Service stops gracefully |
| VS09‑TS‑05 | FGS refused | Mock `startForeground` throw | Caught, no crash |
| VS09‑TS‑06 | RAM < 30 MB | 1 h active use | `dumpsys meminfo` < 30 MB |
| VS09‑TS‑07 | No polling | `grep -rn "postDelayed\|UsageStatsManager"` | 0 results |
| VS09‑TS‑08 | Accessibility disabled | Disable in Settings → resume | Persistent notification shown |
| VS09‑TS‑09 | No new battery cost from HUD prefs | `grep -rn "AlarmManager\|setRepeating\|Handler(Looper"` | 0 new occurrences vs v1.0 baseline |
| VS09‑TS‑10 | Reveal delay survives restart | Set delay 180 s; kill service; open Instagram | HUD reveals at 180 s after session start; no early reveal |
| VS09‑TS‑11 | Reveal delay max 3600 s | Set delay to 3600 s; open Instagram | HUD never reveals; no extra wakeups observed in `dumpsys batterystats` |
| VS09‑TS‑12 | Doze during reveal countdown | Start session, delay 5 min, screen-off mid-countdown | Session ends; countdown paused/discarded; next session uses fresh snapshot |

### Database Schema

None new. Uses existing `preferences_pb` keys from VS-01.

### API Contracts

```kotlin
// core/presentation/permission/PermissionRequestCoordinator.kt
interface PermissionRequestCoordinator {
    fun requestAccessibility(context: Activity)
    fun requestOverlay(context: Activity)
    fun requestBatteryOptimization(context: Activity)
    fun requestNotifications(context: Activity)
    fun isAccessibilityEnabled(context: Context): Boolean
    fun showAccessibilityDisabledNotification(context: Context)
}

// core/overlay/HudWindowManager.kt — failure contract
sealed interface HudAddResult {
    data object Success : HudAddResult
    data class Failure(val reason: FailureReason) : HudAddResult
}
enum class FailureReason { NO_OVERLAY_PERMISSION, FGS_REFUSED, UNKNOWN }
```

**No new public contracts.** VS-09 hardens existing ones. The reveal-delay timer relies on the once-per-second tick already owned by VS-05; VS-09 verifies (via `dumpsys batterystats` and grep) that no additional scheduler was introduced.

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `OemGuidanceProviderTest` — device → steps mapping. `BatteryOptimizationIntentFactoryTest`. |
| **Integration** | `PermissionRequestCoordinatorTest` (Robolectric) — intent URIs. `HudForegroundServiceTimeoutTest` — `onTimeout` path. `RevealDelayRestartTest` — service kill/restart preserves DataStore-backed delay. |
| **API** | `HudWindowManagerFailureContractTest` — returns `Failure(NO_OVERLAY_PERMISSION)` when permission absent. |
| **E2E Manual** | On Xiaomi: follow guide → 8 h soak → HUD appears on next Instagram open with the correct reveal delay. On Pixel: exemption alone works. Additional E2E: set delay 3 min, use app, verify no HUD for first 3 min after opening Instagram, then HUD reveals. Grep source for `AlarmManager` / `postDelayed` / `UsageStatsManager` returns 0 results. |

### File & Folder Mapping

```
:core:presentation  permission/PermissionRequestCoordinator.kt (full)
                    permission/PermissionState.kt
:feature:settings   oem/OemGuidanceProvider.kt
                    oem/OemGuidanceScreen.kt
                    permission/BatteryOptimizationCard.kt
:core:overlay       HudWindowManager.kt (failure contract)
                    HudLayoutParamsFactory.kt
:feature:hud        HudForegroundService.kt (onTimeout, try/catch)
:app                res/values/strings.xml (notifications)
                    res/drawable/ic_notification.xml
```

---

## Contract Deltas Introduced by VS-07–VS-09

| Contract | Change | Consumers | Backward Compatible? |
|---|---|---|---|
| `AwarenessEvent` | + `BrowserDomainInferred(packageName, domain, timestamp)` | VS-07, VS-03 | Yes (sealed — exhaustive `when` in existing mappers must add a branch; VS-03 owns this) |
| `SessionState.Active` | + `inferredDomain: String? = null` | VS-03, VS-05, VS-07 | Yes (default) |
| `HudState` | + `browserDomainLabel: String? = null` | VS-04, VS-05, VS-07 | Yes (default) |
| `SessionEntity` / `sessions` table | + `is_browser INTEGER NOT NULL DEFAULT 0` | VS-08 | Yes (fresh schema, v1) |
| `ObserveHudStateUseCase` mapping | + browser-domain branch | VS-05 | Internal only |

## Updated Test Matrix Summary

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

## Bottom Line

- **VS-07** reintroduces browser support but with three constraints from the config work: reveal delay honored, message rotation preserved, Reel line hidden. Domain inference is best-effort and additive (`inferredDomain` / `browserDomainLabel`, both with `null` defaults — zero impact on non-browser code).
- **VS-08** persists the full `SessionSummary` including `isBrowser`. Browser rows contribute time and session count only; `reelCount` is guaranteed zero by VS-06.
- **VS-09** hardens survivability without adding any scheduler for the new config. The reveal delay rides the existing once-per-second tick from VS-05; VS-09 includes grep and `dumpsys batterystats` assertions to prove no new polling or alarms were introduced.