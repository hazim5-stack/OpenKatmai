package org.openkatmai.toolbox;

import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.service.quicksettings.*;
import android.widget.Toast;

/** Manifest-backed slots allow named per-app tiles without generating APKs. */
public final class TileSlots {
    public abstract static class AppTile extends TileService {
        private final int slot;
        protected AppTile(int slot) { this.slot = slot; }
        @Override public void onStartListening() {
            try {
                ManagedStore store = new ManagedStore(this); String pkg = store.packageAt(slot);
                Tile tile = getQsTile(); if (tile == null) return;
                if (pkg == null) { tile.setState(Tile.STATE_UNAVAILABLE); tile.setLabel("Removed app"); tile.updateTile(); return; }
                tile.setLabel(store.get(pkg).optString("label", pkg));
                tile.setState(ManagedApps.info(this, pkg) == null ? Tile.STATE_UNAVAILABLE : Tile.STATE_ACTIVE);
                try {
                    Drawable drawable = getPackageManager().getApplicationIcon(pkg);
                    Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap); drawable.setBounds(0, 0, 96, 96); drawable.draw(canvas);
                    tile.setIcon(Icon.createWithBitmap(bitmap));
                } catch (Exception ignored) { }
                tile.updateTile();
            } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
        @Override public void onClick() {
            try {
                String pkg = new ManagedStore(this).packageAt(slot);
                if (pkg == null) return;
                // No caller-supplied package or command; the private activity resolves this saved slot.
                startActivityAndCollapse(new Intent(this, LaunchActivity.class).putExtra("slot", slot).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }
    public static final class T01 extends AppTile { public T01() { super(0); } }
    public static final class T02 extends AppTile { public T02() { super(1); } }
    public static final class T03 extends AppTile { public T03() { super(2); } }
    public static final class T04 extends AppTile { public T04() { super(3); } }
    public static final class T05 extends AppTile { public T05() { super(4); } }
    public static final class T06 extends AppTile { public T06() { super(5); } }
    public static final class T07 extends AppTile { public T07() { super(6); } }
    public static final class T08 extends AppTile { public T08() { super(7); } }
    public static final class T09 extends AppTile { public T09() { super(8); } }
    public static final class T10 extends AppTile { public T10() { super(9); } }
    public static final class T11 extends AppTile { public T11() { super(10); } }
    public static final class T12 extends AppTile { public T12() { super(11); } }
    public static final class T13 extends AppTile { public T13() { super(12); } }
    public static final class T14 extends AppTile { public T14() { super(13); } }
    public static final class T15 extends AppTile { public T15() { super(14); } }
    public static final class T16 extends AppTile { public T16() { super(15); } }
    public static final class T17 extends AppTile { public T17() { super(16); } }
    public static final class T18 extends AppTile { public T18() { super(17); } }
    public static final class T19 extends AppTile { public T19() { super(18); } }
    public static final class T20 extends AppTile { public T20() { super(19); } }
    public static final class T21 extends AppTile { public T21() { super(20); } }
    public static final class T22 extends AppTile { public T22() { super(21); } }
    public static final class T23 extends AppTile { public T23() { super(22); } }
    public static final class T24 extends AppTile { public T24() { super(23); } }
}
