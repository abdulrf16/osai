#!/usr/bin/env bash
# Install + launch smoke test for older Android versions. Records only what actually happened.
# Usage: smoke-test.sh <apk> <out-dir>
set -u
APK="$1"; OUT="$2"; PKG=com.fronx.smartlauncher; ACT=$PKG/.launcher.MainActivity
HERE="$(cd "$(dirname "$0")" && pwd)"; UI="python3 $HERE/ui.py"
mkdir -p "$OUT/screenshots"; R="$OUT/compat-results.md"
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "# Compatibility smoke test - Android API $SDK (release $(adb shell getprop ro.build.version.release | tr -d '\r'))" > "$R"
echo "" >> "$R"; echo "| # | Check | Result | Detail |" >> "$R"; echo "|---|---|---|---|" >> "$R"
N=0; PASS=0; FAIL=0
record() { N=$((N+1)); local r=FAIL; [ "$2" = "0" ] && { r=PASS; PASS=$((PASS+1)); } || FAIL=$((FAIL+1)); echo "| $N | $1 | $r | ${3:-} |" >> "$R"; echo "[$r] $1 ${3:-}"; }
alive() { adb shell "ps -A 2>/dev/null || ps" | tr -d '\r' | grep -q "$PKG"; }
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard >/dev/null 2>&1
adb shell wm size 1280x720; adb shell wm density 160; sleep 2
adb logcat -c
OUTI=$(adb install -r "$APK" 2>&1); echo "$OUTI" | grep -q Success; record "Install release APK" $? "$(echo "$OUTI" | tail -1)"
adb shell am start -W -n "$ACT" > "$OUT/start.txt" 2>&1; sleep 6
alive; record "App process alive after launch" $? "$(grep -E 'TotalTime' "$OUT/start.txt" | tr -d '\r')"
adb exec-out screencap -p > "$OUT/screenshots/home-api$SDK.png"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | tr -d '\r' > "$OUT/ui.xml"
grep -q 'text="Home"' "$OUT/ui.xml" && grep -q 'text="Settings"' "$OUT/ui.xml"; record "Dock rendered (Home ... Settings)" $? ""
grep -Eq 'text="[0-9]{1,2}:[0-9]{2}' "$OUT/ui.xml"; record "Clock rendered" $? ""
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG" || adb shell pm resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG" || adb shell dumpsys package "$PKG" | grep -q "android.intent.category.HOME"
record "HOME intent filter registered" $? ""
$UI tap "Apps" lowest; sleep 3; adb exec-out screencap -p > "$OUT/screenshots/apps-api$SDK.png"
$UI has "Applications"; record "Application drawer opens" $? ""
adb logcat -d | tr -d '\r' > "$OUT/logcat.txt"
(grep -A4 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG") && F=1 || F=0
record "No FATAL EXCEPTION involving $PKG" $F ""
tail -n 600 "$OUT/logcat.txt" > "$OUT/logcat-tail.txt"; rm -f "$OUT/logcat.txt"
adb uninstall $PKG 2>&1 | grep -q Success; record "Uninstalls cleanly" $? ""
echo "" >> "$R"; echo "**Totals: $PASS passed, $FAIL failed.**" >> "$R"; cat "$R"; exit 0
