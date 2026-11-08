package org.openkatmai.toolbox;

import android.content.*;
import android.content.pm.*;
import android.service.quicksettings.TileService;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

final class ManagedApps {
    static final Object LOCK = new Object();
    static final class Apk {
        File cache;
        String path, pkg, label, digest;
        boolean arm64, alreadyInstalled;
        void discard() { if (cache != null) cache.delete(); }
    }
    static String hash(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = new byte[65536]; int n;
        while ((n = input.read(bytes)) != -1) digest.update(bytes, 0, n);
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    static PackageInfo info(Context c, String pkg) {
        try { return c.getPackageManager().getPackageInfo(pkg, PackageManager.GET_SIGNATURES); }
        catch (PackageManager.NameNotFoundException e) { return null; }
    }
    static String signature(PackageInfo p) throws Exception {
        if (p.signatures == null || p.signatures.length == 0) throw new IOException("Package signing identity unavailable");
        return hash(new ByteArrayInputStream(p.signatures[0].toByteArray()));
    }
    static Apk inspect(Context c, String path) throws Exception {
        Actions.downloadPath(path);
        Apk apk = new Apk(); apk.path = path;
        apk.cache = File.createTempFile("inspect-", ".apk", c.getCacheDir());
        try {
            try (LocalAdb adb = new LocalAdb(); OutputStream out = new FileOutputStream(apk.cache)) { adb.download(path, out); }
            PackageInfo parsed = c.getPackageManager().getPackageArchiveInfo(apk.cache.getPath(), 0);
            if (parsed == null || parsed.applicationInfo == null) throw new IOException("Invalid APK or unreadable file");
            apk.pkg = Actions.packageName(parsed.packageName);
            if (protectedPackage(c, apk.pkg)) throw new IOException("This system/flight/Toolbox package is protected; use the documented update procedure");
            parsed.applicationInfo.sourceDir = apk.cache.getPath(); parsed.applicationInfo.publicSourceDir = apk.cache.getPath();
            apk.label = parsed.applicationInfo.loadLabel(c.getPackageManager()).toString();
            apk.alreadyInstalled = info(c, apk.pkg) != null;
            try (InputStream in = new FileInputStream(apk.cache)) { apk.digest = hash(in); }
            try (ZipFile zip = new ZipFile(apk.cache)) {
                Enumeration<? extends ZipEntry> it = zip.entries();
                while (it.hasMoreElements()) { String name = it.nextElement().getName(); if (name.startsWith("lib/arm64-v8a/") && name.endsWith(".so")) apk.arm64 = true; }
            }
            return apk;
        } catch (Exception e) { apk.discard(); throw e; }
    }
    static boolean protectedPackage(Context c, String pkg) {
        if (SafetyPolicy.protectedName(c.getPackageName(), pkg)) return true;
        PackageInfo p = info(c, pkg);
        return p != null && (p.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }
    static void ensureHelper(Context c) throws Exception {
        String script;
        try (InputStream in = c.getAssets().open("launch64.sh")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            script = out.toString("UTF-8");
        }
        String expected = hash(new ByteArrayInputStream(script.getBytes("UTF-8")));
        String result = Actions.run("if [ \"$(sha256sum " + Actions.HELPER + " 2>/dev/null | cut -d' ' -f1)\" != "
                + Actions.quote(expected) + " ]; then f=$(mktemp /data/data/com.dpad.fuli/openkatmai-helper.XXXXXX) || exit 1; "
                + "trap 'rm -f \"$f\"' EXIT; printf '%s' " + Actions.quote(script) + " > \"$f\" && chmod 700 \"$f\" && mv \"$f\" " + Actions.HELPER + "; fi; sha256sum " + Actions.HELPER);
        if (!result.startsWith(expected)) throw new IOException("ARM64 helper verification failed: " + result);
    }
    static String open(Context c, String pkg) throws Exception {
        synchronized (LOCK) {
            String abi = Actions.run("dumpsys package " + Actions.quote(Actions.packageName(pkg)) + " | grep primaryCpuAbi");
            if (abi.contains("primaryCpuAbi=arm64-v8a")) ensureHelper(c);
            return Actions.open(pkg);
        }
    }
    static void ensureUninstaller(Context c) throws Exception {
        byte[] bytes;
        try (InputStream in = c.getAssets().open("uninstall-helper.jar")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] block = new byte[4096]; int n;
            while ((n = in.read(block)) != -1) out.write(block, 0, n);
            bytes = out.toByteArray();
        }
        String expected = hash(new ByteArrayInputStream(bytes));
        String encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        String result = Actions.run("if [ \"$(sha256sum " + Actions.UNINSTALL_HELPER + " 2>/dev/null | cut -d' ' -f1)\" != "
                + Actions.quote(expected) + " ]; then f=$(mktemp /data/data/com.dpad.fuli/openkatmai-uninstall.XXXXXX) || exit 1; "
                + "trap 'rm -f \"$f\"' EXIT; printf '%s' " + Actions.quote(encoded) + " | base64 -d > \"$f\" && chmod 600 \"$f\" && mv \"$f\" " + Actions.UNINSTALL_HELPER + "; fi; sha256sum " + Actions.UNINSTALL_HELPER);
        if (!result.startsWith(expected)) throw new IOException("Uninstall helper verification failed: " + result);
    }
    static String install(Context c, Apk apk) throws Exception {
        synchronized (LOCK) {
            try {
                ManagedStore store = new ManagedStore(c);
                JSONObject record = store.get(apk.pkg);
                if (record == null) record = new JSONObject();
                boolean existed = info(c, apk.pkg) != null;
                record.put("label", apk.label).put("source", apk.path).put("digest", apk.digest).put("installPending", true);
                store.save(apk.pkg, record); // No ownership claim until successful installation and readback.
                Actions.install(apk.path, apk.arm64, apk.digest);
                PackageInfo installed = info(c, apk.pkg);
                if (installed == null) throw new IOException("Installed package readback failed: " + apk.pkg);
                record.put("owned", record.optBoolean("owned", false) || !existed)
                    .put("firstInstall", installed.firstInstallTime).put("signature", signature(installed)).put("installPending", false);
                store.save(apk.pkg, record);
                addTile(c, apk.pkg);
                return "Installed: " + apk.label + "\n" + apk.pkg + "\nAdded to Quick Settings.\n"
                    + (apk.arm64 ? "ARM64 compatibility preparation is automatic when opened." : "Standard app launch.");
            } finally { apk.discard(); }
        }
    }
    static String addTile(Context c, String pkg) throws Exception {
        synchronized (LOCK) {
            if (protectedPackage(c, pkg)) throw new IOException("Protected package");
            PackageInfo p = info(c, pkg); if (p == null) throw new IOException("App is not installed");
            // Resolve before reserving a slot so non-launcher packages do not create dead shortcuts.
            Actions.launchComponent(pkg);
            ManagedStore store = new ManagedStore(c); JSONObject record = store.get(pkg);
            if (record == null) record = new JSONObject().put("owned", false).put("label", p.applicationInfo.loadLabel(c.getPackageManager()).toString());
            int slot = record.optInt("slot", -1); if (slot < 0) slot = store.freeSlot();
            if (slot < 0) throw new IOException("All 24 shortcuts are in use. Remove a shortcut first; the app remains installed.");
            record.put("slot", slot); store.save(pkg, record);
            ComponentName component = ComponentName.unflattenFromString(ManagedStore.component(slot));
            c.getPackageManager().setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            Actions.tile(ManagedStore.component(slot), true);
            TileService.requestListeningState(c, component);
            return "Added to Quick Settings: " + record.optString("label", pkg);
        }
    }
    static String removeTile(Context c, String pkg) throws Exception {
        synchronized (LOCK) {
            ManagedStore store = new ManagedStore(c); JSONObject record = store.get(pkg);
            if (record == null || record.optInt("slot", -1) < 0) return "No managed shortcut";
            int slot = record.getInt("slot");
            Actions.tile(ManagedStore.component(slot), false);
            c.getPackageManager().setComponentEnabledSetting(ComponentName.unflattenFromString(ManagedStore.component(slot)),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            record.remove("slot"); store.save(pkg, record);
            return "Shortcut removed";
        }
    }
    static void requireDeletable(Context c, String pkg) throws Exception {
        if (protectedPackage(c, pkg)) throw new IOException("System, flight and Toolbox packages are protected");
        if (info(c, pkg) == null) return;
        String home = Actions.run("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
        if (home.contains(pkg + "/")) throw new IOException("This app is the active Home launcher. Select another Home app before uninstalling it.");
    }
    static String removeApp(Context c, String pkg, boolean deleteApk, boolean ownedOnly) throws Exception {
        synchronized (LOCK) {
            requireDeletable(c, pkg);
            ManagedStore store = new ManagedStore(c); JSONObject record = store.get(pkg);
            PackageInfo current = info(c, pkg);
            if (ownedOnly && current != null && (record == null || !SafetyPolicy.ownsInstalledIdentity(record.optBoolean("owned"),
                    record.optLong("firstInstall"), current.firstInstallTime, record.optString("signature"), signature(current))))
                throw new IOException("Ownership changed; preserved " + pkg + ". Review it in Apps.");
            removeTile(c, pkg);
            if (current != null) { ensureUninstaller(c); Actions.uninstall(pkg); }
            // Keep the journal if a changed source cannot be removed, so a later cleanup can retry safely.
            if (deleteApk && record != null && !record.optString("source").isEmpty())
                Actions.deleteSource(record.getString("source"), record.getString("digest"));
            store = new ManagedStore(c); store.remove(pkg);
            return "Removed app and shortcut: " + pkg;
        }
    }
    static String plan(Context c) throws Exception {
        synchronized (LOCK) {
            ManagedStore store = new ManagedStore(c); StringBuilder out = new StringBuilder();
            for (String pkg : store.packages()) {
                JSONObject r = store.get(pkg);
                out.append(r.optString("label", pkg)).append("\n  ").append(pkg).append("\n  ")
                    .append(r.optBoolean("owned") ? "Uninstall app + remove shortcut" : "Remove shortcut; keep pre-existing app").append("\n");
            }
            if (out.length() == 0) out.append("No managed apps.\n");
            out.append("\nRemove Toolbox tile and its dedicated ARM64 helper.\n");
            return out.toString();
        }
    }
    static String cleanup(Context c, boolean deleteApks, boolean removeSelf) throws Exception {
        synchronized (LOCK) {
            if (Actions.run("[ -d /data/data/com.dpad.fuli/.openkatmai-launch64-lock ] && echo LAUNCH_BUSY").contains("LAUNCH_BUSY"))
                throw new IOException("Compatibility preparation is running. Wait for it to finish before cleanup.");
            ManagedStore store = new ManagedStore(c); StringBuilder results = new StringBuilder(); boolean failed = false;
            for (String pkg : store.packages()) {
                JSONObject record = store.get(pkg);
                try {
                    if (record.optBoolean("owned")) results.append(removeApp(c, pkg, deleteApks, true));
                    else {
                        removeTile(c, pkg);
                        if (deleteApks && !record.optString("source").isEmpty()) Actions.deleteSource(record.getString("source"), record.getString("digest"));
                        new ManagedStore(c).remove(pkg); results.append("Preserved pre-existing app: ").append(pkg);
                    }
                } catch (Exception e) { failed = true; results.append("Needs attention: ").append(pkg).append(" — ").append(e.getMessage()); }
                results.append("\n");
            }
            if (failed) return results + "\nCleanup incomplete. Toolbox and remaining records retained; retry after resolving the errors.";
            Actions.tile("org.openkatmai.toolbox/.ToolboxTileService", false);
            String removed = Actions.run("rm -f " + Actions.HELPER + "; "
                    + "for f in /data/data/com.dpad.fuli/openkatmai-launch.*; do [ ! -f \"$f\" ] || rm -f \"$f\"; done; "
                    + "[ ! -e " + Actions.HELPER + " ] && echo HELPER_REMOVED");
            if (!removed.contains("HELPER_REMOVED")) throw new IOException("Helper cleanup failed; Toolbox retained");
            File[] cached = c.getCacheDir().listFiles();
            if (cached != null) for (File f : cached) if (f.getName().startsWith("inspect-") && f.getName().endsWith(".apk")) f.delete();
            if (removeSelf) {
                ensureUninstaller(c);
                // The independent system-side shell removes its helper after requesting self-removal.
                String result = Actions.run("CLASSPATH=" + Actions.UNINSTALL_HELPER + " app_process64 /system/bin org.openkatmai.toolbox.device.Uninstall "
                        + Actions.quote(c.getPackageName()) + "; r=$?; if [ $r = 0 ]; then rm -f " + Actions.UNINSTALL_HELPER + "; fi; exit $r");
                if (!result.contains("DELETE_REQUESTED")) throw new IOException("Toolbox removal failed; retry cleanup: " + result);
            } else Actions.run("rm -f " + Actions.UNINSTALL_HELPER);
            return results + "\nCleanup completed.";
        }
    }
}
