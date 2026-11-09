package org.openkatmai.flighttrace;
final class GpsStatusTest {
 public static void main(String[] args){
  check(GpsStatus.describe(null,true,false).contains("unavailable"));
  check(!GpsStatus.describe(null,true,false).contains("Satellites: 0"));
  check(GpsStatus.describe(0,true,false).contains("No satellites reported"));
  check(GpsStatus.describe(3,true,false).contains("not confirmed"));
  check(GpsStatus.describe(14,true,false).contains("waiting for confirmed"));
  check(GpsStatus.describe(14,true,true).contains("coordinates received"));
  check(GpsStatus.describe(14,false,true).contains("no current satellite count"));
  System.out.println("GpsStatusTest: unknown, zero, receiving, confirmed and stale states passed");
 }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
}
