Below are **VS-04, VS-05, and VS-06 rewritten from scratch**, aligned with the revised VS-01–VS-03 contracts:

- `SessionState.Active` carries `revealDelayMillisSnapshot`, `reelCount`, and `isBrowser`.
- `HudPreferencesRepository` supplies live message selection and message-duration settings.
- Reveal delay is **snapshotted at session start**; message selection and display duration may apply live from the next rotation boundary.
- Reel counting remains active even while HUD content is hidden by the reveal delay.

---

# VS-04 — HUD Overlay Rendering & Visibility Shell

**Dependencies:** VS-01 (overlay permission, `HudState` contract)  
**Parallel With:** VS-02, VS-03  
**FRD Coverage:** FR-04, FR-06 (foreground service portion)

### User / Actor Story
> **As a user**, I want a small floating overlay on top of Instagram that shows awareness info **without blocking my taps or scrolls**, and I want it to stay visually absent until my configured reveal time, **so that** my experience is not degraded and the HUD does not appear earlier than I chose.

### Acceptance Criteria

1. `HudForegroundService` starts with `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`.
2. Overlay is added via `WindowManager.addView` with `TYPE_APPLICATION_OVERLAY`.
3. Layout params contain `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`.
4. An empty `ComposeView` (background + placeholder text) renders within 100 ms of service start.
5. Touch pass-through verified: taps on HUD area reach underlying app.
6. Service survives `onTimeout` (implements callback, stops gracefully).
7. `startForeground()` wrapped in `try/catch (ForegroundServiceStartNotAllowedException)`.
8. When `HudState.isVisible == false`, the overlay renders **no visible HUD content** (no text, no background card). It may keep an empty `ComposeView` attached or remove it; either is acceptable if touch pass-through and RAM targets hold.
9. When `HudState.isVisible` transitions from `false` to `true`, visible HUD content appears within **200 ms** of the `StateFlow` emission.
10. The service observes `HudState` through `HudViewModel`; it never queries the accessibility tree or reads `HudPreferences` directly.
11. The service does not decide reveal timing or message rotation. It is a passive renderer of `HudState`.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Overlay permission revoked while service running | `HudWindowManager.add()` checks `Settings.canDrawOverlays()`; returns failure → service stops overlay, keeps notification. |
| E2 | ComposeView crash on missing `ViewTreeLifecycleOwner` | `HudWindowManager` injects `ViewTreeLifecycleOwner`, `SavedStateRegistryOwner`, `ViewModelStoreOwner` before `addView`. |
| E3 | `startForeground` throws `ForegroundServiceStartNotAllowedException` | Caught; `stopSelf()`; posts user notification explaining background-start restriction. |
| E4 | `TYPE_APPLICATION_OVERLAY` denied on API 26 (permission missing) | Same as E1. |
| E5 | `HudState.isVisible = false` for entire session | Overlay remains invisible; no touch interception; session and reel counting continue downstream. |
| E6 | Rapid `isVisible` toggles at reveal boundary | Renderer applies latest `HudState`; Compose recomposition scoped to visibility/content changes. |
| E7 | Session ends while HUD visible | `HudState.isVisible` becomes `false`; overlay content disappears within 200 ms. |

### UI/UX Specification — HUD Hidden State (`isVisible = false`)

```
(Target app content only — no visible HUD content)
```

- No visible card, text, timer, or count.
- Touch passes through normally.
- Empty `ComposeView` may remain attached but must render nothing visible.

### UI/UX Specification — HUD Visible Shell (`isVisible = true`)

```
┌─────────────────────────────────────┐
│  (Target app content, dimmed)       │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Awareness                    │  │  ← semi-transparent card
│  │  ─────────                    │  │     rounded 12dp, 8dp padding
│  │  • • •                        │  │     no elevation shadow (battery)
│  └───────────────────────────────┘  │
│                                     │
│  (Target app content)               │
└─────────────────────────────────────┘
```

- **Position:** top-right, 16 dp from top, 16 dp from right.
- **Size:** wrap-content, max width 200 dp.
- **Flags:** `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`. Remove `FLAG_LAYOUT_NO_LIMITS` if it causes status-bar overlap.
- **Background:** `Color.Black.copy(alpha = 0.72f)`, `RoundedCornerShape(12.dp)`.
- **Text:** `Color.White`, 12 sp, no images.
- **No animation**, no progress bars, no interactive modifiers.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS04‑TS‑01 | Service starts foreground | `startForegroundService(intent)` | `dumpsys activity services` shows FGS type `specialUse` |
| VS04‑TS‑02 | Overlay added | Inspect `WindowManager` | `ComposeView` attached, type 2038 |
| VS04‑TS‑03 | Touch pass-through | Tap HUD area in target app | Underlying app receives tap |
| VS04‑TS‑04 | Permission missing | Revoke overlay → start service | Overlay not added; no crash |
| VS04‑TS‑05 | `onTimeout` graceful | Simulate `onTimeout(1, SPECIAL_USE)` | Service stops, notification cleared |
| VS04‑TS‑06 | Hidden state renders nothing | Emit `HudState(isVisible=false, ...)` | No visible HUD text/card; touch still passes through |
| VS04‑TS‑07 | Reveal transition latency | Emit `isVisible=false` then `true` | Visible content appears ≤ 200 ms after `true` emission |
| VS04‑TS‑08 | Hide on session end | Emit `isVisible=true` then `false` | Visible content disappears ≤ 200 ms after `false` emission |

### Database Schema

None.

### API Contracts

```kotlin
// core/overlay/HudWindowManager.kt
interface HudWindowManager {
    fun add(params: HudWindowParams): Result<Unit>
    fun update(state: HudState)
    fun remove()
}

data class HudWindowParams(
    val gravity: Int = Gravity.TOP or Gravity.END,
    val xOffsetDp: Int = 16,
    val yOffsetDp: Int = 16
)

// feature/hud/HudForegroundService.kt — intent actions
const val ACTION_START = "com.awareness.app.hud.START"
const val ACTION_STOP  = "com.awareness.app.hud.STOP"

// Consumed contract (frozen at VS-01)
data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean
)
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `HudLayoutParamsFactoryTest` — asserts `FLAG_NOT_TOUCHABLE` + `FLAG_NOT_FOCUSABLE` present, type = `TYPE_APPLICATION_OVERLAY`. `HudVisibilityMapperTest` — `isVisible=false` maps to no visible content. |
| **Integration** | `HudForegroundServiceTest` (Robolectric `ServiceTestRule`) — start service, assert `startForeground` called with correct type. `HudVisibilityIntegrationTest` — emit `HudState` with `isVisible` toggles, assert renderer state. |
| **API** | `HudWindowManagerContractTest` — add/update/remove idempotency; `add` returns failure if permission missing. `HudStateVisibilityContractTest` — hidden state never exposes visible content. |
| **E2E Manual** | Open Instagram with overlay granted and reveal delay 3 min → no visible HUD for 3 min → HUD appears → tap through to like a post → post likes. Leave app → HUD disappears. |

### File & Folder Mapping

#### 1. Backend (Window Management & Overlay Architecture)
- `core/overlay/src/main/kotlin/com/awareness/core/overlay/HudWindowParams.kt` `[New]` `[Backend]` — Data class holding window gravity and offset coordinates for overlay positioning.
- `core/overlay/src/main/kotlin/com/awareness/core/overlay/OverlayPermissionChecker.kt` `[New]` `[Backend]` — System utility verifying `Settings.canDrawOverlays` permission.
- `core/overlay/src/main/kotlin/com/awareness/core/overlay/HudLayoutParamsFactory.kt` `[New]` `[Backend]` — Factory creating `WindowManager.LayoutParams` with `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCHABLE` for touch pass-through.
- `core/overlay/src/main/kotlin/com/awareness/core/overlay/HudWindowManager.kt` `[New]` `[Backend]` — Interface and implementation injecting LifecycleOwner, SavedStateRegistryOwner, and ViewModelStoreOwner into ComposeView before `windowManager.addView`.

#### 2. Frontend (Passive Renderer & Overlay UI)
- `feature/hud/src/main/kotlin/com/awareness/feature/hud/HudForegroundService.kt` `[New]` `[Frontend]` — Passive specialUse Foreground Service (subtype: `mindfulness_hud`) hosting WindowManager and ComposeView without business logic.
- `feature/hud/src/main/kotlin/com/awareness/feature/hud/HudViewModel.kt` `[New]` `[Frontend]` — State holder exposing reactive `HudState` stream to the HUD overlay composable.
- `feature/hud/src/main/kotlin/com/awareness/feature/hud/HudComposable.kt` `[New]` `[Frontend]` — Visibility-aware Compose overlay rendering 0-size empty Box when `isVisible = false` and StudioBlank 0dp card when `isVisible = true`.

#### 3. Unit Tests
- `core/overlay/src/test/kotlin/com/awareness/core/overlay/HudLayoutParamsFactoryTest.kt` `[New]` `[Unit Test]` — Unit tests verifying touch pass-through flags (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`) and overlay type.
- `core/overlay/src/test/kotlin/com/awareness/core/overlay/HudWindowManagerTest.kt` `[New]` `[Unit Test]` — Unit tests verifying overlay permission rejection and safe attach/remove lifecycle.
- `feature/hud/src/test/kotlin/com/awareness/feature/hud/HudViewModelTest.kt` `[New]` `[Unit Test]` — Unit tests verifying `StateFlow<HudState>` emission from use case.

#### 4. Integration Tests
- `feature/hud/src/test/kotlin/com/awareness/feature/hud/HudForegroundServiceTest.kt` `[New]` `[Integration Test]` — Robolectric integration test for foreground service lifecycle, notification creation, and overlay teardown.

#### 5. Config & Build
- `core/overlay/build.gradle.kts` `[New]` `[Config/Build]` — Module build configuration for WindowManager overlay coordination.
- `feature/hud/build.gradle.kts` `[New]` `[Config/Build]` — Module build configuration for Compose overlay feature and service.
- `settings.gradle.kts` `[Modified]` `[Config/Build]` — Included `:core:overlay` and `:feature:hud` in multi-module hierarchy.
- `app/build.gradle.kts` `[Modified]` `[Config/Build]` — Added `:core:overlay` and `:feature:hud` module dependencies.
- `app/src/main/AndroidManifest.xml` `[Modified]` `[Config/Build]` — Declared `HudForegroundService` with `foregroundServiceType="specialUse"` and `mindfulness_hud` subtype.
- `gradle/libs.versions.toml` `[Modified]` `[Config/Build]` — Added `androidx-savedstate` and `androidx-lifecycle-viewmodel` version definitions.

---

# VS-05 — HUD Content, Reveal Delay & Message Rotation

**Dependencies:** VS-03, VS-04, VS-01 (`HudPreferencesRepository` contract)  
**FRD Coverage:** FR-05, **FR-05.1, FR-05.2, FR-05.3**, NFR-02, NFR-03 (≤100 ms counter update)

### User / Actor Story
> **As a user**, I want to choose which mindfulness messages appear, how long each message stays visible, and how long the app waits after I open social media before showing the HUD, **so that** the awareness prompt matches my intent and does not interrupt me immediately or show messages I do not want.

### Acceptance Criteria

1. HUD content remains **hidden** while the active session elapsed time is less than `Active.revealDelayMillisSnapshot`.
2. If the user sets `3 minutes`, at session elapsed `03:00` the HUD reveals with:
   - the current selected message,
   - timer showing total session elapsed `03:00`,
   - total Reel count for the session, including scrolls that happened before reveal.
3. If `revealDelayMillisSnapshot == 0`, HUD reveals immediately when the session becomes active.
4. After reveal, HUD shows three lines: selected message, `MM:SS` timer, `Reels: N`.
5. Timer updates **once per second**.
6. Message rotates every `messageDisplayDurationSeconds` from the selected message list. If only one message is selected, it stays static.
7. If the selected list is empty due to corrupted data, fall back to default messages; never crash.
8. Reel count updates ≤ 100 ms after a qualifying scroll, even if HUD is currently hidden by reveal delay.
9. HUD hides immediately when session ends.
10. Initial reveal delay is captured on `Idle → Active` transition by VS-03. Changing delay mid-session applies to the **next** session. Message selection and display duration may apply live from the next rotation.
11. Recomposition remains scoped: only changed text recomposes.
12. RAM baseline remains < 30 MB.
13. `HudState.isVisible = sessionState is Active && elapsedMillis >= Active.revealDelayMillisSnapshot`.
14. For browser sessions (`Active.isBrowser == true`), `showReelCount = false`; the Reel line is omitted, not shown as zero. Timer and message rotation still apply.
15. `HudState` is produced by combining `SessionOrchestrator.sessionState`, `HudPreferencesRepository.preferences`, and monotonic timer ticks. The overlay service does not compute visibility or rotation.
16. **Periodic Nudge Timing (Visible & Hidden Cycle):** When `hideDurationSeconds > 0`, the HUD does not remain permanently visible. After reaching `revealDelayMillisSnapshot`, the HUD displays for `messageDisplayDurationSeconds`, hides for `hideDurationSeconds`, and repeats cyclically (`cycleElapsed = elapsedMillis - revealDelayMillisSnapshot`, `cyclePeriod = visibleDuration + hideDuration`, visible if `cycleElapsed % cyclePeriod < visibleDuration`). If `hideDurationSeconds == 0`, HUD remains continuously visible.
17. **Custom Mindfulness Prompts:** Users can create custom mindfulness messages in HUD Personalization. Custom prompts are stored persistently in DataStore (`PreferencesKeys.CUSTOM_MESSAGES_SET`) as `id|text`, appear in the active message selection checklist, and can be deleted individually with clean state cleanup.
18. **HUD Opacity & Scaling:** Users can customize overlay background opacity (20%–100%, default 85%) and size scale (`HudScale.COMPACT`, `HudScale.STANDARD`, `HudScale.LARGE`). The HUD overlay adapts typography, padding, and max-width dynamically per scale while strictly conforming to StudioBlank's 0dp border radius and flat elevation.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Session > 99 min | Format as `H:MM:SS` beyond 59:59. |
| E2 | HUD visible with no active session (bug) | `isVisible` derives from `sessionState is Active`; hides when `Idle`. |
| E3 | Message list exhausted | Cycles (`index % messages.size`). |
| E4 | Device rotation | HUD position is gravity-based; no config dependency. |
| E5 | Dark mode | HUD colors are hard-coded dark for readability over any app. |
| E6 | Reveal delay > session duration | HUD never becomes visible for that session; session still ends/persists normally. |
| E7 | User changes reveal delay mid-session | No effect on current session; `Active.revealDelayMillisSnapshot` is fixed. Next session uses new value. |
| E8 | Selected messages changed mid-session | Next rotation uses new list; current message stays until its duration ends. |
| E9 | Message duration changed mid-session | Applies from next rotation boundary. |
| E10 | Selected list empty due to corruption | Fallback to default selected messages. |
| E11 | Pre-reveal Reel scrolls | Count still increments; displayed at reveal as total session count. |
| E12 | Browser session | `showReelCount = false`; no Reel line. Generic or domain-specific message may be supplied by VS-07. |
| E13 | Reveal delay snapshot is 0 | HUD visible from first active frame. |

### UI/UX Specification — HUD Before Reveal Delay

```
(Target app content only — no visible HUD content)
```

### UI/UX Specification — HUD After Reveal

```
┌─────────────────────────────────────┐
│  (Instagram feed)                   │
│              ┌────────────────────┐ │
│              │ Is this serving    │ │
│              │ you?               │ │
│              │ ⏱ 03:00            │ │
│              │ 🔄 Reels: 4        │ │
│              └────────────────────┘ │
│  (Instagram feed)                   │
└─────────────────────────────────────┘
```

- **Message:** 2-line max, ellipsis, 12 sp.
- **Timer:** monospaced, 14 sp, bold. Shows total session elapsed, not time since reveal.
- **Count:** 14 sp, bold, `🔄` prefix optional (glyph may vary by OEM).
- **Browser HUD:** Reel line omitted.
- **No animation**, no progress bars, no icons beyond static glyph.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS05‑TS‑01 | Timer ticks after reveal | Delay 0, start session, observe 3 s | `00:01`, `00:02`, `00:03` |
| VS05‑TS‑02 | Message rotates | Delay 0, duration 30 s, fast-forward 30 s | Message index increments |
| VS05‑TS‑03 | Count updates ≤ 100 ms | Emit `ReelScrolled` | HUD shows `Reels: 1` within 100 ms |
| VS05‑TS‑04 | HUD hides on end | Emit `SessionEnded` | `isVisible=false` within 200 ms |
| VS05‑TS‑05 | Recomposition scope | Compose compiler metrics | Only timer text recomposes per second |
| VS05‑TS‑06 | RAM baseline | `adb shell dumpsys meminfo` during active session | < 30 MB |
| VS05‑TS‑07 | Delay 180 s, session at 179 s | Start session with snapshot 180 s | `isVisible=false` |
| VS05‑TS‑08 | Delay 180 s, session at 180 s | Advance elapsed to 180 s | `isVisible=true`; timer `03:00` |
| VS05‑TS‑09 | Three messages selected, duration 30 s | Fast-forward 30 s, 60 s | Message rotates at 30 s boundaries |
| VS05‑TS‑10 | One message selected | Fast-forward multiple durations | Message stays static |
| VS05‑TS‑11 | Selected list empty fallback | Corrupt prefs → observe HUD | Default message shown, no crash |
| VS05‑TS‑12 | Delay 600 s, session ends at 300 s | Start delay 600 s, end at 300 s | HUD never visible; session persisted |
| VS05‑TS‑13 | Pre-reveal Reels | Delay 180 s, emit 4 `ReelScrolled` before 180 s | At reveal, `Reels: 4` |
| VS05‑TS‑14 | Browser hides Reel line | Start browser session, delay 0 | `showReelCount=false`; no Reel line |
| VS05‑TS‑15 | Mid-session delay change | Snapshot 180 s, change prefs to 60 s | Current session still reveals at 180 s |
| VS05‑TS‑16 | Mid-session message selection change | Select new list mid-session | Next rotation uses new list |
| VS05‑TS‑17 | Periodic Nudge Cycle | Delay 60s, visible 30s, hide 30s | At 59s: hidden; 60s-89s: visible; 90s-119s: hidden; 120s: visible |
| VS05‑TS‑18 | Custom Mindfulness Prompts | Add custom prompt, toggle in checklist | Appears in rotation; delete removes cleanly |
| VS05‑TS‑19 | HUD Scale & Opacity | Set scale `COMPACT`/`LARGE`, opacity 50% | Overlay dimensions, typography, and alpha match |

### Database Schema

None. Message list is local `DefaultMindfulnessMessages` + custom messages; preferences persist in DataStore `PreferencesKeys`.

### API Contracts

```kotlin
// core/domain/model/HudScale.kt
enum class HudScale { COMPACT, STANDARD, LARGE }

// core/domain/usecase/ObserveHudStateUseCase.kt
interface ObserveHudStateUseCase {
    operator fun invoke(): Flow<HudState>
}

// core/domain/model/HudState.kt (final shape, frozen at VS-01 extension)
data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean,
    val browserDomainLabel: String? = null,
    val opacityPercent: Int = 85,
    val scale: HudScale = HudScale.STANDARD
)

// core/domain/model/HudPreferences.kt
data class HudPreferences(
    val initialRevealDelaySeconds: Int = 0,
    val messageDisplayDurationSeconds: Int = 30,
    val hideDurationSeconds: Int = 0,
    val selectedMessageIds: Set<String> = DefaultMindfulnessMessages.defaultSelectedIds,
    val customMessages: List<MindfulnessMessage> = emptyList(),
    val hudOpacityPercent: Int = 85,
    val hudScale: HudScale = HudScale.STANDARD
)

// core/presentation/util/TimeFormatter.kt
object TimeFormatter {
    fun mmss(elapsedMillis: Long): String           // "12:34"
    fun hms(elapsedMillis: Long): String            // "1:02:34"
}

// core/presentation/util/MessageRotation.kt
object MessageRotation {
    fun indexFor(
        elapsedMillis: Long,
        revealDelayMillis: Long,
        messageDisplayDurationSeconds: Int,
        messageCount: Int
    ): Int
}
```

**Combine logic:**

```text
combine(sessionState, hudPreferences, timerTicks) { session, prefs, elapsed ->
    when (session) {
        Idle -> HudState(isVisible=false, message="", elapsedMillis=0, reelCount=0, showReelCount=false, opacityPercent=prefs.hudOpacityPercent, scale=prefs.hudScale)
        is Active -> {
            val elapsedMillis = maxOf(0L, elapsed - session.startElapsed)
            val visible = if (elapsedMillis < session.revealDelayMillisSnapshot) {
                false
            } else if (prefs.hideDurationSeconds <= 0) {
                true
            } else {
                val cycleElapsed = elapsedMillis - session.revealDelayMillisSnapshot
                val visiblePeriod = prefs.messageDisplayDurationSeconds * 1000L
                val cyclePeriod = visiblePeriod + (prefs.hideDurationSeconds * 1000L)
                (cycleElapsed % cyclePeriod) < visiblePeriod
            }
            val messages = prefs.selectedMessages.ifEmpty { DefaultMindfulnessMessages.fallback }
            val index = if (visible && messages.isNotEmpty() && prefs.messageDisplayDurationSeconds > 0) {
                ((elapsedMillis / (prefs.messageDisplayDurationSeconds * 1000L)) % messages.size).toInt()
            } else 0
            HudState(
                isVisible = visible,
                message = if (visible) messages[index].text else "",
                elapsedMillis = elapsedMillis,
                reelCount = session.reelCount,
                showReelCount = !session.isBrowser,
                opacityPercent = prefs.hudOpacityPercent,
                scale = prefs.hudScale
            )
        }
    }
}
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `TimeFormatterTest` (boundaries 0, 59 999, 60 000, 3 599 999, 3 600 000). `MindfulnessMessagesTest` (non-empty, unique IDs, rotates). `MessageRotationTest` (delay boundaries, one message, multiple messages, empty fallback). `HudStateMapperTest` (visible/hidden, browser flag, pre-reveal counts). `PeriodicNudgeTimingTest` (nudge cycling, continuous mode). `HudStylingAndCustomMessageTest` (custom prompt selection, opacity & scale forwarding). |
| **Integration** | `HudViewModelTest` (Turbine): collect `HudState` after `SessionStarted`, `ReelScrolled`, preference changes, and fake clock advances. `HudContentRotationIntegrationTest` (Turbine): rotation boundaries and nudge periods. |
| **API** | `ObserveHudStateUseCaseContractTest` — initial hidden state, reveal at snapshot delay, monotonic timer, count increments, browser `showReelCount=false`, empty-selection fallback. |
| **E2E Manual** | Configure 3 min delay, 30 s duration, 30 s nudge interval, 1 custom message. Open Instagram → no HUD for 3 min → HUD appears for 30s → hides for 30s → reappears. |

### File & Folder Mapping

#### 1. Backend & Domain Models
- `core/domain/src/main/kotlin/com/awareness/core/domain/model/HudScale.kt` `[New]` `[Backend]` — Enum defining HUD sizing scale tokens (`COMPACT`, `STANDARD`, `LARGE`).
- `core/domain/src/main/kotlin/com/awareness/core/domain/model/HudPreferences.kt` `[Modified]` `[Backend]` — Extended preferences with `hideDurationSeconds`, `customMessages`, `hudOpacityPercent`, and `hudScale`.
- `core/domain/src/main/kotlin/com/awareness/core/domain/model/HudState.kt` `[Modified]` `[Backend]` — Added `opacityPercent` and `scale` rendering attributes.
- `core/domain/src/main/kotlin/com/awareness/core/domain/repository/HudPreferencesRepository.kt` `[Modified]` `[Backend]` — Extended repository contract with methods for custom messages, nudge intervals, and styling updates.
- `core/domain/src/main/kotlin/com/awareness/core/domain/usecase/ObserveHudStateUseCaseImpl.kt` `[Modified]` `[Backend]` — Implemented periodic nudge cycle modulo logic and dynamic opacity/scale state mapping.

#### 2. Database & Persistence
- `core/data/src/main/kotlin/com/awareness/core/data/datastore/HudPreferencesDataStore.kt` `[Modified]` `[Database/Persistence]` — Defined DataStore keys for nudge interval, opacity, scale, and serialized custom messages set.
- `core/data/src/main/kotlin/com/awareness/core/data/repository/HudPreferencesRepositoryImpl.kt` `[Modified]` `[Database/Persistence]` — Implemented DataStore reading, writing, clamping (0-3600s, 20-100%), and `$id|$text` serialization for custom prompts.

#### 3. Frontend & Presentation
- `feature/settings/src/main/kotlin/com/awareness/feature/settings/hud/HudPersonalizationViewModel.kt` `[Modified]` `[Frontend]` — Added state handling and actions for nudge intervals, custom prompt creation/deletion, scale selection, and opacity adjustment.
- `feature/settings/src/main/kotlin/com/awareness/feature/settings/hud/HudPersonalizationScreen.kt` `[Modified]` `[Frontend]` — StudioBlank UI implementing timing configuration, periodic nudge input, scale chips, opacity selector, custom prompt input, and prompt deletion.
- `feature/hud/src/main/kotlin/com/awareness/feature/hud/HudComposable.kt` `[Modified]` `[Frontend]` — Full 3-line StudioBlank HUD overlay composable with isolated monotonic timer recomposition, zero-size layout when hidden, dynamic scale dimensions/typography, and customizable opacity.
- `feature/hud/src/main/kotlin/com/awareness/feature/hud/HudForegroundService.kt` `[Modified]` `[Backend]` — Added companion reactive state stream provider and static `start(context)` / `stop(context)` helpers to bind overlay lifecycle to domain session state.
- `app/src/main/kotlin/com/awareness/app/AppApplication.kt` `[Modified]` `[Backend]` — Wired `SessionOrchestrator`, `ObserveHudStateUseCase`, and `AccessibilityEventBusImpl` to automatically start/stop `HudForegroundService` on Active/Idle session transitions.

#### 4. Unit & Integration Tests
- `core/domain/src/test/kotlin/com/awareness/core/domain/usecase/PeriodicNudgeTimingTest.kt` `[New]` `[Unit Test]` — Unit tests for periodic nudge visible and hidden interval calculations.
- `core/domain/src/test/kotlin/com/awareness/core/domain/model/HudStylingAndCustomMessageTest.kt` `[New]` `[Unit Test]` — Unit tests for custom message selection fallback and styling properties.
- `feature/settings/src/test/kotlin/com/awareness/feature/settings/HudPersonalizationViewModelTest.kt` `[Modified]` `[Unit Test]` — Unit tests for nudge clamping, custom prompt add/delete, and scale/opacity persistence.
- `core/data/src/test/kotlin/com/awareness/core/data/repository/HudPreferencesModelResolutionTest.kt` `[Modified]` `[Unit Test]` — Unit tests for preference defaults, clamping, and custom message resolution.
- `core/presentation/src/test/kotlin/com/awareness/core/presentation/util/TimeFormatterBoundaryTest.kt` `[New]` `[Unit Test]` — Boundary unit tests for `TimeFormatter.formatElapsed` across sub-minute, hour, and multi-hour thresholds.
- `feature/hud/src/test/kotlin/com/awareness/feature/hud/HudContentRotationIntegrationTest.kt` `[New]` `[Integration Test]` — Integration tests verifying delay-aware visibility, message rotation across display duration ticks, and browser reel line suppression.
- `app/src/test/kotlin/com/awareness/app/AppPipelineIntegrationTest.kt` `[New]` `[Integration Test]` — End-to-end pipeline test verifying accessibility sensor event dispatch transitions application state machine and orchestrates HUD foreground service.

---

# VS-06 — Reel/Short Counting

**Dependencies:** VS-05  
**FRD Coverage:** FR-03, NFR-01 (bounded traversal), NFR-04 (privacy — no text)

### User / Actor Story
> **As a user**, I want the app to count the Reels/Shorts I scroll past **so that** the HUD reflects my short-form video engagement without reading any content, and I want those counts retained even if my configured reveal delay has not passed yet.

### Acceptance Criteria

1. `ScrollContainerMatcher` identifies short-form containers by view-ID/class patterns:
   - Instagram: `clips_viewer`, `reel_viewer`
   - YouTube: `reel_watch_fragment`
   - Facebook: `reel_fragment`, `clips_viewer`
   - Snapchat: `snap_reel_container`
2. Traversal is **depth-bounded to 10** (`MAX_TRAVERSAL_DEPTH = 10`).
3. Counting increments **only** on `TYPE_VIEW_SCROLLED` inside a confirmed container.
4. **No** call to `node.text`, `node.contentDescription`, or `node.extras` — enforced by `lint` custom rule + code review checklist.
5. HUD count updates ≤ 100 ms after qualifying scroll.
6. Swiping through 5 Reels increments exactly 5.
7. Scroll on feed (not Reels) does **not** increment.
8. Reel counting is **independent of HUD visibility**. Scrolls before the reveal delay still increment `Active.reelCount`; the total appears when HUD reveals.
9. Debounce 80 ms per container; each user-initiated fling counts once.
10. If `canRetrieveWindowContent=false`, traversal is not attempted; matcher returns `false`; no count (graceful degradation).
11. Counting applies to social target apps only; browser sessions do not increment Reel count.
12. `ReelScrolled` events are emitted on `AccessibilityEventBus` and consumed by `SessionOrchestrator`, which increments `Active.reelCount`.

### Edge Cases

| # | Scenario | Mitigation |
|---|---|---|
| E1 | Instagram updates view-ID | View-ID set is a `Set<String>`; unknown containers fall back to class-name heuristic; silently no-op if unmatched. |
| E2 | Nested scroll containers | Matcher dedupes: first confirmed ancestor wins; scroll inside it counts once. |
| E3 | Fast fling (multiple scrolls < 100 ms) | Debounce 80 ms per container; each user-initiated fling counts once. |
| E4 | `canRetrieveWindowContent=false` | Traversal not attempted; matcher returns `false`; no count. |
| E5 | Depth-bounded traversal hits limit before container | Matcher returns `false`; no false positives. |
| E6 | Scroll before HUD reveal | `reelCount` increments; HUD shows total at reveal. |
| E7 | Browser session scroll | Not counted; browser has no Reel line. |
| E8 | Unknown container in target app | No increment, no crash. |

### UI/UX Specification

HUD line 3: `🔄 Reels: N` (see VS-05 wireframe). **No new UI.** For browser sessions, line 3 is omitted.

### Test Scenarios

| Test ID | Description | Steps | Expected |
|---|---|---|---|
| VS06‑TS‑01 | 5 Reels → count 5 | Open Reels, swipe 5× | HUD `Reels: 5` |
| VS06‑TS‑02 | Feed scroll ignored | Scroll Instagram feed | Count unchanged |
| VS06‑TS‑03 | Depth bound | Fake tree depth 12 with container at 11 | Matcher `false` |
| VS06‑TS‑04 | No text read | `grep -r "\.text\b" accessibility/` | 0 results |
| VS06‑TS‑05 | Debounce | 3 scrolls in 50 ms | Count +1 |
| VS06‑TS‑06 | Unknown container | Scroll in unmapped container | Count unchanged, no crash |
| VS06‑TS‑07 | Pre-reveal Reels | Delay 180 s, scroll 3 Reels before 180 s | `reelCount=3`; HUD shows 3 at reveal |
| VS06‑TS‑08 | Browser scroll ignored | Start Chrome session, scroll | `reelCount` unchanged |
| VS06‑TS‑09 | Counting continues while hidden | Emit qualifying scroll while `isVisible=false` | `Active.reelCount` increments |

### Database Schema

None new. `reelCount` field on `SessionSummary` is populated by `SessionOrchestrator`.

### API Contracts

```kotlin
// core/accessibility/ScrollContainerMatcher.kt
interface ScrollContainerMatcher {
    /**
     * @return true iff the scrolled node is inside a known short-form container
     *         within MAX_TRAVERSAL_DEPTH. Never reads text/contentDescription.
     */
    fun isReelContainer(node: AccessibilityNodeInfo): Boolean
}

// core/accessibility/ScrollContainerConfig.kt
object ScrollContainerConfig {
    const val MAX_TRAVERSAL_DEPTH = 10
    const val SCROLL_DEBOUNCE_MS = 80L
    val instagramViewIds = setOf("clips_viewer", "reel_viewer")
    val facebookViewIds = setOf("reel_fragment", "clips_viewer")
    val snapchatViewIds = setOf("snap_reel_container")
    val youtubeViewIds = setOf("reel_watch_fragment")
}

// core/domain/model/AwarenessEvent.kt
data class ReelScrolled(val timestamp: Long) : AwarenessEvent

// SessionOrchestrator behavior:
// On ReelScrolled and sessionState is Active:
//   Active.copy(reelCount = reelCount + 1)
// No dependency on HudState.isVisible.
```

### Test Classification

| Type | Tests |
|---|---|
| **Unit** | `ScrollContainerMatcherTest` — 20+ cases: each known container, unknown, depth 10 boundary, depth 11 rejection, no-text assertion via mock that throws on `.text`. `ReelCountIncrementTest` — pre-reveal and post-reveal increments. |
| **Integration** | Fake `AccessibilityEvent` stream → `AwarenessAccessibilityService` → orchestrator → HUD `ReelScrolled` count, including while `isVisible=false`. |
| **API** | `ScrollContainerMatcherContractTest` — idempotency, no side effects, `false` on null node. `ReelScrolledContractTest` — emission increments `Active.reelCount` exactly once. |
| **E2E Manual** | Set reveal delay 3 min. Open Instagram Reels, swipe 5× before 3 min → at reveal HUD shows `Reels: 5`. Scroll feed 10× → HUD still 5. Open Chrome, scroll → count unchanged. |

### File & Folder Mapping

```
:core:accessibility  ScrollContainerMatcher.kt
                     ScrollContainerConfig.kt (view-ID constants, depth/debounce)
:core:domain         model/AwarenessEvent.kt (ReelScrolled)
                     SessionOrchestrator.kt (increment on ReelScrolled)
:app                 lint/NoTextReadDetector.kt (custom lint rule)
```

---

## Frozen / Updated Cross-Slice Contracts from VS-04–VS-06

| Contract | File | Consumers |
|---|---|---|
| `HudState` | `core/domain/model/HudState.kt` | VS-04, VS-05, VS-07 |
| `HudWindowManager` | `core/overlay/HudWindowManager.kt` | VS-04, VS-09 |
| `ObserveHudStateUseCase` | `core/domain/usecase/ObserveHudStateUseCase.kt` | VS-05, VS-07 |
| `HudPreferencesRepository` | `core/domain/repository/HudPreferencesRepository.kt` | VS-01, VS-03, VS-05 |
| `ScrollContainerMatcher` | `core/accessibility/ScrollContainerMatcher.kt` | VS-06 |
| `ReelScrolled` | `core/domain/model/AwarenessEvent.kt` | VS-06, VS-03 |
| `SessionState.Active` (with `revealDelayMillisSnapshot`, `reelCount`, `isBrowser`) | `core/domain/model/SessionState.kt` | VS-03, VS-05, VS-06 |