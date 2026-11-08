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
    private ManagedApps.Apk pendingApk;
    private AlertDialog tilePromptDialog;
    private CheckBox tilePromptCheckbox;
    private boolean startupPromptHandled;
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
        nav.addView(UI.dim(this, "Toolbox 1.1.1"));
        button(nav, "Overview", () -> job(Actions::overview, null));
        button(nav, "Apps", () -> job(Actions::apps, this::showApps));
        button(nav, "Download / Install", () -> job(Actions::downloads, this::showApks));
        button(nav, "Remove apps", () -> job(Actions::apps, text -> showRemoveApps(text)));
        button(nav, "Clean up", () -> job(() -> ManagedApps.plan(this), this::showCleanup));
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
            output.setText("OpenKatmai Toolbox\n\nAdapted for system-UID ADB.\n\nUI adapted from RC2 Unlock Toolkit contributors (MIT).\nhttps://github.com/456erddgr/rc2-unlock-toolkit\n\nSource and setup:\nhttps://github.com/hazim5-stack/OpenKatmai\n\nThe original Root Toolbox remains a separate application.\n\nLast top-bar launch:\n"
                + getSharedPreferences("launch-status", MODE_PRIVATE).getString("last", "No launch recorded") + "\n\n" + license);
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
        TilePrompt prompt = new TilePrompt(new TilePromptPreferences(this),
                () -> Actions.tile("org.openkatmai.toolbox/.ToolboxTileService", true));
        startupPromptHandled = saved != null && saved.getBoolean("tile-prompt-handled", false);
        if (prompt.shouldShow(startupPromptHandled)) showTilePrompt(prompt, saved != null && saved.getBoolean("tile-prompt-checked", false));
        else job(Actions::overview, null);
    }
    private void showTilePrompt(TilePrompt prompt, boolean checked) {
        tilePromptCheckbox = new CheckBox(this);
        tilePromptCheckbox.setText(R.string.tile_prompt_dont_show);
        tilePromptCheckbox.setChecked(checked);
        tilePromptCheckbox.setPadding(UI.dp(20), UI.dp(8), UI.dp(20), UI.dp(8));
        tilePromptDialog = new AlertDialog.Builder(this)
            .setTitle(R.string.tile_prompt_title).setMessage(R.string.tile_prompt_question)
            .setView(tilePromptCheckbox).setCancelable(false)
            .setPositiveButton(R.string.tile_prompt_yes, (d, n) -> {
                startupPromptHandled = true;
                boolean remember = tilePromptCheckbox.isChecked();
                job(() -> { prompt.answer(true, remember); return getString(R.string.tile_prompt_added); }, null);
            })
            .setNegativeButton(R.string.tile_prompt_no, (d, n) -> {
                startupPromptHandled = true;
                try { prompt.answer(false, tilePromptCheckbox.isChecked()); job(Actions::overview, null); }
                catch (Exception e) { status.setText("Action failed"); output.setText(e.getMessage()); }
            }).create();
        tilePromptDialog.show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("tile-prompt-handled", startupPromptHandled);
        if (tilePromptDialog != null && tilePromptDialog.isShowing())
            state.putBoolean("tile-prompt-checked", tilePromptCheckbox.isChecked());
        super.onSaveInstanceState(state);
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
                    + "\n\nIf the connection is unavailable, reactivate OpenKatmai from the factory assistant."; ok = false; }
            final String text = result; final boolean success = ok;
            runOnUiThread(() -> {
                if (dead) { if (pendingApk != null) pendingApk.discard(); return; }
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
        new AlertDialog.Builder(this).setTitle("Installed apps")
            .setItems(items, (d, n) -> new AlertDialog.Builder(this).setTitle(items[n])
                .setItems(new String[]{"Open", "Add to top bar", "Remove shortcut only"}, (x, action) -> {
                    if (action == 0) job(() -> ManagedApps.open(this, items[n]), null);
                    if (action == 1) job(() -> ManagedApps.addTile(this, items[n]), null);
                    if (action == 2) job(() -> ManagedApps.removeTile(this, items[n]), null);
                }).setNegativeButton("Cancel", null).show())
            .setNegativeButton("Cancel", null).show();
    }
    private void showApks(String text) {
        ArrayList<String> paths = new ArrayList<>();
        for (String line : text.split("\n")) if (line.startsWith("/sdcard/Download/") && line.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) paths.add(line.trim());
        if (paths.isEmpty()) { output.setText("Copy an APK to internal Download using USB file transfer, then tap Install APK."); return; }
        String[] names = new String[paths.size()];
        for (int i = 0; i < names.length; i++) names[i] = paths.get(i).substring("/sdcard/Download/".length());
        new AlertDialog.Builder(this).setTitle("Internal Download — select APK")
            .setItems(names, (d, n) -> job(() -> {
                pendingApk = ManagedApps.inspect(this, paths.get(n));
                return pendingApk.label + "\n" + pendingApk.pkg;
            }, result -> confirmInstall()))
            .setNegativeButton("Cancel", null).show();
    }
    private void confirmInstall() {
        final ManagedApps.Apk apk = pendingApk;
        if (apk == null) return;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Install " + apk.label + "?")
            .setMessage(apk.pkg + "\n\n" + (apk.alreadyInstalled ? "Updates an existing app.\n" : "Installs a new app.\n")
                + (apk.arm64 ? "ARM64 detected: compatibility preparation will run automatically from its top-bar shortcut.\n" : "Standard Android app.\n")
                + "Adds a named shortcut to the top bar.")
            .setPositiveButton("Install + add", (d, n) -> { pendingApk = null; job(() -> ManagedApps.install(this, apk), null); })
            .setNegativeButton("Cancel", (d, n) -> { apk.discard(); pendingApk = null; }).create();
        dialog.setOnCancelListener(d -> { apk.discard(); pendingApk = null; });
        dialog.show();
    }
    private void showRemoveApps(String text) {
        ArrayList<String> packages = new ArrayList<>();
        for (String line : text.split("\n")) if (line.startsWith("package:")) {
            String pkg = line.substring(8).trim();
            if (!ManagedApps.protectedPackage(this, pkg)) packages.add(pkg);
        }
        if (packages.isEmpty()) { output.setText("No removable third-party apps."); return; }
        String[] items = packages.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Remove app + top-bar shortcut")
            .setItems(items, (d, n) -> {
                CheckBox source = new CheckBox(this); source.setText("Also delete its recorded APK from Download");
                new AlertDialog.Builder(this).setTitle("Uninstall " + items[n] + "?")
                    .setMessage("Deletes this app and its app data, and removes its managed shortcut.")
                    .setView(source).setPositiveButton("Uninstall", (x, y) -> job(() -> ManagedApps.removeApp(this, items[n], source.isChecked(), false), null))
                    .setNegativeButton("Cancel", null).show();
            }).setNegativeButton("Cancel", null).show();
    }
    private void showCleanup(String plan) {
        LinearLayout options = new LinearLayout(this); options.setOrientation(LinearLayout.VERTICAL);
        CheckBox source = new CheckBox(this); source.setText("Delete recorded source APKs from Download"); options.addView(source);
        CheckBox self = new CheckBox(this); self.setText("Also uninstall OpenKatmai Toolbox last"); options.addView(self);
        new AlertDialog.Builder(this).setTitle("Review cleanup")
            .setMessage(plan + "\nApp data is deleted for apps being uninstalled. Changed source APKs are kept.")
            .setView(options).setPositiveButton("Clean up", (d, n) -> job(() -> ManagedApps.cleanup(this, source.isChecked(), self.isChecked()), null))
            .setNegativeButton("Cancel", null).show();
    }
    @Override public void onDestroy() {
        if (tilePromptDialog != null) tilePromptDialog.dismiss();
        dead = true; if (pendingApk != null) pendingApk.discard(); worker.shutdownNow(); super.onDestroy();
    }
}
