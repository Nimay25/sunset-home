#!/usr/bin/env bash
# Installs Sunset Home on Raj's TV and gives it the HOME button (Fire OS 7).
#   ./install.sh          install build/sunset-home.apk and take over HOME
#   ./install.sh --undo   give HOME back to Amazon's launcher (the app stays installed)
# HOME takeover adapted from dumbtv home's launcher/install.sh (MIT).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

PKG=tv.rajs.sunset
GUARD=$PKG/$PKG.Guard
LISTENER=$PKG/$PKG.NowPlaying
SERIAL=${ADB_SERIAL:?set ADB_SERIAL to your TV, e.g. ADB_SERIAL=192.168.1.50:5555}
sh_() { adb -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }

[[ $(adb -s "$SERIAL" get-state 2>/dev/null) == device ]] || { echo "error: TV not connected: adb connect $SERIAL" >&2; exit 1; }

if [[ ${1:-} == --undo ]]; then
  sh_ am force-stop $PKG >/dev/null 2>&1 || true
  sh_ cmd notification disallow_listener $LISTENER >/dev/null 2>&1 || true
  sh_ settings delete secure enabled_accessibility_services >/dev/null
  sh_ settings put secure accessibility_enabled 0
  sh_ settings delete global alarm_manager_constants >/dev/null
  sh_ dumpsys deviceidle whitelist -$PKG >/dev/null 2>&1 || true
  sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME -p com.amazon.tv.launcher >/dev/null 2>&1 || true
  echo "HOME is Amazon's again (Sunset Home stays installed: adb uninstall $PKG)"
  exit 0
fi

out=$(adb -s "$SERIAL" install -r build/sunset-home.apk 2>&1 | grep -m1 -E 'Success|Failure|failed' || true)
[[ $out == *Success* ]] || { echo "error: install failed: $out" >&2; exit 1; }
echo "installed $PKG"

sh_ dumpsys deviceidle whitelist +$PKG >/dev/null
# Media access for the now-playing card (what is playing, play/pause/skip). It reads no notifications.
sh_ cmd notification allow_listener $LISTENER
sh_ cmd package set-home-activity $PKG/.Home >/dev/null 2>&1 || true
# Alarms normally fire no sooner than 5 s from now; the HOME switch needs them immediate.
sh_ settings put global alarm_manager_constants min_futurity=0
# Clear first so the accessibility manager rebinds a service whose process was replaced.
sh_ settings put secure enabled_accessibility_services '""' >/dev/null
sleep 1
sh_ settings put secure enabled_accessibility_services $GUARD
sh_ settings put secure accessibility_enabled 1

bound=0
for _ in $(seq 1 20); do
  if sh_ dumpsys accessibility | grep -qE 'label=Sunset Home button|label=tv\.rajs\.sunset\.Guard'; then bound=1; break; fi
  sleep 1
done
[[ $bound == 1 ]] && echo "HOME guard bound" || echo "warning: the HOME guard did not show as bound yet"

sh_ input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
sh_ am start -n $PKG/.Home >/dev/null
sleep 2
echo "front: $(sh_ dumpsys window | grep -m1 mCurrentFocus | sed 's/.* u0 //; s/}$//')"
