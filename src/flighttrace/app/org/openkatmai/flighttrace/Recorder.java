package org.openkatmai.flighttrace;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Raw samples retain nulls/errors. GPX/GeoJSON include only confirmed usable positions. */
final class Recorder implements Closeable {
    private final BufferedWriter writer;
    final File file;
    Recorder(Context c)throws IOException{
        File dir=new File(c.getFilesDir(),"flights");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create recordings folder");
        String id=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());
        file=new File(dir,"flight-"+id+".ndjson");writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8));
        c.getSharedPreferences("recording",0).edit().putString("latest",file.getAbsolutePath()).apply();
    }
    synchronized void append(JSONObject sample)throws IOException,JSONException{JSONObject row=new JSONObject(sample.toString()).put("received_at_ms",System.currentTimeMillis());writer.write(row.toString());writer.newLine();writer.flush();}
    public synchronized void close()throws IOException{writer.close();}
    private static String iso(long ms){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(ms));}
    private static JSONObject child(JSONObject j,String key){JSONObject o=j.optJSONObject(key);return o==null?new JSONObject():o;}
    private static String csv(Object value){String s=value==null||value==JSONObject.NULL?"":String.valueOf(value);return '"'+s.replace("\"","\"\"")+'"';}
    static void export(Context c,String format,OutputStream target)throws Exception{
        String path=c.getSharedPreferences("recording",0).getString("latest","");File file=new File(path);
        File root=new File(c.getFilesDir(),"flights").getCanonicalFile();
        if(!file.getCanonicalPath().startsWith(root.getPath()+File.separator)||!file.isFile())throw new IOException("No recorded session yet");
        if(format.equals("ndjson")){try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)target.write(b,0,n);}return;}
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(target,StandardCharsets.UTF_8));
        JSONArray segments=new JSONArray(),current=new JSONArray();long last=0;boolean open=false;
        if(format.equals("csv"))out.write("received_utc,source_time_ms,aircraft_connected,position_usable,latitude,longitude,home_latitude,home_longitude,remote_latitude,remote_longitude,battery_percent,battery_voltage_mv,battery_temperature_c,gps_satellites,flight_mode,motors_on,flight_duration_s,cautions_json\n");
        if(format.equals("gpx"))out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"OpenKatmai FlightTrace\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Recorded aircraft GPS trace</name>");
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null){JSONObject row;try{row=new JSONObject(line);}catch(JSONException e){continue;}
                JSONObject a=child(row,"aircraft"),p=child(a,"position"),b=child(a,"battery"),g=child(a,"gps"),f=child(a,"flight"),h=child(child(row,"home"),"position"),r=child(child(row,"remote"),"position");
                long time=row.optLong("received_at_ms");double lat=p.optDouble("latitude",Double.NaN),lon=p.optDouble("longitude",Double.NaN);
                boolean valid=a.optBoolean("position_usable",false)&&a.optBoolean("connected",false)&&PositionPolicy.valid(lat,lon);
                if(format.equals("csv")){Object[] values={iso(time),row.opt("sampled_at_ms"),a.opt("connected"),valid,p.opt("latitude"),p.opt("longitude"),h.opt("latitude"),h.opt("longitude"),r.opt("latitude"),r.opt("longitude"),b.opt("percent"),b.opt("voltage_mv"),b.opt("temperature_c"),g.opt("satellites"),f.opt("mode"),f.opt("motors_on"),f.opt("duration_s"),row.opt("cautions")};for(int i=0;i<values.length;i++){if(i>0)out.write(',');out.write(csv(values[i]));}out.newLine();}
                else if(valid){
                    if(!open||!PositionPolicy.join(last,time)){if(open&&format.equals("gpx"))out.write("</trkseg>");if(current.length()>0)segments.put(current);current=new JSONArray();if(format.equals("gpx"))out.write("<trkseg>");open=true;}
                    current.put(new JSONArray().put(lon).put(lat));last=time;
                    if(format.equals("gpx"))out.write("<trkpt lat=\""+lat+"\" lon=\""+lon+"\"><time>"+iso(time)+"</time></trkpt>");
                }else{if(open&&format.equals("gpx"))out.write("</trkseg>");open=false;if(current.length()>0)segments.put(current);current=new JSONArray();last=0;}
            }
        }
        if(format.equals("gpx")){if(open)out.write("</trkseg>");out.write("</trk></gpx>");}
        if(format.equals("geojson")){if(current.length()>0)segments.put(current);JSONArray validLines=new JSONArray(),singlePoints=new JSONArray();for(int i=0;i<segments.length();i++){JSONArray seg=segments.getJSONArray(i);if(seg.length()>=2)validLines.put(seg);else if(seg.length()==1)singlePoints.put(seg.get(0));}
            JSONArray features=new JSONArray();if(validLines.length()>0)features.put(new JSONObject().put("type","Feature").put("properties",new JSONObject().put("subject","aircraft GPS trace")).put("geometry",new JSONObject().put("type","MultiLineString").put("coordinates",validLines)));
            if(singlePoints.length()>0)features.put(new JSONObject().put("type","Feature").put("properties",new JSONObject().put("subject","isolated confirmed samples")).put("geometry",new JSONObject().put("type","MultiPoint").put("coordinates",singlePoints)));
            out.write(new JSONObject().put("type","FeatureCollection").put("features",features).toString());}
        out.flush();
    }
}
