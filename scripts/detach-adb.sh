#!/system/bin/sh
# Keep our daemon outside the factory assistant's killProcessGroup target.
# Verified only on the recorded RC 2 build; no boot-time or partition changes.
if [ "$(id -u)" != 1000 ]; then echo DETACH_WRONG_UID; exit 1; fi
PID=$(pidof adbd-r)
case "$PID" in ''|*[!0-9]*) echo DETACH_EXPECTED_ONE_ADBD; exit 1;; esac
if [ "$(cat /proc/$PID/comm)" != adbd-r ]; then echo DETACH_WRONG_PROCESS; exit 1; fi
if ! grep -q '^Uid:[[:space:]]*1000[[:space:]]' /proc/$PID/status; then echo DETACH_WRONG_OWNER; exit 1; fi
GROUP=/dev/memcg/apps/uid_1000/openkatmai-adb
if ! grep -q '^[0-9]*:memory:/apps/uid_1000/' /proc/$PID/cgroup; then echo DETACH_UNEXPECTED_LAYOUT; exit 1; fi
mkdir -p "$GROUP" || exit 1
if [ ! -w "$GROUP/cgroup.procs" ]; then echo DETACH_GROUP_NOT_WRITABLE; exit 1; fi
echo "$PID" > "$GROUP/cgroup.procs" || exit 1
if ! grep -q '^[0-9]*:memory:/apps/uid_1000/openkatmai-adb$' /proc/$PID/cgroup; then echo DETACH_NOT_CONFIRMED; exit 1; fi
echo "ADBD_DETACHED pid=$PID"
