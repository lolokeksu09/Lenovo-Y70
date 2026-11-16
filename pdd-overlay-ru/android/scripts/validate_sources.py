#!/usr/bin/env python3
import json
import pathlib
import re
import xml.etree.ElementTree as ET
r=pathlib.Path(__file__).resolve().parents[1]
a=r/'app/src/main'
for p in [a/'AndroidManifest.xml',a/'res/xml/accessibility_service.xml',a/'res/values/strings.xml']:
 ET.parse(p)
 print('XML OK', p.relative_to(r))
manifest=(a/'AndroidManifest.xml').read_text(encoding='utf-8')
assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in manifest
assert 'android.permission.READ_SMS' not in manifest
assert 'android.permission.QUERY_ALL_PACKAGES' not in manifest
assert 'android:allowBackup="false"' in manifest
assert 'com.xunmeng.pinduoduo' in manifest
assert 'moe.shizuku.privileged.api' in manifest
policy=(a/'java/app/pinduoduo/overlayru/PddAccessibilityService.kt').read_text()
assert 'PDD.contentEquals(root.packageName)' in policy
assert 'FLAG_NOT_TOUCHABLE' in policy
assert 'TYPE_ACCESSIBILITY_OVERLAY' in policy
assert 'node.isPassword' in policy
assert 'node.isEditable' in policy
engine=(a/'java/app/pinduoduo/overlayru/TranslationEngine.kt').read_text()
activity=(a/'java/app/pinduoduo/overlayru/MainActivity.kt').read_text()
settings_ui=(a/'java/app/pinduoduo/overlayru/PremiumSettingsScreen.kt').read_text()
assert 'ModelDownloadConditionsFactory.create(modelPreparingWifiOnly)' in engine
assert 'getBoolean(PREF_WIFI_ONLY, false)' in engine
assert 'putBoolean(TranslationEngine.PREF_WIFI_ONLY,checked)' in activity
# Regression: AccessibilityServiceInfo IDs may be abbreviated on Android;
# compare canonical ComponentName values instead of raw IDs.
assert 'Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES' in activity
assert 'AccessibilityServiceIds.contains(' in activity
assert 'PddAccessibilityService.isConnected()' in activity
assert 'ComponentName.unflattenFromString(info.id)' in activity
assert 'mine == listed' in activity
assert 'info.getId().equals(mine)' not in activity
glossary=json.loads((a/'assets/glossary_zh_ru.json').read_text(encoding='utf-8'))
assert len(glossary) >= 400, len(glossary)
assert glossary['查看物流']=='Отследить доставку'
assert glossary['俄罗斯']=='Россия'
assert all(isinstance(k,str) and isinstance(v,str) and k.strip() and v.strip() for k,v in glossary.items())
print('PASS: manifest/privacy/overlay checks, glossary entries:',len(glossary))

assert 'TYPE_ACCESSIBILITY_OVERLAY' in policy
assert 'FLAG_NOT_TOUCHABLE' in policy
assert 'FLAG_NOT_TOUCH_MODAL' in policy
assert 'showFloatingButton()' in policy
assert 'hideFloatingButton()' in policy
assert 'PDD.contentEquals(root.packageName)' in policy
assert 'PREF_FLOAT_BUTTON' in engine
assert 'TranslateLanguage.ENGLISH' in engine
assert 'englishTranslator.downloadModelIfNeeded' in engine
assert 'activeTranslator.translate(clean)' in engine
assert 'englishPreparing' in activity and 'ModelItem(' in settings_ui
assert 'float_button' in engine
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in manifest
print('PASS: English model and Pinduoduo-only floating button source checks')

assert 'panel.isLaidOut' in policy
assert 'panel.getLocationOnScreen(origin)' in policy
assert 'OverlayCoordinates.toLocalBounds' in policy
assert 'sourceY + rect.height()' not in policy  # do not displace labels into following rows
assert 'EnglishUiGlossary.lookup(clean)' in engine
print('PASS: layout-geometry gate and exact English UI glossary')

# v0.1.8: stable overlays, compact accessible bubble and actionable VPN hint.
assert 'activeLabels[key]' in policy
assert 'if (key !in thisFrame)' in policy
assert 'OverlayPlacement.fit(' in policy
assert 'handler.postDelayed(foregroundGuard, 1500L)' in policy
assert 'InsetDrawable(bg, dp(6f).toInt())' in policy
assert 'View.IMPORTANT_FOR_ACCESSIBILITY_YES' in policy
assert 'engine.vpnActive()' in activity
assert 'PddFrame(PddTab.SETTINGS' in activity
assert 'diagnosticsExpanded' in settings_ui
assert 'Настройки перевода' in settings_ui and 'Открыть настройки службы' in settings_ui
print('PASS: v0.1.8 stable-caption lifecycle, floating button and UI structure')

# PRO additions must not gate the existing Accessibility translation path.
assert (a/'java/app/pinduoduo/overlayru/LicenseActivity.kt').exists()
assert (a/'java/app/pinduoduo/overlayru/LicenseTokenVerifier.kt').exists()
assert (a/'java/app/pinduoduo/overlayru/LicenseManager.kt').exists()
service=(a/'java/app/pinduoduo/overlayru/PddAccessibilityService.kt').read_text()
engine=(a/'java/app/pinduoduo/overlayru/TranslationEngine.kt').read_text()
assert 'LicenseManager' not in service
assert 'LicenseManager' not in engine
assert 'LICENSE_PUBLIC_KEY_B64' in (a/'java/app/pinduoduo/overlayru/LicenseTokenVerifier.kt').read_text() or 'SHA256withRSA' in (a/'java/app/pinduoduo/overlayru/LicenseTokenVerifier.kt').read_text()
