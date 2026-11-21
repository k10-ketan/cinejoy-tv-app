# Release CineJoy TV

## Build and sign

Run the checks in the test guide. For each new build intended for distribution, increment `android:versionCode` and update `android:versionName` in the manifest. Use the same release signing key for every update and keep the key and its password outside the repository. [Test guide](testing.md) · [Android manifest](../app/src/main/AndroidManifest.xml) · [Android app signing](https://developer.android.com/studio/publish/app-signing)

From the repository root, use the SDK setup from the build guide and point the signing variables to your release credentials. [Build guide](development.md)

```sh
export CINEJOY_KEYSTORE="/private/path/to/cinejoy-release.p12"
export CINEJOY_KEY_ALIAS="cinejoy"
export CINEJOY_KS_PASS="file:/private/path/to/password"
./scripts/build-apk.sh dist/CineJoy-TV.apk
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
