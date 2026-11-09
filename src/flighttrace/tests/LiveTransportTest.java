package org.openkatmai.flighttrace;
/** Optional read-only host integration test against the existing RC daemon. */
public final class LiveTransportTest {
 public static void main(String[] args)throws Exception{
  try(AdbLink link=new AdbLink(new AdbLink.SocketWire(args[0],Integer.parseInt(args[1])))){
   String identity=link.shell("id -u; getprop ro.product.device").trim();
   if(!identity.matches("1000\\s+rc331"))throw new AssertionError(identity);
   for(int i=0;i<3;i++)if(!link.shell("echo flighttrace-transport").trim().equals("flighttrace-transport"))throw new AssertionError("stream reuse");
   boolean rejected=false;try{link.push(new java.io.ByteArrayInputStream(new byte[0]),"/sdcard/other.apk");}catch(java.io.IOException expected){rejected=true;}
   if(!rejected)throw new AssertionError("unrestricted upload");
   System.out.println("LiveTransportTest: identity, repeated shell streams and upload restriction passed");
  }
 }
}
