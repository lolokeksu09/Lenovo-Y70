#!/usr/bin/env python3
"""Apply the reviewed mobile-network v0.1.1 delta to the unpacked v0.1.0 source.
The original Pinduoduo APK is not used or modified.
"""
from pathlib import Path

base = Path("pinduoduo_overlay_ru")
app = base / "app/src/main/java/app/pinduoduo/overlayru"

def replace(rel, old, new):
    path = base / rel
    data = path.read_text(encoding="utf-8")
    assert data.count(old) == 1, f"Unexpected source revision: {rel}: {old!r}"
    path.write_text(data.replace(old, new), encoding="utf-8")

replace("app/build.gradle", "versionCode 1\n        versionName '0.1.0'",
        "versionCode 2\n        versionName '0.1.1'")
replace("app/src/main/java/app/pinduoduo/overlayru/TranslationEngine.java",
        "import com.google.mlkit.common.model.DownloadConditions;\n", "")
replace("app/src/main/java/app/pinduoduo/overlayru/TranslationEngine.java",
        'static final String PREF_MODEL_ALLOWED = "model_allowed";',
        'static final String PREF_MODEL_ALLOWED = "model_allowed";\n    static final String PREF_WIFI_ONLY = "model_wifi_only";')
replace("app/src/main/java/app/pinduoduo/overlayru/TranslationEngine.java",
        "private boolean preparing = false;",
        "private boolean preparing = false;\n    private boolean preparingWifiOnly = false;")
replace("app/src/main/java/app/pinduoduo/overlayru/TranslationEngine.java",
        "boolean preparing() { return preparing; }",
        "boolean preparing() { return preparing; }\n    boolean wifiOnly() { return prefs().getBoolean(PREF_WIFI_ONLY, false); }\n    boolean preparingWifiOnly() { return preparingWifiOnly; }")
replace("app/src/main/java/app/pinduoduo/overlayru/TranslationEngine.java",
        "        // A user must explicitly approve the download. Even subsequent checks require Wi-Fi.\n        DownloadConditions conditions = new DownloadConditions.Builder().requireWifi().build();\n        translator.downloadModelIfNeeded(conditions)",
        "        // The initial download still requires explicit user consent.\n        // Wi-Fi is optional so mobile data can be used when the user allows it.\n        preparingWifiOnly = wifiOnly();\n        translator.downloadModelIfNeeded(ModelDownloadConditionsFactory.create(preparingWifiOnly))")
replace("app/src/main/java/app/pinduoduo/overlayru/MainActivity.java",
        "private Switch activeSwitch;", "private Switch activeSwitch, wifiOnlySwitch;")
replace("app/src/main/java/app/pinduoduo/overlayru/MainActivity.java",
        '        Button download = actionButton("2. Скачать модель китайский → русский (Wi-Fi)", false);',
        '''        LinearLayout network = panel(root);
        network.addView(text("ЗАГРУЗКА МОДЕЛИ", 12, MUTED, Typeface.BOLD));
        wifiOnlySwitch = new Switch(this);
        wifiOnlySwitch.setText("Скачивать только по Wi-Fi");
        wifiOnlySwitch.setTextColor(TEXT);
        wifiOnlySwitch.setTextSize(15);
        wifiOnlySwitch.setChecked(engine.wifiOnly());
        wifiOnlySwitch.setOnCheckedChangeListener((v, checked) -> {
            engine.prefs().edit().putBoolean(TranslationEngine.PREF_WIFI_ONLY, checked).apply();
            updateStatus();
        });
        marginTop(network, wifiOnlySwitch, 12);
        network.addView(text("Если переключатель выключен, модель можно скачать и через мобильный интернет. Модель может занимать десятки МБ; учитывай тариф. Изменение режима применяется при следующей попытке загрузки.", 12, MUTED, Typeface.NORMAL));
        spacer(root, 14);

        Button download = actionButton("2. Скачать модель китайский → русский", false);''')
replace("app/src/main/java/app/pinduoduo/overlayru/MainActivity.java",
        'else if (engine.preparing()) state = "Модель: загрузка по Wi-Fi…";',
        '''else if (engine.preparing()) state = engine.preparingWifiOnly()
                ? "Модель: загрузка (только Wi-Fi)…"
                : "Модель: загрузка (Wi-Fi или мобильная сеть)…";''')

(app/"ModelDownloadConditionsFactory.java").write_text('''package app.pinduoduo.overlayru;

import com.google.mlkit.common.model.DownloadConditions;

/** Explicitly applies the user's network preference to every model download request. */
public final class ModelDownloadConditionsFactory {
    private ModelDownloadConditionsFactory() {}

    public static DownloadConditions create(boolean wifiOnly) {
        DownloadConditions.Builder builder = new DownloadConditions.Builder();
        if (wifiOnly) builder.requireWifi();
        return builder.build();
    }
}
''', encoding="utf-8")
stub=base/"tests/stubs/com/google/mlkit/common/model/DownloadConditions.java"
stub.parent.mkdir(parents=True,exist_ok=True)
stub.write_text('''package com.google.mlkit.common.model;

/** Test-only API stub: checks the download policy without Android dependencies. */
public final class DownloadConditions {
    private final boolean wifiRequired;
    private DownloadConditions(boolean wifiRequired) { this.wifiRequired = wifiRequired; }
    public boolean isWifiRequired() { return wifiRequired; }
    public static final class Builder {
        private boolean wifiRequired;
        public Builder requireWifi() { wifiRequired = true; return this; }
        public DownloadConditions build() { return new DownloadConditions(wifiRequired); }
    }
}
''', encoding="utf-8")
(base/"tests/ModelDownloadConditionsTest.java").write_text('''import app.pinduoduo.overlayru.ModelDownloadConditionsFactory;
import com.google.mlkit.common.model.DownloadConditions;

public final class ModelDownloadConditionsTest {
    public static void main(String[] args) {
        DownloadConditions mobileAllowed = ModelDownloadConditionsFactory.create(false);
        if (mobileAllowed.isWifiRequired()) throw new AssertionError("Mobile-enabled download must not require Wi-Fi");
        DownloadConditions wifiOnly = ModelDownloadConditionsFactory.create(true);
        if (!wifiOnly.isWifiRequired()) throw new AssertionError("Wi-Fi-only option must require Wi-Fi");
        if (ModelDownloadConditionsFactory.create(false).isWifiRequired()) {
            throw new AssertionError("Changing mode must not retain stale Wi-Fi requirement");
        }
        System.out.println("PASS: 3 model download network policy checks");
    }
}
''', encoding="utf-8")
replace("scripts/test_local.sh", 'java -cp "$TEMP" TranslationPolicyTest\n',
        'java -cp "$TEMP" TranslationPolicyTest\njavac -encoding UTF-8 -d "$TEMP" tests/stubs/com/google/mlkit/common/model/DownloadConditions.java app/src/main/java/app/pinduoduo/overlayru/ModelDownloadConditionsFactory.java tests/ModelDownloadConditionsTest.java\njava -cp "$TEMP" ModelDownloadConditionsTest\n')
replace("scripts/validate_sources.py", "assert 'node.isEditable()' in policy\n",
        """assert 'node.isEditable()' in policy
engine=(a/'java/app/pinduoduo/overlayru/TranslationEngine.java').read_text()
activity=(a/'java/app/pinduoduo/overlayru/MainActivity.java').read_text()
assert 'ModelDownloadConditionsFactory.create(preparingWifiOnly)' in engine
assert 'getBoolean(PREF_WIFI_ONLY, false)' in engine
assert 'putBoolean(TranslationEngine.PREF_WIFI_ONLY, checked)' in activity
""")
replace("README.md", "**Версия прототипа: 0.1.0**","**Версия прототипа: 0.1.1**")
replace("README.md", "после загрузки языковой модели по Wi‑Fi с согласия пользователя.",
        "после загрузки языковой модели через Wi‑Fi или мобильную сеть с согласия пользователя.")
replace("README.md", '5. Нажми «Скачать модель китайский → русский (Wi-Fi)». Требуется доступ к интернету для первичной загрузки; после скачивания перевод выполняется на устройстве.',
        '5. При необходимости включи «Скачивать только по Wi-Fi» (по умолчанию выключено). Нажми «Скачать модель китайский → русский». Модель можно загрузить через мобильную сеть; после скачивания перевод выполняется на устройстве.')
print("PATCH OK: v0.1.1 mobile network + Wi-Fi only switch + focused regression test")
