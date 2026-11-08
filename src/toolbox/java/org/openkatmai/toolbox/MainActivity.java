package org.openkatmai.toolbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UI styles adapted from RC2 Unlock Toolkit; all system actions use local system ADB. */
public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView output, status;
    private boolean busy, dead;
    private final ArrayList<Button> controls = new ArrayList<>();
    private interface Job { String run() throws Exception; }
    private interface Done { void accept(String text); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        UI.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(UI.BG);
        LinearLayout nav = UI.page(this);
        nav.addView(UI.title(this, "OpenKatmai"));
        nav.addView(UI.dim(this, "Toolbox 1.0"));
        button(nav, "Overview", () -> job(Actions::overview, null));
        button(nav, "Apps", () -> job(Actions::apps, this::showApps));
        button(nav, "Install APK", () -> job(Actions::downloads, this::showApks));
        button(nav, "Logs", () -> job(Actions::logs, null));
        button(nav, "Settings", () -> job(() -> Actions.settings("android"), null));
        button(nav, "Developer", () -> job(() -> Actions.settings("developer"), null));
        button(nav, "Shell", () -> job(() -> Actions.settings("shell"), null));
        button(nav, "About", () -> {
            String license;
            try (java.io.InputStream in = getAssets().open("UPSTREAM-LICENSE.txt")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[2048]; int n;
                while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
                license = bytes.toString("UTF-8");
            } catch (Exception e) { license = "See the upstream license in the project source."; }
            output.setText("OpenKatmai Toolbox\n\nAdapted for system-UID ADB.\n\nUI adapted from RC2 Unlock Toolkit contributors (MIT).\nhttps://github.com/456erddgr/rc2-unlock-toolkit\n\nSource and setup:\nhttps://github.com/hazim5-stack/OpenKatmai\n\nThe original Root Toolbox remains a separate application.\n\n" + license);
        });
        ScrollView navScroll = new ScrollView(this);
        navScroll.addView(nav);
        root.addView(navScroll, new LinearLayout.LayoutParams(UI.dp(180), -1));
        LinearLayout content = UI.page(this);
        content.addView(UI.title(this, "Your RC 2 tools"));
        status = UI.dim(this, "Connecting to OpenKatmai...");
        content.addView(status);
        ScrollView scroll = new ScrollView(this);
        output = UI.text(this, "", UI.TEXT, 14);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(0, UI.dp(12), 0, UI.dp(12));
        scroll.addView(output);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
        job(Actions::overview, null);
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(14);
        b.setOnClickListener(v -> action.run());
        parent.addView(b, new LinearLayout.LayoutParams(-1, UI.dp(44)));
        controls.add(b);
    }
    private void job(Job work, Done done) {
        if (busy || dead) return;
        busy = true; for (Button b : controls) b.setEnabled(false);
        status.setText("Working...");
        worker.execute(() -> {
            String result; boolean ok;
            try { result = work.run(); ok = true; }
            catch (Exception e) { result = "Connection/action failed: " + e.getMessage()
                    + "\n\nActivate OpenKatmai from the factory assistant, then try again."; ok = false; }
            final String text = result; final boolean success = ok;
            runOnUiThread(() -> {
                if (dead) return;
                busy = false; for (Button b : controls) b.setEnabled(true);
                status.setText(success ? "OpenKatmai | system ADB | UID 1000" : "Action failed");
                output.setText(text.isEmpty() ? "No results." : text);
                if (success && done != null) done.accept(text);
            });
        });
    }
    private void showApps(String text) {
        ArrayList<String> packages = new ArrayList<>();
        for (String line : text.split("\n")) if (line.startsWith("package:")) packages.add(line.substring(8).trim());
        if (packages.isEmpty()) return;
        String[] items = packages.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Open installed app")
            .setItems(items, (d, n) -> job(() -> Actions.open(items[n]), null))
            .setNegativeButton("Cancel", null).show();
    }
    private void showApks(String text) {
        ArrayList<String> paths = new ArrayList<>();
        for (String line : text.split("\n")) if (line.startsWith("/sdcard/Download/") && line.endsWith(".apk")) paths.add(line);
        if (paths.isEmpty()) { output.setText("Copy an APK to internal Download using USB file transfer, then tap Install APK."); return; }
        String[] names = new String[paths.size()];
        for (int i = 0; i < names.length; i++) names[i] = paths.get(i).substring("/sdcard/Download/".length());
        new AlertDialog.Builder(this).setTitle("APK in internal Download")
            .setItems(names, (d, n) -> new AlertDialog.Builder(this).setTitle("Install " + names[n] + "?")
                .setMessage("An existing app with the same package may be updated. Select ARM64 only when the APK provides arm64-v8a libraries.")
                .setPositiveButton("Install", (x, y) -> job(() -> Actions.install(paths.get(n), false), null))
                .setNeutralButton("Install ARM64", (x, y) -> job(() -> Actions.install(paths.get(n), true), null))
                .setNegativeButton("Cancel", null).show())
            .setNegativeButton("Cancel", null).show();
    }
    @Override public void onDestroy() {
        dead = true; worker.shutdownNow(); super.onDestroy();
    }
}
