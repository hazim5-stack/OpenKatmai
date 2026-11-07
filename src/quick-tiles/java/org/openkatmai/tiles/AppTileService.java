package org.openkatmai.tiles;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class AppTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile=getQsTile();
        if (tile!=null) { tile.setLabel(Config.PLAIN_LABEL);tile.setState(Tile.STATE_ACTIVE);tile.updateTile(); }
    }
    @Override public void onClick() {
        Intent launch=getPackageManager().getLaunchIntentForPackage(Config.PLAIN_PACKAGE);
        if (launch==null) { Toast.makeText(this,"Configured app is not installed or visible",Toast.LENGTH_LONG).show();return; }
        try { startActivityAndCollapse(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException e) { Toast.makeText(this,"Cannot open configured app",Toast.LENGTH_LONG).show(); }
    }
}
