# CineJoy TV

CineJoy TV is an unofficial Android TV and Fire TV app for browsing and watching on [cinejoy.pk](https://cinejoy.pk/). It adapts the live site for a remote control with a compact home banner, visible D-pad focus, and playback shortcuts.

## Install

Download [CineJoy-TV.apk](CineJoy-TV.apk) or get it from the repository’s Releases page. Install it on your TV and open **CineJoy TV** from your apps list.

- **Fire TV**. Connect with ADB using Amazon’s instructions, then run the command below. [Connect to Fire TV through ADB](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device) · [Install and launch an APK](https://developer.amazon.com/docs/fire-tv/installing-and-running-your-app)
- **Android TV and Google TV**. Open the APK using an installer or file manager on the TV. If the TV prompts you, allow that specific installer to install apps from unknown sources. You can also use the ADB command below once your TV is connected and authorized. [Android Debug Bridge](https://developer.android.com/tools/adb)

```sh
adb install -r CineJoy-TV.apk
```

The APK targets Android-based TV devices with Android 5.1 (API 22) or newer and a compatible system WebView. See [test coverage](TESTING.md) for the hardware exercised and the checks to run on other models.

If you have a development build of `app.cinejoy.tv` signed with another key, Android will reject an in-place update. Installing this release then requires removing that earlier build first, which clears its local app data. Later releases signed with the same release key can update in place. [Android app signing](https://developer.android.com/studio/publish/app-signing)

## Remote controls

| Remote action | Result |
| --- | --- |
| D-pad and Select | Move the focus highlight and activate the selected control |
| Left or Right during playback, with focus off the controls | Skip back or forward 10 seconds |
| Down during playback | Focus the playback controls |
| Up from the lower playback controls | Focus the timeline |
| Up from the timeline | Hide the playback controls immediately |
| Left or Right on the timeline | Seek by 10 seconds. Hold to step by 30, then 60 seconds |
| Dedicated media buttons | Toggle playback or skip where the player supports the action |
| Back | Dismiss the current panel or return to the previous screen |
| Menu, where available | Open TV playback or site shortcuts |

The playback bar hides after four seconds of inactivity during playback in CineJoy’s built-in player. Press Down to bring it back. While paused, the bar stays visible until you dismiss it with Up from the timeline. Open player menus stay visible.

The title at the top of the player is display text. Playback and available controls depend on the selected stream and the TV’s WebView. Live or embedded streams may restrict seeking. If a player fails to load, try another entry in **Servers** when one is available.

When a title opens a preview card, the remote highlights **Resume** or **Play**. Press Select again to start playback, or use the D-pad to reach the other actions.

## Build from source

On macOS or Linux, install JDK 17 and the Android SDK command-line tools. Install SDK Platform 34 and Build Tools 34.0.0. The script also uses Bash, curl, zip, unzip, and shasum.

```sh
sdkmanager "platforms;android-34" "build-tools;34.0.0"
export ANDROID_SDK_ROOT="/path/to/android-sdk"
./build-apk.sh
```

The script downloads and checks the SHA-256 of AndroidX WebKit 1.9.0. To use a previously downloaded copy, set `CINEJOY_WEBKIT_AAR` to the path to that AAR. By default, the script uses the Android development keystore at `~/.android/debug.keystore`, which Android Studio creates during a debug build. Build in a separate checkout if you want to keep the supplied release file, since the script writes to `CineJoy-TV.apk` in the project root. [Third-party license notice](app/assets/licenses/NOTICE.txt)

See [PUBLISHING.md](PUBLISHING.md) for release signing and GitHub packaging. Run the checks in [TESTING.md](TESTING.md) before sharing a new release.

## How it works

The app uses Android’s WebView and loads the live CineJoy website. The Java activity handles remote keys, full-screen playback, and website loading errors. The bundled scripts handle D-pad focus and communicate with the active player. The APK contains no movies or episodes. Playback, catalog content, sign-in, and cookies depend on the website and its player providers.
