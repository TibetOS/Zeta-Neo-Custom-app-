#!/bin/bash
# Agent driver for OutlanderHub on a local Android emulator.
# Verified on macOS arm64 with the google `android` CLI + OpenJDK 21.
set -euo pipefail

export PATH="$HOME/.local/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
AVD="${AVD:-medium_tablet}"
APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
OUT="${DRIVER_OUT:-$REPO_ROOT/build/driver}"
APP_ID="com.traffko.outlanderhub"

if [ -z "${JAVA_HOME:-}" ]; then
  for j in /opt/homebrew/opt/openjdk@21 /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk; do
    [ -x "$j/libexec/openjdk.jdk/Contents/Home/bin/java" ] && export JAVA_HOME="$j/libexec/openjdk.jdk/Contents/Home" && break
  done
fi

die() { echo "driver: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null || die "missing '$1' — run: $SCRIPT_DIR/driver.sh doctor"; }

doctor() {
  local ok=0
  if command -v android >/dev/null; then echo "ok   android CLI $(android --version 2>/dev/null | tail -1)"; else
    echo "MISS android CLI — fix: curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android"; ok=1; fi
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version >/dev/null 2>&1; then echo "ok   JDK at $JAVA_HOME"; else
    echo "MISS JDK 17+ — fix: brew install openjdk@21"; ok=1; fi
  if command -v jq >/dev/null; then echo "ok   jq"; else echo "MISS jq — fix: brew install jq"; ok=1; fi
  if command -v adb >/dev/null; then echo "ok   adb"; else echo "MISS adb — comes with 'android sdk install platform-tools' (sdk platform-tools/ dir) or brew install android-platform-tools"; ok=1; fi
  if [ -d "$HOME/Library/Android/sdk/platforms/android-35" ]; then echo "ok   SDK platform-35"; else
    echo "MISS SDK packages — fix: android sdk install platform-tools platforms/android-35 build-tools/35.0.0 build-tools/34.0.0"; ok=1; fi
  if [ -f "$REPO_ROOT/local.properties" ]; then echo "ok   local.properties"; else
    echo "MISS local.properties — fix: echo \"sdk.dir=\$HOME/Library/Android/sdk\" > $REPO_ROOT/local.properties"; ok=1; fi
  if android emulator list 2>/dev/null | grep -q "^$AVD$"; then echo "ok   AVD $AVD"; else
    echo "MISS AVD — fix: android emulator create $AVD  (downloads system image, ~5 min)"; ok=1; fi
  return $ok
}

build() {
  need android; [ -n "${JAVA_HOME:-}" ] || die "no JDK — run doctor"
  [ -f "$REPO_ROOT/local.properties" ] || echo "sdk.dir=$HOME/Library/Android/sdk" > "$REPO_ROOT/local.properties"
  (cd "$REPO_ROOT" && ./gradlew :app:assembleDebug)
  ls -lh "$APK"
}

booted() { adb get-state >/dev/null 2>&1 && [ "$(adb get-state 2>/dev/null)" = "device" ]; }

boot() {
  need android
  if booted; then echo "emulator already running"; return 0; fi
  android emulator list | grep -q "^$AVD$" || android emulator create "$AVD"
  android emulator start "$AVD"   # blocks until fully booted
}

install() {
  need android; [ -f "$APK" ] || die "no APK — run: driver.sh build"
  android run --apks "$APK"       # installs AND launches the launcher activity
}

launch() { adb shell am start -n "$APP_ID/.MainActivity"; }

layout() { need android; android layout -p; }

texts() { need android; need jq; android layout | jq -r '.[].text | select(. != null)'; }

# tap "<visible text>" — find the text node's center in the layout tree and tap it
tap() {
  need android; need jq
  local target="$1"
  local c
  c=$(android layout | jq -r --arg t "$target" \
      'first(.[] | select(.text == $t)) | .center // empty' | tr -d '[]')
  [ -n "$c" ] || die "text '$target' not on screen (try: driver.sh texts)"
  adb shell input tap "${c%,*}" "${c#*,}"
  sleep 1.5   # AnimatedContent fade is ~260ms; leave margin
}

# tab Home|Dash|Car|CAN|Setup — dock labels double as tap targets
tab() { tap "$1"; }

ss() {
  need android; mkdir -p "$OUT"
  local f="$OUT/${1:-screen-$(date +%H%M%S)}.png"
  android screen capture -o "$f" >/dev/null
  echo "$f"
}

assert_text() {
  texts | grep -qxF "$1" && echo "PASS  '$1' visible" || die "FAIL: '$1' not visible"
}

smoke() {
  [ -f "$APK" ] || build
  boot
  install
  sleep 3                       # let demo bus start streaming
  tab "Dash";  assert_text "KM/H";                 ss smoke-dash
  tab "Setup"; assert_text "VEHICLE DATA SOURCE";  ss smoke-setup
  tab "CAN";   texts | grep -q "demo code=" && echo "PASS  demo CAN events streaming" \
                || die "FAIL: no demo events on CAN screen"
  ss smoke-can
  echo "SMOKE PASS — screenshots in $OUT"
}

stop() { need android; android emulator stop "$AVD"; }

usage() {
  sed -n 's/^\([a-z_]*\)() {.*/  \1/p' "$0"
  echo "env: AVD (default medium_tablet), DRIVER_OUT (default <repo>/build/driver), JAVA_HOME (autodetected)"
}

cmd="${1:-usage}"; shift || true
case "$cmd" in
  doctor|build|boot|install|launch|layout|texts|tap|tab|ss|assert_text|smoke|stop|usage) "$cmd" "$@" ;;
  *) usage; exit 1 ;;
esac
