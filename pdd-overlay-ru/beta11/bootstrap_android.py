#!/usr/bin/env python3
"""One-time bootstrap of a readable Android Studio project from the beta10 archive.

After creation, pdd-overlay-ru/android/ is canonical. Subsequent runs must
NEVER overwrite manual changes to that tree from the legacy bundle.
"""
import base64
import io
from pathlib import Path
from zipfile import ZipFile

archive = Path("pdd-overlay-ru/source_bundle.base64")
android = Path("pdd-overlay-ru/android")
patch = Path("pdd-overlay-ru/beta11")
prefix = "pinduoduo_overlay_ru/"
pkg = "app/src/main/java/app/pinduoduo/overlayru/"
release = "app/build.gradle"

if android.exists():
    gradle = android / release
    assert gradle.is_file(), "Partial canonical Android tree: do not overwrite"
    contents = gradle.read_text()
    assert "versionCode 21" in contents and "versionName '0.2.0-beta.11'" in contents
    assert (android / pkg / "CustomGlossaryEditPolicy.kt").is_file()
    print("PASS: canonical Android sources exist; no archive regeneration")
    raise SystemExit(0)

with ZipFile(io.BytesIO(base64.b64decode(archive.read_text().strip()))) as z:
    files = {}
    for name in z.namelist():
        if name.endswith("/"):
            continue
        assert name.startswith(prefix), f"Unexpected ZIP member {name}"
        rel = name[len(prefix):]
        assert rel and ".." not in Path(rel).parts, f"Path traversal {name}"
        files[rel] = z.read(name)

gradle = files[release].decode("utf-8")
assert gradle.count("versionCode 20") == 1, "Expected verified beta10 source"
assert gradle.count("versionName '0.2.0-beta.10'") == 1
assert "applicationId 'app.pinduoduo.overlayru'" in gradle
assert "ndk { abiFilters 'arm64-v8a' }" in gradle
gradle = gradle.replace("versionCode 20", "versionCode 21", 1)
gradle = gradle.replace("versionName '0.2.0-beta.10'", "versionName '0.2.0-beta.11'", 1)
files[release] = gradle.encode()

source_map = {
    "GlossaryEditorActivity.kt": pkg + "GlossaryEditorActivity.kt",
    "CustomGlossary.kt": pkg + "CustomGlossary.kt",
    "CustomGlossaryEditPolicy.kt": pkg + "CustomGlossaryEditPolicy.kt",
    "HomeActivity.kt": pkg + "HomeActivity.kt",
    "ImageOcrActivity.kt": pkg + "ImageOcrActivity.kt",
    "PddNavigation.kt": pkg + "ui/design/PddNavigation.kt",
}
for source, rel in source_map.items():
    if source == "CustomGlossaryEditPolicy.kt":
        assert rel not in files
    else:
        assert rel in files, f"Missing existing production source: {rel}"
    files[rel] = (patch / source).read_bytes()

test_path = "app/src/test/java/app/pinduoduo/overlayru/CustomGlossaryEditPolicyTest.kt"
assert test_path not in files
files[test_path] = (patch / "CustomGlossaryEditPolicyTest.kt").read_bytes()

manifest = files["app/src/main/AndroidManifest.xml"].decode()
assert 'android:allowBackup="false"' in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest
assert 'android:exported="false"' in manifest
assert "CustomGlossary.replace(context,it,source,translation)" in files[
    pkg + "GlossaryEditorActivity.kt"
].decode()
assert "Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP" in files[
    pkg + "ui/design/PddNavigation.kt"
].decode()
assert files[pkg + "HomeActivity.kt"].decode().count("PddNav.open(this, PddTab.") == 3
assert "PddNav.open(this, PddTab.SETTINGS)" in files[pkg + "ImageOcrActivity.kt"].decode()
assert not any(p.endswith(".java") for p in files if p.startswith("app/src/main/java/"))

for relative, data in files.items():
    target = android / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)

(android / ".gitignore").write_text("""/build/
/app/build/
/.gradle/
/.kоtlin/
/local.properties
/.idea/
/.cxx/
*.apk
*.aab
""".replace("к", "k"))
print(f"PASS: bootstrapped {len(files)} Android Studio source files for beta11")
