#!/usr/bin/env bash
# Verifies a minified sample build kept what web3j needs at runtime. A plain assembleRelease
# cannot catch this: a dependency contributes -ignorewarnings, and a stripped Signature only
# fails on device. Run after `./gradlew :app:assembleRelease`.
set -euo pipefail

APK="${1:-app/build/outputs/apk/release/app-release.apk}"
CONFIG="${2:-app/build/outputs/mapping/release/configuration.txt}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -f "$APK" ]] || { echo "missing APK: $APK"; exit 1; }
[[ -f "$CONFIG" ]] || { echo "missing R8 configuration: $CONFIG"; exit 1; }

DEXDUMP=$(ls -d "$SDK"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1)
APKANALYZER="$SDK/cmdline-tools/latest/bin/apkanalyzer"
[[ -x "$DEXDUMP" ]] || { echo "dexdump not found under $SDK/build-tools"; exit 1; }
[[ -x "$APKANALYZER" ]] || { echo "apkanalyzer not found at $APKANALYZER"; exit 1; }

fail=0

# 1. The library consumer rules reached the host's merged R8 configuration.
for rule in '-keep class \* extends org.web3j.abi.TypeReference' \
            '-keep class org.web3j.abi.datatypes.\*\* { \*; }' \
            '-keepattributes Signature'; do
  if ! grep -q -- "$rule" "$CONFIG"; then
    echo "FAIL: merged R8 config lacks: $rule"; fail=1
  fi
done

# 2. Every SDK module's TypeReference subclasses survive under their own names, with the
#    generic Signature attribute web3j reads reflectively.
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
unzip -oq "$APK" 'classes*.dex' -d "$work"
for d in "$work"/classes*.dex; do "$DEXDUMP" "$d"; done > "$work/dump.txt" 2>/dev/null

# Class descriptors whose direct superclass is TypeReference, one per line.
awk '
  /Class descriptor/ { match($0, /'\''[^'\'']+'\''/); cls = substr($0, RSTART+1, RLENGTH-2) }
  /Superclass/ && /Lorg\/web3j\/abi\/TypeReference;/ { print cls }
' "$work/dump.txt" | sort -u > "$work/subclasses.txt"

for pkg in com/rain/sdk/internal com/rain/sdk/portal com/rain/sdk/privy; do
  n=$(grep -c "^L$pkg/" "$work/subclasses.txt" || true)
  if [[ "$n" -lt 1 ]]; then
    echo "FAIL: no TypeReference subclass kept under $pkg (renamed or removed by R8)"; fail=1
  fi
done

while IFS= read -r desc; do
  [[ "$desc" == Lcom/rain/sdk/* ]] || continue
  cls=${desc#L}; cls=${cls%;}; cls=${cls//\//.}
  if ! "$APKANALYZER" dex code --class "$cls" "$APK" 2>/dev/null | grep -q 'dalvik/annotation/Signature'; then
    echo "FAIL: $cls lost its Signature attribute"; fail=1
  else
    echo "ok: $cls"
  fi
done < "$work/subclasses.txt"

[[ $fail -eq 0 ]] && echo "PASS: web3j keep rules are effective in the minified sample" || exit 1
