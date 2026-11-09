package org.openkatmai.flighttrace;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.usb.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.security.SecureRandom;

public final class TelemetryService extends Service {
    private volatile boolean active,recordWanted=true;
    private volatile AdbLink link;
    private volatile Recorder recorder;
    private PowerManager.WakeLock wake;
    private String mode,deviceName;
    private int vendor,product;
    private final LiveState state=LiveState.INSTANCE;
    @Override public void onCreate(){super.onCreate();state.init(this);}
    static String secret(Context c){
        android.content.SharedPreferences prefs=c.getSharedPreferences("pairing",0);String s=prefs.getString("secret",null);
        if(s!=null)return s;byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder text=new StringBuilder();for(byte v:b)text.append(String.format(java.util.Locale.US,"%02x",v&255));s=text.toString();prefs.edit().putString("secret",s).apply();return s;
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        if("STOP".equals(action)){stopSelf();return START_NOT_STICKY;}
        if("RECORD".equals(action)){recordWanted=intent.getBooleanExtra("enabled",true);if(!recordWanted)closeRecorder();return START_NOT_STICKY;}
        if(active)return START_NOT_STICKY;
        mode=intent.getStringExtra("mode");deviceName=intent.getStringExtra("device");vendor=intent.getIntExtra("vendor",-1);product=intent.getIntExtra("product",-1);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("telemetry","FlightTrace recording",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,TelemetryService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"telemetry").setSmallIcon(R.drawable.ic_trace).setContentTitle("FlightTrace · RC 2 telemetry")
            .setContentText("Read-only stream and local flight recording").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(51,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(51,n);
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"flighttrace:recording");wake.acquire(3*60*60*1000L);
        active=true;new Thread(this::receive,"flighttrace-usb-reader").start();return START_NOT_STICKY;
    }
    private AdbLink.Wire wire()throws IOException{
        if("local".equals(mode)){
            if(!"rc331".equals(Build.DEVICE))throw new IOException("The local diagnostic mode is only for the RC 2. Choose USB on an external phone.");
            return new AdbLink.SocketWire("127.0.0.1",5555);
        }
        UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);UsbDevice chosen=null;
        for(UsbDevice device:manager.getDeviceList().values())if(AdbLink.isAdb(device)&&(deviceName!=null&&deviceName.equals(device.getDeviceName())||device.getVendorId()==vendor&&device.getProductId()==product)){chosen=device;break;}
        if(chosen==null)throw new IOException("Connect the phone to RC 2 using a USB data/OTG cable");
        if(!manager.hasPermission(chosen))throw new IOException("Open FlightTrace and allow USB access for the remote");
        return new AdbLink.UsbWire(manager,chosen);
    }
    private void receive(){
        while(active){
            try(AdbLink adb=new AdbLink(wire())){
                link=adb;
                BridgeInstaller.Connection connection=BridgeInstaller.ensure(this,adb,secret(this),text->state.status=text);
                String line=connection.first;
                try(AdbLink.Stream stream=connection.stream){
                    while(active){
                        JSONObject sample=new JSONObject(line);state.accept(sample);
                        if(recordWanted){
                            if(recorder==null){recorder=new Recorder(this);state.recordPath=recorder.file.getAbsolutePath();state.recording=true;}
                            try{Recorder current=recorder;if(current!=null)current.append(sample);}catch(Exception error){state.status="Recording failed: "+error.getMessage();closeRecorder();recordWanted=false;}
                        }
                        line=stream.line(65536);
                    }
                }
            }catch(Exception e){if(active){android.util.Log.w("FlightTrace","Stream interrupted: "+e.getClass().getSimpleName()+": "+e.getMessage());state.lost(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}}
            finally{link=null;}
            if(active)try{Thread.sleep(2000);}catch(InterruptedException ignored){}
        }
    }
    private void closeRecorder(){Recorder r=recorder;recorder=null;state.recording=false;if(r!=null)try{r.close();}catch(IOException ignored){}}
    @Override public void onDestroy(){active=false;AdbLink old=link;if(old!=null)try{old.close();}catch(IOException ignored){}closeRecorder();state.lost("Stopped · last confirmed position retained");if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
