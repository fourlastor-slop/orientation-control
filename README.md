# Orientation Control

Locks the screen rotation from the app or from Quick Settings tiles, without adb. It changes the same two
system settings as:

```bash
adb shell settings put system accelerometer_rotation 0   # 1 turns auto-rotate back on
adb shell settings put system user_rotation 0            # the locked rotation
```

There are four tiles: Portrait, Reverse portrait, Landscape and Auto-rotate. The app's "Quick Settings tiles"
chips add them on Android 13 and later; on older versions add them from the Quick Settings editor.

The first time, the app needs **Modify system settings** (the app and the tiles both open that screen).

`user_rotation` counts from the panel's natural orientation, so the app reads it from the display: on a
portrait-first device Portrait/Landscape/Reverse portrait are 0/1/2, on a landscape-first tablet they are 1/0/3.
Locking also turns `accelerometer_rotation` off, since `user_rotation` is ignored while it's on.

## Building

```bash
./gradlew assembleDebug
```

## Publishing

Every push to `main` runs `.github/workflows/publish.yml`: it builds the release APK, signs it with the
organization keystore (alias `bellwater`) and attaches it to a new GitHub release, `v1.<run number>`. It uses the
organization secrets `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD`.
