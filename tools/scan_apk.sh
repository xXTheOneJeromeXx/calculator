#!/usr/bin/env bash
# Phase gate check: the release APK must hold no religious terms, no INTERNET permission,
# backups off, and nothing exported but the launcher activity.
# Usage: tools/scan_apk.sh [path/to/release.apk]   (either edition; direct is the default)
set -euo pipefail
APK=${1:-app/build/outputs/apk/direct/release/app-direct-release.apk}
BT=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | sort -V | tail -1)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" -d "$TMP/apk"
fail=0
WORDS='hold =|vault|bible|scripture|jesus|christ|gospel|church|psalm|genesis|testament|apostle|prophet|messiah|sermon|prayer|holy spirit|berean|king james|world english|literata|nicodemus|israel|yahweh'
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK" > "$TMP/manifest.txt"
"$BT/aapt2" dump strings "$APK" > "$TMP/res.txt"
pkg=$("$BT/aapt2" dump packagename "$APK")
echo "package: $pkg"
# The direct edition must not name the play edition either (its name leads to the Play listing).
[ "$pkg" = com.tbce.calc ] && WORDS="$WORDS|kanaiic"
# The dictionary's word data (assets/w) is an ordinary English dictionary, which naturally
# defines words like "church"; it is decoy content and skipped here. Everything else is checked:
# manifest and resources, plus every string in dex and other files, and the file names.
hits=$( (cat "$TMP/manifest.txt" "$TMP/res.txt"; find "$TMP/apk" -type f -not -path "$TMP/apk/assets/w/*" -exec strings -n 4 {} \; ; find "$TMP/apk" -type f | sed "s|$TMP/apk/||") | grep -ioE "$WORDS" | sort | uniq -c || true)
if [ -n "$hits" ]; then echo "FAIL: telltale words found:"; echo "$hits"; fail=1; else echo "ok: no telltale words"; fi
if grep -q 'android.permission.INTERNET' "$TMP/manifest.txt"; then echo "FAIL: INTERNET permission"; fail=1; else echo "ok: no INTERNET permission"; fi
perms=$("$BT/aapt2" dump permissions "$APK" | grep -c 'uses-permission' || true)
[ "$perms" = 0 ] && echo "ok: no permissions" || { echo "FAIL: $perms permissions"; fail=1; }
grep -q 'allowBackup.*=false' "$TMP/manifest.txt" && echo "ok: allowBackup=false" || { echo "FAIL: backups allowed"; fail=1; }
# Only launcher entry points may be exported: one per disguise alias, and nothing else
# (no exported providers, services, or receivers). So exported components == launcher filters.
exported=$(grep -c 'exported(0x01010010)=true' "$TMP/manifest.txt" || true)
launchers=$(grep -c 'android.intent.category.LAUNCHER' "$TMP/manifest.txt" || true)
if [ "$exported" = "$launchers" ] && [ "$exported" -ge 1 ]; then
  echo "ok: only launcher faces are exported ($exported)"
else
  echo "FAIL: $exported exported components but $launchers launcher entries"; fail=1
fi
echo "size: $(stat -c %s "$APK") bytes"
exit $fail
