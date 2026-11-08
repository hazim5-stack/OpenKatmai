package org.openkatmai.toolbox;

/** Device-independent first-run decision logic; no connection is made for a No answer. */
final class TilePrompt {
    interface Preferences {
        boolean suppressed();
        boolean added();
        void suppress(boolean value) throws Exception;
        void added(boolean value) throws Exception;
    }
    interface AddTile { void run() throws Exception; }
    private final Preferences preferences;
    private final AddTile action;
    TilePrompt(Preferences preferences, AddTile action) { this.preferences = preferences; this.action = action; }
    boolean shouldShow() { return shouldShow(false); }
    boolean shouldShow(boolean answeredThisLaunch) { return !answeredThisLaunch && !preferences.suppressed() && !preferences.added(); }
    void answer(boolean yes, boolean dontShowAgain) throws Exception {
        // This choice is independent of connectivity: even a failed Yes respects the checkbox.
        preferences.suppress(dontShowAgain);
        if (!yes) return;
        action.run();
        preferences.added(true); // Only suppress an already-added tile after confirmed success.
    }
}
