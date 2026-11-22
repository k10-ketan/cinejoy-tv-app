# CineJoy TV

CineJoy TV is an unofficial Android TV and Fire TV app for browsing and watching on [cinejoy.pk](https://cinejoy.pk/). It adapts the live site for a remote control with a compact home banner, visible D-pad focus, and playback shortcuts.

## Install

Download `CineJoy-TV.apk` from the release’s **Assets** section. Install it on your TV and open **CineJoy TV** from your apps list. [GitHub Releases](https://github.com/k10-ketan/cinejoy-tv-app/releases)

- **Fire TV**. Connect with ADB using Amazon’s instructions, then run the command below. [Connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device) · [Install and launch an APK](https://developer.amazon.com/docs/fire-tv/installing-and-running-your-app)
- **Android TV and Google TV**. Open the APK using an installer or file manager on the TV. If the TV prompts you, allow that specific installer to install apps from unknown sources. You can also use the ADB command below once your TV is connected and authorized. [Android Debug Bridge](https://developer.android.com/tools/adb)

```sh
adb install -r CineJoy-TV.apk
```

The APK requires Android 5.1 (API 22) or newer and a compatible system WebView. [Test coverage](docs/testing.md)

If you have a development build of `app.cinejoy.tv` signed with another key, Android will reject an in-place update. Installing this release then requires removing that earlier build first, which clears its local app data. Later releases signed with the same release key can update in place. [Android app signing](https://developer.android.com/studio/publish/app-signing)

## Remote controls

| Remote action | Result |
| --- | --- |
| D-pad and Select | Move the focus highlight and activate the selected control |
| Left or Right during playback, with focus off the controls | Skip back or forward 10 seconds |
| Down during playback | Focus the playback controls |
| Up from the lower playback controls | Focus the timeline |
| Up from the timeline | Focus Skip Recap or Skip Intro when visible. Otherwise, hide the playback controls |
| Select on the timeline | Pause or resume playback |
| Down from Skip Recap or Skip Intro | Return to the timeline |
| Left or Right on the timeline | Seek by 10 seconds. Hold to step by 30, then 60 seconds |
| Dedicated media buttons | Toggle playback or skip where the player supports the action |
| Back | Dismiss the current panel or return to the previous screen |
| Menu, where available | Open TV playback or site shortcuts |

The playback bar hides after four seconds of inactivity during playback in CineJoy’s built-in player. Press Down to bring it back. While paused, the bar stays visible. When no skip button is visible, press Up from the timeline to dismiss it. Open player menus stay visible.

The title at the top of the player is display text. Playback and available controls depend on the selected stream and the TV’s WebView. Live or embedded streams may restrict seeking. If a player fails to load, try another entry in **Servers** when one is available.

When a title opens a preview card, the remote highlights **Resume** or **Play**. Press Select again to start playback, or use the D-pad to reach the other actions.

## How it works

The app uses Android’s WebView and loads the live CineJoy website. The Java activity handles remote keys, full-screen playback, and website loading errors. The bundled scripts handle D-pad focus and communicate with the active player. The APK contains no movies or episodes. Playback, catalog content, sign-in, and cookies depend on the website and its player providers.

## Contributing

Fixes for remote navigation, playback controls, and device compatibility are welcome. Browser fixtures cover focus and playback controls, so you can start without a TV.

The repository includes the app source, launcher assets, build script, and tests. You can build with your own Android debug keystore. Development builds load the controls from your checkout so you can test your changes on a TV. The maintainer's private keys are used for publishing official APKs and web updates.

1. Fork the repository, clone your fork, and create a branch from `main`.
2. Follow the build guide to set up JDK 17, Android SDK 34, and a local debug keystore. Run `./scripts/build-apk.sh` to produce `build/CineJoy-TV.apk`. [Build guide](docs/development.md)
3. Run `node --test tests/*.test.mjs` with Node.js 20 or newer. For navigation or playback changes, run the relevant browser fixture and try the changed flow with a TV remote if you have one. [Test guide](docs/testing.md)
4. Push your branch and open a pull request against `main`. Describe what changed, how to reproduce the original behavior, and which checks passed. Include the TV model and OS version if you tested on hardware. [GitHub pull request guide](https://docs.github.com/en/pull-requests/how-tos/create-pull-requests/creating-a-pull-request-from-a-fork)

### Where to make changes

| File | What it handles |
| --- | --- |
| [MainActivity.java](app/src/main/java/app/cinejoy/tv/MainActivity.java) | WebView setup, native remote events, and fullscreen playback |
| [remote.js](app/src/main/assets/remote.js) | D-pad focus, page layout, and player controls |
| [media-controls.js](app/src/main/assets/media-controls.js) | Play, pause, and seeking in the page or an embedded player |
| [ControlsUpdate.java](app/src/main/java/app/cinejoy/tv/ControlsUpdate.java) | Download and cache signed web controls for release builds |
| [tests/](tests/) | Media and signature tests, plus browser fixtures |

When reporting a bug, include the app version, TV model and OS version, the screen or player you were on, the exact remote buttons you pressed, and what you expected to happen. [Report an issue](https://github.com/k10-ketan/cinejoy-tv-app/issues)

Maintainers can find signing and publishing instructions in the [release guide](docs/releasing.md).

## License

This repository has no project-wide license. The bundled AndroidX WebKit library is licensed under Apache 2.0. [Third-party notice](app/src/main/assets/licenses/NOTICE.txt)
