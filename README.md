# Garbaggio

Garbaggio is a small Material 3 (Jetpack Compose) app that takes a device back to its stock launcher,
throwing out the ad-filled launchers that sneak onto phones (often ones belonging to older relatives).
No root needed.

1. **First launch** asks for access to the list of installed apps. On MIUI/HyperOS this
   also requests the ROM's "installed apps" permission.
2. **Third-party launchers** are listed with their icon, version, and a *Default* or
   *Disabled* label. The stock launcher (Pixel, Samsung, MIUI, Huawei, OnePlus, ASUS,
   AOSP Launcher3, …) is detected automatically and shown at the top with the current
   home app.
3. **Checklist uninstall**: tick the unwanted launchers (or *Select all*) and tap
   *Uninstall*. The app works through the list on its own; Android shows its
   uninstall confirmation for each one, so you just tap OK each time. It won't remove
   every working launcher, so the device always keeps a home screen.
4. **Restore default**: Android doesn't let apps change the home app. After uninstalling,
   or from the *Restore default* button, a short guide links straight to *App info*
   (clear defaults, re-enable a disabled stock launcher) and to the *Default home app*
   screen to pick the stock launcher.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```sh
cd Alt0134
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow `.github/workflows/build.yml` builds the
debug and release APKs on every push and uploads them as the `garbaggio-apk`
artifact.

minSdk is 21 (Android 5.0).

## Icon

The engraved rubbish-bin icon is drawn procedurally by `tools/icon/make_icon.py`
(Python 3 with Pillow and NumPy). Re-run it from the repository root to regenerate the
mipmaps and `docs/icon-512.png` (Play Store size).
