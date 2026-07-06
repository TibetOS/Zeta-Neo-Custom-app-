---
name: deploy-to-unit
description: Get an OutlanderHub APK onto the real ZETA NEO 14 head unit — build or fetch the APK, serve it over the shared iPhone hotspot, walk the on-unit browser install. Use whenever the user wants to install/update/sideload the app on the unit, the car, or the head unit, or says "put this build on the device". Not for emulator runs (that's run-outlanderhub).
---

# Deploy to the ZETA NEO 14

The unit has no usable adb (the USB-debugging consent dialog is broken on its
firmware), so installs go through its **browser** fetching the APK from this
Mac over a **shared iPhone hotspot**. Everything is wrapped in
`.claude/skills/deploy-to-unit/deploy.sh`; paths are relative to the repo root.

## Consent rule (hard)

`deploy.sh serve` binds `0.0.0.0` — every device on the hotspot can read the
served folder, and the permission classifier blocks it in auto mode. **Ask the
user before serving**, then run with `DEPLOY_CONSENT=yes`. The script refuses
without it.

## The flow (each step verified)

```bash
d=.claude/skills/deploy-to-unit/deploy.sh
$d apk                      # local build via the run-outlanderhub driver, OR:
$d fetch                    # latest successful "Build APK" CI artifact → build/deploy/app-debug.apk
$d status                   # who owns port 8000 right now (do this BEFORE serve)
DEPLOY_CONSENT=yes $d serve build/deploy/app-debug.apk   # after user consent
# → prints: On the head unit's browser, open http://<mac-ip>:8000/app-debug.apk
$d stop                     # when the user confirms the install finished
```

On-unit steps (human, from the working session recorded in project memory):
browser → the printed URL → download → open from the notification → first time
allow "install unknown apps" → Install. Local and CI builds share the
checked-in keystore signature (since 396f1a0), so updates install in place —
no uninstall.

Both Mac and unit must be on the same hotspot; the Mac's address comes from
`ipconfig getifaddr en0` (172.20.10.x on the iPhone hotspot).

## Gotchas (all real)

- **Stale servers linger.** A previous session's `http.server` was still
  serving its scratchpad an hour later. `serve` refuses a busy port; `status`
  shows the owner.
- **`stop` only kills the server this script started** (pidfile). A foreign
  listener on 8000 may be the user's own transfer — one got killed
  mid-download once (lsof showed ESTABLISHED connections from the unit;
  the chained `stop` pattern-killed it anyway). The script now prints the
  owner and stands down; a human decides.
- **`gh run download` must run from the repo root** — it resolves the repo
  from git context and dies with `not a git repository` elsewhere
  (`deploy.sh fetch` cd's for you).
- An ESTABLISHED connection from `172.20.10.2` in `status` output = the unit
  is downloading right now. Don't restart or stop anything.

## Troubleshooting

- `no IP on en0` → the Mac isn't on the hotspot Wi-Fi; join the same iPhone
  hotspot as the unit.
- Serve blocked by the permission classifier even with user consent in chat →
  the user can run the printed `DEPLOY_CONSENT=yes … serve` line themselves
  with the `!` prefix.
- Old install predating 396f1a0 (random CI debug key) → one-time uninstall on
  the unit, then this flow.
