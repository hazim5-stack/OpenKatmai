package org.openkatmai.toolbox.device;

import android.os.IBinder;
import android.os.Process;
import java.lang.reflect.Method;

/** System-UID helper: use PackageManager's delete API, avoiding shell's null caller-package bug. */
public final class Uninstall {
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 1000 || args.length != 1) throw new SecurityException("System UID and one package required");
        String pkg = args[0];
        if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+") || pkg.equals("dji.go.v5")
                || pkg.startsWith("com.dpad.") || pkg.startsWith("com.android.") || pkg.startsWith("android."))
            throw new SecurityException("Protected or invalid package");
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package");
        Object manager = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
        for (Method method : manager.getClass().getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getName().equals("deletePackageAsUser") && types.length == 5 && types[0] == String.class
                    && types[1] == int.class && types[2].getName().equals("android.content.pm.IPackageDeleteObserver")
                    && types[3] == int.class && types[4] == int.class) {
                // VersionCode HIGHEST (-1), owner user 0, DELETE_ALL_USERS (2).
                method.invoke(manager, pkg, -1, null, 0, 2);
                System.out.println("DELETE_REQUESTED"); return;
            }
        }
        throw new UnsupportedOperationException("Expected Android 11 delete API is unavailable");
    }
}
