package org.openkatmai.tiles;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class ShellTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile=getQsTile();
        if (tile!=null) { tile.setLabel("Shell");tile.setState(Tile.STATE_ACTIVE);tile.updateTile(); }
    }
    @Override public void onClick() {
        startActivityAndCollapse(new Intent(this,BridgeActivity.class)
            .putExtra("operation",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
