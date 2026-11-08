package org.openkatmai.toolbox;

import java.io.IOException;

/** Only locally selected values reach these methods; intents never supply commands. */
final class Actions {
    static final String HELPER = "/data/data/com.dpad.fuli/openkatmai-toolbox-launch64.sh";
    static final String UNINSTALL_HELPER = "/data/data/com.dpad.fuli/openkatmai-toolbox-uninstall.jar";
    static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    static String packageName(String s) {
        if (!s.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))
            throw new IllegalArgumentException("Invalid package name");
        return s;
    }
    static String run(String command) throws IOException {
        try (LocalAdb adb = new LocalAdb()) { return adb.execute(command); }
    }
    static String overview() throws IOException {
        return run("echo 'SYSTEM IDENTITY'; echo uid=$(id -u); echo; echo 'DJI FIRMWARE'; getprop dji.prop.external_version; "
                + "echo; echo 'MEMORY'; head -5 /proc/meminfo; echo; echo 'STORAGE'; df -h /data /sdcard; "
                + "echo; echo 'BATTERY'; dumpsys battery; echo; echo 'RUNTIME'; getprop init.svc.zygote; "
                + "getprop init.svc.zygote_secondary; getprop persist.dji.zygote_policy")
                .replace("\n10.00.08.00\r\n", "\nv10.00.0800 (2026.07.30)\r\n")
                .replace("\n10.00.08.00\n", "\nv10.00.0800 (2026.07.30)\n")
                .replace("\n10.00.09.00\r\n", "\nv10.00.0900 (2026.10.20)\r\n")
                .replace("\n10.00.09.00\n", "\nv10.00.0900 (2026.10.20)\n");
    }
    static String apps() throws IOException { return run("pm list packages -3 | sort"); }
    static String downloads() throws IOException {
        return run("find /sdcard/Download -maxdepth 1 -type f -iname '*.apk' | sort");
    }
    static String downloadPath(String path) {
        String base = "/sdcard/Download/";
        if (!path.startsWith(base) || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")
                || path.substring(base.length()).contains("/") || path.contains("\n") || path.contains("\r")
                || path.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Select an APK directly inside internal Download");
        return path;
    }
    static String logs() throws IOException { return run("logcat -d -t 100 -v brief | tail -c 45000"); }
    static String launchComponent(String pkg) throws IOException {
        packageName(pkg);
        String result = run("cmd package query-activities --brief -a android.intent.action.MAIN "
                + "-c android.intent.category.LAUNCHER -p " + quote(pkg));
        // A package may have multiple launchers (for example a debug inspector). resolve-activity
        // can then return Android's chooser; select its first actual launcher like PackageManager.
        for (String line : result.split("\n")) {
            String component = line.trim();
            if (component.startsWith(pkg + "/") && component.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) return component;
        }
        throw new IOException("No launcher activity found for " + pkg);
    }
    static String open(String pkg) throws IOException {
        String component = launchComponent(pkg);
        String info = run("dumpsys package " + quote(pkg) + " | grep primaryCpuAbi");
        if (info.contains("primaryCpuAbi=arm64-v8a")) {
            return launch64(component);
        }
        return activity(run("am start -W -n " + quote(component)));
    }
    private static String launch64(String component) throws IOException {
        // A detached job keeps the bounded restore watchdog independent of an ADB/UI connection.
        // Poll short raw requests: long-lived local shell streams can stall on this firmware.
        String path = run("f=$(mktemp /data/data/com.dpad.fuli/openkatmai-launch.XXXXXX) || exit 1; "
                + "nohup sh " + HELPER + " " + quote(component) + " > \"$f\" 2>&1 </dev/null & echo \"$f\"").trim();
        if (!path.matches("/data/data/com\\.dpad\\.fuli/openkatmai-launch\\.[A-Za-z0-9]+"))
            throw new IOException("Could not start ARM64 preparation: " + path);
        String result = "";
        long deadline = System.nanoTime() + 60000000000L;
        try {
            while (System.nanoTime() < deadline) {
                result = run("cat " + quote(path));
                if (result.contains("RESTORE_DONE") || result.contains("RESTORE_FAILED")) {
                    if (result.contains("APP_READY") && result.contains("RESTORE_DONE")) return result;
                    throw new IOException("ARM64 preparation failed:\n" + result);
                }
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("ARM64 preparation interrupted"); }
            }
            throw new IOException("ARM64 preparation timed out; its restore watchdog remains active.\n" + result);
        } finally {
            try { run("rm -f " + quote(path)); } catch (IOException ignored) { /* Bulk cleanup also removes orphaned job logs. */ }
        }
    }
    static String install(String path, boolean arm64) throws IOException { return install(path, arm64, ""); }
    static String install(String path, boolean arm64, String expectedHash) throws IOException {
        downloadPath(path);
        if (!expectedHash.isEmpty() && !expectedHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid APK hash");
        // mktemp avoids collisions and the EXIT trap removes the staged APK on failure too.
        String result = run("f=$(mktemp /data/data/com.dpad.fuli/openkatmai-apk.XXXXXX) || exit 1; "
                + "trap 'rm -f \"$f\"' EXIT; cp " + quote(path) + " \"$f\" || exit 1; "
                + (expectedHash.isEmpty() ? "" : "[ \"$(sha256sum \"$f\" | cut -d' ' -f1)\" = " + quote(expectedHash) + " ] || { echo APK_CHANGED_ABORT; exit 1; }; ")
                + "pm install -r " + (arm64 ? "--abi arm64-v8a " : "") + "\"$f\"");
        if (!result.trim().equals("Success")) throw new IOException("APK install failed:\n" + result);
        return result;
    }
    static String tile(String component, boolean add) throws IOException {
        if (!component.matches("org\\.openkatmai\\.toolbox/\\.(?:TileSlots\\$T[0-9]{2}|ToolboxTileService)"))
            throw new IllegalArgumentException("Invalid Toolbox tile");
        run("cmd statusbar " + (add ? "add-tile " : "remove-tile ") + quote(component));
        String tiles = run("settings get secure sysui_qs_tiles");
        if (tiles.contains(component) != add) throw new IOException("Tile update not confirmed; retry: " + component);
        return tiles;
    }
    static String uninstall(String pkg) throws IOException {
        packageName(pkg);
        String result = run("CLASSPATH=" + UNINSTALL_HELPER + " app_process64 /system/bin org.openkatmai.toolbox.device.Uninstall " + quote(pkg));
        if (!result.contains("DELETE_REQUESTED")) throw new IOException("Uninstall request failed: " + result);
        for (int n = 0; n < 20; n++) {
            if (!run("pm path " + quote(pkg)).contains("package:")) return "Success";
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Uninstall interrupted"); }
        }
        throw new IOException("Package remains installed: " + pkg);
    }
    static String deleteSource(String path, String digest) throws IOException {
        downloadPath(path);
        if (!digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Missing APK hash");
        String result = run("if [ ! -e " + quote(path) + " ]; then echo SOURCE_REMOVED; "
                + "elif [ \"$(sha256sum " + quote(path) + " | cut -d' ' -f1)\" = " + quote(digest)
                + " ]; then rm " + quote(path) + " && echo SOURCE_REMOVED; else echo SOURCE_CHANGED_KEPT; fi");
        if (!result.contains("SOURCE_REMOVED")) throw new IOException("Source APK was changed and has been kept: " + path);
        return result;
    }
    static String settings(String which) throws IOException {
        if ("android".equals(which)) return activity(run("am start -W -a android.settings.SETTINGS"));
        if ("developer".equals(which)) return activity(run("am start -W -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS"));
        if ("shell".equals(which)) return activity(run("am start -W -n com.dpad.fuli/.ShellCommandActivity"));
        throw new IllegalArgumentException("Unknown settings action");
    }
    private static String activity(String result) throws IOException {
        if (!result.contains("Status: ok")) throw new IOException("Activity launch failed:\n" + result);
        return result;
    }
}
