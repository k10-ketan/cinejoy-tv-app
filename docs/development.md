# Build CineJoy TV

Use JDK 17 and the Android SDK command-line tools on macOS or Linux. The build script also uses Bash, curl, zip, unzip, and shasum.

Make sure `sdkmanager` and JDK 17 are on your `PATH`. Set `JAVA_HOME` if you have multiple Java versions installed. From the repository root, point `ANDROID_SDK_ROOT` to your SDK installation and install the required SDK packages. `platform-tools` provides `adb` for TV testing.

```sh
export ANDROID_SDK_ROOT="/path/to/android-sdk"
sdkmanager --sdk_root="$ANDROID_SDK_ROOT" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

The build uses `~/.android/debug.keystore`. If you have never built a debug APK with Android Studio, create the keystore once with `keytool` from the JDK. These are the standard Android debug credentials.

```sh
if [ ! -f "$HOME/.android/debug.keystore" ]; then
    mkdir -p "$HOME/.android"
    keytool -genkeypair -keystore "$HOME/.android/debug.keystore" \
        -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=Android Debug,O=Android,C=US'
fi
```

```sh
./scripts/build-apk.sh
```

The APK is written to `build/CineJoy-TV.apk`. You can install it on a connected test device with `"$ANDROID_SDK_ROOT/platform-tools/adb" install -r build/CineJoy-TV.apk`. An in-place update requires the same signing key as the installed app. Use the release guide when building an APK for distribution. [Release guide](releasing.md)

Development builds use the scripts in your checkout. Rebuild after editing them to test your changes on a TV. The signature tests create a temporary signing key, and building the app uses the debug keystore you created above. [Test guide](testing.md#signed-controls-updates)

The script downloads and verifies AndroidX WebKit 1.9.0. Set `CINEJOY_WEBKIT_AAR` to the path of a local copy to reuse it. [Third-party notice](../app/src/main/assets/licenses/NOTICE.txt)

## Source layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/` | Android activity, WebView setup, remote key handling, and signed controls updates |
| `app/src/main/assets/` | D-pad navigation and playback scripts bundled with the app |
| `app/src/main/res/` | TV launcher graphics and Android theme |
| `scripts/` | APK builder and controls bundle signer |
| `tests/` | Media unit tests and browser fixtures |
| `docs/` | Build, test, and release instructions |
