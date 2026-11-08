package org.openkatmai.toolbox;

import android.content.Intent;
import android.service.quicksettings.TileService;

public final class ToolboxTileService extends TileService {
    @Override public void onClick() {
        super.onClick();
        startActivityAndCollapse(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
