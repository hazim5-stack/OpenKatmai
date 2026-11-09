package org.openkatmai.flighttrace;

/** Small pure-Java rules shared by recording and rendering. */
public final class PositionPolicy {
    public static boolean valid(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180&&!(lat==0&&lon==0);}
    public static boolean fresh(long ageMs){return ageMs>=0&&ageMs<=4000;}
    public static boolean join(long previousMs,long currentMs){return previousMs>0&&currentMs>=previousMs&&currentMs-previousMs<=4000;}
    public static double distance(double a,double b,double c,double d){double p=Math.toRadians(c-a),q=Math.toRadians(d-b),x=Math.sin(p/2)*Math.sin(p/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(q/2)*Math.sin(q/2);return 6371000*2*Math.atan2(Math.sqrt(x),Math.sqrt(Math.max(0,1-x)));}
    private PositionPolicy(){}
}
