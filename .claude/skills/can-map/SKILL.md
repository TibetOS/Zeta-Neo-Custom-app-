---
name: can-map
description: Work on OutlanderHub's CAN/vehicle-data integration — FYT toolkit binding, com.syu.ms diagnostics, signal mapping (code → Speed/RPM/doors/…), decoder payload rules, CAN log export and analysis. Use whenever the task mentions CAN, FYT, syu, signal mapping, vehicle data not showing, bind failures, the CAN tab, decoder codes, or interpreting an exported can-log file.
---

# CAN signal mapping & FYT diagnostics

The deep protocol/architecture reference is **`docs/CAN-INTEGRATION.md`** — read
it for the toolkit AIDL, the in-car validation checklist, and fallback plans.
This skill is the agent-side layer: where the code lives, how to drive the CAN
tab programmatically, and how to read what it tells you. Drive the app with
`.claude/skills/run-outlanderhub/driver.sh` (the `run-outlanderhub` skill).

## Code map

| File (under `app/src/main/java/com/traffko/outlanderhub/vehicle/`) | Owns |
|---|---|
| `fyt/FytProtocol.kt` | `com.syu.ms` constants, candidate bind actions, module codes, `FytSignalMap.DEFAULTS` (code→signal seed) |
| `fyt/FytVehicleBus.kt` | bind chain → component fallback → whole-device service scan; emits every step as `fyt-info` events |
| `fyt/FytSignalDecoder.kt` | pure JVM payload→`VehicleState` rules (doors bitmask, gear index, climate ints) — unit-testable |
| `fyt/SignalMapRepository.kt` | live DataStore-backed mapping; one code per signal; `resetToDefaults()` |
| `DemoVehicleBus.kt` | simulated drive; echoes manual commands as `demo-cmd` events |

AIDL stubs: `app/src/main/aidl/com/syu/ipc/`.

## Reading FYT bind diagnostics (what the CAN tab prints)

Switching source to FYT emits this sequence when no toolkit exists (captured
verbatim on the emulator; the real ZETA NEO 14 also lacks `com.syu.ms` — see
memory):

```
bindService returned false for action=com.syu.ms.toolkit        ← each candidate action failed
com.syu.ms is NOT installed — this unit may not run the FYT toolkit
scanning 245 packages for a CAN/vehicle service                  ← keyword scan fallback
com.google.android.gms → InitializerIntentService, CanopyService ← candidate (substring hit, may be noise)
found 3 candidate package(s) above — share them so I can target the CAN one
```

On a real FYT unit the success path is `bindService OK with action=…` then
`registered for N CAN update codes`. Candidate lines from the scan are the
input for extending `FytProtocol.SERVICE_ACTIONS`/`HOST_PACKAGE` — keyword
matches like `CanopyService` ("can") are false positives; judge by package.

## Driving the CAN tab (verified on emulator)

```bash
d=.claude/skills/run-outlanderhub/driver.sh
$d tab Setup && $d tap "Zeta CAN decoder (FYT)"     # switch source (or "Demo (simulated drive)")
$d tab CAN && $d texts | rg "fyt-info"              # read diagnostics
$d tap "Export"                                     # → LOG SAVED: …/files/can-logs/can-log-<ts>.txt
adb pull /sdcard/Android/data/com.traffko.outlanderhub/files/can-logs/<file> build/driver/
row=$($d texts | rg '^\d.*demo code' | tail -1) && $d tap "$row"   # open "Map code N" sheet
```

Export format: `<epochMs> <channel> code=<n> ints=[…]` per line. Assignments
in the sheet apply live (no rebuild) and persist; assigning a signal removes
it from its previous code.

**Manual command sender** (bottom of CAN tab): tap `cmd code` field, `adb
shell input text "42"`, ESC to close the IME, same for ints, then tap `Send`.
On demo source it echoes back as `demo-cmd code=42 ints=[7]` — proves the
UI→bus path. On FYT with nothing bound, `sendCommand` returns false silently.

## Log-reading gotchas (all hit for real)

- **The list tails**: it follows the newest row, so a row you found may scroll
  away before you tap it — re-grab the newest match right before tapping.
- **`fyt-info` bursts duplicate** after source toggles: the bus keeps a
  replay buffer (128) and re-injects the previous burst into the log, with old
  timestamps out of order. Trust timestamps, not list position.
- **The log accumulates across sources** and survives activity recreation.
  For clean diagnostics: switch to Demo → `tap "Clear"` → switch to FYT.
- **Never dismiss the IME with BACK** (`keyevent 4`) — the emulator's HOME is
  not this app, so BACK exits the activity. Use ESC (`keyevent 111`), and
  give the keyboard a second to settle before tapping `Send`, or the tap
  lands on the IME instead of the button.

## On the real unit

No adb (USB debugging broken). The workflow is: user drives the car through
the checklist in `docs/CAN-INTEGRATION.md` §Validation, taps Export, and
shares the file (or reads candidate lines aloud). The service-scan output is
designed exactly for that hand-off. Getting a build onto the unit is the
`deploy-to-unit` skill.
