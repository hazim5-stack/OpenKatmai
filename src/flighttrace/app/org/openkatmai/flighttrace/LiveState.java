package org.openkatmai.flighttrace;

import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.util.*;

final class LiveState {
    static final LiveState INSTANCE=new LiveState();
    volatile JSONObject latest;
    volatile String status="Connect the RC 2 by USB",recordPath="";
    volatile long receivedAt;
    volatile boolean connected,recording;
    volatile int frames;
    private JSONObject lastAircraft,lastHome,lastRemote;
    private long lastGoodElapsed;
    private boolean gap=true;
    private final ArrayList<ArrayList<double[]>> segments=new ArrayList<>();
    private Context context;
    synchronized void init(Context c){
        if(context!=null)return;context=c.getApplicationContext();
        try{String saved=context.getSharedPreferences("last-seen",0).getString("aircraft",null);if(saved!=null)lastAircraft=new JSONObject(saved);}catch(Exception ignored){}
    }
    static JSONObject point(JSONObject parent,String key){JSONObject p=parent==null?null:parent.optJSONObject(key);if(p==null||!PositionPolicy.valid(p.optDouble("latitude",Double.NaN),p.optDouble("longitude",Double.NaN)))return null;return p;}
    synchronized void accept(JSONObject packet)throws JSONException{
        if(packet.optInt("schema")!=1)throw new JSONException("Unsupported telemetry schema");
        latest=packet;receivedAt=SystemClock.elapsedRealtime();connected=true;frames++;
        JSONObject aircraft=packet.optJSONObject("aircraft");JSONObject p=point(aircraft,"position");
        boolean usable=aircraft!=null&&aircraft.optBoolean("position_usable",false)&&Boolean.TRUE.equals(aircraft.opt("connected"));
        long wall=System.currentTimeMillis();
        if(usable&&p!=null){
            lastAircraft=new JSONObject(p.toString()).put("observed_at_ms",wall).put("source_sampled_at_ms",packet.optLong("sampled_at_ms"));
            if(context!=null)context.getSharedPreferences("last-seen",0).edit().putString("aircraft",lastAircraft.toString()).apply();
            if(gap||!PositionPolicy.join(lastGoodElapsed,receivedAt)||segments.isEmpty())segments.add(new ArrayList<>());
            ArrayList<double[]> segment=segments.get(segments.size()-1);
            segment.add(new double[]{p.getDouble("latitude"),p.getDouble("longitude"),wall});
            if(segment.size()>20000){ArrayList<double[]> reduced=new ArrayList<>();for(int i=0;i<segment.size();i+=2)reduced.add(segment.get(i));segments.set(segments.size()-1,reduced);}
            lastGoodElapsed=receivedAt;gap=false;
        }else gap=true;
        JSONObject h=point(packet.optJSONObject("home"),"position");if(h!=null)lastHome=new JSONObject(h.toString()).put("observed_at_ms",wall);
        JSONObject r=packet.optJSONObject("remote"),rp=point(r,"position");
        if(rp!=null&&"available".equals(r.optString("status")))lastRemote=new JSONObject(rp.toString()).put("observed_at_ms",wall).put("accuracy_m",r.opt("accuracy_m"));
        status="Telemetry received";
    }
    synchronized void lost(String reason){connected=false;gap=true;status=reason;}
    boolean fresh(){return connected&&PositionPolicy.fresh(SystemClock.elapsedRealtime()-receivedAt);}
    synchronized JSONObject lastAircraft(){return lastAircraft;}
    synchronized JSONObject lastHome(){return lastHome;}
    synchronized JSONObject lastRemote(){return lastRemote;}
    synchronized ArrayList<ArrayList<double[]>> tracks(){ArrayList<ArrayList<double[]>> copy=new ArrayList<>();for(ArrayList<double[]> s:segments)copy.add(new ArrayList<>(s));return copy;}
}
