#!/usr/bin/env bash
# Runs on a GitHub Actions Android 13 (API 33) x86_64 emulator. Records only what actually happened.
# Usage: emulator-test.sh <debug.apk> <testmedia.apk> <out-dir>
set -u
APK="$1"; MEDIA_APK="$2"; OUT="$3"
PKG=com.fronx.smartlauncher
ACT=$PKG/.launcher.MainActivity
HERE="$(cd "$(dirname "$0")" && pwd)"
UI="python3 $HERE/ui.py"
mkdir -p "$OUT/screenshots"
RESULTS="$OUT/emulator-test-results.md"
PASS=0; FAIL=0
echo "# Emulator test results" > "$RESULTS"
echo "" >> "$RESULTS"
echo "Device: Android emulator, API $(adb shell getprop ro.build.version.sdk | tr -d '\r') (release $(adb shell getprop ro.build.version.release | tr -d '\r')), ABI $(adb shell getprop ro.product.cpu.abi | tr -d '\r')" >> "$RESULTS"
echo "" >> "$RESULTS"
echo "| # | Check | Result | Detail |" >> "$RESULTS"
echo "|---|-------|--------|--------|" >> "$RESULTS"
N=0
record() { # name, ok(0/1), detail
  N=$((N+1)); local r="FAIL"; if [ "$2" = "0" ]; then r="PASS"; PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi
  echo "| $N | $1 | $r | ${3:-} |" >> "$RESULTS"; echo "[$r] $1 ${3:-}"
}
shot() { adb exec-out screencap -p > "$OUT/screenshots/$1.png"; }
png_size() { python3 -c "import struct,sys;d=open('$1','rb').read(32);print(struct.unpack('>II',d[16:24])[0],struct.unpack('>II',d[16:24])[1])"; }
settle() { sleep "${1:-2}"; }
start() { adb shell am start -n "$ACT" >/dev/null 2>&1; settle 3; }
alive() { adb shell pidof "$PKG" | tr -d '\r' | grep -q '[0-9]'; }
tap() { $UI tap "$@"; settle 2; }
has() { $UI has "$@"; }

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0; adb shell settings put global animator_duration_scale 0
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1
adb shell wm size 1280x720; adb shell wm density 160; settle 2
adb logcat -c

# 15. Android 13 compatibility
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[ "$SDK" = "33" ]; record "Runs on Android 13 (API 33)" $? "sdk=$SDK"

# install
OUTI=$(adb install -r "$APK" 2>&1); echo "$OUTI" | grep -q Success; record "Install debug APK" $? "$(echo "$OUTI" | tail -1)"
adb install -r "$MEDIA_APK" >/dev/null 2>&1

# 16. HOME intent resolution
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' > "$OUT/home-resolution.txt"
grep -q "$PKG" "$OUT/home-resolution.txt"; record "Launcher HOME intent resolves to Fronx (ACTION_MAIN + CATEGORY_HOME)" $? "$(grep -c . "$OUT/home-resolution.txt") HOME handlers listed"

# 1. startup (cold)
adb shell am force-stop $PKG
T0=$(date +%s%3N); adb shell am start -W -n "$ACT" > "$OUT/start-time.txt" 2>&1; T1=$(date +%s%3N); settle 3
alive; record "Application startup (process alive after cold start)" $? "$(grep -E 'TotalTime|ThisTime' "$OUT/start-time.txt" | tr '\n' ' ')"
shot 01-home

# 2/3. landscape + 1280x720
SZ=$(png_size "$OUT/screenshots/01-home.png"); [ "$SZ" = "1280 720" ]; record "Landscape rendering at 1280x720" $? "screenshot=$SZ"

# 4. clock/date
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | tr -d '\r' > "$OUT/home-ui.xml"
grep -Eq 'text="[0-9]{1,2}:[0-9]{2}( ?(AM|PM))?"' "$OUT/home-ui.xml"; record "Clock displayed" $? ""
grep -Eq 'text="(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)"' "$OUT/home-ui.xml"; record "Day of week displayed" $? ""

# connectivity must not be fabricated
grep -q 'text="Bluetooth"' "$OUT/home-ui.xml" && grep -q 'text="Wi-Fi"' "$OUT/home-ui.xml"; record "Bluetooth & Wi-Fi status chips present" $? ""

# Weather honest state (no API key in CI)
grep -q 'Not configured' "$OUT/home-ui.xml"; record "Weather shows 'Not configured' (no key supplied; nothing fabricated)" $? ""

# media: no access yet
grep -q "Open notification access" "$OUT/home-ui.xml"; record "Media card explains notification access when not granted" $? ""

# 5/6. drawer + search
tap "Apps" lowest; shot 02-drawer
has "Applications"; record "Application drawer opens" $? ""
APPS_ROWS=$(adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | wc -l)
record "Drawer lists installed apps" $([ "$APPS_ROWS" -gt 8 ] && echo 0 || echo 1) "$APPS_ROWS text nodes"
adb shell input tap 640 80; settle 1; adb shell input text "settings"; settle 2; shot 03-search
$UI hasabove "Settings" 600; record "Search 'settings' finds the Settings app in the grid" $? ""
adb shell input keyevent KEYCODE_MOVE_END; for i in $(seq 1 8); do adb shell input keyevent KEYCODE_DEL; done; adb shell input text "zzqqxx"; settle 2; shot 04-search-empty
has 'No apps match' "first" contains; record "Search with no result shows empty state" $? ""
for i in $(seq 1 8); do adb shell input keyevent KEYCODE_DEL; done; adb shell input keyevent KEYCODE_BACK; settle 1

# 7. shortcuts: filter the drawer to one app, launch it through the drawer
filter_apps() { adb shell input tap 640 70; settle 1; for i in $(seq 1 12); do adb shell input keyevent KEYCODE_DEL; done; adb shell input text "$1"; settle 2; adb shell input keyevent KEYCODE_BACK; settle 1; }
for i in $(seq 1 8); do adb shell input keyevent KEYCODE_DEL; done
filter_apps settings
$UI tap "Settings" first; settle 3
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -2 > "$OUT/top-activity.txt"
grep -q "com.android.settings" "$OUT/top-activity.txt"; record "Drawer launches an app via intent (Android Settings opened)" $? "$(head -1 "$OUT/top-activity.txt" | tr -s ' ' | cut -c1-120)"
shot 05-launched-settings
adb shell input keyevent KEYCODE_BACK; settle 2; adb shell am start -n "$ACT" >/dev/null; settle 2

# 8. favourites persistence (Chrome is not a default favourite, so this is a real change)
tap "Apps" lowest
filter_apps chrome
$UI hold "Chrome" first; settle 2
shot 06-fav-menu
has "Add to favourites"; record "Long-press app opens favourites menu" $? ""
$UI tap "Add to favourites" first; settle 2
adb shell am force-stop $PKG; start; tap "Apps" lowest; tap "Favourites" first
shot 07-favourites
$UI hasabove "Chrome" 600; record "Favourite (Chrome) persisted across force-stop and shows under Favourites" $? ""
adb shell run-as $PKG ls files/datastore 2>/dev/null | tr -d '\r' > "$OUT/datastore-files.txt"
grep -q "fronx_launcher" "$OUT/datastore-files.txt"; record "DataStore file written (fronx_launcher.preferences_pb)" $? "$(cat "$OUT/datastore-files.txt" | tr '\n' ' ')"
adb shell run-as $PKG cat files/datastore/fronx_launcher.preferences_pb 2>/dev/null | strings -n 3 > "$OUT/fav-prefs-strings.txt"
grep -q "com.android.chrome" "$OUT/fav-prefs-strings.txt"; record "Chrome package stored in persisted favourites" $? ""

# remaining dock screens (screenshots + presence checks)
tap "Navigation" lowest; shot 18-navigation; has "Favourite destinations"; record "Navigation screen opens from dock" $? ""
tap "Music" lowest; shot 19-music; has "Music apps"; record "Music screen opens from dock" $? ""
tap "Phone" lowest; shot 20-phone; has "Phone & connectivity"; record "Phone screen opens from dock" $? ""
tap "Home" lowest

# 9. media session handling
adb shell cmd notification allow_listener $PKG/com.fronx.smartlauncher.media.MediaNotificationListener >/dev/null 2>&1   # test harness stands in for the user's grant
adb shell am start -n com.fronx.testmedia/.MainActivity >/dev/null 2>&1; settle 3
adb shell am start -n "$ACT" >/dev/null; settle 3
adb shell input keyevent KEYCODE_HOME; settle 1
adb shell cmd package set-home-activity $ACT >/dev/null 2>&1   # stands in for choosing the default Home app
adb shell input keyevent KEYCODE_HOME; settle 3
shot 08-media
has "Test Track"; record "Media: track title from a real MediaSession" $? ""
has "Fronx Test Artist"; record "Media: artist shown" $? ""
has "Test Media Player"; record "Media: media app name shown" $? ""
has "Playing"; record "Media: playback status shown" $? ""
POS=$($UI textof "Playback position"); echo "$POS" | grep -Eq '^[0-9]+:[0-9]{2}$' && [ "$POS" != "3:20" ]; record "Media: live progress position shown" $? "position=$POS"
has "3:20"; record "Media: duration shown (3:20)" $? ""
adb logcat -c
# controls are found by their accessibility labels
$UI tap "Play or pause" first; settle 2
adb logcat -d -s FronxTestMedia:I | tr -d '\r' > "$OUT/media-log.txt"
grep -q "onPause" "$OUT/media-log.txt"; record "Media: play/pause control reaches the media app (onPause received)" $? "$(grep -E 'onPause|onPlay' "$OUT/media-log.txt" | tail -1)"
shot 09-media-paused
$UI tap "Next track" first; settle 2
adb logcat -d -s FronxTestMedia:I | tr -d '\r' > "$OUT/media-log.txt"
grep -q "onSkipToNext" "$OUT/media-log.txt"; record "Media: next control reaches the media app" $? ""
$UI tap "Previous track" first; settle 2
adb logcat -d -s FronxTestMedia:I | tr -d '\r' > "$OUT/media-log.txt"
grep -q "onSkipToPrevious" "$OUT/media-log.txt"; record "Media: previous control reaches the media app" $? ""

# 10. settings navigation
tap "Settings" lowest; shot 10-settings
has "Display"; record "Settings screen opens from dock" $? ""
tap "Bluetooth" first; settle 2
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -2 > "$OUT/top-activity2.txt"
grep -qi "settings" "$OUT/top-activity2.txt"; record "Bluetooth tile opens Android Bluetooth settings" $? ""
shot 11-bluetooth-settings
adb shell input keyevent KEYCODE_BACK; settle 2; adb shell am start -n "$ACT" >/dev/null; settle 2

# 11. preference persistence (24-hour clock)
tap "Settings" lowest; tap "Customize launcher" first
shot 12-customize
tap "24-hour" first
adb shell am force-stop $PKG; start
adb shell run-as $PKG cat files/datastore/fronx_launcher.preferences_pb 2>/dev/null | strings -n 3 | tr -d '\r' > "$OUT/prefs-strings.txt"
grep -q "H24" "$OUT/prefs-strings.txt"; record "Clock-format preference persisted to DataStore (H24)" $? ""
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | tr -d '\r' > "$OUT/home-ui24.xml"
grep -Eq 'text="[0-9]{2}:[0-9]{2}"' "$OUT/home-ui24.xml"; record "24-hour clock applied after restart" $? ""
shot 13-home-24h

# 14. activity recreation (font scale change forces recreate; navigation state must survive)
tap "Apps" lowest
adb shell settings put system font_scale 1.3; settle 3
alive; record "Activity recreation on config change: process alive" $? ""
has "Applications"; record "Activity recreation preserves current screen (Applications)" $? ""
shot 14-recreated
adb shell settings put system font_scale 1.0; settle 2

# 13. rotation behaviour: sensorLandscape must never go portrait
adb shell settings put system user_rotation 0; settle 3; shot 15-rot-portrait-request
SZ=$(png_size "$OUT/screenshots/15-rot-portrait-request.png"); W=${SZ% *}; H=${SZ#* }
[ "$W" -gt "$H" ]; record "Stays landscape when portrait is requested" $? "screenshot=$SZ"
adb shell settings put system user_rotation 3; settle 3; shot 16-rot-reverse-landscape
SZ=$(png_size "$OUT/screenshots/16-rot-reverse-landscape.png"); W=${SZ% *}; H=${SZ#* }
[ "$W" -gt "$H" ]; record "Reverse-landscape renders correctly" $? "screenshot=$SZ"
alive; record "No crash across rotations" $? ""
adb shell settings put system user_rotation 1; settle 2

# 12. offline operation
adb shell svc wifi disable >/dev/null 2>&1; adb shell svc data disable >/dev/null 2>&1; settle 3
adb shell am force-stop $PKG; start; shot 17-offline
alive; record "Offline: launcher starts and stays alive with Wi-Fi and data off" $? ""
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | tr -d '\r' > "$OUT/offline-ui.xml"
grep -Eq 'text="[0-9]{2}:[0-9]{2}"' "$OUT/offline-ui.xml"; record "Offline: dashboard content rendered" $? ""
adb shell svc wifi enable >/dev/null 2>&1; adb shell svc data enable >/dev/null 2>&1

# HOME key returns to Fronx when it is the default
adb shell am start -n com.android.settings/.Settings >/dev/null 2>&1; settle 2
adb shell input keyevent KEYCODE_HOME; settle 2
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1 > "$OUT/top-home.txt"
grep -q "$PKG" "$OUT/top-home.txt"; record "HOME key returns to Fronx once chosen as default Home" $? ""

# stability
adb logcat -d | tr -d '\r' > "$OUT/logcat-full.txt"
grep -n "FATAL EXCEPTION" "$OUT/logcat-full.txt" | head -5 > "$OUT/fatal.txt"
if grep -q "$PKG" "$OUT/fatal.txt" 2>/dev/null || (grep -A3 "FATAL EXCEPTION" "$OUT/logcat-full.txt" | grep -q "$PKG"); then F=1; else F=0; fi
record "No FATAL EXCEPTION involving $PKG in logcat" $F ""
grep -q "ANR in $PKG" "$OUT/logcat-full.txt"; record "No ANR for $PKG" $([ $? -eq 0 ] && echo 1 || echo 0) ""
tail -n 4000 "$OUT/logcat-full.txt" > "$OUT/logcat-tail.txt"; rm -f "$OUT/logcat-full.txt"

# uninstall must work without any recovery step
adb shell cmd package set-home-activity com.android.launcher3/.uioverrides.QuickstepLauncher >/dev/null 2>&1
adb uninstall $PKG 2>&1 | grep -q Success; record "Launcher uninstalls cleanly (no firmware recovery needed)" $? ""

echo "" >> "$RESULTS"; echo "**Totals: $PASS passed, $FAIL failed.**" >> "$RESULTS"
echo "" >> "$RESULTS"; echo "Notes: notification-listener grant and set-home-activity are performed by the test harness through adb to stand in for the user's choices in Android's own UI." >> "$RESULTS"
cat "$RESULTS"
exit 0
