package org.openkatmai.flighttrace;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler();
    private final LiveState state=LiveState.INSTANCE;
    private TextView details,gpsStatus,cautionStatus;
    private android.content.SharedPreferences displayPrefs;
    private TraceView trace;
    private String format="gpx";
    private static final String USB="org.openkatmai.flighttrace.USB_PERMISSION";
    private final BroadcastReceiver permission=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        UsbDevice d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if(USB.equals(i.getAction())&&d!=null&&i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))start(d);
    }};
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);state.init(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,16,20,16);root.setBackgroundColor(Color.rgb(12,20,30));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        displayPrefs=getSharedPreferences("display",0);
        TextView title=new TextView(this);title.setText("FlightTrace · experimental\nRC 2 telemetry over USB");title.setTextSize(24);title.setTextColor(Color.WHITE);root.addView(title);
        button(root,"Connect USB",v->connect());
        button(root,"Live map",v->startActivity(new Intent(this,MapActivity.class)));
        if("rc331".equals(Build.DEVICE))button(root,"Local RC diagnostic",v->startServiceMode(new Intent(this,TelemetryService.class).putExtra("mode","local")));
        LinearLayout controls=new LinearLayout(this);root.addView(controls);
        button(controls,"Stop",v->startService(new Intent(this,TelemetryService.class).setAction("STOP")));
        button(controls,"Recording on/off",v->startService(new Intent(this,TelemetryService.class).setAction("RECORD").putExtra("enabled",!state.recording)));
        Switch awake=new Switch(this);awake.setText("Keep screen on");awake.setChecked(displayPrefs.getBoolean("awake",true));root.addView(awake);
        awake.setOnCheckedChangeListener((v,on)->{displayPrefs.edit().putBoolean("awake",on).apply();applyDisplay();});
        Switch sun=new Switch(this);sun.setText("Sunlight brightness");sun.setChecked(displayPrefs.getBoolean("sun",false));root.addView(sun);
        sun.setOnCheckedChangeListener((v,on)->{displayPrefs.edit().putBoolean("sun",on).apply();applyDisplay();});
        LinearLayout pin=new LinearLayout(this);root.addView(pin);
        button(pin,"Pin app / prevent Home",v->{try{startLockTask();}catch(Exception e){toast("Screen pinning unavailable: "+e.getMessage());}});
        button(pin,"Unpin app",v->{try{stopLockTask();}catch(Exception e){toast("App is not pinned");}});
        cautionStatus=new TextView(this);cautionStatus.setTextSize(20);cautionStatus.setPadding(12,12,12,12);cautionStatus.setTextColor(Color.rgb(255,210,130));cautionStatus.setBackgroundColor(Color.rgb(56,34,20));root.addView(cautionStatus);
        applyDisplay();
        gpsStatus=new TextView(this);gpsStatus.setTextSize(21);gpsStatus.setPadding(8,12,8,16);root.addView(gpsStatus);
        trace=new TraceView(this);root.addView(trace,new LinearLayout.LayoutParams(-1,480));
        details=new TextView(this);details.setTextColor(Color.rgb(211,225,240));details.setTextSize(15);details.setTextIsSelectable(true);root.addView(details);
        button(root,"Aircraft / last seen · Google Maps",v->maps(state.lastAircraft()));
        button(root,"Home point · Google Maps",v->maps(state.lastHome()));
        button(root,"Remote · Google Maps",v->maps(state.lastRemote()));
        button(root,"Export latest recording",v->new AlertDialog.Builder(this).setTitle("Export latest session").setItems(new String[]{"gpx","geojson","csv","ndjson"},(d,n)->{
            format=new String[]{"gpx","geojson","csv","ndjson"}[n];startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"flighttrace."+format),12);
        }).show());
        button(root,"Raw telemetry / read errors",v->new AlertDialog.Builder(this).setMessage(state.latest==null?"No sample received":state.latest.toString()).setPositiveButton("Close",null).show());
        IntentFilter filter=new IntentFilter(USB);if(Build.VERSION.SDK_INT>=33)registerReceiver(permission,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(permission,filter);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
    }
    private void button(LinearLayout root,String label,View.OnClickListener click){Button b=new Button(this);b.setText(label);b.setOnClickListener(click);root.addView(b);}
    private void applyDisplay(){if(displayPrefs.getBoolean("awake",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=displayPrefs.getBoolean("sun",false)?1f:-1f;getWindow().setAttributes(p);}
    private void connect(){UsbManager m=(UsbManager)getSystemService(USB_SERVICE);for(UsbDevice d:m.getDeviceList().values())if(AdbLink.isAdb(d)){
        if(m.hasPermission(d))start(d);else m.requestPermission(d,PendingIntent.getBroadcast(this,2,new Intent(USB).setPackage(getPackageName()),Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));return;
    }toast("No ADB USB interface. Connect the phone as USB host with a data/OTG cable and enable the RC debugging daemon.");}
    private void start(UsbDevice d){startServiceMode(new Intent(this,TelemetryService.class).putExtra("mode","usb").putExtra("device",d.getDeviceName()).putExtra("vendor",d.getVendorId()).putExtra("product",d.getProductId()));}
    private void startServiceMode(Intent i){startForegroundService(i);}
    private static JSONObject child(JSONObject j,String k){JSONObject p=j==null?null:j.optJSONObject(k);return p==null?new JSONObject():p;}
    private static String value(JSONObject j,String k){Object v=j.opt(k);return v==null||v==JSONObject.NULL?"unavailable":String.valueOf(v);}
    private static String position(JSONObject p){if(p==null)return "unavailable";return String.format(Locale.US,"%.7f, %.7f",p.optDouble("latitude"),p.optDouble("longitude"))+"\nObserved: "+new java.util.Date(p.optLong("observed_at_ms"));}
    private void render(){JSONObject packet=state.latest,a=child(packet,"aircraft"),b=child(a,"battery"),g=child(a,"gps"),f=child(a,"flight");
        boolean live=state.fresh()&&a.optBoolean("connected")&&a.optBoolean("position_usable");
        Object satellites=g.opt("satellites");Integer count=satellites instanceof Number?((Number)satellites).intValue():null;
        gpsStatus.setText(GpsStatus.describe(count,state.fresh(),live)+"\nGPS signal level: "+(state.fresh()?value(g,"signal_level"):"unavailable"));
        gpsStatus.setTextColor(live?Color.rgb(74,220,194):Color.rgb(255,198,97));
        cautionStatus.setText(CautionText.describe(child(packet,"cautions"),state.fresh()));
        details.setText(state.status+"\nStream: "+(state.fresh()?"receiving":"disconnected / stale")+" · samples "+state.frames+"\nRecording: "+state.recording+
            "\n\nAIRCRAFT · "+(live?"received cache":"LAST SEEN / no current fix")+"\n"+position(state.lastAircraft())+
            "\n\nHOME POINT · cached\n"+position(state.lastHome())+"\n\nREMOTE GPS · "+value(child(packet,"remote"),"status")+"\n"+position(state.lastRemote())+
            "\n\nBattery: "+value(b,"percent")+"% · "+value(b,"temperature_c")+" °C\nVoltage: "+value(b,"voltage_mv")+" mV\nGPS satellites: "+value(g,"satellites")+
            "\nMode: "+value(f,"mode")+"\nMotors: "+value(f,"motors_on")+" · duration: "+value(f,"duration_s")+" s\n\nCAUTIONS (partial): "+child(packet,"cautions")+
            "\nAircraft cache measurement age is unknown. Stream freshness does not establish GPS measurement freshness. Full DJI warnings are unavailable; use DJI Fly for flight decisions. Phone GPS is never used as the remote location.");trace.invalidate();}
    private void maps(JSONObject p){if(p==null){toast("No confirmed coordinate available");return;}String q=p.optDouble("latitude")+","+p.optDouble("longitude");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/search/?api=1&query="+q)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();if(displayPrefs!=null)applyDisplay();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(permission);super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==12&&result==RESULT_OK&&data!=null){final Uri uri=data.getData();final String selected=format;new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){Recorder.export(this,selected,out);runOnUiThread(()->toast("Recording exported"));}catch(Exception e){runOnUiThread(()->toast("Export failed: "+e.getMessage()));}}).start();}}
}
