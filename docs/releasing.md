# Release CineJoy TV

## Build and sign

Run the checks in the test guide. For each new build intended for distribution, increment `android:versionCode` and update `android:versionName` in the manifest. Use the same release signing key for every update and keep the key and its password outside the repository. [Test guide](testing.md) · [Android manifest](../app/src/main/AndroidManifest.xml) · [Android app signing](https://developer.android.com/studio/publish/app-signing)

From the repository root, use the SDK setup from the build guide and point the signing variables to your release credentials. [Build guide](development.md)

```sh
export CINEJOY_KEYSTORE="/private/path/to/cinejoy-release.p12"
export CINEJOY_KEY_ALIAS="cinejoy"
export CINEJOY_KS_PASS="file:/private/path/to/password"
CINEJOY_ENABLE_CONTROLS_UPDATES=1 ./scripts/build-apk.sh dist/CineJoy-TV.apk
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify --verbose --print-certs dist/CineJoy-TV.apk
(cd dist && shasum -a 256 CineJoy-TV.apk > CineJoy-TV.apk.sha256)
```

If the private key has a separate password, set `CINEJOY_KEY_PASS` to its password file using the same `file:` format.

## Publish on GitHub

1. Push the commit used to build the APK. In GitHub Releases, create a release and tag that commit with the version, such as `v1.11` for `versionName` `1.11`.
2. Add release notes. Attach `dist/CineJoy-TV.apk` and `dist/CineJoy-TV.apk.sha256` as release assets.
3. Publish the release and verify both files download. [GitHub release guide](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

Verify the download from the directory containing both files.

```sh
shasum -a 256 -c CineJoy-TV.apk.sha256
```

## Publish a web controls update

CineJoy TV release builds starting with 1.14 read one signed bundle from the `tv-controls` branch when built with `CINEJOY_ENABLE_CONTROLS_UPDATES=1`. The bundle can update layout, in-app artwork and branding, D-pad navigation, the timeline, and playback logic in the main CineJoy page. The installed APK supplies the TV launcher artwork, Android code, and media bridge inside third-party player frames.

1. Test the new `remote.js` and `media-controls.js` with the [browser fixtures](testing.md#browser-regressions) and the media tests.
2. Choose a bundle version higher than the last published one. Use the dedicated controls signing key, which is separate from the APK signing key. The APK pins the matching public key. Keep the private PEM file outside source control and back it up securely.
3. Sign the bundle, then replace `controls.bin` on the `tv-controls` branch with a normal commit and push. Review and merge the script changes on `main` so the published source is available to contributors and included in future APK builds.

From the source checkout, use Python 3 and OpenSSL to create the bundle.

```sh
python3 scripts/sign-controls.py 2 dist/controls.bin --key /private/path/to/cinejoy-controls.pem
```

The branch contains the signed bundle as a single file. On the first publication from a workstation, clone the channel into a separate checkout.

```sh
git clone --branch tv-controls --single-branch https://github.com/k10-ketan/cinejoy-tv-app.git ../cinejoy-tv-controls-channel
cp dist/controls.bin ../cinejoy-tv-controls-channel/controls.bin
git -C ../cinejoy-tv-controls-channel add controls.bin
git -C ../cinejoy-tv-controls-channel commit -m "Publish CineJoy TV controls bundle 2"
git -C ../cinejoy-tv-controls-channel push origin tv-controls
```

On subsequent releases, fast-forward the channel checkout before copying in the new bundle. GitHub's download cache can briefly serve an earlier version. The TV checks when the app opens or returns to the foreground. It uses a verified cached copy when the check is slow or unavailable. A download that finishes after startup is available on a later launch, and the app defers switching during playback.

To roll back a regression, sign the previous scripts with a **higher** bundle version and publish that bundle. TVs ignore versions at or below the last verified one. To change the signing key itself, ship an APK with the new public key.
