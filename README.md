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

Those settings only apply to apps that don't ask for an orientation: a fullscreen video asking for landscape
still rotates the screen. With **Display over other apps** allowed too, the lock wins: while it's locked,
`LockService` keeps a transparent 1px overlay asking for `SCREEN_ORIENTATION_USER`, which the window manager
ranks above the app's own request and resolves to `user_rotation`. It runs as a foreground service (with a
silent notification), stops when auto-rotate comes back on from anywhere, and starts again after a reboot.

An app that asked for another orientation when going fullscreen can then sit waiting for a rotation that
never comes: YouTube in Firefox leaves the video at the top of the screen. So when an app hides the status bar
while locked, the service turns the screen upside down and back (about half a second), an orientation change
of the same size that gets the app to lay itself out again. No permission lets one app make another relayout,
and nothing tells it which app asked, so this happens for every app going fullscreen.

### YouTube in Firefox

For YouTube in Firefox there's a fix without the flip, inside the page:
[`userscripts/youtube-fullscreen-center.user.js`](userscripts/youtube-fullscreen-center.user.js) sends the page a
`resize` just after it goes fullscreen, which is all YouTube's player needs to centre the video. To install it,
add [Violentmonkey](https://addons.mozilla.org/android/addon/violentmonkey/) to Firefox, then open
[the script's raw link](https://raw.githubusercontent.com/fourlastor-slop/orientation-control/main/userscripts/youtube-fullscreen-center.user.js)
and confirm.

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
