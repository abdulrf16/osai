# Emulator test results

Device: Android emulator, API 33 (release 13), ABI x86_64

| # | Check | Result | Detail |
|---|-------|--------|--------|
| 1 | Runs on Android 13 (API 33) | PASS | sdk=33 |
| 2 | Install debug APK | PASS | Success |
| 3 | Launcher HOME intent resolves to Fronx (ACTION_MAIN + CATEGORY_HOME) | PASS | 10 HOME handlers listed |
| 4 | Application startup (process alive after cold start) | PASS | TotalTime: 5130  |
| 5 | Landscape rendering at 1280x720 | PASS | screenshot=1280 720 |
| 6 | Clock displayed | PASS |  |
| 7 | Day of week displayed | PASS |  |
| 8 | Bluetooth & Wi-Fi status chips present | PASS |  |
| 9 | Weather shows 'Not configured' (no key supplied; nothing fabricated) | PASS |  |
| 10 | Media card explains notification access when not granted | PASS |  |
| 11 | Application drawer opens | PASS |  |
| 12 | Drawer lists installed apps | PASS | 59 text nodes |
| 13 | Search 'settings' finds the Settings app in the grid | PASS |  |
| 14 | Search with no result shows empty state | PASS |  |
| 15 | Drawer launches an app via intent (Android Settings opened) | PASS |  topResumedActivity=ActivityRecord{a7631bc u0 com.android.settings/.Settings$NetworkDashboardActivity} t10} |
| 16 | Long-press app opens favourites menu | PASS |  |
| 17 | Favourite (Chrome) persisted across force-stop and shows under Favourites | PASS |  |
| 18 | DataStore file written (fronx_launcher.preferences_pb) | PASS | fronx_launcher.preferences_pb  |
| 19 | Chrome package stored in persisted favourites | PASS |  |
| 20 | Navigation screen opens from dock | PASS |  |
| 21 | Music screen opens from dock | PASS |  |
| 22 | Phone screen opens from dock | PASS |  |
| 23 | Media: track title from a real MediaSession | PASS |  |
| 24 | Media: artist shown | PASS |  |
| 25 | Media: media app name shown | PASS |  |
| 26 | Media: playback status shown | PASS |  |
| 27 | Media: live progress position shown | PASS | position=0:49 |
| 28 | Media: duration shown (3:20) | PASS |  |
| 29 | Media: play/pause control reaches the media app (onPause received) | PASS | 10-31 14:05:18.086  8044  8044 I FronxTestMedia: onPause |
| 30 | Media: next control reaches the media app | PASS |  |
| 31 | Media: previous control reaches the media app | PASS |  |
| 32 | Settings screen opens from dock | PASS |  |
| 33 | Bluetooth tile opens Android Bluetooth settings | PASS |  |
| 34 | Clock-format preference persisted to DataStore (H24) | PASS |  |
| 35 | 24-hour clock applied after restart | PASS |  |
| 36 | Activity recreation on config change: process alive | PASS |  |
| 37 | Activity recreation preserves current screen (Applications) | PASS |  |
| 38 | Rotation 0 (landscape) renders at landscape size | PASS | screenshot=1280 720 |
| 39 | Rotation 180 (reverse landscape) renders at landscape size | PASS | screenshot=1280 720 |
| 40 | Reverse landscape: UI still present and usable | PASS |  |
| 41 | Portrait display forced (tablet-class screen letterboxes a landscape-only app): no crash | PASS | screenshot=720 1280 |
| 42 | Portrait display forced: dock still rendered (degraded layout, not a failure) | PASS |  |
| 43 | No crash across rotations | PASS |  |
| 44 | Offline: launcher starts and stays alive with Wi-Fi and data off | PASS |  |
| 45 | Offline: dashboard content rendered | PASS |  |
| 46 | HOME key returns to Fronx once chosen as default Home | PASS |  |
| 47 | No FATAL EXCEPTION involving com.fronx.smartlauncher in logcat | PASS |  |
| 48 | No ANR for com.fronx.smartlauncher | PASS |  |
| 49 | Launcher uninstalls cleanly (no firmware recovery needed) | PASS |  |

**Totals: 49 passed, 0 failed.**

Notes: notification-listener grant and set-home-activity are performed by the test harness through adb to stand in for the user's choices in Android's own UI.
