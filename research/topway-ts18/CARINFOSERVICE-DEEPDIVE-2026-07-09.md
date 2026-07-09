# com.tw.carinfoservice deep-dive — can OutlanderHub read doors/speed/fuel, and how

Consolidation of four investigation threads against
[`DEEP-RESEARCH-2026-07-09.md`](DEEP-RESEARCH-2026-07-09.md),
[`raise-protocol-decode.md`](raise-protocol-decode.md), and
[`outlander-2019-canbus-fieldguide.md`](outlander-2019-canbus-fieldguide.md).
Target: ZETA NEO 14 = Topway TS18 (UIS8581A, Android 10), Outlander 2019 ICE.

## 1. Executive summary

- **Yes, but not through `com.tw.carinfoservice`.** That package owns the data but exposes **no app-usable IPC** — no exported broadcast, no ContentProvider, no Settings key, no unprivileged bound service. Every route into it requires the **platform signature / `android.uid.system`**, which a `/data/app` install cannot obtain.
- **Best path now:** read the **canbox serial stream via `android.tw.john.TWUtil`** — the path the app already targets. On the Raise/RZC box, continuous telemetry (speed/RPM/fuel/gear/doors/lights) arrives as MCU-**pre-decoded** frames; our decoder just needs the right handler tag and the right frame layout, both confirmable from one live capture.
- **Fallback:** if the framework path yields nothing (or `com.tw.carinfoservice` proves to have zero exported components on a live `dumpsys`), go **root + LSPosed** to hook inside `com.tw.service`/`com.tw.carinfoservice`, or DIY/replace the canbox and read ELM327 raw IDs (`0x154` fuel, `0x214/0x215` speed).

## 2. carinfoservice access — the exact (non-)surface

**Owner.** The vehicle/CAN owner on TS18 is the platform-signed system package `com.tw.carinfoservice` (uid=1000, successor of `com.tw.car`), with uid=1000 siblings `com.tw.carchoose` (CAN-TYPE chooser), `com.tw.reverse`, `com.tw.service`, `com.tw.coreservice`, `com.tw.core`, `com.dofun.carsetting` ([DEEP-RESEARCH L44](DEEP-RESEARCH-2026-07-09.md); [thread 4](#)).

**The only real IPC is private + platform-gated.** Vehicle/MCU data is served by the bound service `com.tw.service.xt.CommandService` (in priv-app `com.tw.service`, `/system/priv-app/com.tw.service_a5a4/…`), bind action **`com.tw.service.xt.CommandService.Bind`**, binder descriptor **`com.tw.service.xt.aidl.ITWCommandAidl`** ([`classes.string-hits.txt`](classes.string-hits.txt); [Auxio-TS runtime dump](https://github.com/cbkii/Auxio-TS)).

- **AIDL shape** ([`ITWCommandAidl_proxy.java`](ITWCommandAidl_proxy.java), [`ITWCommandCallbackAidl_stub.java`](ITWCommandCallbackAidl_stub.java)): register a callback binder `com.tw.service.xt.aidl.ITWCommandCallbackAidl` via txn2 `a(cb)` / unregister txn1 `b(cb)`; send commands `W(int)`=txn27, `m(int)`=txn29, `d(int,String,String)`=txn30, `a(Bundle)`=txn67. **There is no pull getter** — no `getSpeed`/`getDoor`/`getVehicle`. Vehicle data is **pushed** through the callback's scalar-int methods `O/R/V/X/Z/k(int)` + `c(int,String,String)` + `b(Bundle)`.
- **Consumer requirement — hard gate.** Stock `com.tw.music` (the reference consumer that binds this service) declares `android:sharedUserId="android.uid.system"` and installs to `/system/priv-app`. That is exactly the privilege needed to bind ([`twmusic_manifest_core_components.md`](https://github.com/cbkii/Auxio-TS)). `ITWCommandAidl`, `ITWCommandCallbackAidl`, `android.uid.system`, and `android.tw.john.TWUtil` are listed as **forbidden vendor-private surfaces** for unprivileged apps ([`reference-contracts.json`](reference-contracts.json)). **This needs an on-device pull only in the sense that the callback *semantics* (which int = doors/speed/fuel, the Bundle keys) are undocumented and recoverable only on the unit — but even with them, an unprivileged OutlanderHub still cannot bind.**

**What is verified absent for an unprivileged consumer:**

| Surface | Result | Evidence |
|---|---|---|
| Exported vehicle **broadcast** (`com.tw.carinfoservice`, any `com.tw.*` door/speed action) | **none** — zero hits across every TS10/TS18/DoFun/NavRadio repo; only MUSIC/RADIO actions are public (`com.tw.music.info`, `com.tw.radio.state`, …), none carry vehicle data | gh search (multiple); [`classes.string-hits.txt`](classes.string-hits.txt) L306-313 |
| **ContentProvider** / authority | **none** | gh search `carinfoservice provider` = [] |
| **Settings.System/Global** vehicle key | **none** | gh search `android.tw getSpeed` = [] |
| Unprivileged **bound service** | **none** — purpose-built [BTAndroidTS](https://github.com/cbkii/BTAndroidTS) treats `com.tw.carinfoservice` as **detect-only** (`isInstalled()`/`isEnabled()`), never binds/queries | `AndroidVendorPackageInspector.kt` L57 |

**Corroborating vendor pattern.** A working vendor CAN app (KSW `TsMainUI`) does **not** use IPC for vehicle data either — its whole CAN surface is in-process native JNI (`com.lgb.canmodule.CanJni`, per-car `.so`; `CanJni.OutLanderCarTypeSet(item)`). Its app-facing `ITsCommon` (`com.ts.main.common`) *does* expose typed getters (`float GetSpeed()`, `int GetReverState()`, `int GetBrakeState()`, `int GetMcuPowerState()`, `String GetTemp()`) — proving the getter vocabulary Topway system apps use internally, but it is KSW-private and not `com.tw` ([KswCarProject/TsMainUI](https://github.com/KswCarProject/TsMainUI)). **Both vendors: vehicle data is in-process-native or platform-signed AIDL, never reusable public IPC.**

## 3. APK-pull & runtime-introspect plan (lightest first)

Goal: prove the negative on *this* firmware and recover any callback semantics without the platform key. All steps run from OutlanderHub itself (no root) except where marked.

1. **[do-now] Dump exported components to the CAN log.** In `TopwayProbe.deepInspect`, for each target package run `PackageManager.getPackageInfo(pkg, GET_SERVICES | GET_RECEIVERS | GET_PROVIDERS | GET_ACTIVITIES)` and emit every `ServiceInfo/ActivityInfo/ProviderInfo` with `.exported`, `.permission`, `.name`. This is the cheapest way to confirm whether `com.tw.carinfoservice` has *any* bindable/queryable surface on the ZETA NEO 14 build. Targets: `com.tw.carinfoservice`, `com.tw.reverse`, `com.tw.service`, `com.tw.coreservice`, `com.tw.core`, `com.tw.carchoose`, `com.dofun.carsetting`.
2. **[do-now] Reflect the TWUtil surface.** `Class.forName("android.tw.john.TWUtil", false, cl)` then dump declared constructors + methods (`getDeclaredConstructors()`, `getDeclaredMethods()`) to confirm whether `addHandler(String,Handler)` exists and whether push is fixed-tag vs tag-keyed (resolves the gap in §6). Already partly wired via `TwUtilReader.resolveClass`.
3. **[needs-device] Live-toggle capture.** With the CAN tab logging, physically toggle each door / shift to R / drive, and record `Message(what,arg1,arg2,obj)` — this recovers *which what-byte / subtype* carries doors vs speed vs fuel on the OEM RZC box (the one fact no public source has).
4. **[needs-device, root] If steps 1-3 show no usable framework path:** `adb pull` `/system/priv-app/com.tw.service*/*.apk` + `com.tw.carinfoservice*.apk`, decompile, read the `ITWCommandCallbackAidl` impl to map `O/R/V/X/Z/k`/Bundle keys to signals; then hook it via LSPosed (precedent: [ts18-intent-bridge](https://github.com/cbkii/ts18-intent-bridge), currently radio/music/SAF only — no vehicle rule exists yet).
5. **[needs-device] `pm dump` / `dumpsys package com.tw.carinfoservice`** on the unit is the only thing that *proves* the exported-component negative for this exact firmware.

## 4. MitsubishiRaise decoder — full signal table

**Critical scope correction (thread 3 overturns the prior thread note).** [`AndroidVehicleMiddleServicesToolkits`](https://github.com/zhuchao-octopus/AndroidVehicleMiddleServicesToolkits) is **not** a byte-level speed/fuel/RPM decoder. There is **no `getSpeed`/`getFuel`/`getRpm`/`getGear`/`getWaterTemp`/`getMileage`** in `Canbox.java` or any `*Raise` class. Continuous drive telemetry is delivered **pre-decoded by the canbox MCU firmware** as a fixed **15-byte `mDriveData` block** that this Java layer only forwards by broadcast. The prior claim "car-exact decoder inherits VW-PQ `0x41` door/speed/fuel" is **FALSE** for speed/fuel/rpm — the `0x41` at `Canbox.java:915` is a voice-control command remap (`case 0x8: cmd=0x41`), not a CAN drive-data type. Only **discrete** signals are byte-decoded in Java.

### 4a. The 15-byte drive-data block (MCU-supplied; forwarded by broadcast)

This is where **speed/RPM/fuel/gear/handbrake/lights** actually live for the Mitsubishi profile. `NUM_DRIVE_DATA=15`, layout verbatim from `Canbox.java` header comment L176-192 (low byte first). **Emitted only by broadcast** `Intent(MyCmd.BROADCAST_SEND_FROM_CAN)` with `EXTRA_COMMON_CMD=CANBOX_RETURN_DRIVE_DATA`, `EXTRA_COMMON_DATA=byte[15]`, gated by `mRequestDriveData` (opt in via `requestDriveData(1)`) — `Canbox.java:1258-1282`.

| Byte | Signal | Formula / bits |
|---|---|---|
| 0 | units bitfield | b0 metric/imperial, b2-1 fuel-cons unit, b3 dist km/mi, b4 temp C/F, b6-5 pressure, b7 speed kmh/mph |
| 1-2 | **speed** (车速) | uint16, low byte first |
| 3-4 | **RPM** (转速) | uint16, low byte first |
| 5-7 | total mileage | uint24; display = value × 0.1 |
| 8-9 | range (续航里程) | uint16 |
| 10 | **gear + handbrake** | b0-3: 0=unknown 1=R 2=N 3=P 4=D; b4 handbrake 0=down 1=up |
| 11 | seatbelt/alarm | b0-1: 0=unknown 1=fastened 2=unfastened |
| 12 | **fuel** (油量) | byte |
| 13 | water temp (水温) | value − 40 (°C) |
| 14 | **lights** | b7 reverse, b6 brake, b5 right-turn, b4 left-turn, b3 hazard, b2 low-beam, b1 high-beam, b0 position/width |

> Note the endianness difference vs [`raise-protocol-decode.md`](raise-protocol-decode.md): this MCU block is **little-endian** (low byte first); the `0x41`/`0x2e` frame table in that doc is **big-endian**. They are two different transports — do not conflate. Which one the ZETA NEO 14 emits is a §6 device-capture question.

### 4b. Command-ID bit-packing (confirms `cmd | style<<8 | mask<<16 | dataIndex<<24`)

`Canbox.java:1284-1348`. `buildCmdDoor(cmd,style,mask,dataIndex)` → `mIdDoor = (cmd&0xff) | ((style&0xff)<<8) | ((mask&0xff)<<16) | ((dataIndex&0xff)<<24)`. `buildBrake(cmd,arrayIndex,mask,style)` → `(cmd) | (arrayIndex<<8) | (mask<<16) | (style<<24)`. `buildCmdRadar(cmd,style,max,num)` → `(cmd)|(style<<8)|(max<<16)|(num<<24)`. `buildCmdKey(cmd,style,index,arrayIndex)` → `(cmd)|(style<<8)|(index<<16)`. `buildCmdEQ(cmd,style,len)` → `(cmd)|(style<<8)|(len<<16)`. `buildCmdOutTemp/Version(cmd,style)` → `(cmd)|(style<<8)`. **Bug** in the 5-arg `buildCmdDoor` overload (`:1346`): ignores `subCmd`, duplicates `dataIndex` into both bits24-27 and 28-31.

### 4c. MitsubishiRaise wiring — only 7 channels

`MitsubishiRaise.java:35-46`. Wires: `mIdAC=0x21`; `buildCmdRadarFront(0x23,1,4)`; `buildCmdRadarBack(0x22,1,4)`; `buildCmdEQ(0x17,0,6)`; `buildCmdVersion(0x7f,0)`; `mIdKey=0x20` + `KEYS_WHEEL`; `buildCmdRepeatSendCarType(handshake)`. It does **NOT** wire doors, brake, speed, fuel, rpm, gear, out-temp, or lights (`mIdDoor=mIdBrake=mIdOutTemp=0`) — so for this profile those come **only** from the 15-byte block, not a dedicated frame.

### 4d. Discrete signals byte-decoded in Java (portable) — cmd/style/mask/index/formula

| Signal | cmd (Mits) | style | Decode | Emit |
|---|---|---|---|---|
| **Doors** (`parseDoor`, *not wired* for Mits) | — | style1=VW/PQ | `door=data[index]&mask`; style1 in = b7 RF/b6 LF/b5 RR/b4 LR/b3 trunk/b2 hood → `((d&0x40)>>6)|((d&0x80)>>6)|((d&0x10)>>2)|((d&0x20)>>2)|((d&0x08)<<1)|((d&0x4)<<3)`; styles 2/3/4 = alt permutations | Msg `CANBOX_DOOR_STATUS=0x85` to `"CanService"`, on-change (`:1735-1834`) |
| **Parking-brake** (`parseBrake`, *not wired*) | — | `(id>>24)` | `v=data[data_index]&mask`; `updateCanboxBrake(v==0?0:1)` (`:1703`) | via CarUtil |
| **Radar front** | `0x23` | 1 | `radarChangeStyle1`: if `0<data<=max` → `data=max-data+1`; `ret=((data-1)*100/step)+1`, `step=(max*100)/11`; smaller raw = nearer. Front→`mRadar[4..7]` | `RadarManager` (`:1595-1701`) |
| **Radar back** | `0x22` | 1 | same; back→`mRadar[0..3]` | `RadarManager` |
| **Out-temp** (`parseOutTemp`, *not wired*) | — | 0 | `temp=((data[2]&0xff)-40)*10`; style1 `=(data[2]&0x7f)*10`, neg if b7 | `updateOutDoorTemp` (`:2177`) |
| **AC/climate** (override) | `0x21` | — | repack frame→`airData`: `[1]=data[4]&0xf` + mode nibble map `data[3]&0xf`{0→0x40,1→0x60,2→0x20,3→0xa0,4→0x90}; `[2]=data[5]`,`[3]=data[6]`,`[5]|=0x80`; `super.parseACInfo` | Msg `CANBOX_RETURN_AIR=0x81`/`HIDE=0x8a` + broadcast `sendCanboxAir` extra `"ac"` (`MitsubishiRaise.java:59-92`, `Canbox.java:2105`) |
| **SWC keys** | `0x20` | 0 | `data[1]==2` → `parseWheelKey0(keycode=data[2],down=data[3])`; `==1` single-shot `data[2]`. `KEYS_WHEEL`: 0x1=VOL_UP,0x2=VOL_DOWN,0x3=PREV,0x4=NEXT,0x5=SEEK_PREV,0x6=SEEK_NEXT,0x7=SOURCE,0x8=MUTE,0x9=BT_DIAL,0xa=BT_HANG,0x12=SPEECH,0x15=BACK,0x16=PLAY_PAUSE (+ panel hi-bit 0x81-0x8f) | injected key events (`MitsubishiRaise.java:12-17`, `Canbox.java:1834`) |
| **Version** | `0x7f` | 0 | `data[2..]`→String, `setProperty("canbox_version")` (`:1683`) | property |
| **EQ** (override) | `0x17` | 0 | `mEQData` from `buf[8,7,6,5,4,3]`; `returnEQData(0xf0)` | broadcast → pkg `com.canboxsetting` (`:1496`, `MitsubishiRaise.java:272`) |
| **Steer-angle** (opt `0x7d`) | `0x7d` | case8 | `a=(short)(data[3]|data[4]<<8); angle=-a; angle=angle*3000/540` | Msg `CANBOX_STEER_ANGLE=0x86` to `"Reverse"` (`:1459`) |

### 4e. Framing (confirms the project's serial contract exactly)

- **Outbound (HU→canbox), simple path used by Mitsubishi:** `sendDataToCanbox(data,len)` → `send=[len+2][0x2e][data…][simpleSum]`, `simpleSum = (Σ payload) XOR 0xFF` — exactly `0x2e | type | len | payload | checksum`, checksum = one's-complement (`Canbox.java:500,568`). Second lower wrapper `sendCanboxData` prepends `ProtocolAk47.TYPE_CAN_SEND,0x01` → `Mcu.getInstance().sendCmd` (`:366`).
- **Handshake:** Mitsubishi car-type `byte[]{0x84,0x02,0x09,0x01}` sent repeatedly **only when `CarUtil.getModelId()` ∈ {2,5}** (`MitsubishiRaise.java:48-57`). Base `startConnect` sends `{0x81,0x01,0x01}`, `stopConnect` `{0x81,0x01,0x00}` (`:444/453`). ← the field-guide's observed idle `2e 81 01 01 7c` = this start heartbeat with no RX = **no box answering** ([field guide L23-25](outlander-2019-canbus-fieldguide.md)).
- **Inbound:** `parseCanboxData(data,len)` receives an **already-unframed, checksum-verified** payload where `data[0]` = CAN type byte; dispatch is equality on `data[0] & 0xff` vs each `mIdXxx & 0xff` (`:1406-1458`). The de-framing/checksum-verify lives **upstream in external `CanService`/`McuManager` + `ProtocolAk47`, not in this repo**.

**Two emit mechanisms (corroborates the TWUtil `Message` contract):** (1) `getHandler(tag).obtainMessage(what,arg1,arg2[,obj])` to named handlers `"CanService"` (doors `0x85`, AC `0x81`/`0x8a`), `"Reverse"` (steer `0x86`), `RadarManager.TAG`; (2) `sendBroadcast(BROADCAST_SEND_FROM_CAN)` for drive-data / EQ / study. Same `what/arg1/arg2/obj` shape as `android.tw.john.TWUtil` → strong cross-thread corroboration.

**Port implication:** there is **no `getSpeed`/`getFuel` code to port**. Portable to `FytSignalDecoder`/a new decoder are the **discrete decoders** (doors 4-style, radar, brake, AC repack, out-temp, SWC map, steer-angle) and the **15-byte block byte-map**. The raw-CAN→struct conversion is MCU firmware (C), not in this tree.

## 5. Code action plan (OUR repo, smallest-first, reconciled with thread 4)

All line numbers verified against current `HEAD` (2026-07-09).

**[do-now] P0-a — fix handler tag (1 line, unblocks ALL pushes).**
`TwUtilLink.kt:54` `HANDLER_TAG = "outlanderhub"` → **`"radio"`** (or register under both). The framework `sendHandler()` hardcodes `getHandler("radio")` matched by string equality, so a receiver under any other tag gets **zero** pushes — even the discrete events we *are* allowed to see (reverse/ACC/keys) never arrive ([DEEP-RESEARCH L48](DEEP-RESEARCH-2026-07-09.md)). *Confidence: reported — confirm the tag mechanism via §3 step 2 reflection dump before trusting.*

**[do-now] P0-b — make TopwayProbe see the real owner.**
`TopwayProbe.kt` — add `com.tw.carinfoservice` (+ `com.tw.reverse`, `com.tw.service`, `com.tw.coreservice`, `com.tw.core`, `com.dofun.carsetting`) to **both** `KNOWN_TW_PACKAGES` (L144) and `VEHICLE_PACKAGES` (L158). Today `"carinfo"` is only in `VEHICLE_HINTS` (L168) but `deepInspect` runs solely for names in `VEHICLE_PACKAGES` (gate at L45), so the real owner is never deep-inspected. Then extend `deepInspect` to dump exported services/receivers/providers + `.permission` (§3 step 1).

**[do-now] P1 — delete dead FYT-era assumptions in the Topway path.**
`TopwayVehicleBus.kt` — remove the `0x0501` poke (L151-152), the `CAN_STATUS_ID=0x0501`/`STATUS_POLL=255` constants (L231-232), and `0x0501` from `OPEN_IDS` (L240). [`raise-protocol-decode.md`](raise-protocol-decode.md) states the `0x0501` CAN-status guess is **not** in the Raise protocol — it came from FYT; "on Raise, poke nothing; just subscribe and watch `0x41`/`0x24`." Also drop the misleading dead `channel` plumbing: `TwUtilLink.construct()` ignores its `channel` arg and always passes `0` (L146), while `TopwayVehicleBus` feeds `TwUtilReader.CANBUS_CHANNEL=7` (L158) that goes nowhere — remove the param from `open()` (L92) and the call.

**[after-capture] P2 — add a structural Raise frame decoder (largest change).**
New file `RaiseFrameDecoder.kt`, wired into `TopwayVehicleBus.onMcuMessage` (L188) **instead of/alongside** `FytSignalDecoder.apply` (L200). It must run **structurally per `(what, subtype)`, not gated on the user code→SignalKind map**, because one `what` fans out to many signals. `TwUtilLink.decodePayload` already yields `ints` = payload bytes (`byte&0xFF`, `ints[0]`=subtype), so seed the [`raise-protocol-decode.md`](raise-protocol-decode.md) big-endian layout: `what=0x41` → sub `0x01` doors `mask=ints[1]` (FL=b0…trunk=b4, hood/parkbrake=b5); sub `0x02` `rpm=ints[1]<<8|ints[2]`, `speedKmh=(ints[3]<<8|ints[4])/100`, `volts=(ints[5]<<8|ints[6])/100`, `coolant=(ints[7]<<8|ints[8])/10`, `odo=ints[9]<<16|ints[10]<<8|ints[11]`, `fuel=ints[12]`; sub `0x03` warnings b6 low-volt / b7 low-fuel. **Do NOT hardcode `0x41`** — keep the code→signal remap so a live session can retarget DOORS/SPEED to `0x24`/`0x28`/`0x7D` if that is what actually toggles (dialect ambiguity, §6). Fork rather than overload: keep `FytSignalDecoder` scalar decoding for `FytVehicleBus`; add the frame decoder only for `TopwayVehicleBus`. *This step depends on the P0/§3-step-3 capture to pick the real what-bytes.*

**[after-capture] P3 — cleanup TwUtilReader dead live-read half.**
`TwUtilReader.kt` — delete `attemptLiveRead` (L50), `openStartClose` (L67), `construct` (L88), `invokeOpen`, and constants `CANBUS_CHANNEL=7` (L29), `SERIAL_BAUD=115200` (L30, **wrong** — Raise MCU link is 38400 8N1), `CANBUS_MESSAGE_IDS` (L31); drop its test. Keep only `resolveClass` (L39) + `CLASS_NAME` (L21) which `TopwayProbe`/`Bus` use. Note `construct()` here tries the `(int)` ctor **first** (L89) — the opposite of the SIGABRT-safe order `TwUtilLink.construct` documents (no-arg first, L145) — another reason to delete it.

**[after-capture] P4 — retarget gear + rewrite door test.**
Derive gear from the **reverse bit** (R vs not-R), not a PRND index/string (`FytSignalDecoder.decodeGear` L70; Raise has no gear frame, and `0x318`-gear is verified-absent from the 2019 C-bus — [DEEP-RESEARCH L151](DEEP-RESEARCH-2026-07-09.md)). Rewrite `FytSignalDecoderTest` door assertions (L84-94) around the `{0x01,state}` Raise frame — the current test asserts mask in `ints[0]`, which is the **subtype** byte for Raise (`decodeDoors` L47 reads `ints[0]`, off-by-one — for Raise the mask is `ints[1]`). Keep the FYT scalar tests scoped to `FytVehicleBus` only.

## 6. Contradictions & confidence

**Overturned prior claim (verified).** Thread 3 corrects the standing thread-note "MitsubishiRaise inherits VW-PQ `0x41` door/speed/fuel decoder": **no such byte decoder exists** in `AndroidVehicleMiddleServicesToolkits` — speed/fuel/rpm are MCU-pre-decoded in the 15-byte `mDriveData` block; only discrete signals are byte-decoded in Java. High confidence (grep-verified: zero `getSpeed`/`getFuel`/`getRpm` hits).

**Cross-doc transport contradiction (unresolved, device-only).** Two mutually exclusive telemetry transports are documented and we cannot yet say which the ZETA NEO 14 emits:
- **(A) `Canbox.java` 15-byte block** — little-endian, `requestDriveData(1)` + `CANBOX_RETURN_DRIVE_DATA` broadcast; speed at bytes 1-2.
- **(B) [`raise-protocol-decode.md`](raise-protocol-decode.md) `0x41`/`0x2e` frame** — big-endian, speed bytes 3-4 ÷100.
Thread 4 further flags **dialect ambiguity even within (B)**: doors may be `0x24` (Toyota-Raise, Driver=b7) or `0x28` (newer RZC `BASE_INFO`), not `0x41`; `0x24` collides (lights+reverse in VW-PQ vs doors in Toyota-Raise). Our idle capture `2e 81 01 01 7c` (one's-complement checksum) matches esp32-nissan framing → hints **not** the smartgauges VW build. **Resolution: build the decoder structural, confirm against a live capture — do not ship a fixed table.** Reported/inferred.

**`android.uid.system` obtainable?** Assumed **not** (out of scope). All threads agree the platform-key routes (bind `CommandService`) are closed to a `/data/app` install; realistic no-key routes are LSPosed hook or direct canbox/CAN read. Verified (multiple forbidden-surface lists).

**Confidence ranking.**
- **Verified:** carinfoservice has no unprivileged IPC (broadcast/provider/settings/service all null across the corpus); `ITWCommandAidl`/callback shape + `android.uid.system` gate; the `0x2e | type | len | payload | ^0xFF` framing + `{0x84,0x02,0x09,0x01}` handshake; the 15-byte block byte-map; the discrete decoder table; every OUR-repo line number in §5.
- **Reported:** the `"radio"` handler tag being the delivery gate (inferred from other builds — confirm via §3 reflection dump); the `0x41` big-endian frame layout.
- **Inferred / device-only gaps:** which int callback method (`O/R/V/X/Z/k`) or Bundle key carries doors/speed/fuel (the only public consumer, T-Music, leaves vehicle callbacks as **empty stubs**); which what-byte the OEM RZC box emits and its exact scaling; whether `com.tw.carinfoservice` has *any* exported component on this firmware (only a live `dumpsys` proves it); whether the OEM box is even connected (idle-heartbeat capture says **no RX** — the canbox-connected precondition in [`outlander-2019-canbus-fieldguide.md`](outlander-2019-canbus-fieldguide.md) §Step 1-3 must be satisfied before any decode work matters); **ICE gear source entirely unknown** (`0x318` verified absent — gear may be unrecoverable from the serial stream).
