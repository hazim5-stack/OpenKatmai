#!/system/bin/sh
# Generic, bounded ARM64 launch. No persistent properties or firmware changes.
umask 077
COMPONENT=${1-}
if [ "$#" != 1 ]; then echo 'USAGE: sh launch64.sh package/activity'; exit 2; fi
case "$COMPONENT" in *[!A-Za-z0-9_./$]*|/*|*/|*/*/*|'') echo INVALID_COMPONENT; exit 2;; esac
case "$COMPONENT" in */*) ;; *) echo INVALID_COMPONENT; exit 2;; esac
PACKAGE=${COMPONENT%%/*}
case "$PACKAGE" in *.*) ;; *) echo INVALID_PACKAGE; exit 2;; esac
if [ "$(id -u)" != 1000 ]; then echo WRONG_UID; exit 2; fi
if ! timeout -k 1 5 pm path "$PACKAGE" | grep -q '^package:'; then echo APP_NOT_INSTALLED; exit 2; fi
if ! timeout -k 1 5 dumpsys package "$PACKAGE" | grep -q 'primaryCpuAbi=arm64-v8a'; then echo WRONG_APP_ABI; exit 2; fi
PRIMARY=$(getprop init.svc.zygote)
SECONDARY=$(getprop init.svc.zygote_secondary)
if [ "$SECONDARY" != running ]; then echo SECONDARY_NOT_RUNNING; exit 2; fi
case "$PRIMARY" in running|stopped) ;; *) echo UNEXPECTED_PRIMARY_STATE; exit 2;; esac
LOCK=/data/data/com.dpad.fuli/.openkatmai-launch64-lock
if ! mkdir "$LOCK" 2>/dev/null; then echo PREPARATION_BUSY; exit 2; fi
OWNER=$$
printf '%s' "$OWNER" > "$LOCK/owner"
CHANGED=0
GUARD=
cleanup() {
  if [ "$CHANGED" = 1 ]; then
    setprop ctl.stop zygote
    sleep 1
    if [ "$(getprop init.svc.zygote)" != stopped ]; then
      echo RESTORE_FAILED
      return 1
    fi
  fi
  [ -z "$GUARD" ] || kill "$GUARD" 2>/dev/null
  if [ "$(cat "$LOCK/owner" 2>/dev/null)" = "$OWNER" ]; then
    rm -f "$LOCK/owner"
    rmdir "$LOCK" 2>/dev/null
  fi
  echo RESTORE_DONE
}
trap 'RESULT=$?; trap - EXIT; cleanup || RESULT=5; exit "$RESULT"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
if [ "$PRIMARY" = stopped ]; then
  CHANGED=1
  (
    sleep 45
    if [ "$(cat "$LOCK/owner" 2>/dev/null)" = "$OWNER" ]; then
      setprop ctl.stop zygote
      rm -f "$LOCK/owner"
      rmdir "$LOCK" 2>/dev/null
    fi
  ) </dev/null >/dev/null 2>&1 &
  GUARD=$!
  setprop ctl.start zygote
  sleep 2
fi
WAIT=0
while [ "$WAIT" -lt 5 ]; do
  if [ "$(getprop init.svc.zygote)" = running ] && [ -S /dev/socket/zygote ]; then break; fi
  sleep 1
  WAIT=$((WAIT + 1))
done
if [ "$(getprop init.svc.zygote)" != running ] || [ ! -S /dev/socket/zygote ]; then
  echo ZYGOTE_NOT_READY
  exit 3
fi
for ATTEMPT in 1 2; do
  echo "ATTEMPT=$ATTEMPT"
  # Keep Binder shell-command descriptors on /dev/null + the caller's output file.
  # A nested command-substitution pipe can stall local system-ADB starts on DJI.
  timeout -k 1 7 cmd activity start-activity -W -n "$COMPONENT" </dev/null
  START_RESULT=$?
  sleep 1
  FOREGROUND=$(timeout -k 1 5 dumpsys activity activities | grep mResumedActivity)
  if [ "$START_RESULT" = 0 ]; then
    case "$FOREGROUND" in *" $PACKAGE/"*) echo APP_READY; exit 0;; esac
  fi
done
echo APP_START_FAILED
exit 4
