# Argosy RetroAchievements hardcore policy

Select for changes to `LibretroActivity`, `HotkeyDispatcher`, `InGameMenu`, cheats, rewind, save
states, launch modes, play options, save caches, or anything under RetroAchievements. Hardcore is
strict by intent. Source: ra-compliance skill. All rules are blocking. Loosening hardcore is only
acceptable as a standalone change that says so.

- Quick save, quick load and rewind never run in hardcore, and the gate does not depend on netplay
  role.
- In hardcore, the rewind buffer is not allocated, state support is off, and auto-save-state and
  auto-restore do not run.
- `InGameMenu` shows no state rows in hardcore.
- No cheat reaches the core in hardcore, and every new cheat caller passes the live hardcore flag.
- All four secure-saves strip layers stay intact: the launch delegate strip, the
  `LibretroActivity` intent downgrade, the `switchToHardcore` secure-saves gate, and
  `hardcoreAvailable` requiring secure saves.
- A NEW launch mode caches a rollback before deleting `.srm` or state slots.
- Resume grants hardcore only through `isValidHardcoreSave` (flag and trailer); the demote-to-casual
  branch stays.
- The hardcore flag and trailer survive end to end, and trailer bytes are stripped before bytes
  reach the core or disk. Isolation never keys on the deprecated `"HARDCORE"` slot string.
- On servers without snapshots, hardcore saves never go into a named channel. On RomM snapshot
  servers, hardcore saves stay in the active channel and isolation is the `isHardcore` column plus
  the trailer. `SnapshotSyncRouter.sessionChannel` is the only place that decides this.
- `restoreSaveForLaunchMode` takes no timestamp parameter, and `activeSaveApplied` is not overridden.
- SRAM fallback in resume-hardcore never touches state slots.
- `libretro/speedrun/` references no hardcore, save state, SRAM, rewind or core memory code.
- Hardcore awards are never queued for offline replay, and no path promotes a casual unlock to
  hardcore.
- `generateValidation` keeps its input order and MD5 lowercase hex; `handleAuthFailure` stays empty
  of substring-based credential clearing.
- An RA API endpoint or model change cites the matching vendored rcheevos `rc_api_*` builder or
  parser and its test fixture.
- A new hardcore-adjacent feature (overlay, input path, save mechanism, core capability, core memory
  access) cites RetroAchievements docs or rcheevos for its legality.
- Netplay guests launch only in casual, and new hardcore is not confirmable offline.
- The stored play-mode tokens `ask`, `casual` and `hardcore` do not change.
