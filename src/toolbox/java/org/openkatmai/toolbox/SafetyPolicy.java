package org.openkatmai.toolbox;

/** Pure policy, also tested on the host without Android or device access. */
final class SafetyPolicy {
    static boolean ownsInstalledIdentity(boolean owned, long recordedTime, long installedTime,
                                         String recordedSignature, String installedSignature) {
        return owned && recordedTime > 0 && recordedTime == installedTime && recordedSignature != null
            && !recordedSignature.isEmpty() && recordedSignature.equals(installedSignature);
    }
    static boolean protectedName(String self, String pkg) {
        return pkg.equals(self) || pkg.equals("dji.go.v5") || pkg.startsWith("com.dpad.")
                || pkg.startsWith("com.android.") || pkg.startsWith("android.");
    }
}
