# Publish a release on GitHub

This folder contains the source, documentation, regression tests, and a release-signed APK. Version 1.11 uses Android package `app.cinejoy.tv` and version code 12.

1. Create a GitHub repository and push this folder’s contents as the repository root. Review the files before adding them. Keep signing keys, passwords, and local SDK files outside the repository.
2. Create a GitHub release tagged `v1.11`. Attach `CineJoy-TV.apk` and `CineJoy-TV.apk.sha256` as release assets. [Creating GitHub releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
3. Describe the remote controls, playback limits, and test coverage with the release. Share the GitHub release link so people can download the APK and checksum together.

Verify the checksum from the directory containing the files.

```sh
shasum -a 256 -c CineJoy-TV.apk.sha256
```

## Sign future updates

Keep a secure backup of the release signing key and its password. Android uses the signing certificate to decide whether a new APK can update an existing installation. Reuse the release key, increment `android:versionCode`, and update `android:versionName` in `app/AndroidManifest.xml`. [Android app signing](https://developer.android.com/studio/publish/app-signing)

```sh
export CINEJOY_KEYSTORE="/private/path/to/cinejoy-release.p12"
export CINEJOY_KEY_ALIAS="cinejoy"
export CINEJOY_KS_PASS="file:/private/path/to/password"
./build-apk.sh
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify --verbose --print-certs CineJoy-TV.apk
shasum -a 256 CineJoy-TV.apk > CineJoy-TV.apk.sha256
```

If the private key uses a different password from the keystore, set `CINEJOY_KEY_PASS` to a separate password file using the same `file:` format.

Use a GitHub repository description that identifies this as an unofficial client. If you want others to modify or redistribute your source, choose and add a license for the code you can license before publishing.
