# EdgeReach

One-hand edge gestures for any Android phone. Thin handles sit on the edges of the screen; swipe in
from one with your thumb to go Back, Home, open Recents, pull down notifications, switch to your previous
app and more, without stretching across a big screen.

Inspired by Samsung's One Hand Operation+, which only runs on Galaxy phones. EdgeReach is an independent,
free and open-source app for everyone else. It is not affiliated with Samsung.

- Works on Android 12 and newer. Developed and tested on a OnePlus 15 (Android 16).
- No ads, no tracking, no internet permission. It cannot read what is on your screen.

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

## Install
Get it from Google Play (link coming soon), or download the APK from
[Releases](../../releases) and open it on your phone.

1. Open EdgeReach and tap **Open Accessibility**, read the notice, then Downloaded apps › EdgeReach gestures › On.
2. If you installed the APK and the switch is greyed out: App info › ⋮ › **Allow restricted settings**, then try again.
3. Tap **Open battery settings** on the "Keep EdgeReach running" card and set EdgeReach to not be optimised.

## Why it needs Accessibility, and what it can't do
Android only lets an accessibility service draw gesture handles over other apps and press Back, Home and
Recents for you. EdgeReach uses the service for exactly that, plus two notices Android sends it: which app
is in front (to hide handles in apps you exclude and to find your previous app) and when the keyboard opens
(to hide handles while typing).

The service is declared with `canRetrieveWindowContent="false"`
([accessibility_service.xml](app/src/main/res/xml/accessibility_service.xml)), so Android does not give it
any screen content or text. The app has no `INTERNET` permission, so nothing can leave your phone. See
[PRIVACY.md](PRIVACY.md).

## Moving your settings
To restore a saved setup, put a settings file named `import.json` in
`Android/data/dev.nihal.edgereach/files/` and open the app. It loads the file once and deletes it.

## If it keeps switching itself off
Some phones (OnePlus, Oppo, Realme, Xiaomi, Vivo, Huawei and others) close apps in the background, and
Android then switches off their accessibility service too. In the app, open **Fix problems**: allow
background activity, lock the app in Recents, and keep the keep-alive notification on. With the one-time
adb permission below, EdgeReach also turns the service back on by itself (when the app starts, when Quick
Settings opens, and every 15 minutes).

One-time adb permission (also needed for the back-swipe option):

    adb shell pm grant dev.nihal.edgereach android.permission.WRITE_SECURE_SETTINGS

## Gesture navigation
With gesture navigation on, the system back swipe also uses the edges. Android lets an app take at most
200 dp per edge away from it, so keep each handle under ~200 dp tall (the app warns you), or switch to
3-button navigation. Some phones' back gesture ignores that limit; with the adb permission,
**Fix problems › Turn off system back on handle edges** sets Android's back-swipe width to zero on handle
edges while EdgeReach runs, and restores it when the service stops.

## Build
`./build.sh` builds a signed release APK and an AAB in Docker (`gradle:8.14.3-jdk21`, SDK in the
`android-sdk` volume); output goes to `dist/`. You can also open the project in Android Studio.

If there is no `keystore.properties`, the script creates a new signing key (`release.jks`). Both files are
git-ignored. Keep them: updates must be signed with the same key or Android refuses to install over the
old version.

## License
[GPL-3.0](LICENSE). You may use, study, change and share EdgeReach; versions you share must stay open
source under the same license.
