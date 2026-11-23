Below are **VS-01, VS-02, and VS-03 rewritten from scratch**, incorporating the new HUD personalization requirements:

- configurable **reveal delay**
- configurable **message display duration**
- configurable **message selection**
- local-only persistence
- session-time snapshot semantics so mid-session config changes do not affect the current session

---

# VS-01 — Companion Shell, Permissions, HUD Preferences & Domain Foundation

**Dependencies:** None  
**FRD Coverage:** FR-06 (manifest declarations), FR-08 (permission request UI), Permissions Matrix, §6.3 (companion UI shell), **FR-05.1–05.3 (HUD personalization configuration, validation, local persistence)**, NFR-04 (privacy — local only)

### User / Actor Story
> **As a first-time or returning user**, I want to see a clear setup checklist for required permissions **and** a HUD Personalization screen where I can choose when the HUD appears, which messages it rotates, and how long each message stays visible, **so that** the awareness tool matches my intent without interrupting me immediately or showing messages I do not want.

### Acceptance Criteria

1. The Settings screen lists exactly four permission cards: Accessibility, Overlay, Battery Optimization, Notifications (Android 13+).
2. Tapping **Enable in Settings** on the Accessibility card opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
3. Tapping **Grant Permission** on the Overlay card opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with the app package URI.
4. Each permission card reflects live state (`granted` / `not granted`) on `onResume`.
5. On Android < 13, the Notifications card is hidden.
6. The app does **not** request `INTERNET` permission (manifest assertion).
7. The Settings screen includes a **HUD Personalization** entry showing a summary: reveal delay, number of selected messages, and message duration.
8. The HUD Personalization screen allows the user to set **Reveal HUD after** `N` seconds/minutes. Valid range: `0–3600` seconds. Default: `0`.
9. The HUD Personalization screen allows the user to set **Each message appears for** `N` seconds. Valid range: `5–300` seconds. Default: `30`.
10. The user can select which mindfulness messages are eligible to show. At least one message must remain selected. If the user deselects all, **Save** is disabled or blocked with an inline error.
11. Preferences persist across process death and app relaunch via DataStore.
12. Invalid values are clamped at the domain boundary: reveal delay `coerceIn(0, 3600)`, message duration `coerceIn(5, 300)`.
13. If persisted selected-message IDs are empty or corrupt, the repository returns the default selected messages; the app does not crash.
14. Configuration is local-only. No network permission or sync is introduced.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | User returns from Settings without granting | Card re-reads state via `AccessibilityManager.getEnabledAccessibilityServiceList()`; stays “not granted”. |
| E2 | Overlay permission dialog unavailable on certain OEMs | Fallback intent to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` without URI. |
| E3 | Accessibility service disabled by system after being enabled | `PermissionRequestCoordinator` polls only on `onResume` (no background loop) and shows banner. |
| E4 | Battery optimization exemption denied | Card shows “optional, recommended” state; app still functions. |
| E5 | User enters reveal delay `-5` or `99999` | Clamped to `0` or `3600`; UI shows clamped value after save. |
| E6 | User enters message duration `1` or `999` | Clamped to `5` or `300`; UI shows clamped value after save. |
| E7 | User deselects all messages and taps Save | Save blocked; inline error: “Select at least one message.” |
| E8 | Persisted selected-message IDs reference unknown IDs | Repository filters to known IDs; if result empty, falls back to defaults. |
| E9 | DataStore corrupted / first run | Defaults used: delay `0`, duration `30`, default selected messages. |

### UI/UX Specification — Settings Screen

```
┌─────────────────────────────────────┐
│  Awareness App                      │
│  ─────────────────────────────────  │
│  Setup Checklist                    │
│  ┌───────────────────────────────┐  │
│  │ ⚠ Accessibility Service       │  │
│  │   Required for app detection  │  │
│  │   [ Enable in Settings    ▸ ] │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ ⚠ Overlay Permission          │  │
│  │   Required to show the HUD    │  │
│  │   [ Grant Permission      ▸ ] │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ ○ Battery Optimization        │  │
│  │   Recommended for reliability │  │
│  │   [ Exempt from Doze      ▸ ] │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ ⚠ Notifications (13+)         │  │
│  │   Required for foreground svc │  │
│  │   [ Allow Notifications   ▸ ] │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ HUD Personalization           │  │
│  │ Reveal after 3 min            │  │
│  │ Messages: 3 selected          │  │
│  │ Each message: 30 sec          │  │
│  │ [ Configure HUD           ▸ ] │  │
│  └───────────────────────────────┘  │
│                                     │
│  [ OEM Setup Guide ]  [ View Stats ]│
└─────────────────────────────────────┘
```

**States:** `granted` = ✓ green border, `required` = ⚠ amber, `optional` = ○ grey. **No animation** (battery). Compose `LazyColumn` on Settings screen is acceptable (foreground UI); HUD overlay remains minimal.

### UI/UX Specification — HUD Personalization Screen

```
┌─────────────────────────────────────┐
│  ← HUD Personalization              │
│  ─────────────────────────────────  │
│  Reveal HUD after                   │
│  [ 3 ] min   (0–60 min)             │
│                                     │
│  Each message appears for           │
│  [ 30 ] sec  (5–300 sec)            │
│                                     │
│  Messages                           │
│  [x] Is this serving you?           │
│  [x] You chose this.                │
│  [x] Just five more?                │
│  [ ] Notice how you feel.           │
│  [ ] Does this match your intent?   │
│  ...                                │
│                                     │
│  At least one message must be on.   │
│                                     │
│  [ Save ]                           │
└─────────────────────────────────────┘
```

- No animation.
- Save disabled when zero messages selected.
- Inline validation messages for out-of-range values.
- Compose `LazyColumn` acceptable here (foreground UI).

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS01‑TS‑01 | Accessibility card opens correct settings | Tap card → inspect launched intent | `ACTION_ACCESSIBILITY_SETTINGS` launched |
| VS01‑TS‑02 | Overlay card passes package URI | Tap card | Intent data = `package:com.awareness.app` |
| VS01‑TS‑03 | Card state refresh on resume | Grant permission externally → resume app | Card flips to ✓ |
| VS01‑TS‑04 | Notification card hidden on API 26–32 | Run on API 30 emulator | Card absent |
| VS01‑TS‑05 | Manifest has no INTERNET | `aapt dump permissions` | No `android.permission.INTERNET` |
| VS01‑TS‑06 | Set reveal delay 180 s, relaunch | Configure 3 min → kill app → relaunch | Settings summary shows `3 min`; DataStore persists |
| VS01‑TS‑07 | Set message duration 45 s, relaunch | Configure 45 sec → relaunch | Persisted; HUD uses 45 s |
| VS01‑TS‑08 | Deselect all messages and Save | Uncheck all → tap Save | Save blocked with inline error |
| VS01‑TS‑09 | Submit reveal delay `-5` / `99999` | Enter invalid values → save | Clamped to `0` / `3600` |
| VS01‑TS‑10 | Submit duration `1` / `999` | Enter invalid values → save | Clamped to `5` / `300` |
| VS01‑TS‑11 | Corrupt selected IDs fallback | Persist unknown IDs → read prefs | Default selected messages returned |
| VS01‑TS‑12 | First run defaults | Fresh install → open HUD Personalization | Delay `0`, duration `30`, default messages selected |

### Database Schema

No Room tables in this slice. `DataStore` keys introduced:

```text
preferences_pb
  onboarding_completed : Boolean = false

  hud_initial_reveal_delay_seconds : Int = 0          // clamped 0..3600
  hud_message_display_duration_seconds : Int = 30     // clamped 5..300
  hud_selected_message_ids : Set<String> = DefaultMindfulnessMessages.defaultSelectedIds
```

No network, no export, no sync.

### API Contracts (internal)

```kotlin
// core/domain/repository/SettingsRepository.kt
interface SettingsRepository {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(value: Boolean)
}

// core/domain/model/MindfulnessMessage.kt
data class MindfulnessMessage(
    val id: String,
    val text: String
)

// core/domain/model/DefaultMindfulnessMessages.kt
object DefaultMindfulnessMessages {
    val all: List<MindfulnessMessage> = listOf(
        MindfulnessMessage("serving", "Is this serving you?"),
        MindfulnessMessage("chose", "You chose this."),
        MindfulnessMessage("five_more", "Just five more?"),
        MindfulnessMessage("notice", "Notice how you feel."),
        MindfulnessMessage("intent", "Does this match your intent?")
        // ...
    )

    val defaultSelectedIds: Set<String> =
        setOf("serving", "chose", "five_more")

    val fallback: List<MindfulnessMessage> =
        all.filter { it.id in defaultSelectedIds }
}

// core/domain/model/HudPreferences.kt
data class HudPreferences(
    val initialRevealDelaySeconds: Int = 0,
    val messageDisplayDurationSeconds: Int = 30,
    val selectedMessageIds: Set<String> = DefaultMindfulnessMessages.defaultSelectedIds
) {
    val selectedMessages: List<MindfulnessMessage>
        get() = DefaultMindfulnessMessages.all
            .filter { it.id in selectedMessageIds }
            .ifEmpty { DefaultMindfulnessMessages.fallback }
}

// core/domain/repository/HudPreferencesRepository.kt
interface HudPreferencesRepository {
    val preferences: Flow<HudPreferences>
    suspend fun setInitialRevealDelaySeconds(seconds: Int)
    suspend fun setMessageDisplayDurationSeconds(seconds: Int)
    suspend fun setSelectedMessageIds(ids: Set<String>)
}

// core/presentation/permission/PermissionState.kt
data class PermissionState(
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val notificationsGranted: Boolean // true on < API 33
)
```

**Validation rules** are enforced in the repository implementation and use case layer, not only in UI:

```text
initialRevealDelaySeconds -> coerceIn(0, 3600)
messageDisplayDurationSeconds -> coerceIn(5, 300)
selectedMessageIds -> reject empty in UI; repository reads fallback if empty/corrupt
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `PermissionStateTest` — state mapping for API 26/30/33. `SettingsRepositoryTest` — DataStore round-trip (Turbine). `HudPreferencesTest` — clamping, fallback selection, `selectedMessages` resolution. `DefaultMindfulnessMessagesTest` — non-empty, unique IDs. |
| **Integration** | `PermissionRequestCoordinatorTest` — Robolectric: intent construction for each card. `HudPreferencesDataStoreTest` — persistence and clamping via repository. |
| **API** | `SettingsRepositoryContractTest` — `Flow` emits initial + updated values. `HudPreferencesRepositoryContractTest` — initial defaults, update emissions, empty-ID fallback. |
| **E2E Manual** | Fresh install → Settings shows 4 permission cards + HUD Personalization → configure 3 min delay, 45 sec duration, 2 messages → kill + relaunch → all values persisted. |

### File & Folder Mapping

```
:core:domain        model/TargetApp.kt
                    model/AwarenessEvent.kt
                    model/HudState.kt
                    model/MindfulnessMessage.kt
                    model/DefaultMindfulnessMessages.kt
                    model/HudPreferences.kt
                    repository/SettingsRepository.kt
                    repository/HudPreferencesRepository.kt
:core:data          datastore/SettingsDataStore.kt
                    datastore/HudPreferencesDataStore.kt
                    repository/SettingsRepositoryImpl.kt
                    repository/HudPreferencesRepositoryImpl.kt
:core:presentation  permission/PermissionState.kt
                    permission/PermissionRequestCoordinator.kt
:feature:settings   SettingsActivity.kt
                    SettingsScreen.kt
                    SettingsViewModel.kt
                    permission/AccessibilityPermissionCard.kt
                    permission/OverlayPermissionCard.kt
                    permission/BatteryOptimizationCard.kt
                    permission/NotificationPermissionCard.kt
                    hud/HudPreferencesScreen.kt
                    hud/MessageSelectionScreen.kt
                    hud/HudPreferencesViewModel.kt
                    di/SettingsModule.kt
:app                AppApplication.kt
                    MainActivity.kt
                    di/AppModule.kt
AndroidManifest.xml BIND_ACCESSIBILITY_SERVICE
                    SYSTEM_ALERT_WINDOW
                    FOREGROUND_SERVICE
                    FOREGROUND_SERVICE_SPECIAL_USE
                    REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    POST_NOTIFICATIONS
```

---

# VS-02 — Target App Foreground Detection

**Dependencies:** VS-01  
**FRD Coverage:** FR-01, NFR-01 (package filtering), NFR-03 (≤ 200 ms)

### User / Actor Story
> **As the Awareness system**, I need to know the instant a monitored app (Instagram, Facebook, Snapchat, browser) comes to the foreground, **so that** I can start a session within 200 ms without polling, and later decide whether HUD content should be revealed based on user configuration.

### Acceptance Criteria

1. `AwarenessAccessibilityService` receives `TYPE_WINDOW_STATE_CHANGED` for allowlisted packages only.
2. Allowlist is declared in `accessibility_service_config.xml` (`android:packageNames`), generated from `TargetAppAllowlist`, not filtered in code alone.
3. On allowlisted foreground event, a `SessionStarted(packageName, timestamp, isBrowser)` is emitted on `AccessibilityEventBus`.
4. On transition to a non-allowlisted package, a `SessionEnded` is emitted.
5. Detection → emission latency ≤ 200 ms (measured in instrumentation test).
6. No `UsageStatsManager` polling exists anywhere in the codebase.
7. `isBrowser` is `true` iff `packageName` belongs to `TargetAppAllowlist.browsers`.
8. Detection behavior is independent of HUD personalization settings. The service does not read HUD preferences.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Rapid app switching (Instagram ↔ Home ↔ Instagram) | Events are timestamped; orchestrator coalesces duplicates within 150 ms. |
| E2 | Same package re-emits `TYPE_WINDOW_STATE_CHANGED` for internal Activity change | Orchestrator ignores if package unchanged and session active. |
| E3 | Service killed by system | System restarts `AccessibilityService` automatically; `onServiceConnected` re-emits current state. |
| E4 | `packageNames` XML filter misses a browser variant | Allowlist is a single source of truth in `TargetAppAllowlist.kt` **and** generated into XML via build-logic resource merging. |
| E5 | Browser package launched | `SessionStarted` emitted with `isBrowser = true`; HUD behavior is decided downstream by VS-05/VS-07. |
| E6 | Unknown package launched | No event emitted. |

### UI/UX Specification

No user-facing UI. **Dev-only**: `Logcat` tag `AwarenessAccessibility` logs every detected foreground event with package name, `isBrowser`, and timestamp (stripped in release via `BuildConfig.DEBUG`).

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS02‑TS‑01 | Instagram detected | Launch Instagram with service enabled | `SessionStarted("com.instagram.android", isBrowser=false)` emitted |
| VS02‑TS‑02 | Non-allowlisted ignored | Launch Settings | No event emitted |
| VS02‑TS‑03 | Latency | Instrument `onAccessibilityEvent` → `SharedFlow` emission | ≤ 200 ms |
| VS02‑TS‑04 | Package filter in XML | Inspect merged manifest XML | `packageNames` contains all 7 packages |
| VS02‑TS‑05 | No polling | `grep -r UsageStatsManager` | 0 results |
| VS02‑TS‑06 | Browser flag | Launch Chrome | `SessionStarted("com.android.chrome", isBrowser=true)` emitted |
| VS02‑TS‑07 | Social flag | Launch Snapchat | `SessionStarted("com.snapchat.android", isBrowser=false)` emitted |

### Database Schema

None.

### API Contracts

```kotlin
// core/accessibility/AccessibilityEventBus.kt
interface AccessibilityEventBus {
    val events: SharedFlow<AwarenessEvent>
    fun tryEmit(event: AwarenessEvent): Boolean
}

// core/domain/model/AwarenessEvent.kt
sealed interface AwarenessEvent {
    data class SessionStarted(
        val packageName: String,
        val timestamp: Long,
        val isBrowser: Boolean
    ) : AwarenessEvent

    data class SessionEnded(
        val timestamp: Long
    ) : AwarenessEvent

    data class ReelScrolled(
        val timestamp: Long
    ) : AwarenessEvent
}

// core/accessibility/TargetAppAllowlist.kt
object TargetAppAllowlist {
    val social: Set<String> = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.lite",
        "com.snapchat.android"
    )

    val browsers: Set<String> = setOf(
        "com.android.chrome",
        "org.mozilla.firefox"
    )

    val all: Set<String> get() = social + browsers
}
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `TargetAppAllowlistTest` — membership, disjoint sets, `all` correctness. `SessionStartedFactoryTest` — timestamp monotonicity and `isBrowser` derivation. |
| **Integration** | `AwarenessAccessibilityServiceTest` (Robolectric) — dispatch fake `AccessibilityEvent`, assert emission with correct `isBrowser`. |
| **API** | `AccessibilityEventBusContractTest` — `SharedFlow` buffer/backpressure (`DROP_OLDEST`, capacity 16). |
| **E2E Manual** | Enable service → open Instagram → `adb logcat` shows detection < 1 s. Open Chrome → log shows `isBrowser=true`. |

### File & Folder Mapping

```
:core:accessibility  AwarenessAccessibilityService.kt
                     AccessibilityEventBus.kt
                     TargetAppAllowlist.kt
:core:domain         model/AwarenessEvent.kt (SessionStarted/SessionEnded/ReelScrolled)
:app                 res/xml/accessibility_service_config.xml
:build-logic         AllowlistXmlGenerator.kt (generates packageNames from code)
```

---

# VS-03 — Session Lifecycle & Config Snapshot

**Dependencies:** VS-02, VS-01 (`HudPreferencesRepository` contract)  
**FRD Coverage:** FR-02, NFR-03, **FR-05.1 (snapshot of reveal delay at session start)**

### User / Actor Story
> **As a user**, when I open Instagram a session starts timing me, and when I leave the app the timer stops, **so that** my time is measured accurately per visit. I also expect that if I change my HUD reveal delay while a session is already active, the current session keeps the delay that was active when it started, and the new delay applies from my next session.

### Acceptance Criteria

1. `SessionOrchestrator` transitions `Idle → Active(packageName, startElapsed, wallClockStart, isBrowser, revealDelayMillisSnapshot, reelCount = 0)` on `SessionStarted`.
2. On `Idle → Active`, the orchestrator snapshots the current `HudPreferences.initialRevealDelaySeconds` from `HudPreferencesRepository` into `Active.revealDelayMillisSnapshot`.
3. Transitions `Active → Idle` on `SessionEnded`; emits final `SessionSummary` to `SessionRepository`.
4. A session is **not** restarted if `SessionStarted` fires for the same package while active.
5. Timer elapsed is monotonic and survives wall-clock changes (uses `SystemClock.elapsedRealtime()`).
6. `StateFlow<SessionState>` emits within 50 ms of event.
7. `ReelScrolled` events increment `Active.reelCount` regardless of whether HUD content is currently visible. Pre-reveal scrolls are retained and will be shown when HUD reveals.
8. Changing reveal delay mid-session does **not** affect `Active.revealDelayMillisSnapshot`. The new value applies to the next session.
9. `SessionSummary` includes `packageName`, `startWallClock`, `endWallClock`, `durationMillis`, `reelCount`, and `isBrowser`.
10. If `HudPreferencesRepository` read fails at session start, the orchestrator uses `0` delay and logs a non-fatal warning; session still starts.
11. Process death mid-session: on restart, a recovered session is closed with `endTime = startTime` (zero-duration) to avoid inflating stats.
12. `SessionOrchestrator` exposes `sessionState: StateFlow<SessionState>` and does not construct final HUD content. Final `HudState` is produced by VS-05 by combining `sessionState` with live HUD preferences.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Duplicate `SessionStarted` within 150 ms | Coalesced (de-dupe window). |
| E2 | User locks screen while Instagram foreground | `SessionEnded` fires on `TYPE_WINDOWS_CHANGED` focus loss; timer stops. |
| E3 | Process death mid-session | Session start timestamp persisted to `DataStore`; on restart, recovered session is closed with zero duration. |
| E4 | Clock skew (user changes time) | Uses `elapsedRealtime()`; wall-clock only for display/aggregation keys. |
| E5 | User changes reveal delay mid-session | Current session keeps `revealDelayMillisSnapshot`; next session uses new value. |
| E6 | HUD preferences read fails at session start | Use `0` delay; session starts normally; warning logged. |
| E7 | Reveal delay longer than session duration | HUD never reveals for that session; session still ends and persists with `reelCount`. |
| E8 | `ReelScrolled` before reveal | `reelCount` increments; value retained in `Active.reelCount` and final `SessionSummary`. |
| E9 | Browser session | `Active.isBrowser = true`; VS-05/VS-07 decide HUD content. |

### UI/UX Specification

No user-facing UI in this slice. `SessionState` is consumed by VS-05 to produce `HudState`.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS03‑TS‑01 | Start session | Emit `SessionStarted("com.instagram.android", t0, isBrowser=false)` | State = Active, `startElapsed = t0`, `reelCount = 0` |
| VS03‑TS‑02 | Duplicate start ignored | Emit `SessionStarted` twice within 100 ms | One Active state, no duplicate |
| VS03‑TS‑03 | End session | Emit `SessionEnded` after 5 s | State = Idle, `SessionSummary(duration=5s)` persisted |
| VS03‑TS‑04 | Package switch | Instagram → Home → Instagram | Two sessions, both persisted |
| VS03‑TS‑05 | Process death recovery | Kill process mid-session, relaunch | Zero-duration session persisted, no crash |
| VS03‑TS‑06 | Reveal delay snapshot | Set prefs delay = 180 s → start session → change prefs to 60 s mid-session | `Active.revealDelayMillisSnapshot = 180_000`; next session uses 60 s |
| VS03‑TS‑07 | Pre-reveal reel count | Start session with 180 s delay → emit 3 `ReelScrolled` before 180 s | `Active.reelCount = 3`; final summary has `reelCount = 3` |
| VS03‑TS‑08 | Prefs read failure | Mock `HudPreferencesRepository` throw on read | Session starts with `revealDelayMillisSnapshot = 0`; no crash |
| VS03‑TS‑09 | Browser flag propagated | Emit `SessionStarted(..., isBrowser=true)` | `Active.isBrowser = true`; summary has `isBrowser = true` |

### Database Schema

Introduced **contract** only (VS-08 implements Room):

```text
SessionSummary (domain model)
  packageName : String
  startWallClock : Long
  endWallClock : Long
  durationMillis : Long   // elapsedRealtime diff
  reelCount : Int         // filled by orchestrator; 0 until first qualifying scroll
  isBrowser : Boolean
```

No Room schema change in this slice. VS-08 persists `SessionSummary`.

### API Contracts

```kotlin
// core/domain/model/SessionState.kt
sealed interface SessionState {
    data object Idle : SessionState

    data class Active(
        val packageName: String,
        val startElapsed: Long,
        val wallClockStart: Long,
        val isBrowser: Boolean,
        val revealDelayMillisSnapshot: Long,
        val reelCount: Int
    ) : SessionState
}

// core/domain/model/SessionSummary.kt
data class SessionSummary(
    val packageName: String,
    val startWallClock: Long,
    val endWallClock: Long,
    val durationMillis: Long,
    val reelCount: Int,
    val isBrowser: Boolean
)

// core/domain/repository/SessionRepository.kt
interface SessionRepository {
    suspend fun save(summary: SessionSummary)
    fun observeDailyAggregates(since: Instant): Flow<List<DailyAggregate>>
}

// core/domain/SessionOrchestrator.kt
interface SessionOrchestrator {
    val sessionState: StateFlow<SessionState>
    fun start(scope: CoroutineScope)
}
```

**Snapshot behavior:**

```text
On SessionStarted:
  val prefs = runCatching { hudPreferencesRepository.preferences.first() }
      .getOrElse { HudPreferences() } // default delay 0
  Active(
    ...,
    revealDelayMillisSnapshot = prefs.initialRevealDelaySeconds * 1000L,
    reelCount = 0
  )
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `SessionOrchestratorTest` (JUnit5 + Turbine): start/duplicate/end/switch/recovery, reveal-delay snapshot, pre-reveal reel count, prefs-read failure, browser flag. 100% branch coverage. |
| **Integration** | `AccessibilityEventBus → SessionOrchestrator` end-to-end with fake clock and fake `HudPreferencesRepository`. |
| **API** | `SessionOrchestratorContractTest` — `StateFlow` emits initial `Idle`, then `Active` with snapshot fields, then `Idle`. `SessionRepositoryContractTest` — `save` receives summary with `reelCount` and `isBrowser`. |
| **E2E Manual** | Set reveal delay 3 min → open Instagram → swipe 3 Reels before 3 min → leave → check persisted summary has 3 Reels. Change delay to 1 min → open Instagram again → HUD reveals at 1 min. |

### File & Folder Mapping

```
:core:domain   SessionOrchestrator.kt
               model/SessionState.kt
               model/SessionSummary.kt
               repository/SessionRepository.kt
               repository/HudPreferencesRepository.kt (consumer)
:core:data     repository/SessionRepositoryImpl.kt (stub until VS-08)
:app:di        AccessibilityModule.kt (binds orchestrator, injects HudPreferencesRepository)
```

---

## Frozen Contracts Introduced by VS-01–VS-03

| Contract | File | Consumers |
|---|---|---|
| `AwarenessEvent` sealed hierarchy | `core/domain/model/AwarenessEvent.kt` | VS-02, VS-03, VS-06, VS-07 |
| `TargetAppAllowlist` | `core/accessibility/TargetAppAllowlist.kt` | VS-02, VS-07 |
| `AccessibilityEventBus` | `core/accessibility/AccessibilityEventBus.kt` | VS-02, VS-03 |
| `SessionState` | `core/domain/model/SessionState.kt` | VS-03, VS-05 |
| `SessionSummary` | `core/domain/model/SessionSummary.kt` | VS-03, VS-08 |
| `SessionRepository` | `core/domain/repository/SessionRepository.kt` | VS-03, VS-08 |
| `MindfulnessMessage` | `core/domain/model/MindfulnessMessage.kt` | VS-01, VS-05 |
| `HudPreferences` | `core/domain/model/HudPreferences.kt` | VS-01, VS-03, VS-05, VS-07 |
| `HudPreferencesRepository` | `core/domain/repository/HudPreferencesRepository.kt` | VS-01, VS-03, VS-05 |
| `SettingsRepository` | `core/domain/repository/SettingsRepository.kt` | VS-01, VS-09 |
| `PermissionState` | `core/presentation/permission/PermissionState.kt` | VS-01, VS-09 |