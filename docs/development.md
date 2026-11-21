# Build CineJoy TV

Use JDK 17 and the Android SDK command-line tools on macOS or Linux. The build script also uses Bash, curl, zip, unzip, and shasum.

From the repository root, point `ANDROID_SDK_ROOT` to your SDK installation and run the build.

```sh
export ANDROID_SDK_ROOT="/path/to/android-sdk"
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "platforms;android-34" "build-tools;34.0.0"
./scripts/build-apk.sh
```

The APK is written to `build/CineJoy-TV.apk`. The script uses the Android debug keystore at `~/.android/debug.keystore`, which Android Studio creates during a debug build. See the release guide to sign with a release key and choose an output path. [Release guide](releasing.md)

The script downloads and verifies AndroidX WebKit 1.9.0. Set `CINEJOY_WEBKIT_AAR` to the path of a local copy to reuse it. [Third-party notice](../app/src/main/assets/licenses/NOTICE.txt)

## Source layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/` | Android activity, WebView setup, and remote key handling |
| `app/src/main/assets/` | D-pad navigation and playback scripts bundled with the app |
| `app/src/main/res/` | TV launcher graphics and Android theme |
| `scripts/` | APK build script |
| `tests/` | Media unit tests and browser fixtures |
| `docs/` | Build, test, and release instructions |
