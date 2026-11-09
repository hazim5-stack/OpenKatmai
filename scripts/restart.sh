#!/system/bin/sh
OUT=/sdcard/Download/restart-out.txt
{
echo RESTART_BEGIN
id
setprop ctl.stop adbd
sleep 2
echo "init_adbd=$(getprop init.svc.adbd)"
setprop service.adb.tcp.port 5555
( LD_LIBRARY_PATH=/apex/com.android.adbd/lib64 /data/data/com.dpad.fuli/adbd-r ) </dev/null >>/sdcard/Download/adbd-r.log 2>&1 &
sleep 3
echo "pid=$(pidof adbd-r)"
if [ -f /data/data/com.dpad.fuli/detach-adb.sh ]; then
  sh /data/data/com.dpad.fuli/detach-adb.sh || echo DETACH_FAILED_CAMERA_VIEW_MAY_STOP_ADB
fi
ss -ltn | grep :5555
echo RESTART_END
} > "$OUT" 2>&1
