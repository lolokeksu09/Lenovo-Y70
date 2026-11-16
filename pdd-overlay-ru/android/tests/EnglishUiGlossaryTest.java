package app.pinduoduo.overlayru;
public final class EnglishUiGlossaryTest {
    private static void expect(String in, String out) {
        if (!out.equals(EnglishUiGlossary.lookup(in))) throw new AssertionError(in);
    }
    public static void main(String[] args) {
        expect("Settings", "Настройки");
        expect("Media & Calls", "Медиа и звонки");
        expect("Teen Mode", "Подростковый режим");
        expect("  Notifications ", "Уведомления");
        expect("Region & Language", "Регион и язык");
        if (EnglishUiGlossary.lookup("Unsupported label") != null) throw new AssertionError("fallback must remain available");
        System.out.println("PASS: known English labels and unknown fallback");
    }
}
