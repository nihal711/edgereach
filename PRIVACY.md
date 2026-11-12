# EdgeReach privacy policy

_Last updated: 12 November 2026_

EdgeReach is a free, open-source app that adds gesture handles to the edges of your screen.

## What the app collects
Nothing. EdgeReach has no account, no analytics, no ads and no crash reporting. It does not have Android's
`INTERNET` permission, so it cannot send any data off your phone.

## Accessibility Service
EdgeReach uses Android's Accessibility Service API so it can:

- draw the gesture handles over other apps,
- carry out the actions you choose (such as Back, Home, Recents, notifications and screenshots),
- know which app is open and when the keyboard is showing, so it can hide the handles in apps you exclude
  or while you type, and switch to your previous app.

The service cannot read the contents of your screen or anything you type: it is declared with
`canRetrieveWindowContent="false"`. The names of recently used apps are kept only in memory while the
service runs, to power the Previous app action, and are never stored or shared.

## Data stored on your phone
Your settings (handle size, colours, chosen actions, excluded apps) and a short activity log used by the
Fix problems screen are stored only on your device. The log notes when the service starts, stops or hides
the handles, and can include the package name of the app that was open at the time. Everything is removed
when you uninstall the app or clear its data. The log leaves your phone only if you tap Copy report and
paste it somewhere yourself.

## Permissions
- Accessibility Service: described above.
- Modify system settings: only used if you assign the Auto-rotate action.
- Notifications and foreground service: an optional silent notification that stops some phones closing
  the app in the background.
- Run at startup: to bring the handles back after a restart.

## Children
EdgeReach is not directed at children and collects no data from anyone.

## Changes and contact
Changes to this policy are published in this file, with the full history visible in the repository.
Questions: open an issue at https://github.com/nihal711/edgereach/issues
