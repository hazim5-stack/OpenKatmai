package org.openkatmai.tiles;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-exported bridge: no caller-controlled shell commands or endpoints. */
public final class BridgeActivity extends Activity {
    private static final AtomicBoolean busy=new AtomicBoolean();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final int operation=getIntent().getIntExtra("operation",0);
        // Finish before opening another app's task; do not cover its new UI later.
        finish();
        if (operation!=1 && operation!=2) return;
        if (!busy.compareAndSet(false,true)) {
            Toast.makeText(this,"Preparation is already running",Toast.LENGTH_LONG).show();return;
        }
        Toast.makeText(this,operation==1 ? "Preparing ARM64 app..." : "Opening Shell...",Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                String failure=null;
                try (LocalAdb adb=new LocalAdb()) {
                    String result=operation==1 ? adb.launch64() : adb.openShell();
                    boolean success=operation==1
                        ? result.contains("APP_READY") && result.contains("RESTORE_DONE")
                        : result.contains("Status: ok");
                    if (!success) failure=result;
                    Log.i("OpenKatmaiTiles",result);
                } catch (Exception e) { failure=e.getMessage(); }
                finally { busy.set(false); }
                if (failure==null) return;
                final String error=failure;
                Log.w("OpenKatmaiTiles",error);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (operation==2) {
                            try {
                                getApplicationContext().startActivity(new Intent()
                                    .setClassName("com.dpad.fuli","com.dpad.fuli.DevActivity")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                Toast.makeText(getApplicationContext(),"Open Shell in the assistant; local ADB is unavailable",Toast.LENGTH_LONG).show();
                                return;
                            } catch (RuntimeException ignored) { /* Report below. */ }
                        }
                        Toast.makeText(getApplicationContext(),"Preparation failed; check local ADB and OpenKatmaiTiles log",Toast.LENGTH_LONG).show();
                    }
                });
            }
        },"openkatmai-tile").start();
    }
}
