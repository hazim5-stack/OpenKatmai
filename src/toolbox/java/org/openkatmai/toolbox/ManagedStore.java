package org.openkatmai.toolbox;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Synchronous journal writes precede shell mutations; failed operations remain retryable. */
final class ManagedStore {
    static final int SLOTS = 24;
    private final android.content.SharedPreferences prefs;
    private final JSONObject records;
    ManagedStore(Context context) throws Exception {
        prefs = context.getSharedPreferences("managed-apps-v1", Context.MODE_PRIVATE);
        records = new JSONObject(prefs.getString("records", "{}"));
    }
    JSONObject get(String pkg) { return records.optJSONObject(pkg); }
    List<String> packages() {
        List<String> names = new ArrayList<>();
        Iterator<String> it = records.keys(); while (it.hasNext()) names.add(it.next());
        Collections.sort(names); return names;
    }
    void save(String pkg, JSONObject record) throws Exception { records.put(pkg, record); commit(); }
    void remove(String pkg) throws Exception { records.remove(pkg); commit(); }
    private void commit() throws IOException {
        if (!prefs.edit().putString("records", records.toString()).commit()) throw new IOException("Could not save management journal");
    }
    int freeSlot() {
        boolean[] used = new boolean[SLOTS];
        for (String pkg : packages()) { int n = get(pkg).optInt("slot", -1); if (n >= 0 && n < SLOTS) used[n] = true; }
        for (int n = 0; n < SLOTS; n++) if (!used[n]) return n;
        return -1;
    }
    String packageAt(int slot) {
        for (String pkg : packages()) if (get(pkg).optInt("slot", -1) == slot) return pkg;
        return null;
    }
    static String component(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Invalid tile slot");
        return String.format(java.util.Locale.ROOT, "org.openkatmai.toolbox/.TileSlots$T%02d", slot + 1);
    }
}
