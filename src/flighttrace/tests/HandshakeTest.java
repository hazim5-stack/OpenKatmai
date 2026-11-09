package org.openkatmai.flighttrace;
import java.io.*;
final class HandshakeTest {
 static byte[] packet(int type,int length){byte[] b=new byte[24];AdbLink.put(b,0,type);AdbLink.put(b,8,length);AdbLink.put(b,20,type^-1);return b;}
 static final class Fake implements AdbLink.Wire {
  final ByteArrayInputStream input;final ByteArrayOutputStream output=new ByteArrayOutputStream();boolean closed;
  Fake(int... commands)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(int c:commands)b.write(packet(c,4096));input=new ByteArrayInputStream(b.toByteArray());}
  public void read(byte[] b,int off,int n)throws IOException{if(input.read(b,off,n)!=n)throw new EOFException();}
  public void write(byte[] b,int off,int n){output.write(b,off,n);}public void close(){closed=true;}
 }
 public static void main(String[] args)throws Exception{
  Fake f=new Fake(0x45534c43,0x59414b4f,0x4e584e43);try(AdbLink link=new AdbLink(f)){}if(!f.closed)throw new AssertionError();
  Fake auth=new Fake(0x48545541);try{new AdbLink(auth);throw new AssertionError();}catch(IOException expected){if(!expected.getMessage().contains("authentication"))throw expected;}
  if(!auth.closed)throw new AssertionError();System.out.println("HandshakeTest: stale USB packets drained; authentication rejected; connections closed");
 }
}
