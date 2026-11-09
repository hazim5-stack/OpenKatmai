package org.openkatmai.flighttrace.bridge;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;

/** Version-specific cache adapter. No action keys, setters, subscriptions or flight commands. */
final class Reader {
    private final Class<?> root, observable;
    private final Context context;
    private final Map<String,Method> methods=new HashMap<>();
    private JSONObject errors;
    Reader(ClassLoader loader,Context context) throws Exception {
        this.context=context;
        root=Class.forName("com.uav.flymodel.api.FlyModel",false,loader);
        observable=Class.forName("com.uav.flymodel.api.observable.FlyObservable",false,loader);
    }
    private Method method(Class<?> c,String name) throws Exception {
        String k=c.getName()+"#"+name;Method m=methods.get(k);
        if(m==null){m=c.getMethod(name);methods.put(k,m);}return m;
    }
    private Object cache(Object source) throws Exception {
        if(source==null || !observable.isInstance(source))return source;
        Object selected=source;
        if(source.getClass().getName().equals("com.uav.flymodel.internal.observable.ToggleFlyObservable")) {
            Method choose=source.getClass().getDeclaredMethod("O");choose.setAccessible(true);
            Object toggle=choose.invoke(source);selected=method(toggle.getClass(),"a").invoke(toggle);
        }
        // In Fly 1.21.8 c() is the DEFAULT value, not the received cache.
        Method read=method(selected.getClass(),"getOrNull");
        if(read.getParameterTypes().length!=0 || read.getReturnType()!=Object.class)
            throw new IllegalStateException("Cache signature changed");
        return read.invoke(selected);
    }
    private Object read(String path) {
        try {
            Object value=null;
            for(String part:path.split("/")) {
                if(value==null && (part.equals("getFlight")||part.equals("getAircraft")||part.equals("getData")))value=root.getMethod(part).invoke(null);
                else if(value==null)return null;
                else value=method(value.getClass(),part).invoke(value);
            }
            return cache(value);
        } catch(Throwable failure) {
            Throwable t=failure;while(t.getCause()!=null&&t.getCause()!=t)t=t.getCause();
            try {errors.put(path,t.getClass().getSimpleName());}catch(JSONException ignored){}
            return null;
        }
    }
    private Object json(Object value,int depth) {
        if(value==null)return JSONObject.NULL;
        if(value instanceof Number){double v=((Number)value).doubleValue();return Double.isFinite(v)?value:JSONObject.NULL;}
        if(value instanceof Boolean||value instanceof String)return value;
        if(value instanceof Enum)return ((Enum<?>)value).name();
        if(depth>3)return String.valueOf(value);
        if(value instanceof Iterable) {
            JSONArray a=new JSONArray();for(Object item:(Iterable<?>)value){if(a.length()>=64)break;a.put(json(item,depth+1));}return a;
        }
        JSONObject out=new JSONObject();
        for(String field:new String[]{"latitude","longitude","altitude","altitudeType","x","y","z","pitch","roll","yaw","code","message","description","level","status","name","currentState","changedReason","alarmId","componentType","sensorIndex","displayScene","debugInfo","detailAlarmList"}) {
            try {
                String getter="get"+Character.toUpperCase(field.charAt(0))+field.substring(1);
                Object v=method(value.getClass(),getter).invoke(value);out.put(field,json(v,depth+1));
            } catch(Exception ignored) {}
        }
        return out.length()>0?out:String.valueOf(value);
    }
    private void metric(JSONObject into,String key,String path) throws JSONException {into.put(key,json(read(path),0));}
    private static double num(Object value,String getter) {
        try{return ((Number)value.getClass().getMethod(getter).invoke(value)).doubleValue();}catch(Exception e){return Double.NaN;}
    }
    private JSONObject position(Object value) throws JSONException {
        if(value==null)return null;
        double lat=num(value,"getLatitude"),lon=num(value,"getLongitude");
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<-90||lat>90||lon<-180||lon>180)return null;
        return new JSONObject().put("latitude",lat).put("longitude",lon);
    }
    private JSONObject remote() throws JSONException {
        JSONObject r=new JSONObject().put("source","rc_android_gps").put("position",JSONObject.NULL);
        try {
            LocationManager lm=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            Location l=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(l==null)return r.put("status","no_fix");
            long age=Math.max(0,(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000L);
            r.put("position",new JSONObject().put("latitude",l.getLatitude()).put("longitude",l.getLongitude()));
            r.put("age_ms",age).put("accuracy_m",l.hasAccuracy()?l.getAccuracy():JSONObject.NULL);
            r.put("status",age<=10000?"available":"stale");
        }catch(SecurityException e){r.put("status","permission_denied");}
        catch(Exception e){r.put("status","unavailable");}
        return r;
    }
    JSONObject sample(long sequence) throws Exception {
        errors=new JSONObject();
        JSONObject out=new JSONObject().put("schema",1).put("sequence",sequence)
            .put("sampled_at_ms",System.currentTimeMillis()).put("sample_elapsed_ms",SystemClock.elapsedRealtime())
            .put("source","DJI Fly 1.21.8 received cache").put("cache_source_timestamp_available",false);
        JSONObject aircraft=new JSONObject(),battery=new JSONObject(),gps=new JSONObject(),flight=new JSONObject();
        Object connection=read("getFlight/getConnectState");
        aircraft.put("connected",json(connection,0));
        Object location=read("getFlight/getFlightOSD/getAircraftLocation2D");
        JSONObject p=position(location);
        Object satellites=read("getFlight/getPositioning/getGps/getSatelliteCount");
        gps.put("satellites",json(satellites,0));
        metric(gps,"signal_level","getFlight/getPositioning/getGps/getSignalLevel");
        boolean usable=Boolean.TRUE.equals(connection)&&p!=null&&satellites instanceof Number&&((Number)satellites).intValue()>=4;
        aircraft.put("position",p==null?JSONObject.NULL:p).put("position_usable",usable);
        aircraft.put("position_status",usable?"available":p!=null?"unconfirmed":"unavailable");
        String b="getFlight/getBattery/getBatteryStatus/";
        metric(battery,"percent",b+"getRemainingPowerInPercent");metric(battery,"temperature_c",b+"getBatteryTemperature");
        metric(battery,"voltage_mv",b+"getBatteryVoltage");metric(battery,"current_raw",b+"getBatteryCurrent");
        metric(battery,"discharge_count",b+"getNumberOfDischarges");
        String f="getFlight/getFlightOSD/";
        metric(flight,"mode",f+"getFlightMode/getValue");
        metric(flight,"motors_on",f+"getFlightStatus/getAreMotorsOn");
        metric(flight,"duration_s",f+"getFlightStatus/getCurrentFlightDurationInSeconds");
        metric(flight,"attitude",f+"getFlightStatus/getAttitude");
        metric(flight,"velocity_raw",f+"getFlightStatus/getVelocity");
        metric(flight,"wind_speed_raw",f+"getFlightStatus/getWindSpeed");
        metric(flight,"wind_warning_level",f+"getFlightStatus/getWindWarningLevel");
        // Candidate altitude/distance keys for Fly 1.21.8; misses are recorded
        // under read_errors and never fail the sample.
        metric(flight,"altitude_a",f+"getAltitude");
        metric(flight,"altitude_b",f+"getAircraftAltitude");
        metric(flight,"altitude_c",f+"getAircraftLocation3D");
        metric(flight,"distance_home",f+"getDistanceToHome");
        aircraft.put("gps",gps).put("battery",battery).put("flight",flight);
        String h="getFlight/getReturnToHome/getReturnToHomeSettings/";
        JSONObject homePos=position(read(h+"getHomeLocation"));
        JSONObject home=new JSONObject().put("position",homePos==null?JSONObject.NULL:homePos)
            .put("status",homePos==null?"unavailable":"cached");
        metric(home,"type",h+"getHomeLocationType");metric(home,"distance_raw",h+"getDistance");
        JSONObject cautions=new JSONObject().put("coverage","partial_read_only");
        metric(cautions,"power_mode","getAircraft/getPowerMode/getPowerModeStateInfo");
        metric(cautions,"hms_diagnostics","getData/getHms/getHmsCode/getValue");
        cautions.put("adapter_revision",2);
        metric(cautions,"wind_warning_level",f+"getFlightStatus/getWindWarningLevel");
        metric(cautions,"motor_overload","getFlight/getAircraftInfo/getDiagnosticCode/getMotorOverloadExisted");
        cautions.put("full_dji_hms_available",false);
        out.put("aircraft",aircraft).put("home",home).put("remote",remote()).put("cautions",cautions).put("read_errors",errors);
        return out;
    }
}
