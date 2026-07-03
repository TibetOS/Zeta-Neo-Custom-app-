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
4. Trigger events and note the codes that fire:
   | Action in car          | Expected signal      |
   |------------------------|----------------------|
   | Open/close each door   | doors bitmask        |
   | Handbrake on/off       | handbrake            |
   | Change fan / temp      | climate              |
   | Start engine, rev      | RPM                  |
   | Drive                  | speed, gear          |
5. Write the observed codes into `FytSignalMap`, rebuild, verify the Dash and
   Car screens now show live values.

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
