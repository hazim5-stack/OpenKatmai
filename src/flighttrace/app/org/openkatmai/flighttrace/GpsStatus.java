package org.openkatmai.flighttrace;

/** A missing satellite count is never interpreted as zero or as a GPS fix. */
final class GpsStatus {
    static String describe(Integer count,boolean fresh,boolean usable){
        if(!fresh)return "GPS: waiting for telemetry (no current satellite count)";
        if(count==null||count<0)return "Satellites: unavailable\nWaiting for aircraft satellite data";
        String number="Satellites: "+count;
        if(count==0)return number+"\nNo satellites reported - waiting for GPS reception";
        if(usable)return number+"\nAircraft coordinates received (cache measurement age unknown)";
        if(count<4)return number+"\nSatellite reception detected - position not confirmed";
        return number+"\nSatellites received - waiting for confirmed aircraft coordinates";
    }
}
