package org.openkatmai.flighttrace;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class BridgeInstaller {
    interface Stage {void update(String text);}
    static final class Connection {final AdbLink.Stream stream;final String first;Connection(AdbLink.Stream s,String f){stream=s;first=f;}}
    static Connection open(AdbLink link,String secret)throws IOException{
        AdbLink.Stream s=link.open("tcp:37686");
        try{s.write(("AUTH "+secret+"\n").getBytes(StandardCharsets.US_ASCII));return new Connection(s,s.line(65536));}
        catch(IOException e){s.close();throw e;}
    }
    static Connection ensure(Context context,AdbLink link,String secret,Stage stage)throws Exception{
        stage.update("Checking RC 2 identity");
        String identity=link.shell("id -u; getprop ro.product.device").trim();
        if(!identity.matches("1000\\s+rc331"))throw new IOException("Expected RC 2 (rc331) with system UID 1000. Got: "+identity);
        try{return open(link,secret);}catch(IOException ignored){}
        String version=link.shell("dumpsys package dji.go.v5 | grep versionName").trim();
        if(!version.contains("versionName=1.21.8"))throw new IOException("This cache adapter is verified for DJI Fly 1.21.8 only. "+version);
        stage.update("Installing the small read-only bridge helper");
        String digest=hash(context.getAssets().open("bridge-helper.apk"));
        String path="/data/data/com.dpad.fuli/openkatmai-flighttrace-helper.apk";
        try(InputStream in=context.getAssets().open("bridge-helper.apk")){link.push(in,path);}
        String actual=link.shell("sha256sum "+AdbLink.quote(path)).trim();
        if(!actual.startsWith(digest+" "))throw new IOException("Bridge helper checksum mismatch");
        String installed=link.shell("pm install --abi armeabi-v7a -r "+AdbLink.quote(path)).trim();
        if(!installed.equals("Success"))throw new IOException("Bridge helper installation failed: "+installed);
        link.shell("rm -f "+AdbLink.quote(path));
        String resolved=link.shell("pm path org.openkatmai.flightbridge").trim();
        if(!resolved.matches("package:/data/app/[A-Za-z0-9_~./+=-]+/base\\.apk"))throw new IOException("Unexpected helper code path: "+resolved);
        String apk=resolved.substring(8),dir=apk.substring(0,apk.lastIndexOf('/'));
        // ART splits an agent specification at the FIRST '='. Android's randomized
        // /data/app installation directories contain '=' padding, so passing the
        // installed native path directly truncates its filename before loading.
        String agent="/data/app/openkatmai-flighttrace/agent-"+digest.substring(0,16)+".so";
        String code="/data/app/openkatmai-flighttrace/helper-"+digest.substring(0,16)+".apk";
        String prepared=link.shell("mkdir -p /data/app/openkatmai-flighttrace && chmod 755 /data/app/openkatmai-flighttrace && ln -sf "+AdbLink.quote(dir+"/lib/arm/libflightbridge.so")+" "+AdbLink.quote(agent)+" && ln -sf "+AdbLink.quote(apk)+" "+AdbLink.quote(code)+" && echo READY").trim();
        if(!prepared.equals("READY"))throw new IOException("Cannot prepare the agent path: "+prepared);
        stage.update("Starting received-data cache reader in DJI Fly");
        String result=link.shell("am attach-agent dji.go.v5 "+AdbLink.quote(agent+"="+code+"|"+secret));
        if(result.contains("Exception")||result.contains("Error"))throw new IOException("Open DJI Fly on the RC 2 first. "+result);
        IOException last=null;
        for(int n=0;n<6;n++){try{Thread.sleep(600);return open(link,secret);}catch(IOException e){last=e;}}
        throw new IOException("Bridge is unavailable. Keep DJI Fly open. If a different phone paired this session, restart DJI Fly only while the aircraft is safely grounded.",last);
    }
    private static String hash(InputStream stream)throws Exception{try(InputStream in=stream){MessageDigest hash=MessageDigest.getInstance("SHA-256");byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)hash.update(b,0,n);StringBuilder s=new StringBuilder();for(byte v:hash.digest())s.append(String.format(Locale.US,"%02x",v&255));return s.toString();}}
}
