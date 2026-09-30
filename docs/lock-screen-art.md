# Lock screen art

`data/wallpaper/LockScreenArtManager` sets the lock-screen wallpaper (`WallpaperManager.FLAG_LOCK`)
on every `ACTION_SCREEN_OFF`, when the Interface setting `lock_screen_art` is on (default on) and
Argosy is the default home app (`SecondaryHomeComponent.isDefaultHome`).

- A live play session (`PlaySessionTracker.activeSession`) shows that game's hero art: the
  background, including the user's Artwork override, falling back to the cover, then to the
  mosaic when neither loads.
- Otherwise it shows a wall of distinct box art from the whole library
  (`GameRepository.showcaseCovers(null, oneEntryPerGroup = true)`), dimmed so the system clock
  reads over it. No text is drawn; the lock screen puts its own clock on top.
- Art loads through the app's Coil image loader, so covers not yet in Argosy's own cache are
  fetched from the server (with its auth) and land in Coil's disk cache for the next time.
- A key of what was drawn skips the write when nothing changed since the last screen-off.

## Restoring the user's own lock wallpaper

Android 13+ does not let an app read the current lock wallpaper back, so the one the user had
before cannot be saved and restored. When the setting is turned off, or on the next screen-off
after Argosy stops being the home app, the manager clears the lock wallpaper
(`WallpaperManager.clear(FLAG_LOCK)`) and the lock screen falls back to the home wallpaper. It
only clears when it set one (`lock_screen_art_applied`), so a device where the feature never ran
keeps its lock wallpaper untouched.

The lock screen only exists on the default display; a second screen is unaffected.
