---
name: run-outlanderhub
description: Build, run, screenshot, and drive the OutlanderHub head-unit app on a local Android emulator. Use when asked to run/start/launch the app, take a screenshot, verify a UI change works, or smoke-test before release. Wraps the google `android` CLI + adb in driver.sh.
---

# Run OutlanderHub

OutlanderHub is a landscape launcher/dashboard app for the ZETA NEO 14 head
unit (Mitsubishi Outlander). On an emulator it runs fully: the vehicle data
source defaults to **DEMO** (`DemoVehicleBus` simulates a drive), so the
Dash/Car/CAN screens show live data with no car attached.

Everything is driven through **`.claude/skills/run-outlanderhub/driver.sh`**.
All paths below are relative to the repo root. Screenshots land in
`build/driver/` (gitignored).

## Agent path (use this)

```bash
.claude/skills/run-outlanderhub/driver.sh doctor   # verify toolchain, prints exact fix per missing piece
.claude/skills/run-outlanderhub/driver.sh smoke    # build-if-needed → boot → install+launch → drive 3 screens → assert → screenshots
```

`smoke` passes when Dash shows `KM/H`, Setup shows `VEHICLE DATA SOURCE`, and
the CAN screen streams `demo code=...` events. Piecemeal commands:

```bash
d=.claude/skills/run-outlanderhub/driver.sh
$d build          # gradle :app:assembleDebug → app/build/outputs/apk/debug/app-debug.apk (~58M)
$d boot           # create AVD if missing; start; BLOCKS until fully booted (first create downloads image, ~5 min)
$d install        # android run --apks ... — installs AND launches
$d launch         # am start .MainActivity (app must already be installed)
$d tab Dash       # tap a dock tab: Home | Dash | Car | CAN | Setup
$d texts          # visible text nodes (layout tree) — use to assert state
$d tap "Clear"    # tap any visible text by its layout center
$d ss my-shot     # screenshot → build/driver/my-shot.png
$d layout         # full UI tree JSON (pretty)
$d stop           # shut the emulator down
```

## Prerequisites (macOS arm64 — exact commands that worked)

```bash
brew install openjdk@21 jq        # /usr/bin/java is a STUB — "Unable to locate a Java Runtime"
curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/android -o ~/.local/bin/android
chmod +x ~/.local/bin/android     # binary self-unpacks + prints ToS on first run
android sdk install platform-tools platforms/android-35 build-tools/35.0.0 build-tools/34.0.0
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
android emulator create medium_tablet
```

Notes: the official installer is `curl ... install.sh | bash`, but agent
permission rules often block executing downloaded scripts — the direct binary
download above is what the script does anyway. Installed this way, `android`
is NOT on PATH for new shells (`~/.local/bin`); driver.sh handles that and
JAVA_HOME itself. SDK lives at `~/Library/Android/sdk`.

## Build & test

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest
```

First build downloads Gradle 8.14.3 + deps (~2 min); incremental ~10 s. Debug
builds are signed with the checked-in `release-signing.keystore`, so any local
build installs over any CI/release build without uninstalling.

## Human path

The emulator opens as a normal window on the desktop — click around freely.
On the real head unit there is no adb (USB debugging is broken on the unit);
installs happen by sideloading a release APK from GitHub Releases over a phone
hotspot. Not verifiable from this machine — see project memory/docs.

## Gotchas

- **The emulator window is interactive on the desktop.** A stray human click
  changes the app's screen mid-drive. Never assume which tab is current —
  always `tab <name>` before asserting.
- **`boot` restores a snapshot**, so the app may resume already-running with
  accumulated state (old CAN events, last tab). `install` is the clean-slate
  relaunch. A cold boot exists via `android emulator start --cold` (untested).
- **Layout `center` is a string** `"[x,y]"`, not an array — and coordinates
  are physical pixels that match `adb shell input tap` 1:1 (screen 2560×1600).
- **Icon-only nodes have no `text` field** — `jq -r '.[].text'` prints
  `null`s; filter with `select(. != null)` (driver's `texts` does).
- **Dock tabs are tappable via their label text** ("Home"…"Setup" at y≈1550);
  tab switch animation is ~260 ms — driver sleeps 1.5 s after every tap.
- **Switching Setup → data source to FYT on the emulator** binds nothing
  (no `com.syu.ms` there — nor on the real unit, see memory); the CAN screen
  then shows bind diagnostics instead of demo events. Demo is the default.
- `android emulator stop` takes ~10 s (waits for clean termination); `start`
  blocks until boot completes — no manual `wait-for-device` loops needed.

## Troubleshooting (hit + fixed this session)

- `Unable to locate a Java Runtime` from `/usr/bin/java` → `brew install
  openjdk@21`; driver.sh autodetects keg-only Homebrew JDKs (no sudo needed).
- `android: command not found` in a fresh shell → it's in `~/.local/bin`,
  which the direct-download install does not add to PATH.
- `driver.sh tap` dies "text not on screen" → run `driver.sh texts`; the
  string must match a whole node exactly (case-sensitive, e.g. `KM/H`,
  `VEHICLE DATA SOURCE` are uppercase on screen).
