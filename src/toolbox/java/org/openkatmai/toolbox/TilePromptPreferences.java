package org.openkatmai.toolbox;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

final class TilePromptPreferences implements TilePrompt.Preferences {
    private final SharedPreferences prefs;
    TilePromptPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("toolbox-tile-prompt", Context.MODE_PRIVATE);
    }
    public boolean suppressed() { return prefs.getBoolean("dont-show-again", false); }
    public boolean added() { return prefs.getBoolean("tile-added", false); }
    public void suppress(boolean value) throws IOException { save("dont-show-again", value); }
    public void added(boolean value) throws IOException { save("tile-added", value); }
    private void save(String key, boolean value) throws IOException {
        if (!prefs.edit().putBoolean(key, value).commit()) throw new IOException("Could not save the top-bar preference");
    }
}
