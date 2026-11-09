package org.openkatmai.flighttrace;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;
/** Offline coordinate plot, intentionally not a street basemap. Never bridges recording gaps. */
final class TraceView extends View {
    private final Paint paint=new Paint(3);
    TraceView(Context c){super(c);}
    protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.rgb(20,32,46));paint.setTextSize(27);paint.setColor(Color.LTGRAY);c.drawText("Aircraft path · offline GPS plot",16,34,paint);
        ArrayList<ArrayList<double[]>> tracks=LiveState.INSTANCE.tracks();double loLat=90,hiLat=-90,loLon=180,hiLon=-180;int count=0;
        for(ArrayList<double[]> s:tracks)for(double[] p:s){loLat=Math.min(loLat,p[0]);hiLat=Math.max(hiLat,p[0]);loLon=Math.min(loLon,p[1]);hiLon=Math.max(hiLon,p[1]);count++;}
        if(count==0){c.drawText("Waiting for confirmed aircraft GPS",16,85,paint);return;}
        double mid=(loLat+hiLat)/2,cos=Math.max(.01,Math.cos(Math.toRadians(mid))),span=Math.max(.0001,Math.max(hiLat-loLat,(hiLon-loLon)*cos));double scale=Math.min(getWidth()-50,getHeight()-100)/span;
        paint.setStrokeWidth(4);paint.setColor(Color.rgb(74,220,194));
        for(ArrayList<double[]> s:tracks){float oldX=0,oldY=0;boolean first=true;for(double[] p:s){float x=(float)(getWidth()/2.0+(p[1]-(loLon+hiLon)/2)*cos*scale),y=(float)(getHeight()/2.0+25-(p[0]-mid)*scale);if(!first)c.drawLine(oldX,oldY,x,y,paint);c.drawCircle(x,y,4,paint);first=false;oldX=x;oldY=y;}}
    }
}
