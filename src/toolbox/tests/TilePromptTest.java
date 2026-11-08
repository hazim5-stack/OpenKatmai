package org.openkatmai.toolbox;

/** Pure Java test doubles only: does not connect to ADB or any remote device. */
public final class TilePromptTest {
    static final class Memory implements TilePrompt.Preferences {
        boolean suppress, added, failSave;
        public boolean suppressed() { return suppress; }
        public boolean added() { return added; }
        public void suppress(boolean value) throws Exception {
            if (failSave) throw new Exception("storage failure");
            suppress = value;
        }
        public void added(boolean value) { added = value; }
    }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    static void choice(boolean yes, boolean dontShow) throws Exception {
        Memory saved = new Memory(); int[] calls = {0};
        TilePrompt prompt = new TilePrompt(saved, () -> calls[0]++);
        check(prompt.shouldShow(), "Fresh launch must ask");
        prompt.answer(yes, dontShow);
        check(calls[0] == (yes ? 1 : 0), "No must not add a tile");
        check(saved.suppress == dontShow, "Checkbox must persist independently");
        check(saved.added == yes, "Only successful Yes records addition");
        TilePrompt reopened = new TilePrompt(saved, () -> calls[0]++);
        check(reopened.shouldShow() == (!dontShow && !yes), "Reopening must respect saved choice");
        check(!reopened.shouldShow(true), "Activity recreation must not repeat a question already answered this launch");
    }
    public static void main(String[] args) throws Exception {
        choice(false, false); choice(false, true); choice(true, false); choice(true, true);
        for (boolean remember : new boolean[]{false, true}) {
            Memory saved = new Memory();
            TilePrompt failed = new TilePrompt(saved, () -> { throw new Exception("ADB unavailable"); });
            try { failed.answer(true, remember); throw new AssertionError("Failure swallowed"); }
            catch (Exception expected) { }
            check(!saved.added, "Failed addition must not be marked successful");
            check(failed.shouldShow() == !remember, "Failure/retry must respect checkbox");
        }
        Memory saved = new Memory(); saved.failSave = true; int[] calls = {0};
        try { new TilePrompt(saved, () -> calls[0]++).answer(true, true); throw new AssertionError("Storage failure swallowed"); }
        catch (Exception expected) { }
        check(calls[0] == 0, "Do not change the panel if the choice cannot be saved");
        saved = new Memory(); saved.added = true;
        check(!new TilePrompt(saved, () -> {}).shouldShow(), "Successful addition avoids duplicate prompts");
        saved.added(false);
        check(new TilePrompt(saved, () -> {}).shouldShow(), "Cleanup allows re-adding on next launch");
        saved.suppress(true);
        check(!new TilePrompt(saved, () -> {}).shouldShow(), "Cleanup must preserve Do not show again");
        System.out.println("PASS: four choices, persistent reopening, offline failure, retry, storage failure, duplicate suppression, cleanup");
    }
}
