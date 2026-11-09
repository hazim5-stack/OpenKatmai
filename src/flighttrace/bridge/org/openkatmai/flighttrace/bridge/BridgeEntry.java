package org.openkatmai.flighttrace.bridge;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Authenticated loopback-only telemetry stream. This protocol has no aircraft commands. */
public final class BridgeEntry {
    public static final int PORT=37686;
    private static volatile boolean running;
    private static volatile String latest;
    private static long started;
    public static synchronized String start(ClassLoader fly,Context context,String secret) throws Exception {
        if(secret==null||!secret.matches("[a-f0-9]{32}"))throw new SecurityException("Invalid local pairing secret");
        if(running)return "ALREADY_RUNNING";
        Reader reader=new Reader(fly,context);
        ServerSocket listener=new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress("127.0.0.1",PORT),3);
        started=System.currentTimeMillis();running=true;
        Thread sample=new Thread(() -> {
            long seq=0;
            while(running){
                try{latest=reader.sample(++seq).put("bridge_started_at_ms",started).toString();}
                catch(Throwable t){try{latest=new JSONObject().put("schema",1).put("sequence",++seq).put("sampled_at_ms",System.currentTimeMillis()).put("source_error",t.getClass().getSimpleName()).toString();}catch(Exception ignored){}}
                try{Thread.sleep(1000);}catch(InterruptedException e){break;}
            }
        },"flighttrace-read-cache");sample.setDaemon(true);sample.start();
        AtomicInteger clients=new AtomicInteger();
        Thread accept=new Thread(() -> {
            while(running){
                try{
                    Socket socket=listener.accept();
                    if(clients.incrementAndGet()>3){clients.decrementAndGet();socket.close();continue;}
                    Thread client=new Thread(() -> {
                        try(Socket s=socket){
                            s.setTcpNoDelay(true);s.setSoTimeout(5000);
                            StringBuilder auth=new StringBuilder();InputStream input=s.getInputStream();
                            for(int i=0;i<64;i++){int ch=input.read();if(ch<0)throw new EOFException();if(ch=='\n')break;auth.append((char)ch);}
                            if(!("AUTH "+secret).equals(auth.toString()))return;
                            OutputStream output=new BufferedOutputStream(s.getOutputStream());long last=0;
                            while(running){String text=latest;
                                if(text!=null&&System.currentTimeMillis()-last>=900){output.write((text+"\n").getBytes(StandardCharsets.UTF_8));output.flush();last=System.currentTimeMillis();}
                                Thread.sleep(150);
                            }
                        }catch(Exception ignored){}finally{clients.decrementAndGet();}
                    },"flighttrace-client");client.setDaemon(true);client.start();
                }catch(Exception e){running=false;}
            }
            try{listener.close();}catch(IOException ignored){}
        },"flighttrace-accept");accept.setDaemon(true);accept.start();
        Log.i("FlightTraceBridge","Read-only bridge started on loopback; no control endpoints");
        return "STARTED";
    }
}
