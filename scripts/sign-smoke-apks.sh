#!/usr/bin/env bash
# Sign test copies with a disposable key, never with release secrets.
set -euo pipefail
smoke_dir="${1:?Usage: sign-smoke-apks.sh OUTPUT_DIRECTORY ALPHA_APK RELEASE_APK}"
alpha_apk="${2:?}"
release_apk="${3:?}"
mkdir -p "$smoke_dir"
keytool -genkeypair -keystore "$smoke_dir/smoke.jks" -alias smoke \
  -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 2 \
  -dname 'CN=Disposable APK Smoke Test' -noprompt
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK is required}}"
apksigner="$(find "$sdk_root/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$apksigner" sign --ks "$smoke_dir/smoke.jks" --ks-key-alias smoke \
  --ks-pass pass:android --key-pass pass:android --out "$smoke_dir/alpha.apk" "$alpha_apk"
"$apksigner" sign --ks "$smoke_dir/smoke.jks" --ks-key-alias smoke \
  --ks-pass pass:android --key-pass pass:android --out "$smoke_dir/release.apk" "$release_apk"
