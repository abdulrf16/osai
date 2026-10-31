# Fronx Smart Launcher 1.0.0 - features

Package `com.fronx.smartlauncher` · min Android 10 (API 29) · targets Android 13 (API 33) · landscape ·
Kotlin + Jetpack Compose + Material 3 (custom theme) + DataStore + Coroutines/Flow · no analytics, no accounts.

## Dashboard (1280 x 720 landscape, three zones + fixed dock)
| Zone | Width | Content |
|---|---|---|
| Info | ~27% | large light-weight clock, day, date, weather card, Navigation & Phone shortcuts |
| Media | ~43% | artwork, title, artist, source app, status, prev/play-pause/next, progress |
| Quick apps | ~30% | 2 x 4 grid of installed favourites + Bluetooth tile + All apps |

* Top status bar: time, date, Bluetooth and Wi-Fi state (real state only: On/Off/Connected/N/A), Settings button.
* Zones hidden in Customize give their width back to the others. "Mirrored" layout swaps left/right.
* Fixed dock (Home, Apps, Navigation, Music, Phone, Settings) with a blue active indicator, same on every screen.
  It never touches Android's own navigation (no immersive mode, no key interception).

## Applications
* Launchable apps found with `PackageManager.queryIntentActivities(MAIN/LAUNCHER)` under a narrow `<queries>` block.
  **`QUERY_ALL_PACKAGES` is not used** - the launcher-intent query is sufficient.
* Icons and names loaded dynamically; scrolling grid; configurable 4-7 columns; instant search with an empty state.
* Favourites: long-press an app > Add/Remove, or Settings > Customize > Favourite apps. Persisted in DataStore.
* Defaults (until you customise): Maps, YouTube Music, Spotify, Phone, Radio, Settings - **only those actually installed**.
  Package names are candidates, never assumed present.

## Media
* `MediaSessionManager` + `MediaController`: title, artist, album, artwork, playing/paused/buffering, position/duration.
* Controls are enabled only when the app advertises the action; progress only when the app reports position + duration.
* Needs the user to enable **Notification access** (explained in the card, with a button to the settings screen).
  Fronx's listener service is empty - it never reads notifications.
* Listeners exist only while the launcher is on screen; the progress ticker runs only while playing and visible.
* Artwork supplied only as a URI (not a bitmap) is not loaded; a clean placeholder is shown instead.

## Navigation
* Google Maps shortcut (or any installed maps app), choose your preferred navigation app (installed apps only).
* Favourite destinations (name + address) and one-tap fuel/EV/parking/restaurant searches, using documented
  `google.navigation:` / `geo:` intents. No paid SDK, **no location permission, no location collection**.

## Phone, Bluetooth, Wi-Fi
* Shortcuts to the dialer, contacts, Bluetooth settings and Wi-Fi settings through standard Android intents.
  No hidden APIs, no runtime permissions. Pairing/calls stay with the head unit's own apps.

## Quick settings
Display, Sound, Brightness (opens Display settings - Android offers no public direct control), Bluetooth, Wi-Fi,
Android Settings, Launcher customization. Plus default-Home-app helper and notification-access status.
No `WRITE_SECURE_SETTINGS` or privileged permissions.

## Personalisation (DataStore, local only)
12/24-hour clock · dark/light theme · 5 accent colours · wallpaper (Plain/Carbon/Midnight/your own image) ·
widget visibility (weather/media/quick apps) · layout (Standard/Mirrored) · drawer grid size · °C/°F · weather city.

## Weather (optional)
OpenWeatherMap current-weather API, used only if you build with a key (`weather.apiKey` in git-ignored
`local.properties` or env `FRONX_WEATHER_API_KEY`). Last response cached; refreshed at most every 30 min while the
launcher is visible; never blocks startup. States: not configured / set a city / loading / unavailable (offline) /
ready (marked stale when offline).

## Home launcher
`MAIN` + `HOME` + `DEFAULT` intent filter on an exported `singleTask` activity (plus a normal `LAUNCHER` entry so it can be
opened before being made default). Default selection uses Android's role request or Settings > Home app; never silent.

## Safety / scope (deliberately NOT done)
No CAN bus, ECU, MCU, diagnostics, gearbox, steering, camera or reverse-camera access; no vehicle speed/fuel/RPM/gear/tyre/door
data (none is shown, none is faked); no root; no firmware changes; no interception of system navigation or emergency functions.

## Robustness
Every external launch is wrapped (a missing app shows a toast instead of crashing). The last crash stack trace is saved
privately and shown in Settings. Home screen loads with no network. Permissions: INTERNET (weather only),
ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, BLUETOOTH (API <= 30 only). Nothing else.
