#!/usr/bin/env bash
# Phase gate check: the release APK must hold no religious terms, no INTERNET permission,
# backups off, and nothing exported but the launcher activity.
# Usage: tools/scan_apk.sh [path/to/app-release.apk]
set -euo pipefail
APK=${1:-app/build/outputs/apk/release/app-release.apk}
BT=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | sort -V | tail -1)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" -d "$TMP/apk"
fail=0
WORDS='hold =|vault|bible|scripture|jesus|christ|gospel|church|psalm|genesis|testament|apostle|prophet|messiah|sermon|prayer|holy spirit|berean|king james|world english|literata|nicodemus|israel|yahweh'
# Text of the manifest and resources, plus every string in dex and other files.
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK" > "$TMP/manifest.txt"
"$BT/aapt2" dump strings "$APK" > "$TMP/res.txt"
hits=$( (cat "$TMP/manifest.txt" "$TMP/res.txt"; find "$TMP/apk" -type f -exec strings -n 4 {} \; ; find "$TMP/apk" -type f | sed "s|$TMP/apk/||") | grep -ioE "$WORDS" | sort | uniq -c || true)
if [ -n "$hits" ]; then echo "FAIL: telltale words found:"; echo "$hits"; fail=1; else echo "ok: no telltale words"; fi
if grep -q 'android.permission.INTERNET' "$TMP/manifest.txt"; then echo "FAIL: INTERNET permission"; fail=1; else echo "ok: no INTERNET permission"; fi
perms=$("$BT/aapt2" dump permissions "$APK" | grep -c 'uses-permission' || true)
[ "$perms" = 0 ] && echo "ok: no permissions" || { echo "FAIL: $perms permissions"; fail=1; }
grep -q 'allowBackup.*=false' "$TMP/manifest.txt" && echo "ok: allowBackup=false" || { echo "FAIL: backups allowed"; fail=1; }
exported=$(grep -c 'exported(0x01010010)=true' "$TMP/manifest.txt" || true)
[ "$exported" = 1 ] && echo "ok: only the launcher is exported" || { echo "FAIL: $exported exported components"; fail=1; }
echo "size: $(stat -c %s "$APK") bytes"
exit $fail
