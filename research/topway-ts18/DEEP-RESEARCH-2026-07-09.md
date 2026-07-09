# Deep research — Outlander 2019 + TS18 vehicle-data path (2026-07-09)

Four parallel research threads (raw Outlander CAN, TWUtil framework internals, in-car canbox/profile, Raise/Hiworld dialect library). Consolidated, de-marketed, cross-checked against [`raise-protocol-decode.md`](./raise-protocol-decode.md) and [`outlander-2019-canbus-fieldguide.md`](./outlander-2019-canbus-fieldguide.md).

## Executive summary

- **KNOWN — wire path fully resolved.** Our heartbeat `2e 81 01 01 7c` is a *HeadUnit→CanBox* `0x81` SWITCH/power announce, checksum one's-complement verified (`(0x81+1+1)&0xFF=0x83`, `0x83^0xFF=0x7C`). Zero RX = no canbox is answering with the `0xFF` ack or any `0x2e` frames. A present canbox would reply.
- **KNOWN — the decoder is data-driven, not one fixed table.** The Raise base parser packs `cmd | style<<8 | mask<<16 | dataIndex<<24` per signal per car; `MitsubishiRaise.java` exists and inherits the base VW-PQ `0x41` door/speed/fuel layout, overriding only AC=`0x21`/keys=`0x20`/radar. This is *the* car-exact decoder.
- **KNOWN — in-car profile.** Multiple real 3rd-gen ICE installs light up under brand column **XP/SIMPLE → MITSUBISHI → OUTLANDER → a 2014 entry** (`XP(H)2014` / `XP(M)2014`); Hiworld (`MT04.21`) is the working alternative. A firmware update, not just the menu pick, unlocks doors/climate; a post-update loss is fixed by factory reset.
- **STILL-UNKNOWN — no canbox appears connected.** Everything above is moot until Step 1–3 of the field guide are done in the car: confirm/insert the box, pick the profile, get RX. The unit is currently talking to nobody.
- **STILL-UNKNOWN — door/ignition/ebrake raw bits on the ICE car, and which exact type byte our box uses.** No public capture ever toggled a door; TWUtil never pushes doors/speed/fuel to normal apps (only reverse=`772/40732`, ACC=`514`, keys=`513`). Both gaps close only with a live CAN-tab capture.

---

## Thread 1 — Raw Outlander CAN IDs & byte layouts (OBD-II / DIY-gateway plan-B)

Primary source is a genuine 2019 Outlander (ACAS trim, crashed-but-driveable) dual-bus log: [projectgus/mitsubishi-outlander-phev-can-logs](https://github.com/projectgus/mitsubishi-outlander-phev-can-logs). README verbatim: *"Logger channel 0 is the main 'C' bus (the one with the diagnostic connector), channel 1 is the EV-CAN bus."* GVRET CSV for SavvyCAN. Second corroborating source: [damienmaguire/Outlander-PHEV](https://github.com/damienmaguire/Outlander-PHEV). **All confirmed IDs are from PHEV cars; ICE-identical status for C-bus body IDs is inferred from shared body-bus architecture, not measured.**

**These raw CAN IDs matter ONLY for the OBD-II / DIY-gateway plan-B.** On the normal (canbox) path they live upstream of the box and never reach the app.

| Signal | ID | Bus | Byte layout / formula | Certainty |
|---|---|---|---|---|
| **Fuel level** | `0x154` | C | bytes 0-3 each `A*100/255`; observed `65 65 60 65 …` → 40/40/38/40 %; b0 jitters `0x65↔0x66` live | **verified** (PHEV C-bus, in *both* repos) |
| **Speed** | `0x214` (SPEED1), `0x215` (SPEED2) | C | bytes 0-1 `(A*256+B)/2`; byte 7 = rolling frame counter (`0x214` D7 cycles 0-3, `0x215` D7 4-7) | byte location **verified** (clean 0-at-rest); `/2` scale reported |
| **Gear / reverse** | `0x288` (front), `0x289` (rear) | **EV-CAN** | byte 8: P=`0x10` N=`0x10` D=`0x14` R=`0x1C`; bit2(`0x04`)=drive engaged, **bit3(`0x08`)=reverse** | **verified** (anchored to P/N/D/R shift video) — **PHEV-ONLY, absent on ICE** |
| **Climate/AC** | `0x185` | C+EV | 6 of 8 bytes swing off↔fan-max: D2 `00→C0`, D4 `00→64`(=100), D6 `00→2A`, D8 `00→10`; repo note: *"Control message for AC compressor"* | **verified** (both repos) |
| AC compressor status | `0x388` | C | byte7: `02` off / `06` on | verified |
| Steering angle | `0x236` | C | — | present-verified (byte decode unresolved) |
| Electric parking brake | `0x32F` | C | bytes 0-1 = fast rolling counter/checksum, **not** a clean ebrake bit | present-verified; state bit unresolved |
| VIN (multiplexed) | `0x6FA` | C | — | present-verified |
| EV torque (ref) | `0x287` req, `0x288` FrTrq, `0x289` RrTrq, `0x28B` GenTrq | EV | `nm = ((byte1*256+byte2)-10000)/10` | verified — PHEV-ONLY |
| EV current (ref) | `0x732` rear, `0x734` gen, `0x75A` front | EV | `A*256+B-1000` amps | verified — PHEV-ONLY |

**Full C-bus ID set present in `pb.csv`** (for cross-matching a live sniff): `0x101 0x133 0x134 0x143 0x144 0x145 0x151 0x154 0x185 0x200 0x207 0x208 0x210 0x214 0x215 0x229 0x236 0x2E1 0x2F1 0x300 0x308 0x315 0x325 0x328 0x32D 0x32F 0x330 0x345-0x34A 0x353 0x355 0x387 0x388 0x415 0x418 0x424 0x4A0 0x520 0x572 0x608 0x672 0x6F1-0x6FF 0x7BC`. **`0x318` is ABSENT** — see Contradictions. IDs cross-validated in both repos: `0x154`, `0x185`, `0x424`. ([pb.csv](https://github.com/projectgus/mitsubishi-outlander-phev-can-logs/blob/main/main-ev/202109061517-parking-brake.csv))

**DOOR-AJAR / hood / trunk / illumination / outside-temp / ignition-ACC transition = UNRESOLVED from all public data.** Every public Outlander capture kept doors shut, lights off, car already-Ready — the bits were never exercised, so event-diffing has nothing to latch onto. Candidate C-bus IDs that changed during activity but weren't door-isolable (watch these first when toggling doors on the real car): **`0x208`** (`00 20 00…` vs `00 20 30…`), `0x229`, `0x210`, `0x315`, `0x424`. ([DRIVING1.csv](https://github.com/damienmaguire/Outlander-PHEV/blob/main/CAN_Logs/DRIVING1.csv))

**OBD-II plan-B warning (Gen4 2022+, NOT our car):** newer PHEV OBD port broadcasts only 29-bit extended IDs at 500 kbaud; passive `ATMA` returns nothing — must poll via UDS service-22. Real PIDs: Odometer `22F0D0` bytes `[B4:B6]` km; SOC `2201D1` = `(B5*0.64)-19.2`; BMU header filter `ATCRA18DAF1DB`; init `ATSP7;ATST96;`. For our 3rd-gen 2019, passive C-bus broadcast is expected to work (the projectgus car broadcasts freely). ([wican-fw #468](https://github.com/meatpiHQ/wican-fw/issues/468))

---

## Thread 2 — TWUtil / TS18 framework internals

**The vehicle/CAN owner on TS18 is the system package `com.tw.carinfoservice`** (uid=1000, platform-signed) — renamed successor of `com.tw.car`. Live TS18 `dumpsys media_session` uid=1000 list: `com.tw.carinfoservice, com.tw.carchoose, com.tw.reverse, com.dofun.carsetting, com.tw.service, com.tw.coreservice, com.tw.core`. `com.tw.carchoose` = the brand/model + CANbus chooser behind Factory "CAN TYPE SET"; `com.tw.reverse` = reverse-cam trigger. ([ts18 runtime dump](https://github.com/cbkii/Auxio-TS/blob/main/docs/evidence/topway-dofun-navradio-static-analysis/excerpts/runtime-v2/ts18_media_session_runtime_v2_selected_phases.md), [TS10 Device Info](https://github.com/liaojack8/TopWay-TS10-Note/blob/master/hw-info/Device%20Info.txt))

**Message shape (verified from decompiled framework [`TWUtil-real.java`](./community/TWUtil-real.java) L145-160):** a TWUtil message is NOT a raw multi-byte `0x2e` frame split across ids. Each logical MCU event arrives as ONE `android.os.Message`: **`what` = the id, `arg1`/`arg2` = decoded scalars, `obj` = a single optional Object (String for text like RDS; else null).** The MCU pre-decodes. `sendHandler(int what,int arg1,int arg2,Object) → Message.obtain(handler,what,arg1,arg2,obj)`; all send-sites pass `obj` as String or null (e.g. `sendHandler(1025,2,freq,"")`, `sendHandler(1029,0,0,"RDS Text")`). Clients read exactly `msg.what/arg1/arg2/obj`.

**addHandler tag = arbitrary per-client string**, matched by string equality; the framework's own `sendHandler()` hardcodes tag **`"radio"`** and calls `getHandler("radio")`. So a receiver wanting MCU pushes on these builds effectively must register under `"radio"` (or own both ends). Observed tags: `"radio"` (fytFM, Radio-MST768), `"TWUtilHandler"` (d51x KaierUtils, ru.d51x.twutil), `"sleep_listener"` (An21utools TWSleeper).

**CAN is its own id family (from framework companion [`TWClient.java`](./community/TWClient.java) L38-51):** `T_CAN=0`, read `R_CAN=0xFF00` (65280) and `R_CAN_L=0xFF01` (65281), write `W_CAN_L=0xFF01` — distinct from radio (513/769/1025-1030). `TWClient.write()` body is native/un-decompilable. Same stub verbatim in [ivvlev/CarRadio](https://github.com/ivvlev/CarRadio/blob/master/twutil/src/main/java/android/tw/john/TWClient.java).

**Vehicle-event decode (d51x [`TWUtilConst`](https://github.com/d51x/KaierUtils/blob/master/kaierutils/src/main/java/ru/d51x/kaierutils/TWUtils/TWUtilConst.java) + [`TWUtilEx.java`](./community/TWUtilEx.java) L46-108):** ids are signed shorts; unsigned = the command/write id.
- **Reverse:** `TW_CONTEXT_REVERSE_ACTIVITY = -24804` (`0x9F1C`) == unsigned `40732`; `TW_CONTEXT_REVERSE_TAG = 772`. Dispatch `case 40732`: `arg1==0` reverse-finish, else reverse-start.
- **ACC/sleep:** `TW_CONTEXT_SLEEP = 514`. Dispatch `case 514`: `arg1==3 & arg2==1` sleep, `arg2==0` wake, `arg1==1 & arg2==0` shutdown.
- Others: `REQUEST_SHUTDOWN = -24816` (`40720`), `ROOT_CMD = -24806` (`40730`), `AUDIO_FOCUS = 769/40465`, `KEY_PRESS = 513`.

**CRITICAL: no TWUtil client ever receives raw doors/speed/fuel.** All six community clients (KaierUtils, An21utools, fytFM, Radio-MST768, CarRadio, Orbit/dvd-bt) only ever see discrete events (reverse=772/40732, ACC/sleep=514, keys=513) and only ever read `obj` as String — never a `byte[]` frame. Doors/speed/fuel are cluster/canbox-side signals the MCU does **not** push over TWUtil to normal apps; they live inside `com.tw.carinfoservice`. Recovering that id vocabulary requires an **on-device APK pull/decompile or logcat/broadcast capture** — no GitHub source exposes it (code search for `com.tw.car*` vehicle consumers = 0 hits).

**`open()` constraints (from [community README](./community/README.md)):** id-array must stay small — **≤13 ids; a large array SIGABRTs** the native subscription table (prior 5120-id crash). Constructor int = vendor mode/domain (radio=`1`), not a CAN channel; `open` flag (0 vs 115200) is advisory/accepted either way. All `open()==0` = success. d51x CAN-inclusive probe array = `{260,262,263,266,274,282,513,514,515,769,770,1281,-25088,-25071,-24816,-24805,-24804}`.

**`0x0501` is FYT-only, not Raise/Topway** — corroborates our own note ([`raise-protocol-decode.md`](./raise-protocol-decode.md) L133-135). On Raise, poke nothing; subscribe and watch `0x41`/`0x24`.

---

## Thread 3 — Which canbox + profile makes a 3rd-gen ICE Outlander work (real installs)

| # | Car / unit | Box / harness | **Exact menu selection** | Result | Source |
|---|---|---|---|---|---|
| A | Reseller doc, Outlander 2012-2018 | — | `carsettings → factory settings → 16176699 → CAN TYPE SET → SIMPLE → MITSUBISHI → OUTLANDER → year closest` | recommended path | [CJ Industries](https://www.cjindustriesaustralia.com/blogs/canbus-settings/mitsubishi-outlander-2012-2018-canbus-settings) |
| B | **2018 3G gasoline (ICE)**, Teyes CC3 2K 360 | — | **`XP → Mitsubishi → Mitsubishi → XP(M or L) 2014`** | reverse cam + SWC + car-settings menu worked; long-MODE mute disabled | [drive2](https://www.drive2.ru/l/701992270798262798/) |
| C | Teyes ecosystem, 3rd-gen | — | **`XP(H)2014`** (after firmware update) | enables Car app, climate panel, **open-door info**, dynamic reverse lines | [cc2.teyes.ru](https://cc2.teyes.ru/canbus/index.html) |
| D | **2016 3G 2.4L (ICE)**, Teyes CC3L | with-canbus harness option | (model pick) | SWC + doors + climate worked; **post-firmware-update canbox lost → FACTORY RESET restored** | [drive2](https://www.drive2.ru/l/684495742265477737/) |
| E | **2022 3G 2.0L gasoline (UAE import)** | Wide Media **WS-MTMT08** = **Hiworld MT04.21 / H1S8MT040A** | Hiworld brand | reverse cam + climate display + **door-open notifications** worked | [drive2](https://www.drive2.ru/l/684638129021258239/) |

**Physical boxes behind the SIMPLE/XP path:** Bestycar 16-pin `Support OEM Rockford Amplifier Camera SWC` (B0BM9CN4PT) and non-Rockford (B0BMFPK7H6); **XIBEIKE 16-pin "for Mitsubishi Outlander 2014-2020 / Pajero" (B0DXN9WBV5)**. Wiring: find 8-pin CAN plug → stereo 8-pin CAN socket → Setting → Factory Settings → CANBUS/Model Settings. ([Bestycar](https://www.amazon.com/Bestycar-Mitsubishi-Outlander-Rockford-Amplifier/dp/B0BM9CN4PT))

**Bus architecture:** the radio sits on the **medium-speed body network (CAN-IHS)** carrying HVAC, infotainment, instrument cluster, KOS/keyless — the bus the canbox reads for doors/reverse/climate/SWC. Separate from the high-speed powertrain bus (ECM/TCM/ABS/EPS/SWS/AWD). Implication: body CAN reaches the radio connector independent of the Rockford-amp option (inferred, not directly confirmed for a base non-amp 2019). ([alibaba insights](https://www.alibaba.com/product-insights/mitsubishi-outlander-radio-rockford-canbus.html))

**Baud:** car-side CAN = **250 kbps (older) or 500 kbps (newer)** — mismatch → SWC lags/fails. Exact 2019 value not pinned by any sniff log. Canbox↔HU UART = **38400 8N1**. Config guides say **SKIP "Simple"/"Auto Detect" auto-modes — pick the model manually.** 2015+/2018 add a **LIN bus** for the instrument cluster. ([Alibaba setup guide](https://electronics.alibaba.com/buyingguides/outlander-canbus-setup-guide-what-you-actually-need))

**Market caveat:** Chinese-market 3rd-gen cars use **resistive SWC (single "Rem1" wire), not CAN** — canbox not needed there for SWC; RU/EU use CAN SWC. (Also: factory cams ~6.2V vs Teyes 12.5V → LM2596 buck needed.) ([drive2 CN install](https://www.drive2.ru/l/671143547935684196/))

---

## Thread 4 — Raise/Hiworld dialect library (for live-capture pattern-matching)

Richest source: **[zhuchao-octopus/AndroidVehicleMiddleServicesToolkits](https://github.com/zhuchao-octopus/AndroidVehicleMiddleServicesToolkits)** — base [`Canbox.java`](https://github.com/zhuchao-octopus/AndroidVehicleMiddleServicesToolkits/blob/HEAD/app/src/main/java/com/zhuchao/android/car/canbox/Canbox.java) (2338 lines) + per-car decoders under `cartype/raise/` and `cartype/hiworld/`, **including car-exact [`MitsubishiRaise.java`](https://github.com/zhuchao-octopus/AndroidVehicleMiddleServicesToolkits/blob/HEAD/app/src/main/java/com/zhuchao/android/car/cartype/raise/MitsubishiRaise.java)**.

**The parser is DATA-DRIVEN.** Each signal packs into an int: `mIdDoor = cmd | style<<8 | mask<<16 | dataIndex<<24`, set per-car in the constructor via `buildCmdXxx()`. `parseCanboxData()` dispatches by comparing `data[0]` to `(mIdDoor&0xff)`, `(mIdAC&0xff)`, etc. `parseDoor()`: `style=(mIdDoor>>8)&0xff; mask=(mIdDoor>>16)&0xff; index=(mIdDoor>>24)&0x0f; door=data[index]&mask` → `doorChangeStyleN()`. **This is why smartgauges (FL=bit0) and esp32-nissan (Driver=bit7) disagree — they are just different `style` values of ONE parser.**

**MitsubishiRaise (car-exact) — inherits base VW-PQ `0x41` door/speed/fuel; overrides:** `mIdAC=0x21`, `mIdKey=0x20`, front radar `0x23`, rear radar `0x22`, EQ `0x17`, version `0x7f`. Handshake the HU must send = **`{0x84,0x02,0x09,0x01}`** (modelId 2/5). SWC 2nd bank `0x81-0x8f` (`0x81`=POWER, `0x87`=RADIO, `0x8b`=HOME).

**Four Raise door "styles" (base Canbox), all normalized to canonical FL=bit0…hood=bit5:**
- Style1 input: bit7=FR, bit6=FL, bit5=RR, bit4=RL, bit3=trunk, bit2=hood — *matches esp32-nissan*
- Style2 input: bit7=FL, bit6=FR, bit5=RL, bit4=RR, bit3=trunk
- Style3 input: bit7=FR, bit6=FL, bit5=RL, bit4=RR, bit3=trunk, bit2=hood
- Style4 input: bit5=FL, bit4=FR, bit3=RL, bit2=RR, bit1=trunk, bit0=hood

**Cross-vendor FRAMING table** ([runmousefly/Work `CanBoxProtocol.java`](https://github.com/runmousefly/Work/blob/HEAD/MctCoreServices/src/com/mct/coreservices/CanBoxProtocol.java)) — pins our profile:

| Canbox | Header | Checksum | Baud | Ack |
|---|---|---|---|---|
| **CAN_BOX_RZC (Raise)** | `0x2E` | mode 1 = `^0xFF` (one's-complement) | 38400 | needAck=1, ok=`0xFF`, fail=`0xF0` |
| **CAN_BOX_XP (Simple, our menu)** | `0x2E` | `^0xFF` | 38400 | **identical to RZC** — differs only in car-model tables |
| CAN_BOX_SS | `0xAA 0x55` | mode 0 = `&0xFF` additive | — | — |
| RZC Peugeot/Citroën (carModel 250-349) | `0xFD` | additive | 19200 | — |

Comment L36 verbatim: *"mChecksumMode 0 &0xFF; 1 ^0xFF"*. **CAN_BOX_HW/Hiworld is NOT in this framing table** — take Hiworld framing from the zhuchao Hiworld decoders.

**Our heartbeat verified against this dialect:** `0x81` = `HC_CMD_SWITCH` (power/ACC state), a HeadUnit→CanBox OUTBOUND command in [`RZC_NissanSeriesProtocol.java`](https://github.com/runmousefly/Work/blob/HEAD/MctCoreServices/src/com/mct/carmodels/RZC_NissanSeriesProtocol.java) L38 — never a canbox→HU sensor frame. `mNeedAck=1/mOkAck=0xFF` → a present canbox answers `0xFF`; [`CanboxRaiseHandler.cpp`](https://github.com/nakedg/CanbusDecoder/blob/HEAD/main/CanboxRaiseHandler.cpp) writes a single `0xFF` ack after every received frame. **Zero RX = no canbox replying.**

**Newer RZC generation (runmousefly) uses `0x28 BASE_INFO`, not `0x41`.** CanBox→HU: `0x20` SWC key, `0x21` AC, `0x22` rear radar, `0x23` front radar, **`0x28` BASE_INFO (doors/speed here)**, `0x29` wheel-angle, `0x30` version. HU→CanBox: `0x81` SWITCH, `0x83` vehicle-set, `0xC0` media-src, `0xC4` volume, `0xC8` time. RZC_Toyota ≈ RZC_Nissan (only adds `0x52`), unlike the older esp32 split.

**Independent confirmations of the VW-PQ `0x41` layout** (different radar/park type bytes, same framing):
- [icarome/VwRaiseCanbox `VwRaiseCanbox.h`](https://github.com/icarome/VwRaiseCanbox/blob/HEAD/VwRaiseCanbox.h): `CarStatus=0x41, AC=0x21, Button=0x20, WheelAngle=0x26, ParkingStatus=0x25, FrontRadar=0x23, RearRadar=0x22, Version=0x30`. Door bits FL=bit0…RR=bit3, trunk=bit4, hand_brake=bit5, clean_fluid=bit6, seat_belt=bit7. CarStatus struct order: `uint16 rpm, uint16 speed, uint8 voltage, uint16 temp, uint32 mileage, uint8 fuel`.
- [nakedg/CanbusDecoder](https://github.com/nakedg/CanbusDecoder/blob/HEAD/main/CanboxRaiseHandler.cpp): door `0x41 {0x01,state}` FL=`0x01`…bonnet=`0x20`; CarInfo `0x41 {0x02,t1..t12}` t1t2=taho BE, t3t4=speed×100 BE, t5t6=voltage, t7t8=temp BE, t9t10t11=odo 24-bit BE, t12=fuel; low-warn `0x41 {0x03,state}` low_fuel=`0x80`, low_voltage=`0x40`. Wheel `0x26`.
- [KswCarProject/BCLauncher `ProtocolAnalyzer.java`](https://github.com/KswCarProject/BCLauncher/blob/HEAD/sources/com/backaudio/android/driver/ProtocolAnalyzer.java): one's-complement `(checksum^255)==last`; type switch on `buffer[1]`: 1=CarBase, 3=AirInfo, 6=CarBase1, 7=CarRunInfo1, 8=Time, `0x12`=OriginalCar, `0x2C`=AUX, `0x35`=CarRunInfo, `0x36`=CarRunInfo2, `0x7F`=CanboxPro. Length check `buffer[2]==len-4`.

**Hiworld map** ([`NissanHiworld.java`](https://github.com/zhuchao-octopus/AndroidVehicleMiddleServicesToolkits/blob/HEAD/app/src/main/java/com/zhuchao/android/car/cartype/hiworld/NissanHiworld.java)) — confirms door frame `0x12`: `buildCmdDoor(0x12,0x02,0xfc,0x04)` (style2, mask `0xFC`, byte-index 4). Hiworld reuses type bytes differently: **radar=`0x41`** (which is *vehicle info* in Raise — a direct collision), AC=`0x31` (vs `0x21`), angle=`0x72`, EQ=`0xA6`, key=`0x72`. Handshake `{0x02,0x24,0x00,0x04}`.

`ASampleRaise.java` shows yet another door assignment — **`0x24` as DOORS** (style1, mask `0xFC`, byte-index 2): confirms `0x24` is genuinely overloaded across Raise sub-dialects (the "MQB variant" our decode note warned about).

---

## Decode dialect library — comparison table

Pattern-match a live CAN-tab capture against every dialect found. `state` bit numbering is the **on-wire input byte** (before any style normalization).

| Dialect | Door frame | Door bits (on-wire) | Speed | Fuel | RPM | Reverse | Lights / brake | Steering | AC | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| **smartgauges VW-PQ** | `0x41`/sub`0x01` `{01,state}` | FL=bit0, FR=bit1, RL=bit2, RR=bit3, trunk=bit4, hood=bit5 | `0x41`/sub`0x02` b3-4 ×100 BE | `0x41`/sub`0x02` b12 | `0x41`/sub`0x02` b1-2 BE | `0x24` bit0 | `0x24`: brake bit1, low-beam bit2 | `0x26`/`0x29` | `0x21` 5B | odo `0x41`/sub`0x02` b9-11 |
| **esp32-nissan (Toyota-Raise)** | `0x24` 1B | Driver=bit7, Pass=bit6, RR=bit5, RL=bit4, trunk=bit3 | `0x7D`/sub`0x03` b1-2 ×100 LE | `0x22` inst / `0x23` avg | `0x7D`/sub`0x0A` (RPM×4) | (gear via `0x7D`) | `0x7D`/sub`0x01`: parking `0x80`, low `0x40`, high `0x20` | `0x29` s16 LE | — | trip `0x21` 7B; **`0x24`=doors, `0x21`=trip here** |
| **MitsubishiRaise (car-exact)** | inherits VW-PQ `0x41`/sub`0x01` | FL=bit0…hood=bit5 (base) | `0x41`/sub`0x02` b3-4 ×100 BE | `0x41`/sub`0x02` b12 | `0x41`/sub`0x02` b1-2 BE | (base `0x24` bit0) | (base `0x24`) | radar F=`0x23` R=`0x22` | `0x21` | key=`0x20`, EQ=`0x17`, ver=`0x7f`; handshake `{0x84,0x02,0x09,0x01}` |
| **icarome VwRaise** | `0x41` `{01,state}` | FL=bit0, FR=bit1, RL=bit2, RR=bit3, trunk=bit4, hand_brake=bit5, washer=bit6, belt=bit7 | `0x41` CarStatus u16 (after rpm) | `0x41` CarStatus last u8 | `0x41` CarStatus first u16 | `ParkingStatus 0x25` | brake=door bit5 | `0x26` | `0x21` | ver=`0x30`, button=`0x20` |
| **nakedg Raise** | `0x41` `{01,state}` | FL=`0x01`, FR=`0x02`, RL=`0x04`, RR=`0x08`, tailgate=`0x10`, bonnet=`0x20` | `0x41` `{02,…}` t3t4 ×100 BE | `0x41` `{02,…}` t12 | `0x41` `{02,…}` t1t2 BE | — | low-warn `0x41` `{03}`: low_fuel `0x80`, low_volt `0x40` | `0x26` | — | odo t9t10t11 24-bit BE; `0xFF` ack per frame |
| **RZC newer-gen (runmousefly)** | inside `0x28` BASE_INFO | (byte offsets in HU service, not traced) | `0x28` BASE_INFO | `0x28` BASE_INFO | `0x28` BASE_INFO | (`0x28`) | (`0x28`) | `0x29` | `0x21` | key `0x20`, radar `0x22`/`0x23`, ver `0x30`; HU→box `0x81` switch |
| **ASampleRaise (MQB variant)** | **`0x24`** (style1, mask `0xFC`, idx 2) | via style1 (FR=bit7,FL=bit6…) | — | — | — | — | — | `0x26` (max `0x2198`) | `0x20` | outTemp `0x41` idx `0x10`, ver `0x30`, key `0x20` |
| **NissanHiworld (HW family)** | **`0x12`** (style2, mask `0xFC`, idx 4) | via style2 (FL=bit7,FR=bit6…) | — | — | — | — | — | `0x72` | `0x31` | radar=`0x41` (collides w/ Raise vehicle-info!), EQ=`0xA6`, key `0x72`; handshake `{0x02,0x24,0x00,0x04}` |
| **BCLauncher (backaudio MTK)** | type `0x12`=OriginalCar | (per-payload) | `0x35`/`0x36` CarRunInfo | — | — | — | — | — | `0x03` AirInfo | types: 1/6 CarBase, 7 CarRunInfo1, `0x7F` CanboxPro |

**Framing common to ALL Raise/HW dialects:** `0x2E | type | len | payload[len] | checksum`, checksum = one's-complement `(Σ(type..payload))^0xFF`, UART 38400 8N1, canbox acks each frame with `0xFF`. (SS = `0xAA 0x55` additive; Peugeot-RZC = `0xFD` additive 19200 — different families, unlikely on Outlander.)

**Live-Raise RX expectation:** repeating `type=0x41` with payload[0] alternating `0x01`(door)/`0x02`(vehicle-info), OR `type=0x28` BASE_INFO on newer RZC, plus `0x24` on gear/brake/light change. **Disambiguate by which signals actually toggle** when you open a door / shift to R — never trust a fixed table.

---

## Contradictions & confidence

**Rank per key claim: `verified` (primary source, cross-checked) · `reported` (single credible source) · `inferred` (deduced from architecture).**

1. **`0x318` = gear (our field guide) vs ABSENT from the 2019 C-bus.** [`outlander-2019-canbus-fieldguide.md`](./outlander-2019-canbus-fieldguide.md) L13/L100 lists `0x318` for gear, but Thread 1 confirms `0x318` **does not appear** in the projectgus 2019 C-bus ID set (neighbors present: `0x312 0x315 0x325 0x328 0x32D 0x32F 0x330`). **Confidence: `0x318`-gear is `reported`/likely-wrong; its absence is `verified`.** On PHEV, gear lives on EV-CAN `0x288/0x289` byte8 — but that bus doesn't exist on ICE. ICE gear source is unknown. → **Correct the field guide; do not rely on `0x318` for the ICE car.**

2. **`0x236` steering / `0x32F` ebrake (field guide) — present but not decoded.** Both `verified`-present on the 2019 C-bus, but `0x32F` bytes 0-1 are a fast rolling counter, not a clean ebrake bit. **Confidence: presence `verified`, decode `unresolved`.** The field guide's "0x32F ebrake" is a location hint only, not a working decode.

3. **Brand column: SIMPLE/XP (Thread 3) vs RZC/Raise (our CONTEXT + field guide default).** Our context describes the RZC/Raise start-heartbeat; Thread 3's *real installs* all lit up under **XP/SIMPLE** (a 2014 entry). **These are not contradictory:** XP and RZC share **identical framing** (`0x2E`/`^0xFF`/38400) per `CanBoxProtocol.java` — they differ only in car-model tables. The heartbeat we captured is valid for both. **Confidence: XP-2014-works `reported` (3 installs); Raise-viable `verified` (framing). → Try XP/SIMPLE first, Raise/Hiworld as the A/B fallback the field guide already prescribes.**

4. **`0x41` (Raise VW-PQ) vs `0x28 BASE_INFO` (newer RZC) as the vehicle-info carrier.** Two RZC generations exist. The car-exact `MitsubishiRaise.java` uses the **`0x41`** base layout (`verified`), but a newer OEM box could emit `0x28`. **Confidence: `0x41` for our decoder `verified`; `0x28`-possible `verified` as a generation variant.** → Watch for BOTH `0x41` and `0x28` in the live capture.

5. **`0x24` meaning — reverse/lights vs doors.** `verified` collision: VW-PQ `0x24`=reverse|brake|lights; Toyota-Raise & ASampleRaise `0x24`=doors. **Confidence: `verified` that it's overloaded.** → Disambiguate by payload length + what toggles.

6. **`0x41` meaning — Raise vehicle-info vs Hiworld radar.** `verified` cross-family collision (Raise `0x41`=vehicle-info; NissanHiworld `0x41`=radar). → The brand column picked in the factory menu tells you which family, hence which meaning.

7. **Door bit order — FL=bit0 (smartgauges/our decode note) vs Driver=bit7 (esp32).** Resolved by Thread 4: both are `style` values of one parser (`verified`). Our note L104 is correct that they disagree; the new fact is *why*.

8. **`0x0501` CAN-status.** `verified` FYT-only across Thread 2 + our own note — **not** on the Raise/Topway path. Consistent, no contradiction.

9. **ICE-vs-PHEV C-bus identity.** All raw IDs (`0x154/0x214/0x215/0x185/0x236/0x32F`) are `inferred`-identical on ICE from shared body-bus architecture — **never measured on an ICE car.** EV-CAN IDs (`0x287/0x288/0x289/0x28B`, `0x732/0x734/0x75A`) are `verified` PHEV-ONLY, definitively absent on ICE.

10. **Base non-amp 2019 outputs body CAN to the radio connector.** `inferred` (radio is on CAN-IHS regardless of amp) — **no direct "non-amp trim, doors worked" report located.**

---

## Next in-car actions (prioritized — most likely to unblock first)

1. **Confirm a canbox is physically connected** (field guide Step 1). Everything else is moot until RX ≠ 0. Look behind the radio for the module + tiny white 8-pin plug. If absent, the car was wired analog-only → buy **XIBEIKE 16-pin for Outlander 2014-2020 (B0DXN9WBV5)** or Bestycar 16-pin.
2. **Set the factory profile: `16176699 → CAN TYPE SET → XP/SIMPLE → MITSUBISHI → OUTLANDER → XP(H)2014` (or XP(M)2014).** This is the exact string from three real ICE installs. If nothing, A/B-switch the brand column to **RZC (Raise)** then **HW (Hiworld)** — identical framing, different car tables.
3. **After picking the profile, apply any pending firmware update, then power-cycle `OFF→ACC→ON→start`.** Installs C and D show a firmware update (not just the menu) unlocks doors/climate; install D shows a post-update canbox loss that **factory reset** fixes. Let CAN handshake finish before cranking (baud 250-vs-500 handshake).
4. **Once RX flows, capture the CAN tab and pattern-match against the dialect table.** Expect repeating `0x41` (payload[0] `0x01`↔`0x02`) or `0x28` BASE_INFO, plus `0x24` on state change. Confirm checksum = `^0xFF`.
5. **Actively exercise the unknown signals during capture** (the whole reason public logs failed): open each door in sequence, pop hood + trunk, toggle lights, shift to R, set + release parking brake, turn ignition OFF→ACC→ON. Diff the frames to isolate door bits and the ebrake/reverse bits our data can't currently resolve.
6. **Recover `com.tw.carinfoservice`'s id vocabulary on-device** if TWUtil delivers nothing useful to the app: pull/decompile the APK or watch its broadcasts/logcat. No public source has it, and TWUtil pushes only reverse=`772/40732`, ACC=`514`, keys=`513` to normal apps — doors/speed/fuel may require reading `carinfoservice`'s own channel (`R_CAN=0xFF00`) or its broadcasts, not the radio-tagged handler.
7. **Plan-B only if no canbox path works:** ELM327 passive sniff of the C-bus (expected to broadcast freely on the 2019), decode `0x154` fuel / `0x214-0x215` speed directly; or DIY a Raise gateway (smartgauges/esp32-canbox-nissan/VwRaiseCanbox, all 38400 8N1) mapping Outlander C-bus → Raise `0x41` frames. Door/hood/trunk/ignition IDs still need the in-car capture from action 5 first.
