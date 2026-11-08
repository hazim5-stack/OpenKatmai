package org.openkatmai.toolbox;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private tile launch bridge. A tile slot is resolved from the durable journal. */
public final class LaunchActivity extends Activity {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int slot = getIntent().getIntExtra("slot", -1);
        if (slot < 0 || slot >= ManagedStore.SLOTS) { finish(); return; }
        if (!BUSY.compareAndSet(false, true)) {
            Toast.makeText(this, "An app launch is already running", Toast.LENGTH_LONG).show(); finish(); return;
        }
        android.widget.TextView progress = new android.widget.TextView(this);
        progress.setText("Opening app...\nPreparing compatibility when required.");
        progress.setTextSize(22); progress.setPadding(40, 40, 40, 40); setContentView(progress);
        new Thread(() -> {
            try {
                String pkg = new ManagedStore(this).packageAt(slot);
                if (pkg == null) throw new java.io.IOException("Shortcut was removed");
                String result = ManagedApps.open(getApplicationContext(), pkg);
                getSharedPreferences("launch-status", MODE_PRIVATE).edit().putString("last", result).commit();
                android.util.Log.i("OpenKatmaiToolbox", result);
            } catch (Exception e) {
                getSharedPreferences("launch-status", MODE_PRIVATE).edit().putString("last", e.toString()).commit();
                android.util.Log.w("OpenKatmaiToolbox", "Tile launch failed", e);
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            } finally { BUSY.set(false); runOnUiThread(() -> finish()); }
        }, "openkatmai-app-tile").start();
    }
}
