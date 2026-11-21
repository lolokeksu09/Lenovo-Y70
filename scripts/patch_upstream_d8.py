"""Adapt pinned upstream build_apk.py to Windows D8 length limits.

No change to Java compilation, signing key or app behavior. Package compiled
.class files as a single JAR input for D8 rather than hundreds of absolute
paths. Refuse to patch unknown upstream versions.
"""
from pathlib import Path
import sys

SOURCE = '''class_pkg_dir = os.path.join(bin_dir, 'com', 'moto', 'resolutionmaster')
class_files = [os.path.join(class_pkg_dir, f) for f in os.listdir(class_pkg_dir) if f.endswith('.class')]
d8_cmd = [java, '-cp', r8, 'com.android.tools.r8.D8', '--min-api', '26', '--lib', android_jar, '--output', bin_dir] + class_files + shizuku_jars'''

TARGET = '''# Input archive avoids Windows CreateProcess command-line length limits.
# Include all generated inner classes and Android R classes, not just the
# app's direct package; D8 accepts a JAR just like individual class files.
d8_inputs_jar = os.path.join(bin_dir, 'compiled_classes_for_d8.jar')
with zipfile.ZipFile(d8_inputs_jar, 'w', zipfile.ZIP_DEFLATED) as archive:
    for classes_root, _directories, names in os.walk(bin_dir):
        for name in names:
            if not name.endswith('.class'):
                continue
            absolute = os.path.join(classes_root, name)
            relative = os.path.relpath(absolute, bin_dir).replace(os.sep, '/')
            archive.write(absolute, relative)
d8_cmd = [java, '-cp', r8, 'com.android.tools.r8.D8', '--min-api', '26', '--lib', android_jar, '--output', bin_dir, d8_inputs_jar] + shizuku_jars'''


def transform(source: str) -> str:
    if source.count(SOURCE) != 1:
        raise ValueError("Pinned upstream D8 input code changed; refusing blind patch")
    return source.replace(SOURCE, TARGET)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_upstream_d8.py <upstream/build_apk.py>")
    target = Path(sys.argv[1])
    original = target.read_text(encoding="utf-8")
    target.write_text(transform(original), encoding="utf-8")
    print("PASS: D8 class inputs packed to one JAR instead of Windows-long argv")


if __name__ == "__main__":
    main()
