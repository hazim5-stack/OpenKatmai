package org.openkatmai.flighttrace;

import android.hardware.usb.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Minimal ADB host transport. Only the selected ADB interface is claimed. */
final class AdbLink implements Closeable {
    interface Wire extends Closeable {void read(byte[] dst,int off,int length)throws IOException;void write(byte[] src,int off,int length)throws IOException;}
    static final class SocketWire implements Wire {
        final Socket socket=new Socket();final InputStream in;final OutputStream out;
        SocketWire(String host,int port)throws IOException{socket.connect(new InetSocketAddress(host,port),2500);socket.setSoTimeout(12000);socket.setTcpNoDelay(true);in=new BufferedInputStream(socket.getInputStream());out=new BufferedOutputStream(socket.getOutputStream());}
        public void read(byte[] b,int o,int n)throws IOException{while(n>0){int got=in.read(b,o,n);if(got<0)throw new EOFException();o+=got;n-=got;}}
        public void write(byte[] b,int o,int n)throws IOException{out.write(b,o,n);out.flush();}
        public void close()throws IOException{socket.close();}
    }
    static final class UsbWire implements Wire {
        final UsbDeviceConnection connection;final UsbInterface face;final UsbEndpoint input,output;
        final byte[] buffer=new byte[16384];int position,limit;
        UsbWire(UsbManager manager,UsbDevice device)throws IOException{
            UsbInterface chosen=null;UsbEndpoint in=null,out=null;
            for(int i=0;i<device.getInterfaceCount();i++){
                UsbInterface f=device.getInterface(i);
                if(f.getInterfaceClass()!=255||f.getInterfaceSubclass()!=66||f.getInterfaceProtocol()!=1)continue;
                for(int e=0;e<f.getEndpointCount();e++){UsbEndpoint ep=f.getEndpoint(e);if(ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;if(ep.getDirection()==UsbConstants.USB_DIR_IN)in=ep;else out=ep;}
                if(in!=null&&out!=null){chosen=f;break;}
            }
            if(chosen==null)throw new IOException("No ADB USB interface. Activate OpenKatmai on the remote.");
            connection=manager.openDevice(device);if(connection==null)throw new IOException("USB access was not granted");
            face=chosen;input=in;output=out;
            if(!connection.claimInterface(face,false)){connection.close();throw new IOException("ADB USB interface is busy. Disconnect another USB debugging client.");}
            // Bulk endpoint buffers survive release/claim. A previous app session
            // may have ended halfway through a packet: discard those raw bytes
            // before sending CNXN, rather than interpreting them as a new header.
            byte[] stale=new byte[16384];
            for(int i=0;i<32;i++)if(connection.bulkTransfer(input,stale,stale.length,100)<=0)break;
        }
        public synchronized void read(byte[] b,int off,int length)throws IOException{
            while(length>0){
                if(position==limit){limit=connection.bulkTransfer(input,buffer,buffer.length,12000);position=0;if(limit<=0)throw new IOException("USB read timed out or remote disconnected");}
                int n=Math.min(length,limit-position);System.arraycopy(buffer,position,b,off,n);position+=n;off+=n;length-=n;
            }
        }
        public void write(byte[] b,int off,int length)throws IOException{
            int original=length;
            while(length>0){int n=connection.bulkTransfer(output,b,off,Math.min(length,16384),12000);if(n<=0)throw new IOException("USB write failed");off+=n;length-=n;}
            if(original>0&&original%output.getMaxPacketSize()==0)connection.bulkTransfer(output,new byte[0],0,1000);
        }
        public void close(){try{connection.releaseInterface(face);}finally{connection.close();}}
    }
    static boolean isAdb(UsbDevice device){for(int i=0;i<device.getInterfaceCount();i++){UsbInterface f=device.getInterface(i);if(f.getInterfaceClass()==255&&f.getInterfaceSubclass()==66&&f.getInterfaceProtocol()==1)return true;}return false;}
    private static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
    private final Wire wire;private int next=1;private int peerMax=4096;
    private static final class Packet {int type,a,b;byte[] data;}
    AdbLink(Wire wire)throws IOException{
        this.wire=wire;
        try{send(CNXN,0x01000000,262144,"host::openkatmai-flighttrace\0".getBytes(StandardCharsets.UTF_8));Packet p=receive();
            // A reopened Android USB interface can still contain packets from the
            // previous session. Drain a bounded amount before the new CNXN reply.
            for(int stale=0;p.type!=CNXN&&p.type!=AUTH&&stale<16;stale++){
                if(p.type!=WRTE&&p.type!=OKAY&&p.type!=CLSE)throw new IOException("Unexpected ADB handshake packet");
                if(p.type==WRTE)send(CLSE,p.b,p.a,new byte[0]);p=receive();
            }
            if(p.type==AUTH)throw new IOException("Remote requires ADB authentication. This build supports the existing OpenKatmai clean daemon.");
            if(p.type!=CNXN)throw new IOException("ADB handshake failed");peerMax=Math.max(4096,Math.min(262144,p.b));
        }catch(IOException e){wire.close();throw e;}
    }
    static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    static void put(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>>8);b[o+2]=(byte)(v>>>16);b[o+3]=(byte)(v>>>24);}
    static int get(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16)|(b[o+3]<<24);}
    private synchronized void send(int type,int a,int b,byte[] data)throws IOException{
        int sum=0;for(byte v:data)sum+=v&255;byte[] header=new byte[24];put(header,0,type);put(header,4,a);put(header,8,b);put(header,12,data.length);put(header,16,sum);put(header,20,type^-1);
        wire.write(header,0,header.length);if(data.length>0)wire.write(data,0,data.length);
    }
    private Packet receive()throws IOException{
        byte[] h=new byte[24];wire.read(h,0,24);Packet p=new Packet();p.type=get(h,0);p.a=get(h,4);p.b=get(h,8);int size=get(h,12);
        if(get(h,20)!=(p.type^-1)||size<0||size>1048576)throw new IOException("Invalid ADB packet");
        p.data=new byte[size];if(size>0)wire.read(p.data,0,size);int sum=0;for(byte v:p.data)sum+=v&255;
        if(get(h,16)!=0&&get(h,16)!=sum)throw new IOException("ADB checksum error");return p;
    }
    final class Stream implements Closeable {
        final int local;int remote;boolean ended;ByteArrayOutputStream queued=new ByteArrayOutputStream();byte[] buffer=new byte[0];int offset;
        Stream(String service)throws IOException{
            local=next++;send(OPEN,local,0,(service+"\0").getBytes(StandardCharsets.UTF_8));
            Packet p=packet();if(p.type==CLSE)throw new IOException("Remote service refused: "+service);if(p.type!=OKAY)throw new IOException("Unexpected service handshake");remote=p.a;
        }
        private Packet packet()throws IOException{for(int stale=0;stale<64;stale++){Packet p=receive();if(p.b==local)return p;
            if(p.type==OKAY||p.type==CLSE)continue;
            if(p.type==WRTE){send(CLSE,p.b,p.a,new byte[0]);continue;}
            throw new IOException("ADB stream mismatch: command="+Integer.toHexString(p.type)+", recipient="+p.b+", expected="+local);
        }throw new IOException("Too many stale ADB stream packets");}
        private void incoming(Packet p)throws IOException{if(p.type==WRTE){queued.write(p.data,0,p.data.length);if(queued.size()>1048576)throw new IOException("ADB receive queue overflow");send(OKAY,local,p.a,new byte[0]);}else if(p.type==CLSE){ended=true;send(CLSE,local,p.a,new byte[0]);}}
        void write(byte[] bytes)throws IOException{
            for(int off=0;off<bytes.length;){int count=Math.min(peerMax,bytes.length-off);send(WRTE,local,remote,Arrays.copyOfRange(bytes,off,off+count));
                while(true){Packet p=packet();if(p.type==OKAY)break;incoming(p);if(ended)throw new EOFException("Remote stream closed");}off+=count;
            }
        }
        int read(byte[] dst,int off,int count)throws IOException{
            while(offset==buffer.length){
                if(queued.size()>0){buffer=queued.toByteArray();queued.reset();offset=0;break;}
                if(ended)return -1;incoming(packet());
            }
            int n=Math.min(count,buffer.length-offset);System.arraycopy(buffer,offset,dst,off,n);offset+=n;return n;
        }
        void exact(byte[] out)throws IOException{int pos=0;while(pos<out.length){int n=read(out,pos,out.length-pos);if(n<0)throw new EOFException();pos+=n;}}
        String line(int max)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] one=new byte[1];while(b.size()<max){int n=read(one,0,1);if(n<0)throw new EOFException("Telemetry connection closed");if(one[0]=='\n')return b.toString("UTF-8");b.write(one[0]);}throw new IOException("Telemetry line too large");}
        public void close()throws IOException{if(!ended){send(CLSE,local,remote,new byte[0]);ended=true;}}
    }
    Stream open(String service)throws IOException{return new Stream(service);}
    String shell(String command)throws IOException{
        try(Stream s=open("exec:sh -c "+quote(command))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=s.read(b,0,b.length))>=0){if(out.size()+n>131072)throw new IOException("Command output too large");out.write(b,0,n);}return out.toString("UTF-8");}
    }
    void push(InputStream input,String path)throws IOException{
        if(!path.equals("/data/data/com.dpad.fuli/openkatmai-flighttrace-helper.apk"))throw new IOException("Invalid staging path");
        try(Stream s=open("sync:")){
            byte[] name=(path+",33188").getBytes(StandardCharsets.UTF_8);s.write(sync("SEND",name));byte[] b=new byte[32768];int n;
            while((n=input.read(b))>=0){if(n==0)continue;s.write(sync("DATA",Arrays.copyOf(b,n)));}
            byte[] done=new byte[8];System.arraycopy("DONE".getBytes(StandardCharsets.US_ASCII),0,done,0,4);put(done,4,(int)(System.currentTimeMillis()/1000));s.write(done);
            byte[] answer=new byte[8];s.exact(answer);String id=new String(answer,0,4,StandardCharsets.US_ASCII);
            if(!id.equals("OKAY")){int len=get(answer,4);if(len<0||len>4096)throw new IOException("Invalid sync response");byte[] error=new byte[len];s.exact(error);throw new IOException("APK upload failed: "+new String(error,StandardCharsets.UTF_8));}
            s.write(sync("QUIT",new byte[0]));
        }
    }
    private static byte[] sync(String id,byte[] data){byte[] b=new byte[8+data.length];System.arraycopy(id.getBytes(StandardCharsets.US_ASCII),0,b,0,4);put(b,4,data.length);System.arraycopy(data,0,b,8,data.length);return b;}
    public void close()throws IOException{wire.close();}
}
