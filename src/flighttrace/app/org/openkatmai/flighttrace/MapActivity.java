package org.openkatmai.flighttrace;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Live map: aircraft position, home point, RC position and the recorded track. */
public final class MapActivity extends Activity {
    private final Handler handler=new Handler();
    private final LiveState state=LiveState.INSTANCE;
    private MapView map;
    private TextView info;
    private Marker aircraft,home,remote;
    private Polyline track;
    private boolean follow=true,followedOnce;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        state.init(this);
        Configuration.getInstance().load(this,getSharedPreferences("osmdroid",0));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root=new FrameLayout(this);
        map=new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);
        map.getController().setCenter(new GeoPoint(21.4225,39.8262));
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        info=new TextView(this);
        info.setTextSize(15);
        info.setTextColor(Color.WHITE);
        info.setPadding(20,14,20,14);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.argb(190,10,16,24));bg.setCornerRadius(18);
        info.setBackground(bg);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-2,-2);
        ip.setMargins(16,16,16,16);root.addView(info,ip);

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2);
        cp.gravity=0x800005;cp.setMargins(16,16,16,16); // bottom|end
        cp.gravity=0x000050|0x000005;
        root.addView(controls,cp);
        Button followButton=new Button(this);followButton.setText("Follow: ON");
        followButton.setOnClickListener(v->{follow=!follow;followButton.setText(follow?"Follow: ON":"Follow: OFF");});
        controls.addView(followButton);
        Button close=new Button(this);close.setText("Close map");
        close.setOnClickListener(v->finish());
        controls.addView(close);

        aircraft=marker("Aircraft",Color.rgb(74,220,194));
        home=marker("Home",Color.rgb(255,198,97));
        remote=marker("RC",Color.rgb(130,170,255));
        track=new Polyline(map);
        track.getOutlinePaint().setColor(Color.rgb(74,220,194));
        track.getOutlinePaint().setStrokeWidth(8f);
        map.getOverlays().add(track);
        setContentView(root);
    }

    private Marker marker(String title,int color){
        Marker m=new Marker(map);
        m.setTitle(title);
        m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER);
        m.setTextLabelBackgroundColor(color);
        map.getOverlays().add(m);
        return m;
    }

    private static GeoPoint geo(JSONObject p){
        if(p==null)return null;
        double lat=p.optDouble("latitude",Double.NaN),lon=p.optDouble("longitude",Double.NaN);
        if(!PositionPolicy.valid(lat,lon))return null;
        return new GeoPoint(lat,lon);
    }

    private static void place(Marker m,GeoPoint p){
        m.setVisible(p!=null);
        if(p!=null)m.setPosition(p);
    }

    private void render(){
        JSONObject packet=state.latest;
        JSONObject a=packet==null?null:packet.optJSONObject("aircraft");
        JSONObject g=a==null?null:a.optJSONObject("gps");
        JSONObject b=a==null?null:a.optJSONObject("battery");
        JSONObject f=a==null?null:a.optJSONObject("flight");
        boolean fresh=state.fresh();

        JSONObject ap=state.lastAircraft();
        JSONObject attitude=f==null?null:f.optJSONObject("attitude");
        JSONObject velocity=f==null?null:f.optJSONObject("velocity_raw");
        String altitude=firstNumber(f,"altitude_a","altitude_b","altitude_c");
        String speed="—";
        if(velocity!=null&&velocity.has("x")&&velocity.has("y")){
            double vx=velocity.optDouble("x"),vy=velocity.optDouble("y");
            speed=String.format(Locale.US,"%.1f",Math.hypot(vx,vy));
        }
        GeoPoint aircraftPoint=geo(ap);
        place(aircraft,aircraftPoint);
        if(aircraftPoint!=null){
            if(attitude!=null&&attitude.has("yaw"))aircraft.setRotation(-(float)attitude.optDouble("yaw"));
            if(follow){map.getController().animateTo(aircraftPoint);followedOnce=true;}
        }
        place(home,geo(state.lastHome()));
        place(remote,geo(state.lastRemote()));

        ArrayList<GeoPoint> points=new ArrayList<>();
        for(ArrayList<double[]> segment:state.tracks())for(double[] p:segment)points.add(new GeoPoint(p[0],p[1]));
        track.setPoints(points);

        info.setText(String.format(Locale.US,
            "%s\nAircraft: %s\nGPS: %s sats · signal %s\nBattery: %s%% · %s °C\nMode: %s · motors %s\nAltitude: %s m · Speed: %s m/s\nDistance to home: %s\nTrack points: %d",
            fresh?"● LIVE":"○ STALE / disconnected",
            aircraftPoint==null?"no confirmed fix":String.format(Locale.US,"%.7f, %.7f",aircraftPoint.getLatitude(),aircraftPoint.getLongitude()),
            g==null?"—":String.valueOf(g.opt("satellites")),
            g==null?"—":String.valueOf(g.opt("signal_level")),
            b==null?"—":String.valueOf(b.opt("percent")),
            b==null?"—":String.valueOf(b.opt("temperature_c")),
            f==null?"—":String.valueOf(f.opt("mode")),
            f==null?"—":String.valueOf(f.opt("motors_on")),
            altitude,
            speed,
            distanceText(ap,state.lastHome()),
            points.size()));
        map.invalidate();
    }

    private static String firstNumber(JSONObject f,String... keys){
        if(f==null)return "—";
        for(String k:keys){
            Object v=f.opt(k);
            if(v instanceof Number)return String.format(Locale.US,"%.1f",((Number)v).doubleValue());
            if(v instanceof JSONObject){
                double h=((JSONObject)v).optDouble("altitude",Double.NaN);
                if(Double.isFinite(h))return String.format(Locale.US,"%.1f",h);
            }
        }
        return "—";
    }

    private static String distanceText(JSONObject aircraft,JSONObject home){
        GeoPoint a=geo(aircraft),h=geo(home);
        if(a==null||h==null)return "unavailable";
        double d=PositionPolicy.distance(a.getLatitude(),a.getLongitude(),h.getLatitude(),h.getLongitude());
        return String.format(Locale.US,"%.0f m",d);
    }

    @Override protected void onResume(){super.onResume();map.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);map.onPause();super.onPause();}
}
