#!/system/bin/sh
# Give Argosy access to Android/data, where several emulators keep their saves.
#
# Run this through your device's "run script as root" setting. Do NOT run it
# over adb. It is separate from systemize-argosy.sh on purpose: that one keeps
# Argosy alive under memory pressure, this one unlocks emulator save folders.
# Neither needs the other.
#
# Android hides Android/data from every app on 11 and later, and no permission
# reopens it. This grants Argosy's own uid group access to the real directories
# underneath, which the app reaches without going through that block.
#
# It only works where SELinux is permissive. On an enforcing device the grant is
# written and then ignored, so the script checks first and stops rather than
# pretending it worked.
#
# Re-run after installing a new emulator, and after updating Argosy. New folders
# are created with their own ownership, and Argosy's uid changes if it is ever
# reinstalled rather than updated.
#
# Every run appends to the first writable of /data/local/tmp/argosy-android-data.log
# or /sdcard/argosy-android-data.log.

PKG="${1:-com.nendo.argosy}"
ROOT="/data/media/0"
DATA_DIR="$ROOT/Android/data"

LOG=""
for candidate in \
    /data/local/tmp/argosy-android-data.log \
    /sdcard/argosy-android-data.log \
    /data/argosy-android-data.log
do
    if echo "" >> "$candidate" 2>/dev/null; then
        LOG="$candidate"
        chmod 666 "$LOG" 2>/dev/null
        break
    fi
done

if [ -n "$LOG" ]; then
    exec 3>>"$LOG"
else
    exec 3>&1
fi

log() {
    echo "[argosy-android-data] $1"
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >&3
}

fail() {
    log "FAILED: $1"
    log "--- run ended ---"
    exit 1
}

log "--- run started ---"
log "uid=$(id -u)"

if [ "$(id -u)" != "0" ]; then
    fail "not running as root. Use the device's 'run script as root' option."
fi

ENFORCE=$(getenforce 2>/dev/null)
log "selinux: ${ENFORCE:-unknown}"
if [ "$ENFORCE" = "Enforcing" ]; then
    log "SELinux is enforcing, which blocks an ordinary app from the real storage"
    log "directories no matter what this script changes."
    fail "this device cannot grant Android/data access by permissions alone."
fi

APP_UID=$(dumpsys package "$PKG" 2>/dev/null | grep -m1 "appId=" | sed 's/.*appId=\([0-9]*\).*/\1/')
case "$APP_UID" in
    ''|*[!0-9]*)
        APP_UID=$(dumpsys package "$PKG" 2>/dev/null | grep -m1 "userId=" | sed 's/.*userId=\([0-9]*\).*/\1/')
        ;;
esac
case "$APP_UID" in
    ''|*[!0-9]*) fail "could not read the uid of $PKG. Is Argosy installed?" ;;
esac
log "$PKG runs as uid $APP_UID"

[ -d "$DATA_DIR" ] || fail "$DATA_DIR does not exist"

for parent in /data/media "$ROOT" "$ROOT/Android"; do
    before=$(stat -c '%a' "$parent" 2>/dev/null)
    if chmod o+x "$parent" 2>/dev/null; then
        log "traversal: $parent $before -> $(stat -c '%a' "$parent" 2>/dev/null)"
    else
        fail "could not open traversal on $parent"
    fi
done

GRANTED=0
SKIPPED=0
for pkgdir in "$DATA_DIR"/*; do
    [ -d "$pkgdir" ] || continue
    name=$(basename "$pkgdir")
    case "$name" in
        com.nendo.argosy*) continue ;;
    esac
    if chgrp -R "$APP_UID" "$pkgdir" 2>/dev/null && chmod -R g+rX "$pkgdir" 2>/dev/null; then
        GRANTED=$((GRANTED + 1))
    else
        log "  could not grant $name"
        SKIPPED=$((SKIPPED + 1))
    fi
done

log "granted access to $GRANTED package folder(s), $SKIPPED could not be changed"

PROBE=$(ls "$DATA_DIR" 2>/dev/null | grep -v '^com\.nendo\.argosy' | head -n1)
if [ -n "$PROBE" ]; then
    owner=$(stat -c '%U %G' "$DATA_DIR/$PROBE" 2>/dev/null)
    log "spot check: $PROBE is now owned by $owner (group should be Argosy's uid)"
fi

if [ "$GRANTED" -gt 0 ]; then
    log "OK: Argosy can now read and write these emulator save folders."
    log "No reboot needed. Re-run after installing a new emulator."
else
    fail "nothing was granted; no emulator folders were writable by this script."
fi

log "--- run ended ---"
