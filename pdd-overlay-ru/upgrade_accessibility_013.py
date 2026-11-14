#!/usr/bin/env python3
"""Patch the verified v0.1.2 PDD Overlay RU source to v0.1.3.
Fix: Android AccessibilityServiceInfo.getId uses short ComponentName IDs.
"""
from pathlib import Path
base=Path("pinduoduo_overlay_ru")
main=base/"app/src/main/java/app/pinduoduo/overlayru/MainActivity.java"
s=main.read_text(encoding="utf-8")
before="""        String mine = new ComponentName(this, PddAccessibilityService.class).flattenToString();
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getId() != null && info.getId().equals(mine)) return true;
        }"""
after="""        // AccessibilityServiceInfo.getId() uses a shortened ComponentName.
        // Compare actual components, not their full/short flattened strings.
        ComponentName mine = new ComponentName(this, PddAccessibilityService.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ComponentName listed = ComponentName.unflattenFromString(info.getId());
            if (mine.equals(listed)) return true;
        }"""
assert s.count(before)==1, "Expected v0.1.2 accessibility check was not found"
main.write_text(s.replace(before,after),encoding="utf-8")

gradle=base/"app/build.gradle"
s=gradle.read_text(encoding="utf-8")
before="versionCode 3\n        versionName '0.1.2'"
after="versionCode 4\n        versionName '0.1.3'"
assert s.count(before)==1, "Expected version 0.1.2 not found"
gradle.write_text(s.replace(before,after),encoding="utf-8")

checker=base/"scripts/validate_sources.py"
s=checker.read_text(encoding="utf-8")
anchor="assert 'putBoolean(TranslationEngine.PREF_WIFI_ONLY, checked)' in activity\n"
assert s.count(anchor)==1
s=s.replace(anchor,anchor+"""assert 'ComponentName.unflattenFromString(info.getId())' in activity
assert 'mine.equals(listed)' in activity
assert 'info.getId().equals(mine)' not in activity
""")
checker.write_text(s,encoding="utf-8")
readme=base/"README.md"
s=readme.read_text(encoding="utf-8")
s=s.replace("0.1.2","0.1.3")
s+="\n## Fix v0.1.3\n\nAccessibility status checks now compare normalized ComponentName instances instead of comparing the abbreviated service ID with the full component string. No permission, package or signature changes. ML Kit download state remains a separate issue.\n"
readme.write_text(s,encoding="utf-8")
print("PASS: version 0.1.3; correct component ID normalization")
