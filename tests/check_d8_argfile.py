"""Test pinned build change is scoped, deterministic and preserves D8 inputs."""
from pathlib import Path
import ast
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from patch_upstream_d8 import SOURCE, TARGET, transform

assert "class_files" in SOURCE
assert "os.path.relpath" in TARGET
assert "zipfile.ZipFile" in TARGET
assert "name.endswith('.class')" in TARGET
assert "shizuku_jars" in TARGET
assert "d8_inputs_jar" in TARGET
assert "--min-api', '26'" in TARGET
assert "compiled_classes_for_d8.jar" in TARGET
assert "archive.write(absolute, relative)" in TARGET
assert "os.walk(bin_dir)" in TARGET
assert transform("prefix\n" + SOURCE + "\nsuffix").count(TARGET) == 1
try:
    transform("foreign upstream source")
    raise AssertionError("Unrecognized upstream must fail")
except ValueError:
    pass
sample = "import os\nimport zipfile\nbin_dir='/tmp'\n" + TARGET.split("d8_cmd =")[0]
ast.parse(sample)
print("PASS: Windows D8 JAR packaging, all inner classes and upstream guard")
