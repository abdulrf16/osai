# Emulator test results

Device: Android emulator, API 33 (release 13), ABI x86_64

| # | Check | Result | Detail |
|---|-------|--------|--------|
| 1 | Runs on Android 13 (API 33) | PASS | sdk=33 |
| 2 | Install debug APK | PASS | Success |
| 3 | Launcher HOME intent resolves to Fronx (ACTION_MAIN + CATEGORY_HOME) | PASS | 10 HOME handlers listed |
| 4 | Application startup (process alive after cold start) | PASS | TotalTime: 1448  |
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
| 15 | Drawer launches an app via intent (Android Settings opened) | FAIL |  topResumedActivity=ActivityRecord{3b216d3 u0 com.fronx.smartlauncher/.launcher.MainActivity} t9} |
| 16 | Long-press app opens favourites menu | FAIL |  |
| 17 | Favourite persisted across force-stop (shown under Favourites) | PASS |  |
| 18 | DataStore file written (fronx_launcher.preferences_pb) | FAIL |  |
| 19 | Media: track title from a real MediaSession | PASS |  |
| 20 | Media: artist shown | PASS |  |
| 21 | Media: media app name shown | PASS |  |
| 22 | Media: playback status shown | PASS |  |
| 23 | Media: progress position shown | FAIL |  |
| 24 | Media: duration shown (3:20) | PASS |  |
| 25 | Media: play/pause control reaches the media app (onPause received) | PASS | 10-31 13:14:30.494  7424  7424 I FronxTestMedia: onPause |
| 26 | Media: next control reaches the media app | PASS |  |
| 27 | Media: previous control reaches the media app | PASS |  |
| 28 | Settings screen opens from dock | PASS |  |
| 29 | Bluetooth tile opens Android Bluetooth settings | PASS |  |
| 30 | Clock-format preference persisted to DataStore (H24) | FAIL |  |
| 31 | 24-hour clock applied after restart | PASS |  |
| 32 | Activity recreation on config change: process alive | PASS |  |
| 33 | Activity recreation preserves current screen (Applications) | PASS |  |
| 34 | Stays landscape when portrait is requested | PASS | screenshot=1280 720 |
| 35 | Reverse-landscape renders correctly | PASS | screenshot=1280 720 |
| 36 | No crash across rotations | PASS |  |
| 37 | Offline: launcher starts and stays alive with Wi-Fi and data off | PASS |  |
| 38 | Offline: dashboard content rendered | PASS |  |
| 39 | HOME key returns to Fronx once chosen as default Home | PASS |  |
| 40 | No FATAL EXCEPTION involving com.fronx.smartlauncher in logcat | PASS |  |
| 41 | No ANR for com.fronx.smartlauncher | PASS |  |
| 42 | Launcher uninstalls cleanly (no firmware recovery needed) | PASS |  |

**Totals: 37 passed, 5 failed.**

Notes: notification-listener grant and set-home-activity are performed by the test harness through adb to stand in for the user's choices in Android's own UI.
