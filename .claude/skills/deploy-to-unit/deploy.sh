#!/bin/bash
# Sideload helper: get an OutlanderHub APK onto the ZETA NEO 14 over a shared
# hotspot. The unit has no working adb — installs go through its browser.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUT="$REPO_ROOT/build/deploy"
PORT="${PORT:-8000}"
PIDFILE="$OUT/http-server.pid"
LOCAL_APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"

die() { echo "deploy: $*" >&2; exit 1; }

mac_ip() { ipconfig getifaddr en0 2>/dev/null || die "no IP on en0 — is the Mac on the hotspot Wi-Fi?"; }

apk() { "$REPO_ROOT/.claude/skills/run-outlanderhub/driver.sh" build; }

fetch() {
  mkdir -p "$OUT"
  cd "$REPO_ROOT"   # gh resolves the repo from git context; fails elsewhere
  local run
  run=$(gh run list --workflow "Build APK" --branch main --status success --limit 1 --json databaseId -q '.[0].databaseId')
  [ -n "$run" ] || die "no successful 'Build APK' run on main"
  rm -f "$OUT/app-debug.apk"
  gh run download "$run" -n outlander-hub-debug --dir "$OUT"
  ls -lh "$OUT/app-debug.apk"
}

status() {
  if lsof -i ":$PORT" >/dev/null 2>&1; then
    echo "port $PORT IN USE:"; lsof -i ":$PORT" | tail -n +2
  else
    echo "port $PORT free"
  fi
}

serve() {
  local apk_path="${1:-$LOCAL_APK}"
  [ -f "$apk_path" ] || die "no APK at $apk_path — run: deploy.sh apk (local build) or deploy.sh fetch (CI)"
  # Serving binds 0.0.0.0: every device on the hotspot can fetch this folder.
  # An agent must get explicit user consent, then re-run with DEPLOY_CONSENT=yes.
  if [ "${DEPLOY_CONSENT:-}" != "yes" ]; then
    die "refusing to expose a network file server without consent.
Ask the user first, then: DEPLOY_CONSENT=yes $0 serve $apk_path"
  fi
  lsof -i ":$PORT" >/dev/null 2>&1 && die "port $PORT busy (a stale server from an old session? — run: deploy.sh stop)"
  local ip dir name
  ip=$(mac_ip); dir=$(dirname "$apk_path"); name=$(basename "$apk_path")
  mkdir -p "$OUT"
  (cd "$dir" && python3 -m http.server "$PORT" --bind 0.0.0.0 >"$OUT/http-server.log" 2>&1 & echo $! >"$PIDFILE")
  sleep 1
  curl -sf -o /dev/null "http://127.0.0.1:$PORT/$name" || { stop; die "server did not come up (see $OUT/http-server.log)"; }
  cat <<EOF
Serving $name

  On the head unit's browser, open:  http://$ip:$PORT/$name

  1. Tap the file to download, then open it from the download notification
  2. First time: allow "install unknown apps" for the browser when prompted
  3. Install (it updates in place — same signature since 396f1a0)

When done: $0 stop
EOF
}

# Only kills the server THIS script started (pidfile). A foreign listener on
# the port may be the user's own transfer — killing one mid-download happened
# once; never again. Point at it and let a human decide.
stop() {
  if [ -f "$PIDFILE" ]; then
    kill "$(cat "$PIDFILE")" 2>/dev/null && echo "server stopped" || echo "server was not running"
    rm -f "$PIDFILE"
  elif lsof -i ":$PORT" >/dev/null 2>&1; then
    echo "port $PORT is in use, but not by this script — NOT killing it:"
    lsof -i ":$PORT" | tail -n +2
    echo "if it is yours and stale: kill <PID>"
  else
    echo "nothing to stop"
  fi
}

cmd="${1:-help}"; shift || true
case "$cmd" in
  apk|fetch|serve|status|stop) "$cmd" "$@" ;;
  *) echo "usage: deploy.sh apk|fetch|serve [apk-path]|status|stop   (env: PORT=$PORT, DEPLOY_CONSENT)";;
esac
