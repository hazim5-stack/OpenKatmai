package org.openkatmai.toolbox;

import java.io.IOException;

/** Only locally selected values reach these methods; intents never supply commands. */
final class Actions {
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
                .replace("\n10.00.08.00\n", "\nv10.00.0800 (2026.07.30)\n");
    }
    static String apps() throws IOException { return run("pm list packages -3 | sort"); }
    static String downloads() throws IOException {
        return run("find /sdcard/Download -maxdepth 1 -type f -name '*.apk' | sort");
    }
    static String logs() throws IOException { return run("logcat -d -t 100 -v brief | tail -c 45000"); }
    static String open(String pkg) throws IOException {
        packageName(pkg);
        String component = run("cmd package resolve-activity --brief -a android.intent.action.MAIN "
                + "-c android.intent.category.LAUNCHER " + quote(pkg)).trim();
        int newline = component.lastIndexOf('\n');
        if (newline >= 0) component = component.substring(newline + 1).trim();
        if (!component.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))
            throw new IOException("No launcher activity found for " + pkg);
        String info = run("dumpsys package " + quote(pkg) + " | grep primaryCpuAbi");
        if (info.contains("primaryCpuAbi=arm64-v8a")) {
            String result = run("sh /data/data/com.dpad.fuli/launch64.sh " + quote(component));
            if (!result.contains("APP_READY") || !result.contains("RESTORE_DONE"))
                throw new IOException("ARM64 launch failed; install the verified launch64.sh helper.\n" + result);
            return result;
        }
        return activity(run("am start -W -n " + quote(component)));
    }
    static String install(String path, boolean arm64) throws IOException {
        String base = "/sdcard/Download/";
        if (!path.startsWith(base) || !path.endsWith(".apk") || path.substring(base.length()).contains("/")
                || path.contains("\n") || path.contains("\r"))
            throw new IllegalArgumentException("Select an APK directly inside internal Download");
        // mktemp avoids collisions and the EXIT trap removes the staged APK on failure too.
        String result = run("f=$(mktemp /data/data/com.dpad.fuli/openkatmai-apk.XXXXXX) || exit 1; "
                + "trap 'rm -f \"$f\"' EXIT; cp " + quote(path) + " \"$f\" && "
                + "pm install -r " + (arm64 ? "--abi arm64-v8a " : "") + "\"$f\"");
        if (!result.trim().equals("Success")) throw new IOException("APK install failed:\n" + result);
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
