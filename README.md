# Outlander Hub

Custom launcher / dashboard / vehicle-status app for a **Mitsubishi Outlander (2019)**
fitted with a **Zeta Neo 14** Android head unit (FYT platform).

## Features

- **Home (launcher)** — replaces the stock Zeta launcher: big clock, quick vehicle
  stats (outside temp, fuel, battery), a trip computer (distance / driving time /
  average speed, integrated from speed samples — works without an odometer
  signal), door-open warning, and a grid of all installed apps. The app
  registers as an Android HOME activity, so it can be chosen as the default
  launcher.
- **Dash** — live gauges: speed, RPM (with redline zone), coolant temperature,
  battery voltage, fuel, odometer, gear.
- **Car** — body status: doors/trunk, handbrake, seatbelt, climate readout and
  tire pressures (TPMS).
- **CAN** — diagnostics screen showing raw CAN-decoder traffic, used to map the
  Outlander decoder signals (see below). Rows whose payload just changed light
  up amber; tap a row to assign its code to a vehicle signal **at runtime** —
  no rebuild. Includes a manual command sender and a log exporter.
- **Setup** — switch between the *Demo* data source (simulated drive, for testing
  the UI anywhere) and the *Zeta CAN decoder* source.
- **Overlay** — optional floating vehicle pill (speed, fuel, alerts) drawn over
  other apps, most usefully over the CarPlay/ZLink projection where vehicle
  data is otherwise invisible. Tap to expand, drag to move; door/seatbelt/
  coolant/tire alerts auto-expand it. Enable in Setup (requires the
  "display over other apps" permission; the overlay hides automatically while
  Outlander Hub itself is on screen).

## Building

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

Or push to GitHub — the **Build APK** workflow compiles every pull request and
every push to `main`, uploading the APK as an artifact (Actions tab → latest
run → `outlander-hub-debug`).

## Releases & updates

Tag a commit `vX.Y.Z` and push the tag — the **Release APK** workflow builds
the release APK and publishes it on a GitHub Release. On the unit,
Setup → About → **Check for updates** compares the installed version against
the latest release and links straight to the APK download.

Release signing is driven by repository secrets (`RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` —
see `.github/workflows/release.yml`). Without them the release APK is
unsigned (not installable), so configure the keystore before tagging. Keep
using the same keystore forever: a signature change forces uninstall/reinstall
on the unit, losing settings and signal mappings.

## Installing on the Zeta Neo 14

1. Copy `app-debug.apk` to a USB stick (or download it directly on the unit).
2. On the unit: Settings → allow installs from unknown sources (naming varies by
   firmware), then open the APK with the built-in file manager.
3. To use it as the launcher: press the HOME button → Android asks which home app
   to use → pick **Outlander Hub** → *Always*.
   (The stock launcher stays installed; switch back anytime via
   Settings → Apps → Default apps → Home app.)

## Getting real vehicle data (important)

Zeta Neo units are built on the FYT platform. Vehicle data from the CAN decoder
box is distributed by the proprietary system service `com.syu.ms`. This app
binds that service and subscribes to its CAN module — but the interface is
undocumented and the exact signal codes differ per vehicle decoder.

**First run in the car:**

1. Setup → select **Zeta CAN decoder (FYT)**.
2. Open the **CAN** tab. You should see `bindService OK` and
   `registered for N CAN update codes`. If binding fails, the firmware uses a
   different service action — see `docs/CAN-INTEGRATION.md`.
3. Do things in the car (open a door, turn the wheel, press the brake, change
   fan speed, drive) and watch which rows light up **amber** — those codes'
   payloads just changed.
4. Tap the row → assign the code to the matching signal (Speed, Doors, …).
   The mapping applies live and is persisted; the Dash/Car screens update
   immediately. No rebuild needed. (`FytSignalMap.DEFAULTS` in
   `vehicle/fyt/FytProtocol.kt` only seeds the initial map; "Reset all to
   defaults" in the assign dialog restores it.) Use **Export** to save the
   raw log for offline analysis.

Full details and fallback strategies: [`docs/CAN-INTEGRATION.md`](docs/CAN-INTEGRATION.md).

## Project layout

```
app/src/main/java/com/traffko/outlanderhub/
├── MainActivity.kt / OutlanderApp.kt / MainViewModel.kt
├── vehicle/            # data layer: VehicleBus interface, VehicleState model
│   ├── DemoVehicleBus.kt   # simulated drive
│   └── fyt/                # FYT (com.syu.ms) CAN-decoder integration
├── apps/               # installed-app listing for the launcher
├── settings/           # DataStore-backed preferences
└── ui/                 # Compose screens: launcher, dashboard, controls,
                        # diagnostics, settings
app/src/main/aidl/com/syu/ipc/   # FYT toolkit AIDL (community-reproduced)
```
