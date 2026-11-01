<p align="center"><img src="docs/banner.png" alt="Glide Pad" width="720"></p>

# Glide Pad

[![Build](https://github.com/Neerajreddyapi/GlidePad/actions/workflows/build.yml/badge.svg)](https://github.com/Neerajreddyapi/GlidePad/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/Neerajreddyapi/GlidePad)](https://github.com/Neerajreddyapi/GlidePad/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84)

Turn your Android phone into a Bluetooth trackpad and keyboard for your Mac. There is nothing to install on the Mac: it simply sees a Bluetooth keyboard and mouse.

<p>
  <img src="docs/screenshots/trackpad.jpg" width="160" alt="Trackpad">
  <img src="docs/screenshots/shortcuts.jpg" width="160" alt="Shortcuts panel">
  <img src="docs/screenshots/settings-light.jpg" width="160" alt="Settings in light mode">
</p>
<p>
  <img src="docs/screenshots/keyboard-landscape.jpg" width="400" alt="Landscape with the built-in keyboard">
  <img src="docs/screenshots/settings-landscape.jpg" width="400" alt="Landscape settings">
</p>

## Features

- **Trackpad:** move the pointer with pointer acceleration, tap to click, double-tap to double-click, press and hold then move to drag.
- **Two-finger gestures:** two-finger tap to right-click, two-finger scroll in any direction with momentum, and pinch to zoom (⌘+ / ⌘−).
- **Four-finger gestures:** swipe up for Mission Control, down for App Exposé, left or right to switch desktops, and spread or pinch to show the desktop.
- **Keyboard:** type with your phone's keyboard in portrait, or with a built-in Mac-style keyboard in landscape that leaves half the trackpad free. A key row adds esc, tab, arrows and ⌘ ⌥ ⌃ ⇧.
- **Shortcuts:** one tap for Spotlight, Mission Control, app switcher, copy, paste, undo, screenshot, close window, lock screen and more.
- **Media keys:** play/pause, next, previous, volume and mute.
- **Landscape mode:** a wide, full-screen trackpad. Panels and settings slide up on the right half.
- **Stays connected:** keeps the Mac connected while you switch apps, and reconnects to your last Mac when you open the app.
- **Make it yours:** pointer speed, scroll speed, lines per scroll step, reverse scrolling, momentum, tap to click, optional click buttons, haptics, and light or dark mode.
- **Private:** no internet permission, no accounts, no ads, no tracking.

## Getting started

1. Install the app: [download the APK](https://github.com/Neerajreddyapi/GlidePad/releases/latest) (Google Play coming soon).
2. Open Glide Pad, allow Bluetooth, and tap **Make phone visible**.
3. On the Mac, open **System Settings → Bluetooth**, click **Connect** next to your phone, and accept the code on both screens.

Paired the phone with the Mac before? Remove it on both devices first, then pair again so the Mac sees it as a keyboard and mouse.

## Good to know

- **Three and four-finger gestures may not work on every phone.** Many phones (OnePlus and others) use multi-finger swipes for their own features, such as screenshots, and Android doesn't let apps override that. Glide Pad leaves three fingers to your phone. If four-finger swipes don't reach your Mac, the ⌘ shortcuts panel does the same things.
- **Scrolling moves in steps,** like a mouse wheel. Only Apple's own trackpads can do pixel-smooth scrolling and real Mac gestures, because they use a private protocol.
- **Phone support:** you need Android 9 or newer. A few phone makers turn off Android's "Bluetooth keyboard and mouse" mode, and only one app can use it at a time.
- **Other computers:** it's made for the Mac, and pointer, clicks, scrolling and typing should work with any computer that supports Bluetooth keyboards and mice. The gesture and shortcut keys are Mac shortcuts.

## How it works

Android 9 added [`BluetoothHidDevice`](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice), which lets a phone act as a Bluetooth HID device. Glide Pad registers one combined device with three reports: a mouse (5 buttons, 16-bit X/Y, wheel and horizontal pan), a keyboard and media keys. Gestures that a mouse can't express are sent as the matching Mac keyboard shortcuts (for example ⌃↑ for Mission Control).

| File | What it does |
|---|---|
| [`HidManager.java`](app/src/main/java/com/glidepad/app/HidManager.java) | HID descriptor, Bluetooth registration and connection, sending reports |
| [`TrackpadView.java`](app/src/main/java/com/glidepad/app/TrackpadView.java) | The touch surface and gesture recognition |
| [`MacKeyboardView.java`](app/src/main/java/com/glidepad/app/MacKeyboardView.java) | The built-in landscape keyboard |
| [`HidService.java`](app/src/main/java/com/glidepad/app/HidService.java) | Foreground service that keeps the Mac connected in the background |
| [`MainActivity.java`](app/src/main/java/com/glidepad/app/MainActivity.java) | Screens, panels, settings and pairing |

## Build

Requirements: Android Studio (or JDK 17+) and the Android SDK. No third-party libraries.

```sh
./gradlew assembleRelease   # APK  -> app/build/outputs/apk/release/
./gradlew bundleRelease     # AAB  -> app/build/outputs/bundle/release/
```

Release signing reads `keystore.properties` from the repo root (not committed):

```properties
storeFile=your-upload-key.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Without that file, release builds are signed with the debug key so a fresh clone still builds and installs.

## Contributing

Contributions are welcome: bug reports, device test results, ideas and pull requests. Read the [contributing guide](CONTRIBUTING.md) for the development setup, how to test on a real phone, and how to open a pull request. Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md). Found a security problem? See the [security policy](SECURITY.md).

## Privacy

Glide Pad collects nothing. Read the [privacy policy](https://neerajreddyapi.github.io/GlidePad/privacy-policy.html).

## License

[MIT](LICENSE) © 2026 Neeraj Reddy.
