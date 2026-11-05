# Thumb Edge

One-handed edge gestures for Android 16, built for the OnePlus 15 (OxygenOS 16). It works like Samsung's
One Hand Operation+: thin handles sit on the screen edges, and swiping in from one runs an action.

## Features
- Left and right handles. Each has its own position, height, touch width, colour (or system accent) and visibility.
- 6 gestures per handle: straight, diagonally up and diagonally down, each as a short or long gesture.
  Long gestures are either "swipe further" or "swipe and hold" (global setting).
- The action runs when you let go. Slide back to the edge to cancel.
- Live preview bubble with the action's icon and name, plus haptic ticks at each threshold.
- Taps and scrolls that start on a handle are passed through to the app underneath (optional).
- Actions: Back, Home, Recents, Previous app, App drawer, Split screen, Notifications, Quick settings,
  Close panels, Screenshot, Screen off, Power menu, Play/pause, Next/previous track, Volume panel/up/down,
  Sound/vibrate, Flashlight, Auto-rotate, Assistant, Open any app.
- Hide in landscape, while typing, on the lock screen, or in excluded apps.
- Quick Settings tile ("Edge handles") to pause or resume.

## Install on the phone
1. Copy `dist/ThumbEdge-<version>.apk` to the phone and open it (allow installs from that app if asked).
2. Open Thumb Edge and tap **Open Accessibility**, then Downloaded apps › Thumb Edge gestures › On.
3. If the switch is greyed out: App info › ⋮ › **Allow restricted settings**, then try step 2 again.
4. Tap **Allow** on the "Keep Thumb Edge running" card so OxygenOS doesn't freeze it.

## If it keeps switching itself off
When OxygenOS force-closes an app in the background, Android also switches off its accessibility
service. In the app, open **Fix problems**: allow background activity, lock the app in Recents, and keep
the keep-alive notification on. With the one-time adb permission below, Thumb Edge also turns the
service back on by itself (when the app starts, when Quick Settings opens, and every 15 minutes).

One-time adb permission (also needed for the back-swipe option):

    adb shell pm grant dev.nihal.thumbedge android.permission.WRITE_SECURE_SETTINGS

## Gesture navigation
With gesture navigation on, the system back swipe also uses the edges. Android lets an app take at most
200 dp per edge away from it, so keep each handle under ~200 dp tall (the app warns you), or switch to
3-button navigation in Settings › System › Navigation. OxygenOS's own back gesture may ignore that limit;
with the adb permission, **Fix problems › Turn off system back on handle edges** sets Android's back-swipe
width to zero on handle edges while Thumb Edge runs, and restores it when the service stops.

## Build
`./build.sh` builds a signed release APK in Docker (`gradle:8.14.3-jdk21`, SDK in the `android-sdk` volume).
The signing key is `thumbedge-release.jks` + `keystore.properties` (both git-ignored). Keep them: updates must
be signed with the same key or Android refuses to install over the old version.
