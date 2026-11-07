#!/system/bin/sh
OUT=/sdcard/Download/runroot-out.txt
SRC=/sdcard/Download/adbd-root
DST=/data/data/com.dpad.fuli/adbd-r
WANT=f45e7917c094e6905557057b8f2af516c658458e675c23eb00de7fe34cb6b3ab
{
echo RUNROOT_BEGIN
id
echo "== userlock state =="
getprop rc.userlock.state
getprop persist.rc.lockscreen.state
echo "== source hash =="
sha256sum "$SRC"
cp "$SRC" "$DST"
echo "cp_exit=$?"
chmod 0755 "$DST"
H=$(sha256sum "$DST" | cut -d' ' -f1)
echo "dst_hash=$H"
if [ "$H" != "$WANT" ]; then
  echo "HASH_MISMATCH_ABORT"
  rm -f "$DST"
  echo RUNROOT_END
  exit 1
fi
echo "HASH_OK"
echo "== stop stock adbd =="
setprop ctl.stop adbd
sleep 2
echo "init_adbd=$(getprop init.svc.adbd)"
setprop service.adb.tcp.port 5555
echo "== start adbd-root =="
( LD_LIBRARY_PATH=/apex/com.android.adbd/lib64 "$DST" ) </dev/null >>/sdcard/Download/adbd-r.log 2>&1 &
sleep 3
echo "root_pid=$(pidof adbd-r)"
echo "== listeners =="
ss -ltn | grep -e :5555 -e :5037
echo RUNROOT_END
} > "$OUT" 2>&1
(
  sleep 1800
  kill $(pidof adbd-r) 2>/dev/null
  setprop service.adb.tcp.port ""
  setprop ctl.start adbd
  echo AUTO_RESTORE_FIRED >> "$OUT"
) </dev/null >/dev/null 2>&1 &
