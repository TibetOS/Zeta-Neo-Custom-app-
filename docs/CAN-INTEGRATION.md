# CAN decoder integration (Zeta Neo 14 / FYT platform)

## Background

The Zeta Neo 14 is an FYT-platform Android head unit (Unisoc UIS7862-class).
Car integration works like this:

```
Outlander CAN bus ──> CAN decoder box (in the install harness)
                      └─ serial ──> head unit MCU ──> com.syu.ms (system app)
                                                       └─ binder "toolkit" service
                                                           └─ this app
```

The decoder box translates Mitsubishi CAN frames into a simple serial protocol
(usually the "Raise" or "Hiworld" protocol matching the vehicle). The system
app `com.syu.ms` owns that serial port and re-broadcasts decoded values to any
app that binds its toolkit service.

There is **no official documentation** for any of this. Everything below comes
from community reverse engineering of FYT firmwares and must be validated on
the actual unit — which is exactly what the app's **CAN** diagnostics tab is for.

## The toolkit interface

AIDL (in `app/src/main/aidl/com/syu/ipc/`):

- `IRemoteToolkit.getRemoteModule(int moduleCode)` → module handle
- `IRemoteModule.register(IModuleCallback cb, int updateCode, int flag)` — subscribe
- `IRemoteModule.cmd(int cmdCode, int[] ints, float[] flts, String[] strs)` — send command
- `IModuleCallback.update(int updateCode, ...)` — value pushed to us

`FytVehicleBus` binds with each candidate action in
`FytProtocol.SERVICE_ACTIONS` until one succeeds, requests the CANBUS module,
and registers for update codes 0..255. Every callback is:

1. shown raw in the **CAN** tab, and
2. mapped to `VehicleState` through `FytSignalMap`.

## Reference implementation (the closest thing to "official")

There is no vendor SDK, but the community reference is
**`github.com/AxesOfEvil/FYTCanbusMonitor`** — a library + example app whose
`IRemoteToolkit`/`IRemoteModule`/`IModuleCallback` are JADX-decompiled straight
from `com.syu.ms`, so its descriptors are the real `com.syu.ipc.*`. The wider
protocol write-up lives at the XDA thread *"Developing an OBD2/Canbus data
logger"* (xdaforums.com/t/…/4454275). Architecture it confirms:
`com.syu.ms.apk` owns the MCU/serial hardware and exposes the toolkit binder;
`com.syu.canbus.apk` is the stock consumer; any app binds the same service.

Verbatim facts from that source, and where our code disagreed:

- **Bind by explicit component, not action alone.** The reference binds
  `Intent("com.syu.ms.toolkit")` **with** `setComponent(ComponentName("com.syu.ms",
  "app.ToolkitService"))`. Action-only binds are unreliable.
- **CANBUS module code is `7`, not 6.** Full map: MAIN 0, RADIO 1, BT 2, DVD 3,
  SOUND 4, IPOD 5, TV 6, **CANBUS 7**, TPMS 8, DVR 9, STEER 10, CUSTOMER 11,
  OBD 12, TEST 13, CAN_UP 14, AMP 15, EMITTER 16, GSENSOR 17. (Code 6 = TV — so
  the old value bound the wrong module.)
- **Update codes are ≥1000, not 0..255.** Examples: `U_CUR_SPEED=1031`,
  `U_ENGINE_SPEED=1032`, `U_CANBUS_ID=1000`, `U_EXIST_DOOR=1004`,
  `U_CANBUS_FRAME_TO_UI=1019` (raw frame passthrough). The 0..255 sweep + the
  guessed `FytSignalMap.DEFAULTS` were for the wrong ID space entirely.
- **`IRemoteModule` transaction order is `cmd=1, get=2, register=3,
  unregister=4`** — there is a `get(int, int[], float[], String[]) →
  ModuleObject` between cmd and register. An AIDL missing `get` makes
  `register` land on the service's `get` handler and `unregister` on
  `register`; subscriptions silently break. Our `.aidl` must be
  `cmd; get; register; unregister` in that order.
- **`get()` is pull-mode.** `getI(code, default)`/`getS(code)` read a value
  once (returns `ModuleObject{ints,flts,strs}`); `register` is the push
  subscription. Both are useful — poll static config with `get`, stream live
  values with `register`.
- **Sending MCU commands:** `IRemoteModule.cmd(cmdCode, ints, flts, strs)` on
  the CAN module; the low-level escape hatch discussed on XDA is
  `writeMcu(0xE3, PID, len, data…)`.

Applying these four corrections to `FytProtocol`/the `.aidl` is prerequisite to
the checklist below producing anything on a real `com.syu.ms` unit.

## Validation checklist (do this in the car)

1. Set the data source to **Zeta CAN decoder** and open the **CAN** tab.
2. Confirm `bindService OK with action=...` appears.
   - If not: the firmware exposes a different action or package. Run
     `adb shell dumpsys package com.syu.ms | grep -A3 Service` (enable ADB over
     WiFi in developer settings) and add the real action to
     `FytProtocol.SERVICE_ACTIONS`.
3. Confirm `registered for N CAN update codes` with N > 0.
   - If registration throws for every code, the AIDL method order likely
     differs on this firmware. Pull the app for verification:
     `adb pull $(adb shell pm path com.syu.ms | cut -d: -f2)` and decompile
     (jadx) → check `com.syu.ipc.IRemoteModule$Stub` transaction order, and fix
     the `.aidl` files to match.
4. Trigger events and watch which rows light up **amber** (payload changed):
   | Action in car          | Expected signal      |
   |------------------------|----------------------|
   | Open/close each door   | doors bitmask        |
   | Handbrake on/off       | handbrake            |
   | Change fan / temp      | climate              |
   | Start engine, rev      | RPM                  |
   | Drive                  | speed, gear          |
5. Tap the changed row and assign its code to the matching signal. The
   mapping applies **live** (DataStore-backed, survives restarts — no
   rebuild): the Dash and Car screens update immediately. Assigning a signal
   to a new code automatically unassigns it from the old one; "Reset all to
   defaults" restores `FytSignalMap.DEFAULTS`. Use **Export** to dump the
   raw log to a file (`Android/data/com.traffko.outlanderhub/files/can-logs/`)
   for offline analysis.

## If the toolkit route fails entirely

Fallbacks, in order of preference:

1. **Modded syu.ms / different module code** — some firmwares put CAN data on a
   different module index. Try values 0..10 in
   `FytProtocol.MODULE_CANBUS`.
2. **OBD-II Bluetooth dongle** — implement `ObdVehicleBus` against an ELM327
   dongle (the `VehicleBus` interface is already designed for it). Reliable and
   vehicle-independent; loses body signals (doors/climate) but provides full
   engine data.

## Controls (sending commands)

Climate/vehicle *control* (as opposed to reading status) goes through
`IRemoteModule.cmd(...)` on the CAN module. Command codes are
decoder-specific; use the manual sender at the bottom of the CAN tab to probe
carefully (start with reads/toggles you can immediately see, e.g. recirculation).
Avoid blasting unknown command codes while driving.
