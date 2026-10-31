# Fronx Smart Launcher

Premium Dark automotive home launcher for the Maruti Suzuki Fronx Delta Plus AGS head unit
(Android 13, 1280 x 720 landscape). Standalone app: no root, no vehicle-bus access, no firmware changes.

* **Install:** `INSTALLATION.md` · **Features:** `FEATURES.md` · **Build record:** `BUILD-INFO.md`
* **Emulator test record + screenshots:** `emulator/`
* APKs: `Fronx-Smart-Launcher-release.apk` (signed) and `Fronx-Smart-Launcher-debug.apk`; checksums in `SHA256SUMS.txt`.

## Honest status
* Built and tested with GitHub Actions on an **Android 13 (API 33) x86_64 emulator forced to 1280x720 @160dpi**.
  The results table in `emulator/emulator-test-results.md` lists every check and whether it passed (final run: 49 passed, 0 failed).
  Caveat: the emulator uses a tablet profile with its own taskbar, and the test harness grants notification access and sets the
  default Home app through `adb` to stand in for the user's choices. Earlier runs found and fixed bugs in the *tests* (not the app);
  one run showed that on a tablet-size screen forced into portrait, Android letterboxes this landscape-only app and the dashboard is cramped.
* **Not tested on the real head unit.** Chipset, USB-install support and Home-app switching on that unit are unverified.
  The APK has no app-specific native code, only small AndroidX libraries, which are bundled for arm64-v8a, armeabi-v7a, x86 and
  x86_64, so it should install on any common head-unit CPU. "Should" is not "tested": compatibility is unproven until you try it.
* **Android Auto / phone projection: not tested.** When Android Auto is projecting, Google's app draws the screen with its own
  theme; Fronx has no influence over it. Which launcher the unit's Home button returns to afterwards is firmware behaviour that
  has not been verified. Media playing on the phone through Android Auto will most likely not appear in Fronx's media card.
* The release APK in this folder is signed with a key generated for that CI run (random password, never stored).
  Its signature therefore differs from any later build; to ship updates that install over each other, use your own key (below).

## Project layout (`fronx-smart-launcher/`)
```
app/src/main/java/com/fronx/smartlauncher/
  app/           application class + manual DI container, crash recorder
  design/        palette, theme, typography, canvas icons, shared components
  preferences/   LauncherPreferences model + DataStore repository
  applications/  PackageManager repository, app drawer UI, quick-tile logic
  media/         MediaSession repository, media card, music screen, (empty) notification listener
  connectivity/  Bluetooth/Wi-Fi state, phone screen
  weather/       optional OpenWeatherMap client + cache
  navigation/    navigation hub UI
  dashboard/     home dashboard
  settings/      quick settings, customization, favourites
  launcher/      MainActivity (HOME), dock, root navigation, ViewModel
  utils/         safe intent helpers, formatting, bitmap helpers
testmedia/       CI-only helper app that publishes a real MediaSession for the emulator test
tools/           emulator-test.sh + ui.py (used by CI)
../.github/workflows/fronx-build.yml
```

## Build it yourself
Requirements: JDK 17, Android SDK (platform 34, build-tools), internet access to Google Maven.
```bash
cd fronx-smart-launcher
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease     # unsigned unless signing is configured, see below
```
Outputs: `app/build/outputs/apk/{debug,release}/`.

### Your own release key (recommended)
```bash
keytool -genkeypair -v -keystore fronx-release.jks -alias fronx -keyalg RSA -keysize 3072 -validity 9125
```
Create `keystore.properties` (git-ignored; never commit it or the `.jks`):
```
storeFile=/absolute/path/fronx-release.jks
storePassword=...
keyAlias=fronx
keyPassword=...
```
or export `FRONX_KEYSTORE_FILE`, `FRONX_KEYSTORE_PASSWORD`, `FRONX_KEY_ALIAS`, `FRONX_KEY_PASSWORD`.
For CI, add repository secrets `FRONX_KEYSTORE_B64` (base64 of the .jks), `FRONX_KEYSTORE_PASSWORD`, `FRONX_KEY_ALIAS`, `FRONX_KEY_PASSWORD`;
the workflow uses them automatically instead of generating a throw-away key. Back the keystore up: without it you cannot publish updates.

### Weather (optional)
Put `weather.apiKey=YOUR_OPENWEATHERMAP_KEY` in git-ignored `local.properties` (or export `FRONX_WEATHER_API_KEY`) and rebuild.
Then set a city in Settings > Customize launcher. Without a key the card says "Not configured".

## Safety statement
Never interact with the launcher while driving. The app shows no vehicle data and does not touch camera/reverse-camera,
CAN, ECU, MCU or diagnostics. All testing should be done while parked.
