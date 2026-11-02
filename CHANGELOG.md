# Changelog

All notable changes to Glide Pad are listed here. Versions follow `versionName` in `app/build.gradle.kts`.

## [1.1] - 2026-11-02

### Added
- Works with more than Macs: on first launch, choose what you're connecting to (Mac, Windows PC, Chromebook, iPad, Linux, Smart TV or Other). Change it any time in Settings → Device.
- Each device gets its own shortcuts, four-finger gestures, zoom keys and key names. For example, Windows uses Ctrl/Alt/Win, Task View, Ctrl+Win+arrows for desktops and Win+D. A Smart TV gets a remote-style panel (Home, Back, OK, arrows, Search, volume).
- Pairing steps for each kind of device.
- "Make phone visible" opens a popup on the same screen: it asks for Android's permission, then shows a 2-minute countdown ring with the pairing steps, and closes by itself when your device connects.
- A note explaining that a regular monitor can't be connected on its own: connect to the computer it's plugged into, or choose Smart TV for smart TVs and monitors.
- Smart TVs, smart monitors and TV boxes show up in the paired devices list.

### Changed
- Scrolling direction is set per device, so content follows your fingers on Windows, Linux, Chromebook and TVs too.
- Wording says "computer" or the device's name instead of always "Mac", and the delete key shows as "Del" on non-Apple devices.

## [1.0.1] - 2026-11-02

### Added
- A Website link (glidepad.vercel.app) at the bottom of Settings, beside Privacy policy and Source code.

## [1.0] - 2026-11-01

First public release.

### Added
- Trackpad: pointer with acceleration, tap to click, double-tap to double-click, press and hold then move to drag.
- Two-finger tap to right-click, two-finger scroll with momentum, and pinch to zoom (⌘+ / ⌘−).
- Four-finger swipes for Mission Control, App Exposé and switching desktops, and spread or pinch for Show Desktop. Three fingers are left to the phone.
- Typing with the phone's keyboard in portrait, and a built-in keyboard in landscape that leaves half the trackpad free.
- Key row with esc, tab, arrows and sticky ⌘ ⌥ ⌃ ⇧.
- Shortcuts panel (Spotlight, Mission Control, app switcher, copy, paste, undo, screenshot, close window, lock screen and more) and media keys.
- Landscape mode, with panels and settings sliding up on the right half.
- Light and dark mode.
- Settings: pointer speed, scroll speed, lines per scroll step, reverse scrolling, momentum, tap to click, click buttons and haptics.
- Stays connected while you switch apps (foreground service with a "Connected to …" notification), and reconnects to the last computer when you open the app.
- Privacy policy and source code links, and "Crafted by Neeraj Reddy", at the end of Settings.
