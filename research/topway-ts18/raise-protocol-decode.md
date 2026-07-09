# Raise canbox → head-unit serial protocol (decode reference)

Source: [smartgauges/canbox](https://github.com/smartgauges/canbox) `canbox.c`
(full copy: `community/smartgauges-canbox-full.c`). This is an **open-source
reimplementation** of the Raise/HiWorld MCU protocols that VW-platform canboxes
speak to Android head units — the same protocol family a TS18/Topway unit
consumes via `android.tw.john.TWUtil`.

## Why this matters for us

Our ZETA NEO 14 does **not** read raw vehicle CAN. A Raise (RZC) canbox reads
the Outlander bus and forwards a **pre-digested serial protocol** to the head
unit; TWUtil is what carries those frames into our app (as `Message` what/obj).
So the ids to map in the CAN tab are **these `type` bytes**, not raw Outlander
CAN ids (0x318 gear, 0x236 steering — those live upstream of the canbox).

## Wire framing (`snd_canbox_msg`)

```
0x2e  <type>  <size>  <payload[size]>  <checksum>
```
- `0x2e` — start byte
- `type` — message class (0x41 vehicle, 0x24 reverse/brake/lights, 0x21 AC, 0x20 SWC, 0x22/0x23/0x25/0x26/0x29 radar/wheel)
- `checksum` — over `type..last payload byte`

How this surfaces through TWUtil: the framework strips the wire framing and
delivers `type` as the message **`what`**, with `payload` as `msg.obj`
(byte[]). Our `TwUtilLink.decodePayload` already turns byte[] → `ints`, so the
decoder switches on `what` then `payload[0]` (the subtype).

## Vehicle frames — `type = 0x41` (Raise VW-PQ)

Sub-dispatched by **payload byte 0** (subtype):

### Doors — subtype `0x01` → `{ 0x01, state }`
`state` bitmask (byte 1):
| bit | mask | signal |
|---|---|---|
| 0 | 0x01 | front-left door |
| 1 | 0x02 | front-right door |
| 2 | 0x04 | rear-left door |
| 3 | 0x08 | rear-right door |
| 4 | 0x10 | tailgate / trunk |
| 5 | 0x20 | bonnet/hood (or park-brake on Skoda/Q3/Toyota) |
| 6 | 0x40 | low washer (Skoda/Q3/Toyota only) |
| 7 | 0x80 | driver seatbelt (Skoda/Q3/Toyota only) |

### Vehicle info — subtype `0x02` → 13 bytes `{ 0x02, t1..t12 }`
| bytes | field | encoding |
|---|---|---|
| 1-2 | tacho / RPM | uint16 big-endian |
| 3-4 | **speed** | uint16 BE, **value = speed×100** (so ÷100 for km/h) |
| 5-6 | voltage | uint16 BE, ×100 |
| 7-8 | temperature | uint16 BE, ×10 |
| 9-11 | odometer | uint24 BE |
| 12 | **fuel** | `car_get_low_fuel_level()` (byte) |

### Warnings — subtype `0x03` → `{ 0x03, state }` (only sent on change)
| bit | mask | signal |
|---|---|---|
| 6 | 0x40 | low voltage |
| 7 | 0x80 | low fuel |

## Reverse / park-brake / lights — `type = 0x24` (Raise VW-PQ), 1 byte

```
state = reverse | (park_break << 1) | (near_lights << 2)
```
| bit | mask | signal |
|---|---|---|
| 0 | 0x01 | **reverse gear** (selector == R) |
| 1 | 0x02 | parking brake |
| 2 | 0x04 | near/low-beam lights |

(MQB variant reuses `0x24` as a **door** bitmask instead — bonnet 0x04,
tailgate 0x08, RL 0x10, RR 0x20, FL 0x40, FR 0x80 — so which meaning `0x24`
carries depends on the canbox mode. Disambiguate by payload length + context.)

## Steering-wheel buttons — `type = 0x20` → `{ key, pressed }`
`key`: 0x01 vol+, 0x02 vol-, 0x03 prev, 0x04 next, 0x09 cont, 0x0a mode,
0x0c mic … ; `pressed`: 0x01 down / 0x00 up. (Input to the unit, not a sensor.)

## AC/climate — `type = 0x21`, 5 bytes (temp, fan, seat heat). Radar:
`0x22` rear, `0x23` front, `0x25`/`0x26`/`0x29` wheel-angle.

## What streams every tick (`canbox_process`, Raise VW-PQ)

`wheel(0x26)` → `door(0x41/0x01)` → `ac(0x21)` → `vehicle_info(0x41/0x02)`.
Radar (`canbox_park_process`) only while parking. So on a live Raise unit the
CAN tab should show **repeating `what=0x41`** frames whose first payload byte
alternates 0x01/0x02, plus `0x24` on gear/brake/light changes.

## A SECOND Raise dialect — the type bytes are NOT universal

`aerodomigue/esp32-canbox-nissan` (`docs/technical/RADIO_SEND.md`, a
Toyota/Nissan-style Raise box) speaks the **same framing** (`0x2e|cmd|len|payload|
checksum`, checksum = `(cmd+len+Σpayload) XOR 0xFF` = the one's-complement /
Topway variant, matching our factory monitor's `2e 81 01 01 7c`) but a
**different command map** from smartgauges. This is the whole point: **you must
map from the live capture, not hardcode** — our box could be either dialect.

| signal | smartgauges (VW) | esp32-nissan (Toyota/Raise) |
|---|---|---|
| doors | `0x41`/sub `0x01`, byte1 FL=**bit0**…trunk bit4 | `0x24` 1B, Driver=**bit7**, Pass bit6, RR bit5, RL bit4, trunk bit3 |
| speed | `0x41`/sub `0x02` bytes 3-4 (×100 BE) | `0x7D`/sub `0x03` bytes 1-2 (×100 LE) |
| fuel | `0x41`/sub `0x02` byte 12 | `0x22` instant / `0x23` avg (separate frames) |
| RPM | `0x41`/sub `0x02` bytes 1-2 | `0x7D`/sub `0x0A` (RPM×4) |
| lights | `0x24` bit2 | `0x7D`/sub `0x01` (parking 0x80, low 0x40, high 0x20, turns) |
| odo | `0x41`/sub `0x02` bytes 9-11 | `0x7D`/sub `0x04` |
| steering | `0x26`/`0x29` | `0x29` signed16 LE |
| trip/range | — | `0x21` 7B |

Note the collisions: `0x24` = *lights+reverse* in VW-PQ but *doors* in
Toyota-Raise; `0x21` = *AC* in VW but *trip info* here. So **disambiguate by the
`what` value AND payload length AND which signals actually toggle** when you
open a door / shift to R — never assume a fixed table.

Baud on the canbox↔MCU serial link is **38400 8N1** in both esp32 clients (and
VwRaiseCanbox) — this is upstream of TWUtil (doesn't change our `open()` flag),
but it's the number the factory unit expects from a Raise box, and a mismatch
here is the classic "SWC works, sensors don't" symptom.

## Caveats (unverified against OUR unit)

- This is the **VW**-targeted smartgauges build. The **frame layout is the
  Raise wire protocol** and should generalise, but the OEM Raise box in the
  Outlander may use **different `type` numbers or subtypes** (see the second
  dialect above), and fuel/speed scaling can differ per firmware. Treat as
  "what to look for," confirm against the real CAN-tab dump.
- No Mitsubishi decoder exists in smartgauges `cars/` (LR2, Q3, Skoda Fabia,
  Toyota Premio, XC90 only) — the Outlander mapping is done by the OEM canbox,
  which we can't read; we only see its serial output.
- Our earlier `0x0501` CAN-status guess is **not** in this protocol — it came
  from FYT, a different platform. On Raise, poke nothing; just subscribe and
  watch `0x41`/`0x24`.
