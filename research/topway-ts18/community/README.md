# Community `android.tw.john.TWUtil` clients

Reference clients that talk to the Topway/MST framework serial-MCU class
`android.tw.john.TWUtil`, collected from GitHub to pin down the real call shape
(our unit ships no vehicle APK to copy from). Files here are third-party sources,
kept verbatim for provenance.

## The call shape (agreed across all clients)

```
construct → open(ids) → start() → addHandler("<tag>", handler) → write(id, arg1[, arg2])
```

- `open()` returns `int`, **`0` == success**.
- Handler receives each MCU message as `android.os.Message`; callback reads
  `what`, `arg1`, `arg2`, `obj` (`obj` is `Object` — String / byte[] / short[] /
  int[] / a `TWUtil.TWObject` pair).
- Subscribe with a **small curated id list** (≤13). A large array SIGABRTs — it
  overflows the native subscription table. (This was our 5120-id crash.)

## The split that matters (constructor + open arity)

Two firmware flavours exist. **Both are handled by our reflective fallback chain
in `TwUtilLink.kt`** (`construct`: no-arg → `(int,0)`; `invokeOpen`:
`open(short[], int)` → `open(short[])`), so no code change is forced — but know
which is which when reading a client:

| Client | Access | Constructor | open() |
|---|---|---|---|
| bphillips09/Orbit (`TopwayTwUtilAux.kt`) | direct | no-arg | `open(ids, 0)` |
| asb72/dvd-bt (`com.tw/bt/twUtil.java`) | direct | no-arg | `open(ids, 115200)` |
| d51x/KaierUtils (`TWUtilEx.java`) | direct | no-arg | `open(ids, flag)` |
| **Planqton/fytFM** (`TWUtilHelper.java`) | **reflection** | **`(int)` → `1`** | **`open(ids)`** |
| **dipcore/Radio-MST768** (`Radio.java`) | direct | **`new TWUtil(1)`** | **`open(ids)`, `==0`** |
| ivvlev/CarRadio (`McuRadioEmulator.java`) | injected | (passed in) | — |

`(int)` ctor arg is a **vendor mode/domain id, not a CAN channel** — Radio uses
`1` = radio. `open(short[])` (no flag) and `open(short[], int)` are both seen;
where a flag is passed it's `0` or a baud (`115200`), and both are accepted, so
the flag looks advisory on these builds.

## Message-id vocabulary (radio domain — from Radio-MST768 + fytFM)

Useful because it's the most complete `write()` map we have, and confirms the
`write(id,arg)` / `write(id,arg1,arg2)` overloads:

| id | write args | meaning |
|---|---|---|
| 265 / 0x101 | 255 | settings / power query |
| 513 | — | key press (subscribe) |
| 769 | 255, `192,(0/1)` | audio focus query / release |
| 1025 | `5,rangeId` `0,0` `4,(0/1)` `1,(0/1)` | freq range / auto-scan / LOC-DX / seek |
| 1026 | `255,freq` | set frequency |
| 1028 | `3,x` `1,x` `0,x` | REG / TA / AF flags |
| 1029 | 255 | RDS text |
| 1030 | 0 / 1 | freq-range params request |
| 40465 | `192,(0/1)` | audio-focus parameter |

fytFM's set uses the `0x1xx` aliases (`0x101`=power, `0x102`=freq, `0x105`=mute,
`0x110`=source, `0x112`=area) — same ids, hex-written.

## Files

- `fytFM-TWUtilHelper.java` — Planqton/fytFM. **Closest to ours: pure reflection**
  (`Class.forName` + `getConstructor(int.class)` + `getMethod("open", short[])`),
  callback `onMcuMessage(int,int,int,Object)`.
- `Radio-MST768.java` — dipcore/Radio-MST768. Direct API, full radio write map.
- `ivvlev-McuRadioEmulator.java` — ivvlev/CarRadio. TWUtil injected; shows the
  receive-side decode (what/arg1/arg2 → radio state).
- `TWUtilEx.java`, `TWUtilDecorator.java`, `TWClient.java`, `TWUtil-real.java`,
  `TWUtil-MainActivity.java`, `McuRadioEmulator.java` — earlier pull (KaierUtils
  + decompiled framework `TWUtil`/`TWObject`).
- `smartgauges-canbox.c` — Raise/RZC MCU serial protocol in C (payload framing).
