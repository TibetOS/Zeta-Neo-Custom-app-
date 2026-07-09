# Outlander 2019 + Topway TS18 — CANbus field guide

Car: **Mitsubishi Outlander 2019** (3rd gen, ICE — not PHEV). Head unit: **Topway
TS18** (UIS8581A, Android 10). This is the *in-car* checklist to get vehicle
data flowing at the **firmware/canbox level** — separate from our app. If these
steps produce nothing, no app change can help: the data isn't reaching the unit.

## The architecture (why our app sees nothing yet)

```
Outlander CAN (split: front/comfort  ⟷ gateway ⟷  back/powertrain)
        │  raw frames (0x318 gear, 0x32F ebrake, 0x236 steering, doors, fuel)
        ▼
   OEM CANbox behind radio  ── "tiny white 8-pin plug" ──┐   ← must be connected
        │  Raise/RZC serial protocol (0x2e|type|…)        │
        ▼                                                  │
   TS18 head unit  ←── factory CAN menu picks the box brand + car
        │  android.tw.john.TWUtil delivers frames as Messages
        ▼
   OutlanderHub (our app)
```

Our factory-monitor capture showed the unit endlessly sending the Raise "start"
heartbeat (`2e 81 01 01 7c`) with **zero RX** → no canbox is answering. Fixing
that is steps 1–3 below, in the car.

## Step 1 — is a canbox physically connected?

- Look behind the radio for the **canbox module** and its **tiny white 8-pin
  plug** (the CJ Industries Outlander note calls this out explicitly). A 2019
  Outlander aftermarket install normally ships one; it may be unplugged or the
  car may have been wired analog-only (resistive SWC, no CAN box).
- Box brand = its colour/label (needed for Step 3):
  | menu label | brand | looks like |
  |---|---|---|
  | **RZC** | **Raise** | matte black plastic |
  | HW | Hiworld | glossy black, recessed |
  | XP / XinPu | Simple Soft ("SIMPLE") | transparent dark-red |
  | XBS | Xinbas | bright blue |
  | BNR | Binary | burgundy |
  | OD | Oudi | black w/ sticker |
  | LZ | LuzHeng | compact brown |

## Step 2 — enter the factory CAN menu

`Car settings → Factory settings →` enter a code. Codes seen for TS18/Topway
(try in order): **`16176699`** (most common), **`3368`**, **`8888`** (car-model
picker on TS18), **`1234`**. Then open **CAN TYPE SET** / **CanBus Setting**.

## Step 3 — pick box + car (4 columns, fill every one)

1. **Manufacturer** — match the physical box from Step 1. CJ Industries'
   recommended starting point for the Outlander is **SIMPLE** (col 1); if the
   real box is a matte-black Raise, pick **RZC** instead.
2. **Make** → MITSUBISHI
3. **Model** → OUTLANDER
4. **Year/trim** → closest to 2019 (trims marked **L / M / H / AMP** — AMP =
   Rockford Fosgate amplifier cars).

**The box brand is reverse-engineered per vendor**, so if the correct-looking
choice yields nothing, **switch manufacturer (Raise↔Hiworld↔Simple) and retry**
— different vendors use different codes for the same car. This is a legitimate
one-setting A/B test, cheap to run.

## Step 4 — init ritual if SWC works but sensors don't

Partial data (steering-wheel buttons work, doors/reverse don't) usually =
**baud mismatch (250 vs 500 kbps)** or incomplete CAN init. Fix: cycle ignition
**OFF → ACC → ON, then start engine** (let the unit finish CAN handshake before
cranking).

## Model-year facts that bite (2019 specifically)

- **2015+ Outlander adds a LIN-bus layer** alongside CAN for the digital
  instrument cluster. A canbox/profile made for a pre-2015 Outlander may not read
  cluster-side signals (some door chimes / warnings) even when SWC works. Pick a
  **2015-or-later** profile.
- **The CAN is split front/back by a gateway.** The OEM box taps one segment;
  signals on the other segment (some powertrain data) may simply never reach it.
  Comfort signals we care about (doors, reverse, illumination, fuel level, speed)
  are normally on the segment the radio-side box taps.
- Connector is **16-pin**; Outlander-specific harness+canbox adapters exist
  (Bestycar/Fiegromech etc.), AMP variants differ (Rockford Fosgate).

## If Step 1 finds no box at all

Then the car was wired without CAN. Remedies:
- **Buy the exact-fit box.** Concrete part: **XIBEIKE 16-pin Canbus Box for
  Mitsubishi Outlander 2014-2020** (Amazon) — covers our 2019; also Bestycar
  16-pin (Outlander/Pajero, incl. Rockford-amp variant). Wiring: the box's
  **8-pin CAN plug goes into the head unit's 8-pin CAN socket** (this *is* the
  "tiny white 8-pin plug"); the 16-pin side is plug-and-play to the factory
  harness, no cutting.
- **DIY a Raise gateway.** Working open-source implementations of the exact
  `0x2e` Raise protocol the unit expects: **smartgauges/canbox** (STM32),
  **aerodomigue/esp32-canbox-nissan** (ESP32, well-documented frames in
  `docs/technical/RADIO_SEND.md`), **icarome/VwRaiseCanbox**. All emit **38400
  8N1**. You'd map the Outlander's raw CAN → Raise frames yourself.
- **ELM327 OBD-II** plan-B — and only *there* do the raw Outlander CAN IDs
  matter (0x318 gear, 0x236 steering, 0x32F ebrake; door/fuel IDs still need a
  capture on this ICE car — most public reverse-engineering is PHEV).

**Prior-art note:** an all-GitHub sweep found **no existing TWUtil client that
decodes vehicle CAN** — every TWUtil app uses it for radio/BT/aux only, and
`"new TWUtil" door` returns 0 hits. So there's nothing to copy for the decode
side; it must come from our own CAN-tab capture. Our own repo is now the top
hit for "TS18 canbus door reverse" — we're at the public frontier here.

## Sources

XDA TS18/Topway + Outlander threads, DRIVE2/4PDA TS18 logbooks,
[CJ Industries — Outlander 2012-2018 canbus settings](https://www.cjindustriesaustralia.com/blogs/canbus-settings/mitsubishi-outlander-2012-2018-canbus-settings),
[smarty-trend canbus setup + passwords](https://smarty-trend.com/en/setup-canbus-android-car-radio),
Alibaba Outlander canbus practical guide. Raw-CAN architecture from
myoutlanderphev.com canbus-deciphering + OVMS-3 Outlander.
