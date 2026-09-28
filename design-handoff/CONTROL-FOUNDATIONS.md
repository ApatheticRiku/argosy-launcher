# Control Foundations

The base design language for Argosy's menu/control system. Built bottom-up: controls and
primitives are defined here first; screens are composed from instances of them. Lives ahead of
any screen redesign.

Source of truth for VISUAL intent is the Penpot `Foundations - Controls` page; this doc is the
decision record and the annotation text for that page. Token VALUES live in `tokens.json`.

## Direction

- Modern, clean, real game console. Not Material, not maximal-cinematic.
- Two locked anchors, validated on-device: Home and Game Detail. Everything conforms to their
  language; neither is up for redesign.
- Bottom-up: lock controls/primitives, then screens fall out as instances + overrides.

## Color usage

- Chrome is neutral monochrome built as a deep base + elevation ramp, never a flat fill. Dark
  mode anchors to Home's rendered near-black family (`#050507` base -> `#13141a` surface ->
  `#1c1e26` raised -> `#262834` elevated), NOT the stale Material `#121212`. Light mode mirrors
  the same principle inverted (near-white base + subtle grey elevation). Color lives in accents
  and per-game art, never in chrome.
- Accents carry per-mode variants (cyan `#00ACC1` dark / `#007C91` light, etc.) so contrast
  holds both ways; the art-tint bloom is lighter-touch in light mode to avoid washout.
- Accent COLORATION is theme-biased: DARK mode biases toward white (lighter/whiter-tinted accent
  lifts off near-black); LIGHT mode biases toward the raw saturated color (a whitened accent washes
  out on near-white). Same token, biased per mode - applies to fills, outline rims, and focus
  washes, not just the base swatch. The existing variants already lean this way; formalize it.
  This INCLUDES the destructive red (2026-07-06, on-device): raw `#E53935` labels read harsh at
  rest on near-black; `theme.destructive` = the token lerped 0.25 toward white in dark, raw in
  light. All danger coloration resolves through it.
- No outline-only buttons. Action buttons are always filled (accent = primary, neutral = secondary);
  a hollow outline with no fill is not a button style we use.
- `accentPrimary` (default cyan `#00ACC1`) + `accentSecondary` (user-pick, default
  complementary). Both user-themeable; defaults are just defaults.
- The existing scheme primary/secondary/tertiary (cyan / teal / green) is already an analogous
  near-triad - formalize it, do not reinvent.
- Supporting-tone derivation, pick per accent: single-hue mono ramp (vary saturation/brightness)
  OR analogous hue offsets of 15-30 degrees.
- Per-game cover-art tint (computed from cover palette[0]): focus halo, CTA shadow, wallpaper
  bloom, progress fill. The game brings the color; the system stays neutral.

## Radius roles

Sharp surfaces, soft controls - the inverse of Material's over-rounded panels.

- `radiusPanel` 6dp: modals, drawers, sheets, side rails, panels (today `GlassPanel` uses 16dp - too round).
- `radiusControl` 8dp: action buttons (rounded-rect), list rows. Settled by the canvas: every
  button and row on the V2 boards is drawn at r8 (earlier drafts said ~10-12 and ~6; 8 is canonical).
- `radiusPill` 999: capsule TOKENS only - chips, tags, filter/segment pills, toggles, the enum
  inline. NOT action buttons.

All three live in `tokens.json` `dimension.radius` as `panel` / `control` / `pill`.

Shape encodes role: rounded-RECT = a command you trigger; full PILL = a value/state you pick.

## Density

The default UI is ~30 percent too big - Material's metrics carry excess whitespace (56dp list
rows, 16dp gaps). Decision (2026-07-02): cut CONTAINER metrics, not type. The type scale holds
for the 10-foot read; row heights, paddings, and gaps shrink. Scope is menus/controls only -
Home and Game Detail (the locked anchors) keep their validated layouts. Lands as baked token
defaults, not a user-facing density setting.

Canonical metrics (formalized from the tightest V2 boards, Nav Sidebar and Modals, which
applied the cut by eye at 40-46px rows before it was written down):

- Menu row: 40 single-line / 52 two-line (`layout.menuRowHeight` / `menuRowHeightLg`), list gap 8
  (`layout.listGap`).
- `layout.settingsItemMinHeight` 56 -> 40; `layout.footerHeight` 50 -> 44.
- Action button: 44 tall (`layout.buttonHeight`), padding 16h/10v (was 22h/14v).
- The Controls V2 sheet (56px rows, 16px gaps) predates this decision and needs a retrofit pass.

Supersedes the ui-redesign branch's `cc959405` (0.7 global uiScale) - a global scale shrinks
type and the anchors too; container metrics are the chosen mechanism.

VERDICT (2026-07-06, on-device): the container-metric density at the default 100 uiScale is
final - no handheld default-scale multiplier. The parked ~0.85 proposal is dropped.

## States

One canonical set, reused by every interactive primitive: `neutral / focused / active(selected) /
disabled`.

Focus is the primary visual event, but its TREATMENT is per component class, composed from five
indicators (implemented as `FocusIndicators` in `primitives/Focus.kt` ON THE `ui-redesign`
BRANCH - not yet on `ui-redesign-beta`; the port is part of the migration):

- `lift` - translateY -6dp + scale 1.04
- `halo` - radial accent glow behind (0.55 -> 0.14 -> transparent)
- `fill` - accent background wash, alpha 0.18 focused / 0.108 selected-not-focused
- `stripe` - 4dp accent left-stripe
- `ring` - 2dp accent border

| Class | Focus treatment |
|---|---|
| Cover tile (Home / Library) | halo + lift |
| Nav row / tab row | stripe + fill |
| Menu item / list row | fill |
| Button / pill | fill |

- HARD RULE: focus changes color / fill / glow / border only - NEVER position or scale. No
  "elevate on focus" (the AI-default tell). The element stays put; only its surface treatment
  changes. `fill`, `halo`, `stripe`, `ring` are all in-place; `lift` (translate + scale) is
  disallowed on controls, rows, buttons, chips - anything in a menu.
- The cover tile's `lift` is the only open exception (the one place Home moves on focus). Default
  is to keep it as the single deliberate signature; it can go static (halo only) if you want zero
  movement anywhere. See open items.
- Focus tint resolves: explicit -> user focus-halo override -> user primary -> per-game art
  palette[0] -> theme focus accent. Focus inherits the game's color when nothing overrides.
- `active/selected` = the persistent fill (0.108) / stripe marking the current value or page, shown
  even when focus is elsewhere. Distinct from `focused`.
- `disabled` = reduced content alpha + no focus treatment, skipped in d-pad traversal. (Not yet in
  `Focus.kt`; define as a token.)
- Focus signals WHERE you are; the always-visible inline affordance signals WHAT the control does.

## Menu item

- Anatomy: title + optional sub-text + trailing control slot.
- Inline affordances are ALWAYS visible, on every row, not only the focused one - a menu must
  self-document at a glance. Focus adds the fill on top (no lift - see States).

### List scrolling

- Centered cursor: in a scrollable menu list the focused item stays vertically CENTERED and the
  list scrolls beneath a fixed cursor, so the eye never tracks the highlight down the screen.
  Standard for every overflowing menu list.
- Engages only when content exceeds the viewport. A list that fits stays static (no scroll); focus
  just moves between rows.
- End clamping includes NON-FOCUSABLE content. The scroll range is bounded by the content edges -
  including leading/trailing non-focusable sections (headers, descriptions, disabled rows) - not by
  the focusable items. Focusing the first or last FOCUSABLE entry shifts the list to bring those
  end sections FULLY into view (the cursor can never land on them to reveal them by centering).
- Consequence: the cursor is centered in the interior and rides toward the boundary at the head/
  tail (it cannot stay centered with nothing left to scroll) while the end content stays fully
  visible. Centered-interior + clamp-at-ends are one behavior.
- Section headers obey the SAME rule, not a separate one - a header is non-focusable content that
  belongs to its section. Focusing the FIRST focusable row of a section guarantees its header is
  fully in view above; focusing the LAST row before a trailing non-focusable block reveals that
  block. The list-end behavior is just this rule at the outermost sections.
- Section-navigation chrome is aspect-ratio adaptive. WIDE screens put sections in a LEFT RAIL
  (active highlighted) - the rail is the orientation, so no sticky header. On 4:3 / 1:1 devices
  there is no room for the rail, so sections are inline AND the active section header STICKS to the
  top edge while scrolling within it. The centered-cursor + reveal model is identical in both.

## Control modes

Each mode has a deliberately distinct silhouette - no two readable as the same thing.

| Mode | Visual | A / Confirm | Left / Right | Touch |
|---|---|---|---|---|
| Nav row (drills in) | row + chevron `>` | open submenu | - | tap = open |
| Check | checkbox + mark | toggle | - | tap = toggle |
| Toggle | track + knob | flip | left=off / right=on | tap or drag |
| Enum | filled triangles `< value >` | open full list (modal) | cycle one option | tap triangle = cycle, tap value = open list |
| Stepper | `- value +` | - | step down / up | tap -/+ |
| Slider | track-height knob + filled track | - | step down / up | drag / tap track |

- Enum inline cue = small FILLED TRIANGLES that rhyme with the d-pad glyph language, never `<>`
  chevrons (those read as math, not motion). Tint accent on focus; pressed direction flashes.
- Enum is ONE control, two access paths: L/R (or tapping a triangle) is the fast cycle;
  A/Confirm (or tapping the value) opens a modal with all options as a scrollable list. No
  inline-vs-picker split, and A stays "open," never "adjust."
- Stepper stays `- value +` (quantity), distinct from enum (lateral option movement).
- Slider knob is flush with the track (no Material lollipop). Knob distinguishes it from a
  progress bar; continuous fill distinguishes it from a stepper.

### List reorder (sortable list)

THE idiom for ordering items in a vertical list. Region Priority in Sync settings
(`RegionPriorityPopup` in `RegionPickerPopup.kt`) uses it; the sync region filter picker
beside it stays a plain include/exclude list.
One lift/move/drop state machine drives both modalities; the lifted item is the same
object either way.

| Phase | Controller | Touch |
|---|---|---|
| Lift | X on focused sortable row | long-press row (haptic) |
| Move | d-pad up/down, one slot per press | drag; list autoscrolls at viewport edges |
| Drop (commit) | A | release |
| Cancel (revert) | B | - (release commits) |

- A keeps its meaning: commit. X lifts because A on these rows already toggles.
- Lifted row: accent fill + thin accent border, translates with the finger (touch) or slot
  (controller); displaced rows animate out of the way. No scale, no shadow theatrics.
- Rank is shown as a number on each sortable row (position = rank); rows that cannot be
  sorted (disabled block, ordering mode off) show no number and refuse the lift.
- Footer while held: `dpad Move / A Drop / B Cancel`. Idle: X earns its hint (non-obvious).
- Back/B while held cancels the hold FIRST; the surface closes on the next press.
- Reorder within one contiguous block only - drag/move clamps at the block boundary.
- Implementation: `ui/components/DragReorder.kt` (`rememberDragReorderState`,
  `Modifier.dragReorderContainer`, `Modifier.dragReorderItem`); grid reorder (ManagePins)
  and shoulder-nudge (shader stack) predate this idiom and stay as-is.

## Interaction principles

- A/Confirm means enter / commit / toggle - NEVER adjust. Sliders, steppers, and inline enums
  adjust on Left/Right only, so A's meaning stays constant everywhere.
- The input axis is shown by the control itself (triangles, knob, plus/minus), not by words.

## Footer philosophy: the control is the guide

Reduce footer reliance across the board. The inline affordance carries the interaction; the
footer is not a crutch.

- One app-root bottom bar (singleton, push/pop stack). No modal or drawer owns its own footer.
- With self-documenting controls the bar is usually quiet or empty. When empty it collapses
  smoothly by sliding below the screen edge + fading (MotionTokens) - never blanks, never pops.
- Layout contract for the rise-from-below reveal: hint entries are grounded to the TOP of the
  bar container with fixed top padding and FIXED width (no adaptive/hug sizing). The bar then
  translates up from below the screen as a stable unit - no internal reflow, no vertical drift.
  Collapse reverses. (Compose: fixed-width + top-aligned `FooterHintItem`, bar animates offset.)
- Reserved for what is NOT attached to a focused control: screen-global verbs (filter, search,
  compose), shoulder paging between sections, genuinely non-obvious bound buttons.
- Back / A / B / d-pad are low-priority: alone they never justify a bar. The bar appears only to
  surface something non-obvious; if A / B / d-pad are the only candidates, no bar shows.
- Positional layout (matches `FooterHint.kt`): two groups split by a spacer. LEFT = d-pad, then
  bumpers (LB/RB). RIGHT = triggers/menu (LT/RT/Start/Select), then face buttons ordered Y, X, B, A.
- Horizontal real estate is the budget. When a narrow screen or a crowded hint set forces a trim,
  shed the obvious guides FIRST (d-pad, then A/B) - never drop a non-obvious hint (X/Y, trigger,
  shoulder page, global verb) to keep an obvious one. NOTE: `FooterHint.kt` `hidePriority` currently
  inverts this (holds A/B longest, sheds d-pad/bumpers/triggers first) - reconcile in the migration.

## Buttons

Two families already exist as primitives (`PillButton`, `RowButton`); formalize their roles.

- **Action button** (rounded-rect, `radiusControl`) - the command affordance (Play, Save, Delete,
  Continue). NOT a full pill - rect = command. Two tiers:
  - `primary` (WRAPPED fill - accent fill inside a brighter accent outline rim). States by opacity,
    focus IS the cursor:
    - neutral: 80% fill / 90% outline
    - focused: 100% fill / 100% outline (the whole button crisps up as one move - no halo, no movement)
    - disabled: 60% fill + 30% desaturation (dim the label too)
    Accent defaults to theme focus accent but takes an override (e.g. Continue Playing in the game's
    art tint). One ACCENT primary per context; coloration is theme-biased per mode (see Color usage).
  - `secondary` - the SAME wrapped fill in a NEUTRAL color, not accent. SOLID surfaceElevated
    fill at rest (amended 2026-07-06: the translucent treatment read as an outline button on
    device - actionable content is always visibly FILLED, no outline-look anywhere). Focus is
    accent PRESENCE: accent wash over the fill + accent rim + accent-white label. Primary focus
    is a brightness jump instead (fill and rim lerp toward white) since accent is already its
    resting color. Several neutral secondaries are fine, one accent primary per context.
  - Padding 16h / 10v, 44 tall (density pass; was 22h/14v); `pressScale` press feedback (touch + gamepad).
  - `PillButton` primitive swaps its shape `radiusPill` -> `radiusControl` (then mis-named; rename
    is a later cleanup). Full pills are reserved for capsule tokens, never commands.
- **Row** (`RowButton`, `radiusMd` 8dp, full-width) - the list affordance. Plain = list row
  (focus fill); accented = nav/drill row (focus stripe + fill). This IS the menu-item base -
  control modes plug into its trailing slot.
- Anatomy: title + optional subtitle + optional icon (left or right).
- States: `neutral / focused / active / disabled`, plus advanced (progress/working, destructive).
- The "progress/working" button still needs design - show in-progress work without becoming
  confusable with a slider or progress bar (same distinct-silhouette rule as the controls).

## Warning / confirm modal

Today these are raw Material `AlertDialog`s (20 call sites across 10 files - SettingsScreen x8,
4 collections dialogs, GameDataSection, Downloads, FileBrowser, GameModeSelection, CheatDialogs
x4; `ModalInputRouter` passes them through so they escape the custom InputHandler entirely) -
foreign focus model, over-rounded, and
the destructive button uses Material dark `colorScheme.error`, a pale red that is a hard read on
near-black. Replace with an Argosy modal that splits the two signals Material conflates:

- SYMMETRIC base states: both actions rest at the SAME weight - both FILLED neutral
  (surfaceElevated + hairline rim; amended 2026-07-06 from "fill-less/text", which read as
  outline buttons on device). Focus is one consistent signal that reads identically on either.
  The original bug was dramatic base asymmetry - flat-text Cancel vs solid-fill Delete - which
  made the focus cursor illegible on top of two different baselines.
- FOCUS is the standard fill, like everywhere else, applied identically to whichever button is
  focused (neutral/cyan on Cancel, red on Delete). Default focus = the SAFE action (Cancel), never
  the destructive one.
- DESTRUCTIVENESS is a legible accent on the dangerous action - red label/icon, neutral background
  at rest - using a saturated token red (`color.domain.difficulty #E53935` / `semantic.warning`),
  never Material's pale error fill.
- When the destructive action is focused, its fill is red-tinted so the dangerous moment is
  unmistakable. Color says WHICH action is dangerous; fill says WHICH is focused - never the same
  channel.
- Built on `ModalScaffold` + `radiusPanel` (sharp), our buttons, no modal-owned footer.

## Input plumbing - focus footguns

Navigation focus is owned by ONE authority: the custom `InputHandler` / `InputDispatcher` driving a
selection index. Compose's native focus system must NOT navigate. When a second authority goes live
you get the dual-focus ghost - a hidden native cursor that travels to a boundary before the real
(custom) focus moves. These re-arm native focus and cause it; avoid each:

- `Modifier.clickable()` - use `clickableNoFocus`. Plain clickable enables native focus.
- `.focusable()` anywhere except a real text input.
- `.onPreviewKeyEvent` on a focusable element, and especially `return false` at a boundary - that
  return is exactly what tells Compose to run a native focus search (the travel-to-the-end ghost).
  Route the boundary through the custom handler and consume (`return true`).
- `MainActivity.dispatchKeyEvent` falling through to `super` for a navigation key - nav keys must be
  consumed so the native focus system never sees them.
- Material components that own focus (`Button`, `Switch`, `AlertDialog`) - they draw and traverse
  native focus. Use the Argosy primitives instead.

STATUS (2026-07-06 investigation): the offender list below is RESOLVED. The AlertDialogs are
gone; no onPreviewKeyEvent exists anywhere; the 28 text fields already implement the
hidden-slave pattern (requestFocus on index entry, clearFocus on exit; d-pad never reaches a
field, typing keys do). The root sink is KEPT DELIBERATELY as the parking spot - key delivery
never depended on it (dispatchKeyEvent intercepts before the view hierarchy). Mapped nav keys
now consume ACTION_UP too, so native focus search is fully starved, and the last un-neutralized
Material buttons carry canFocus=false. Remaining known gap: three androidx Dialog-window
surfaces (Licenses, SystemizeResult, BIOS distribute-result) run in their own windows outside
the input system - back/touch only, convert to Argosy modals someday.

Historical offender list (kept for context): the root `.focusable()` sink + resume-`requestFocus`
in `ArgosyApp`, the 20 Material `AlertDialog`s, and `MainActivity.dispatchKeyEvent`'s `super`
fallthrough. (`ControllerColumn` / `ControllerRow` exist
only on the old `ui-redesign` branch - do not port them.) If native focus is kept for a11y/IME, it must be a hidden slave mirrored to
the custom index - never self-navigating. The durable fix is a primitive that owns this wiring so
menus declare items instead of re-authoring input; until then, this list is the manual guard.

## V2 build decisions (Penpot `V2 / Controls` page)

Concrete treatments settled while building the control specimen sheet (default + focused columns).

- Icon provider: Phosphor. `caret-left-fill`/`caret-right-fill` = filled d-pad triangles (enum/value
  cues); `caret-right`/`caret-down` (regular) = outline chevrons (nav/expandable); `check-bold` =
  checkmark. Frame-normalize for uniform size (see penpot skill); glyph-center marks inside boxes.
- Dark-mode accent value: `#40C6D6` (white-biased cyan, softer than raw `#00ACC1`); focused
  text/icons use the brighter `#7FE0EC`. This is the dark side of the Color-usage bias rule.
- Toggle is BOXY, not an M3 switch: rounded-square track (h24, r7) + a narrow vertical rounded-rect
  knob (12x20, r5), not a circle. ON = knob right.
- Track slider shares the boxy language: squared track (r2) + a boxy flush thumb (8x8 rounded-square
  at the fill end) - still the track-height-knob rule (flush, no lollipop).
- Color preset / swatches: selected indicator = a white ring around the active swatch (both states).
- Row focus treatment: accent fill wash (~0.15) + accent border + title/value/icons tinted accent.
  EXCEPTIONS: destructive focus is RED (red wash/border/text), not accent; an action button
  solidifies its wrapped fill to 100% on focus. Action at rest = wrapped 82% fill + accent outline,
  squarer (`radiusControl` ~6).

## Resolved (2026-07-02)

- Token reconciliation: LANDED. `tokens.json` carries the full dark ramp (`surfaceElevated
  #262834` added), a real light ramp (cool near-white base `#FBFBFD` -> `#DEE1E9` elevated,
  replacing the flat warm M3 values), radius roles (`panel`/`control`/`pill`), and the density
  metrics. Generated Kotlin regenerated.
- Cover tile focus motion: KEEP `lift` as the single deliberate signature on Home/Library tiles.
- `pressScale`: KEEP as tactile press feedback (momentary, not a focus state).
- Density: decided and recorded above; mechanism is container metrics, not global scale.
- Primitives source: `Focus.kt` / `PillButton` / `RowButton` / `GlassPanel` / `ModalScaffold`
  live on the `ui-redesign` branch (26 commits, 402 behind beta). Decision: PORT the primitives
  + theme substrate (`LocalArgosyTheme`, `pressScale`, motion tier springs, `clickableNoFocus`
  interactionSource overload) onto beta; redo screen conversions fresh. Do not merge the branch.

## In-game suite vs V2 boards (verified 2026-07-06)

The in-game menu suite is IMPLEMENTED and matches the V2 boards in anatomy (pause menu
stack + quick-load history affordance, Manage States split view + carousel fallback,
frame selection, shader chain). Two deviations are ACCEPTED as built, code wins over
canvas:
- Cheats is its own screen with CHEATS/DISCOVER tabs, NOT a fourth in-game Settings tab
  (user-confirmed 2026-07-06; it outgrew the board).
- Quick Load Timeline is a preview + vertical list split, not the board's horizontal strip.
Styling note: these surfaces predate the V2 primitives (bespoke MenuButton/SlotCard);
anatomy is locked, a token-alignment restyle is optional future polish.

## Open items

- Migrate the 20 Material `AlertDialog` call sites to the Argosy warning/confirm modal pattern
  (destructive-confirm subset first: Purge, Reset Library, Reset/Clear Save Cache, Delete
  Collection, Remove Game, Delete Cheat).
- Light-mode QA: ramp VALUES are in tokens; on-device light-mode verification (accent bias,
  bloom washout) is a follow-up phase.
- Centered-cursor list scrolling: `SectionScroll.kt` already centers; still missing end-clamping
  for non-focusable content, no-scroll-when-content-fits, and the aspect-adaptive section rail.
- Guide bar: `FooterHint.hidePriority` inversion fix, singleton push/pop bar, empty-bar collapse
  animation.
- Retrofit the Controls V2 / Buttons V2 Penpot sheets to the density metrics.
