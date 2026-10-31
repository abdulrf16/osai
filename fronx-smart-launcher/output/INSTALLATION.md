# Installing Fronx Smart Launcher on the Fronx head unit

> **Status of this guide:** the APK has been built and tested on an Android 13 *emulator* only
> (see `emulator/emulator-test-results.md`). It has **not** been tested on the real infotainment unit.
> Whether your unit lets you install APKs from USB, and whether it lets you change the Home app,
> is **unverified** - those are decisions made by the head-unit firmware. Do all of this **while parked**.

## Before you start

* Use `Fronx-Smart-Launcher-release.apk` (signed, optimised). `Fronx-Smart-Launcher-debug.apk` is the same
  app with debug info - use it only if the release build misbehaves.
* Both builds share package name `com.fronx.smartlauncher`, so you can only have one installed at a time.
  Uninstall one before installing the other if Android reports a signature conflict.
* The app needs no root, no ADB and no firmware change. It does not talk to the car's CAN bus, MCU or cameras.

## Steps

1. **Copy the APK to a USB drive.** Use a FAT32/exFAT stick; put `Fronx-Smart-Launcher-release.apk` in the top folder.
2. **Connect the USB drive** to the infotainment USB port. (Some head units only mount USB in a specific port.)
3. **Open the file manager** on the head unit (often under Settings > Storage, or an app called *Files* / *File Manager*).
   If there is none, this method won't work on your unit - see "If you cannot install" below.
4. **Install the APK.** Tap the file, then **Install**.
   * Android 8+ asks you to allow *"Install unknown apps"* for the app you opened the file with (the file manager).
     Switch **Allow from this source** on, go back and tap **Install** again. This is a per-app permission you can
     switch off again afterwards.
   * If a "Play Protect" or "blocked by administrator" message appears, the unit's firmware is restricting installs;
     do not try to bypass it.
5. **Grant the permissions.** The app asks for no runtime permissions. One *optional* feature needs your approval:
   * **Media card (track, artwork, play/pause):** open **Settings > Notification access** (the launcher's Settings
     screen has a button), switch **Fronx Smart Launcher** on. Android requires this for any app that lists other
     apps' media sessions. Fronx never reads, stores or sends notifications.
   * On Android 13, a sideloaded app may show the toggle greyed out ("Restricted setting"). Fix: Settings > Apps >
     Fronx Smart Launcher > ⋮ (top right) > **Allow restricted settings**, then try again.
6. **Select Fronx Smart Launcher as the default Home app.** Open the app (it also appears as a normal app icon), tap the banner
   **Choose**, or go to Fronx **Settings > Choose default Home app**. Android shows its own chooser (or Settings >
   Apps > Default apps > Home app): pick **Fronx Smart Launcher**, then **Just once** (to try it) or **Always**.
   Fronx never changes the default silently.
7. **Test the launcher (parked):**
   * Press the unit's Home button - you should land on the Fronx dashboard.
   * Tap each dock item: Home, Apps, Navigation, Music, Phone, Settings.
   * Open Maps / a music app from the Quick Apps grid; start music and check the media card.
   * Tap the Bluetooth tile and confirm the head unit's own Bluetooth screen opens.
   * Check the reverse camera and the unit's physical buttons still behave exactly as before.
   * If you use Android Auto / phone projection: start it, then press Home and note which launcher comes back. This is **untested**; Android Auto draws its own screen and Fronx cannot change it.
8. **Switch back to the original launcher if needed.** Any of these works:
   * Fronx **Settings > Choose default Home app** and select the original launcher.
   * Press Home; if you chose *Just once*, Android asks again every time.
   * Android Settings > Apps > Default apps > **Home app** > select the original launcher.
   * If the screen is unusable: use the unit's own app list/Settings from its status bar or physical button, or
     reboot the unit - choosing *Just once* means a reboot normally returns to the factory launcher.
9. **Uninstall if required.** Android Settings > Apps > **Fronx Smart Launcher** > **Uninstall**. If it is the current default Home app,
   Android falls back to the system launcher. No firmware recovery is needed; the app changes nothing outside its own data.

## If you cannot install

* The head unit may have no file manager or may block unknown sources. Options (none verified on your unit):
  an *Install from USB* feature in the manufacturer's settings, or ADB over USB/Wi-Fi if the manufacturer exposes it.
  Ask the dealer/manufacturer before using developer features; this project deliberately does not use them.

## Updating

Releases from CI are signed with a one-off key unless you configure your own (see `README.md`). Android only
updates in place when the signature matches; if it does not, uninstall the old build first (your favourites and
settings are stored in the app's private data and will be reset).
