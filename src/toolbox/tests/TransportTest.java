package org.openkatmai.toolbox;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Host-only protocol fixture; no Android device, credentials or mutations. */
public final class TransportTest {
    static final int CNXN=0x4e584e43, OPEN=0x4e45504f, OKAY=0x59414b4f,
            WRTE=0x45545257, CLSE=0x45534c43;
    static void number(DataOutputStream o, int n) throws IOException { o.writeInt(Integer.reverseBytes(n)); }
    static int number(DataInputStream i) throws IOException { return Integer.reverseBytes(i.readInt()); }
    static void send(DataOutputStream o, int c, int a, int b, String text) throws IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        int sum=0; for(byte v:bytes) sum+=v&255;
        number(o,c);number(o,a);number(o,b);number(o,bytes.length);number(o,sum);number(o,c^-1);
        o.write(bytes);o.flush();
    }
    static int[] read(DataInputStream i) throws IOException {
        int c=number(i),a=number(i),b=number(i),n=number(i);number(i);number(i);
        if(n<0||n>65536)throw new IOException("Bad fixture packet");
        byte[] data=new byte[n];i.readFully(data);return new int[]{c,a,b};
    }
    static void expect(boolean b,String message) { if(!b)throw new AssertionError(message); }
    static void scenario(String uid, boolean malformed) throws Exception {
        // Production LocalAdb always connects to loopback:5555. Fail if this host port is occupied.
        try(ServerSocket server=new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1",5555));
            ExecutorService pool=Executors.newSingleThreadExecutor();
            Future<?> peer=pool.submit(() -> {
                try(Socket s=server.accept()) {
                    s.setSoTimeout(5000);
                    DataInputStream i=new DataInputStream(s.getInputStream());
                    DataOutputStream o=new DataOutputStream(s.getOutputStream());
                    expect(read(i)[0]==CNXN,"Expected connect");
                    if(malformed) {
                        number(o,CNXN);number(o,0);number(o,0);number(o,1048577);number(o,0);number(o,CNXN^-1);o.flush();return;
                    }
                    send(o,CNXN,0x01000000,4096,"device::fixture\0");
                    int[] open=read(i);expect(open[0]==OPEN,"Expected UID stream");
                    send(o,OKAY,7,open[1],"");send(o,WRTE,7,open[1],uid+"\n");
                    expect(read(i)[0]==OKAY,"Expected write acknowledgment");
                    send(o,CLSE,7,open[1],"");read(i);
                    if(!uid.equals("1000")) return;
                    int old=open[1];open=read(i);expect(open[0]==OPEN,"Expected action stream");
                    send(o,CLSE,7,old,""); // Late close from previous stream must not break next command.
                    send(o,OKAY,8,open[1],"");send(o,WRTE,8,open[1],"fixture-result\n");read(i);
                    send(o,CLSE,8,open[1],"");read(i);
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            try {
                boolean rejected=false;
                try(LocalAdb adb=new LocalAdb()) {
                    expect(adb.execute("fixture").contains("fixture-result"),"Missing response");
                } catch(IOException e) { rejected=true; }
                expect(rejected==(malformed||!uid.equals("1000")),"UID/packet rejection mismatch");
                peer.get(8,TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); }
        }
    }
    public static void main(String[] args) throws Exception {
        scenario("1000",false);scenario("2000",false);scenario("1000",true);
        expect(Actions.quote("a'b").equals("'a'\\''b'"),"Shell quoting failed");
        boolean rejected=false;
        try { Actions.packageName("good.app;reboot"); } catch(IllegalArgumentException e) { rejected=true; }
        expect(rejected,"Package injection accepted");
        rejected=false;
        try { Actions.install("/sdcard/Download/../bad.apk",false); } catch(IllegalArgumentException e) { rejected=true; }
        expect(rejected,"Parent traversal accepted");
        System.out.println("PASS: system UID, wrong UID, oversized packet, late close, quoting, injection, path traversal");
    }
}
