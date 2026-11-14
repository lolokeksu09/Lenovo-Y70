#!/usr/bin/env python3
"""Apply audited v0.1.7 alignment fix to the unpacked v0.1.6 source."""
from pathlib import Path
root = Path("pinduoduo_overlay_ru")
base = root / "app/src/main/java/app/pinduoduo/overlayru"
service = base / "PddAccessibilityService.java"

def replace_file(path, old, new):
    text = path.read_text()
    assert text.count(old) == 1, f"Unexpected source revision: {path} / {old[:65]}"
    path.write_text(text.replace(old, new))

replace_file(service,
"""        ensureOverlay();
        if (overlay == null) return;
        overlay.removeAllViews();
        int[] origin = new int[2];
        overlay.getLocationOnScreen(origin);
        int screenWidth = overlay.getWidth() > 0 ? overlay.getWidth() : size.x;
        int screenHeight = overlay.getHeight() > 0 ? overlay.getHeight() : size.y;
""",
"""        ensureOverlay();
        if (overlay == null) return;
        // WindowManager has not assigned a real screen origin until first layout.
        if (!overlay.isLaidOut() || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
            overlay.post(this::scheduleRedraw);
            return;
        }
        int[] origin = new int[2];
        overlay.getLocationOnScreen(origin);
        int screenWidth = overlay.getWidth();
        int screenHeight = overlay.getHeight();
        overlay.removeAllViews();
""")
replace_file(service,
"""        int sourceX = Math.max(0, rect.left - origin[0]);
        int sourceY = Math.max(0, rect.top - origin[1]);
        if (sourceX >= maxX || sourceY >= maxY) return;
""",
"""        int[] local = OverlayCoordinates.toLocalBounds(rect.left, rect.top,
                rect.right, rect.bottom, origin[0], origin[1]);
        int sourceX = local[0];
        int sourceY = local[1];
        if (sourceX >= maxX || sourceY >= maxY ||
                sourceX + local[2] <= 0 || sourceY + local[3] <= 0) return;
""")
replace_file(service,
"""            params.y = 0;
            windows.addView(overlay, params);
""",
"""            params.y = 0;
            overlay.addOnLayoutChangeListener((view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                    scheduleRedraw();
            });
            windows.addView(overlay, params);
""")
s = service.read_text()
start = s.index('        int maxWidth = Math.min((int) (maxX * .65f),')
end = s.index('        occupied.add(placement);', start)
s = s[:start] + """        // Keep translations within the original row; never relocate into the next row.
        int maxWidth = Math.min((int) (maxX * .60f),
                Math.max(rect.width() * 2, (int) dp(128)));
        int desiredWidth = (int) Math.ceil(label.getPaint().measureText(translation) + dp(16));
        int w = Math.min(Math.max(rect.width(), desiredWidth), maxWidth);
        w = Math.max(1, Math.min(w, maxX - Math.max(0, sourceX)));
        int usableH = Math.min(maxY - Math.max(0, sourceY),
                Math.max((int) dp(25), Math.min((int) dp(48), rect.height())));
        if (usableH <= 0 || w < (int) dp(24)) return;
        label.setMaxLines(2);
        label.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(usableH, View.MeasureSpec.AT_MOST));
        int h = Math.min(usableH, Math.max((int) dp(24), label.getMeasuredHeight()));
        int x = Math.max(0, sourceX);
        int y = Math.max(0, sourceY);
        Rect placement = new Rect(x, y, x + w, y + h);
        if (overlaps(placement, occupied)) return;
""" + s[end:]
service.write_text(s)
(base / "OverlayCoordinates.java").write_text("""package app.pinduoduo.overlayru;
final class OverlayCoordinates {
    private OverlayCoordinates() {}
    static int[] toLocalBounds(int left,int top,int right,int bottom,int ox,int oy) {
        return new int[]{left-ox,top-oy,Math.max(0,right-left),Math.max(0,bottom-top)};
    }
}
""")
(base / "EnglishUiGlossary.java").write_text("""package app.pinduoduo.overlayru;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
/** Exact-match English UI terms. Unknown phrases fall back to ML Kit. */
final class EnglishUiGlossary {
    private static final Map<String,String> UI = new HashMap<>();
    static {
        UI.put("settings","Настройки");
        UI.put("linked phone","Привязанный телефон");
        UI.put("account & security","Аккаунт и безопасность");
        UI.put("account and security","Аккаунт и безопасность");
        UI.put("buy now, pay later","Купить сейчас — оплатить позже");
        UI.put("region & language","Регион и язык");
        UI.put("region and language","Регион и язык");
        UI.put("media & calls","Медиа и звонки");
        UI.put("media and calls","Медиа и звонки");
        UI.put("notifications","Уведомления");
        UI.put("font size","Размер шрифта");
        UI.put("dark mode","Тёмная тема");
        UI.put("feedback","Обратная связь");
        UI.put("partnership","Партнёрство");
        UI.put("teen mode","Подростковый режим");
        UI.put("clear cache","Очистить кеш");
        UI.put("data exchange","Обмен данными");
        UI.put("information collection","Сбор информации");
    }
    static String lookup(String source) {
        return source==null?null:UI.get(source.trim().toLowerCase(Locale.ROOT));
    }
}
""")
replace_file(base/"TranslationEngine.java",
"""        if (sourceLanguage == TranslationPolicy.CHINESE) {
            String known = Glossary.lookup(context, clean);
            if (known != null && !known.isEmpty()) return known;
        }
""",
"""        if (sourceLanguage == TranslationPolicy.CHINESE) {
            String known = Glossary.lookup(context, clean);
            if (known != null && !known.isEmpty()) return known;
        } else if (sourceLanguage == TranslationPolicy.ENGLISH) {
            String known = EnglishUiGlossary.lookup(clean);
            if (known != null) return known;
        }
""")
replace_file(base/"TranslationPolicy.java",
"""            "open", "track", "tracking", "pending", "received", "total"));""",
"""            "open", "track", "tracking", "pending", "received", "total",
            "notifications", "feedback", "partnership"));""")
replace_file(root/"app/build.gradle",
"""versionCode 7
        versionName '0.1.6'""",
"""versionCode 8
        versionName '0.1.7'""")
(root/"tests/OverlayCoordinatesTest.java").write_text("""package app.pinduoduo.overlayru;
public final class OverlayCoordinatesTest {
    private static void eq(boolean b) {if(!b) throw new AssertionError("Wrong screen inset");}
    public static void main(String[] args) {
        int[] a=OverlayCoordinates.toLocalBounds(64,118,270,146,0,80);
        eq(a[0]==64&&a[1]==38&&a[2]==206&&a[3]==28&&a[1]+80==118);
        a=OverlayCoordinates.toLocalBounds(20,40,30,45,0,0);
        eq(a[0]==20&&a[1]==40);
        System.out.println("PASS: screen-to-overlay coordinate conversion");
    }
}
""")
(root/"tests/EnglishUiGlossaryTest.java").write_text("""package app.pinduoduo.overlayru;
public final class EnglishUiGlossaryTest {
    public static void main(String[] args) {
        if (!"Медиа и звонки".equals(EnglishUiGlossary.lookup("Media & Calls")))
            throw new AssertionError("Media mistranslated");
        if (!"Подростковый режим".equals(EnglishUiGlossary.lookup("Teen Mode")))
            throw new AssertionError("Teen mode mistranslated");
        if (EnglishUiGlossary.lookup("Unknown text")!=null) throw new AssertionError("Fallback blocked");
        System.out.println("PASS: exact English settings glossary");
    }
}
""")
with (root/"scripts/test_local.sh").open("a") as fh:
    fh.write("""
javac -encoding UTF-8 -d "$TEMP" app/src/main/java/app/pinduoduo/overlayru/OverlayCoordinates.java tests/OverlayCoordinatesTest.java
java -cp "$TEMP" app.pinduoduo.overlayru.OverlayCoordinatesTest
javac -encoding UTF-8 -d "$TEMP" app/src/main/java/app/pinduoduo/overlayru/EnglishUiGlossary.java tests/EnglishUiGlossaryTest.java
java -cp "$TEMP" app.pinduoduo.overlayru.EnglishUiGlossaryTest
""")
with (root/"scripts/validate_sources.py").open("a") as fh:
    fh.write("""
assert 'overlay.isLaidOut()' in policy
assert 'OverlayCoordinates.toLocalBounds' in policy
assert 'sourceY + rect.height()' not in policy
assert 'EnglishUiGlossary.lookup(clean)' in engine
print('PASS: window origin and English glossary checks')
""")
with (root/"README.md").open("a") as fh:
    fh.write("""
## v0.1.7 Overlay alignment
Waits for actual accessibility overlay layout before translating screen coordinates
into local view coordinates. Prevents moving Russian labels to nearby settings rows.
Includes small exact English UI glossary. The official Pinduoduo APK is unchanged.
""")
print("PATCH PASS: PDD Overlay RU v0.1.7")
