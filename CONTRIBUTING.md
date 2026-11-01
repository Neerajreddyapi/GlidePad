# Contributing to Glide Pad

Thanks for your interest in Glide Pad! Bug reports, ideas, documentation fixes and code are all welcome. This guide explains how to get set up and how changes get in.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a bug:** [open a bug report](https://github.com/Neerajreddyapi/GlidePad/issues/new?template=bug_report.yml). Please include your phone model, Android version and the computer you connected to. Bluetooth behaviour differs a lot between phones.
- **Suggest a feature:** [open a feature request](https://github.com/Neerajreddyapi/GlidePad/issues/new?template=feature_request.yml) describing the problem it solves.
- **Test on your devices:** tell us which phones and computers work (or don't) in an issue. This is genuinely useful.
- **Send a pull request:** fix a bug, improve the docs, or build something from an issue labelled `good first issue` or `help wanted`.

For anything bigger than a small fix, please open an issue first so we can agree on the approach before you spend time on it.

## Development setup

Follow the **[development setup in the README](README.md#development-setup)** to install Android Studio and run Glide Pad on your phone. You'll need:

- an Android phone running **Android 9 or newer** with USB debugging on
- a computer with Bluetooth to connect to (Glide Pad is designed for macOS)

When contributing, fork the repo on GitHub, clone your fork, and add the original repo as `upstream`:

```sh
git clone https://github.com/<your-username>/GlidePad.git
cd GlidePad
git remote add upstream https://github.com/Neerajreddyapi/GlidePad.git
```

You don't need any signing keys: without `keystore.properties` the release build falls back to the debug key.

### Project structure

| Path | What's there |
|---|---|
| `app/src/main/java/com/glidepad/app/HidManager.java` | Bluetooth HID descriptor, registration, connection and report sending |
| `app/src/main/java/com/glidepad/app/TrackpadView.java` | Touch surface and gesture recognition |
| `app/src/main/java/com/glidepad/app/MacKeyboardView.java` | Built-in landscape keyboard |
| `app/src/main/java/com/glidepad/app/KeyInputView.java` | Bridge from the phone's keyboard to HID key presses |
| `app/src/main/java/com/glidepad/app/HidService.java` | Foreground service that keeps the computer connected in the background |
| `app/src/main/java/com/glidepad/app/MainActivity.java` | Screens, panels, settings and pairing |
| `app/src/main/java/com/glidepad/app/Keys.java` | HID key codes and character mapping (US layout) |
| `app/src/main/res/` | Layouts, colours (light in `values/`, dark in `values-night/`), icons |
| `docs/` | Project website and privacy policy (served by GitHub Pages) |

### Testing your change

There are no automated UI tests yet, so please test by hand on a real phone before opening a pull request:

1. Pair and connect to a computer, then check the pointer, tap to click, drag, two-finger scroll and right-click.
2. Type a few words with the phone keyboard (portrait) and the built-in keyboard (landscape).
3. Switch to another app and back: the computer should stay connected.
4. Check light and dark mode, and portrait and landscape, if you touched the UI.

Say in the pull request which phone, Android version and computer you tested on.

## Code guidelines

- **Java 17, no third-party dependencies.** The app relies only on the Android SDK, and it should stay that way.
- **Match the existing style:** 4-space indentation, small focused classes, and comments that explain *why*, not *what*.
- **Keep changes focused:** one bug fix or feature per pull request. Don't reformat or refactor unrelated code in the same PR.
- **User-visible text** should be short, plain and friendly.
- **Colours** go in `values/colors.xml` (light) and `values-night/colors.xml` (dark). Never hard-code colours in layouts or code.
- **Privacy:** the app has no internet permission and collects nothing. Pull requests that add analytics, ads, tracking or network access won't be accepted.

## Commits and pull requests

1. Create a branch from an up-to-date `main`:
   ```sh
   git fetch upstream
   git checkout -b fix/scroll-direction upstream/main
   ```
2. Make your change and commit with a clear message in the imperative mood, for example `Fix horizontal scroll direction on macOS`. Explain the reason in the body if it isn't obvious.
3. Make sure it builds: `./gradlew assembleDebug`
4. Push to your fork and open a pull request against `main`. Fill in the template.
5. GitHub Actions builds every pull request automatically. A maintainer will review it, may ask for changes, and will merge it when it's ready.

## Releases

Maintainers handle releases: bump `versionCode` and `versionName` in `app/build.gradle.kts`, tag the commit (`v1.1`), publish the APK on [GitHub Releases](https://github.com/Neerajreddyapi/GlidePad/releases), and upload the bundle to Google Play.

## Security

Please don't report security problems in public issues. See [SECURITY.md](SECURITY.md).

## License

Glide Pad is released under the [MIT License](LICENSE). By contributing, you agree that your contributions are licensed under the same terms.
