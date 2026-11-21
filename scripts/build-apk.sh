#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
source_dir="$project_dir/app/src/main"
if (( $# > 1 )); then
    echo "Usage: $0 [output-apk]" >&2
    exit 2
fi
output="${1:-$project_dir/build/CineJoy-TV.apk}"

sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk" ]]; then
    if [[ -d "$HOME/Library/Android/sdk" ]]; then
        sdk="$HOME/Library/Android/sdk"
    else
        sdk="/opt/homebrew/share/android-commandlinetools"
    fi
fi
tools_dir="$sdk/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
android_jar="$sdk/platforms/android-34/android.jar"
if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@17/bin/javac ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ ! -f "$android_jar" || ! -x "$tools_dir/aapt2" ]]; then
    echo "Set ANDROID_SDK_ROOT to an SDK with Android 34 and build-tools 34.0.0." >&2
    exit 1
fi

work="$(mktemp -d "${TMPDIR:-/tmp}/cinejoy-apk.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/classes" "$work/dex"

case "${CINEJOY_ENABLE_CONTROLS_UPDATES:-0}" in
    0) controls_updates=false ;;
    1) controls_updates=true ;;
    *) echo "Set CINEJOY_ENABLE_CONTROLS_UPDATES to 0 or 1." >&2; exit 2 ;;
esac
cat > "$work/BuildConfig.java" <<EOF
package app.cinejoy.tv;

final class BuildConfig {
    static final boolean CONTROLS_UPDATES = $controls_updates;
}
EOF

webkit_version=1.9.0
webkit_aar="${CINEJOY_WEBKIT_AAR:-$work/webkit.aar}"
if [[ ! -f "$webkit_aar" ]]; then
    curl -fLsS "https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/$webkit_version/webkit-$webkit_version.aar" -o "$webkit_aar"
fi
echo "27bc203135f05b984fc218d5a9c709f0949a19a488615f2349ebdcd9e6f722a2  $webkit_aar" | shasum -a 256 -c --status
unzip -p "$webkit_aar" classes.jar > "$work/webkit.jar"

"$tools_dir/aapt2" compile --dir "$source_dir/res" -o "$work/resources.zip"
"$tools_dir/aapt2" link -I "$android_jar" --manifest "$source_dir/AndroidManifest.xml" \
    -A "$source_dir/assets" -o "$work/unsigned.apk" "$work/resources.zip"
javac --release 8 -classpath "$android_jar:$work/webkit.jar" -d "$work/classes" \
    "$source_dir/java/app/cinejoy/tv/"*.java "$work/BuildConfig.java"
jar cf "$work/classes.jar" -C "$work/classes" .
"$tools_dir/d8" --min-api 22 --lib "$android_jar" --output "$work/dex" "$work/classes.jar" "$work/webkit.jar"
(cd "$work/dex" && zip -q "$work/unsigned.apk" classes.dex)
"$tools_dir/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"

keystore="${CINEJOY_KEYSTORE:-$HOME/.android/debug.keystore}"
if [[ ! -f "$keystore" ]]; then
    echo "Create an Android debug keystore or set CINEJOY_KEYSTORE to an existing signing keystore." >&2
    exit 1
fi
signing_args=(--ks "$keystore" --ks-key-alias "${CINEJOY_KEY_ALIAS:-androiddebugkey}"
    --ks-pass "${CINEJOY_KS_PASS:-pass:android}")
if [[ -n "${CINEJOY_KEY_PASS:-}" ]]; then
    signing_args+=(--key-pass "$CINEJOY_KEY_PASS")
fi
"$tools_dir/apksigner" sign "${signing_args[@]}" \
    --v4-signing-enabled false --out "$work/CineJoy-TV.apk" "$work/aligned.apk"
"$tools_dir/apksigner" verify "$work/CineJoy-TV.apk"
mkdir -p -- "$(dirname "$output")"
mv -- "$work/CineJoy-TV.apk" "$output"
echo "Built $output"
