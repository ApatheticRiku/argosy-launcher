---
name: dual-screen
description: Dual-screen development reference. Load this before implementing any dual-screen feature.
---

# Dual-Screen Development Reference

STATUS: rewritten 2026-09-17 after the screen-role rework. The spine below is verified on
device. The surface is still being restored, so read
`~/.claude/projects/-Users-nendo-projects-argosy-launcher/memory/project_ds_restoration_manifest.md`
before assuming a capability exists. Everything the rework deleted is recoverable with
`git show cb4c385e^:<path>`.

All paths are under `app/src/main/kotlin/com/nendo/argosy/`. Reference by SYMBOL, not line
number; offsets rot on the next edit above them.

## The one thing to understand first

There is no "upper screen" and no "companion UI" any more. Screens hold ROLES, and the role
decides everything.

```
ScreenRole { PRIMARY, PRESENTATION, APP_TARGET, OFF }   domain/model/ScreenLayout.kt

PRIMARY       hosts the real ArgosyApp. Menus open here, the pad drives it.
PRESENTATION  renders PresentationSlotContent: describes what PRIMARY has focused.
APP_TARGET    where launched apps and games open. Takes no launcher UI.
OFF           Argosy draws nothing.
```

Either physical activity can hold PRIMARY. `MainActivity` and `SecondaryHomeActivity` are
interchangeable hosts, and which one is hosting is `DualScreenManager.companionHoldsPrimary`.

LAW: `companionHoldsPrimary` decides BOTH what an activity renders AND where its key events go.
- Why: a screen that draws a presentation slot while still handling input for a launcher it is
  not showing is exactly the bug class that cost a full day here. Both decisions must read one
  flow or they drift.
- Boundary: if you add a third consumer of "who is hosting", it reads that flow too.

## Screen layouts are per attached SET

`ScreenLayouts` (`domain/model/ScreenLayout.kt`) maps an attached-screen set to a `ScreenLayout`.
The set key is `ScreenLayouts.setKeyOf(keys)` - every attached panel's stable key, sorted, joined
with `|`. A stable key is the display's `getUniqueId()` when the OS offers one, else
`display:<id>:<short>x<long>` (`util/ScreenCatalog.kt`).

Consequence, and it is the feature: plug a monitor in and the set key changes, so that
combination gets its own profile. Unplug and the old profile returns untouched. Replug the same
monitor and you land back in its profile.

`ScreenCatalog.attachedScreens()` is the only enumerator. It reports true panel pixels from
`Display.Mode` (NOT `getRealSize()`, which reflects the CALLING context, not the target panel -
that bug made every screen card draw at the same aspect) and filters to BUILT_IN and EXTERNAL.

`DualScreenManager.applyStoredScreenLayout(promptWhenUnknown)` is THE entry point that resolves
and applies a layout. Called from `MainActivity.onCreate`, from the display listener on add and
remove, and by the settings router after a role change. Do not resolve a layout anywhere else.

Swap rule (`ScreenLayout.withRole`): only PRIMARY and PRESENTATION ever trade. Assigning
APP_TARGET never disturbs the primary, so a media screen stays put. Tests in
`app/src/test/.../domain/model/ScreenLayoutTest.kt` cover both directions plus the arrangement
where an app-target screen must survive a swap.

## What each surface renders

Both activities choose from the same flow, with opposite polarity:

```
MainActivity                         SecondaryHomeActivity
  companionHoldsPrimary                !companionHoldsPrimary
  || game on another screen            || game on another screen
    ? PresentationSlotContent(slot)      ? PresentationSlotContent(slot)
    : ArgosyApp()                        : ArgosyApp()
```

RULE: an activity's render choice reads `DualScreenManager.companionHoldsPrimary`, the same
flow its input routing reads. A render flag held on the activity (a `mutableStateOf` set from
role callbacks) is a second copy of "who is hosting" and violates the law above.
`SecondaryHomeActivity.isShowcaseRole` is that second copy today and is due to be replaced.

`ArgosyApp` takes no dual-screen parameters. It reads `DualScreenManagerHolder.instance`.

LAW: inside `ArgosyApp` and anything it hosts, reach DSM through the Holder, never through
`context as? MainActivity`.
- Why: on the companion that cast is null, and roughly twenty sites went silently inert when the
  companion held PRIMARY - overlay events, keyboard toggle, media routing, the screen dimmer.
- Exception: genuinely MainActivity-owned things, e.g. `pendingDeepLink`, which exists because
  MainActivity receives the Intent.

## PresentationSlot

`ui/dualscreen/PresentationSlot.kt`. What the presentation screen draws.

| Case | Published by |
|---|---|
| `Fallback` | nobody; draws wallpaper via `surfaceBackdrop(BackdropRole.WALLPAPER)` |
| `HomeLayoutPreview` | `HomeScreenSection` via `PresentOnCompanion` |
| `PlayTime` | `PlayTimePresentation` via `PresentOnCompanion` |
| `ScreenIdentity` | `ScreensSection`, the numbered badge |
| `Detail` | `setCompanionDetail` from Home, Library, Media, GameDetail |
| `PlatformShowcase` | `LibraryViewModel` via `DualScreenManager.presentSlot` while the platform grid is focused |
| `InGame` | DSM while a session is live |

Three publishing mechanisms, deliberately:
- `PresentOnCompanion(SlotOwner("id"), slot)` - a composable publishes while on screen and
  releases on dispose. Held per owner so a screen taking over and the screen it replaced can
  publish and release in any order. The default for a slot built from what the composable
  already has.
- `setCompanionDetail(CompanionDetail?)` - a ViewModel describes its focused item. Publish on
  focus change, republish on resume, clear on dispose. Copy `MediaLibraryScreen`'s
  `DisposableEffect`; it is the canonical shape.
- `DualScreenManager.presentSlot(owner, slot)` / `releaseSlot(owner)` called from a ViewModel -
  for a dedicated slot case (not a `Detail`) that the ViewModel builds from suspend repository
  queries, in step with its own focus flow. `LibraryViewModel`'s
  `publishPlatformShowcase` is the reference. The contract comes from DSM:
  - Slots are keyed by `SlotOwner`. `presentSlot` replaces that owner's entry and moves it to
    the top; `releaseSlot` removes only that owner's entry. Use one fixed owner id per publisher.
  - The most recently presented slot wins over any `Detail`, so a slot left published hides
    every later `setCompanionDetail`. The ViewModel owns the release: call `releaseSlot` when
    focus leaves the thing it describes, and on every path that stops driving the screen.
  - Reach DSM through `DualScreenManagerHolder.instance` and return early when it is null.
  - Not for a composable that can publish from state it already holds. Use
    `PresentOnCompanion` there, because composition gives it release on dispose for free.

`presentationSlot` combines them with a live session outranking everything, then the most recent
published slot, then the described detail, then Fallback.

GOTCHA: `presentationSlot` is declared AFTER the flows it combines. Kotlin initialises properties
in declaration order, so a combine placed above its inputs reads nulls at construction and throws.
Same trap applies to `companionHoldsPrimary`.

## Input

`InputDispatcher` tiers, highest first:

```
interceptHandler    may DECLINE (returns UNHANDLED) and let the event fall through
criticalHandler     app-level modals
modalStack
drawerHandler
viewHandler
```

`interceptHandler` is the only pass-through tier. It exists so a transient prompt can own ONE
button without freezing the screen behind it; the new-monitor prompt is its only user today.
Every other tier is all-or-nothing.

`GamepadInputHandler` is a `@Singleton` and `eventFlow()` is a SharedFlow. Both activities feed
it; only the composed `ArgosyApp` collects it, and only one is composed at a time. So an activity
"routing input" means calling `gamepadInputHandler.handleKeyEvent(event)`.

Dedup: every dispatch path calls `dsm.claimInput(event)` first; first claimant wins.

LAW: an activity that is NOT hosting must decline to claim.
- Why: `claimInput` drops parallel deliveries of the same physical press. A companion drawing a
  presentation slot that claimed keys and then did nothing with them starved the panel that was
  actually hosting. That was the "input isn't captured to the primary display" bug.

`onForwardKey` (CompanionHost) must feed `gamepadInputHandler`, not `super.dispatchKeyEvent`.
The view hierarchy consumes nothing, because the launcher uses no Compose focus for navigation,
so a forwarded key handed to `super` vanishes silently.

SELECT on Home swaps PRIMARY and PRESENTATION (`HomeInputHandler.onSelect`), gated on a
dual-screen device WITH a presentation screen. Hold-A opens the game/tile menu, which is where
SELECT's older job moved. Also reachable from Quick Settings' Swap Displays tile.

Role-state writers: `swapRoles()` toggles, `applyDisplayRoleOverride(override)` sets, and
`setPrimaryDisplayId` applies a stored layout. While a session is active each one commits only
through `moveGameWithRoles`, which carries the game to the display its role lands on first and
refuses otherwise. Do not write `_isRolesSwapped` from anywhere else.

Live move (AYN Thor only, `liveSwapAvailable`): needs `hardware/ThorTaskMover` answering its probe,
a software-rendered built-in core registered as `LiveMoveHost`, and a game on the PRIMARY or
PRESENTATION display (never the app screen, never docked). Order: `isMovingGame` raised, second
screen hidden, `emulatorDisplayId` set to the target, task moved, arrival reported by
`LibretroActivity.onConfigurationChanged`, roles committed, focus sent to the game's display,
guard cleared. No arrival within `GAME_MOVE_CONFIRM_MS` restores the old display and commits
nothing. Every refocus, teardown and focus-restore path checks `isMovingGame`; a new one must too.
External emulators never move: their task id is not visible to the app.

## New-monitor prompt

On display add, `applyStoredScreenLayout(promptWhenUnknown = true)` applies the resolved layout
immediately, then sets `unconfiguredScreenSet` when the set has no stored layout and more than one
screen. `ArgosyApp` shows a 5-second countdown offering the display setup screen on X, registered
as the intercept handler so every other button still reaches the screen behind it.

Known-set changes never prompt; they just apply the stored profile.

UNPROVEN: this path has not been exercised with real hardware. `overlay_display_devices` cannot
fake it, because `ScreenCatalog` filters to BUILT_IN/EXTERNAL and the listener gates on
`isPhysicalDisplay`. Needs a monitor plugged into the Thor.

## Process and lifecycle (unchanged, still true)

ONE process, two activities. `SecondaryHomeActivity` is the SECONDARY_HOME activity, intent-filter
priority 1000, `launchMode=singleTop`, `taskAffinity=""`, `excludeFromRecents`. The OS pins it:
`finish()` respawns it. To remove it, disable the component via
`util/SecondaryHomeComponent.setEnabled(context, false)`.

It IS a Hilt entry point now (`@AndroidEntryPoint`), so it can inject. It reaches shared
singletons through `dsm.*` for anything DSM owns.

- FGS guard: `CompanionGuardService`, subtype `companion_display_guard`.
- onCreate gate: `SessionStateStore.isDualScreenEnabled()` finishes immediately when off.
- Stale-DSM reconnect: `onResume` compares `dsm` to the Holder instance and re-runs
  `initializeCompanion()` on mismatch. Init-time wiring you add MUST live inside that path.
- Theme: `SecondaryHomeTheme` provides the same locals as `ALauncherTheme`, including
  `LocalSurfaceBackdrop`.

## Display affinity

`hasSecondaryDisplay` is gated by THREE conditions:
`dualScreenEnabled && secondaryDisplayUsable && hasPhysicalSecondaryDisplay`.

`secondaryDisplayUsable` is a FALLBACK LATCH, not a preference. It is set false by
`fallbackToSingleScreen(persistent)` once the companion has proven unable to start on the
secondary display. `reprobeSecondaryDisplay()` clears it. On a device that latched false, every
dual-screen entry point is off even though hardware and preference both say yes. Check the latch
before diagnosing "dual screen does nothing".

`DisplayAffinityHelper.appTargetDisplayId` routes launches to the APP_TARGET screen, which is what
makes launching to a media screen work even in single-display mode.

## Checklist for a new dual-screen feature

1. [ ] State on DSM as a StateFlow, never on an activity.
2. [ ] Does the presentation screen show it? Add a `PresentationSlot` case and publish it, rather
       than reaching for a second render path.
3. [ ] Reach DSM via the Holder, not via MainActivity.
4. [ ] Gamepad AND touch. An app bar you can tap but not reach with the d-pad fails the
       completeness matrix; that has already shipped once.
5. [ ] If you add a CompanionHost method, implement it for real or do not add it. Three `= Unit`
       stubs sat behind a manager that reported success, and the drawer's Library entry silently
       did nothing for a week.
6. [ ] Verify in BOTH role arrangements. A feature that only works with the built-in screen
       primary is half done.
7. [ ] Shared components, never a DS-only copy. See the standing rule in
       `feedback_share_home_surface_components`.

## Things that no longer exist

`DualHomeViewModel`, `DualHomeLowerScreen`, `DualHomeUpperScreen`, `DualShowcaseComponents`,
`CompanionHomeHints`, `SecondaryHomeInputHandler`, `SecondaryHomeComposables`,
`SecondaryHomeStateManager`, `SecondaryHomeBroadcastHelper`, `ShowcaseViewModel`,
`ControlRoleContent`, the whole `ui/dualscreen/gamedetail` and `ui/dualscreen/media` packages,
`ui/screens/secondaryhome`, `DualCustomGridInputRouter`, `ActiveModal`, `ForwardingMode`,
`CarouselNavContext`, and the ~70 `moveDual*` / `confirmDual*` relay methods on DSM.

If a doc, comment or plan mentions any of those, it predates 2026-09-16 and is wrong.
