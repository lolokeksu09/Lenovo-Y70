#!/usr/bin/env python3
"""ADB UIAutomator control for an isolated synthetic emulator acceptance test."""
from __future__ import annotations
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

UI_PATH = "/sdcard/pdd-e2e-window.xml"

def run(*args: str, timeout: int = 35) -> bytes:
    return subprocess.check_output(args, timeout=timeout, stderr=subprocess.DEVNULL)

def snapshot():
    run("adb", "shell", "uiautomator", "dump", UI_PATH, timeout=45)
    raw = run("adb", "exec-out", "cat", UI_PATH, timeout=25)
    root = ET.fromstring(raw)
    parents = {child: parent for parent in root.iter() for child in parent}
    return root, parents, raw

def matching(root, parents, label, action):
    targets = []
    for node in root.iter("node"):
        if node.get("text") != label and node.get("content-desc") != label:
            continue
        selected = node
        if action == "field":
            while selected is not None and selected.get("class") != "android.widget.EditText":
                selected = parents.get(selected)
            if selected is None:
                continue
        elif action == "tap":
            while selected is not None and selected.get("clickable") != "true":
                selected = parents.get(selected)
            if selected is None:
                continue
        bounds = selected.get("bounds", "")
        m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        targets.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return targets

def main():
    if len(sys.argv) not in (3, 4):
        raise SystemExit("usage: ui_control.py tap|field|exists|absent LABEL [SNAPSHOT_OUT]")
    action, label = sys.argv[1:3]
    if action not in ("tap", "field", "exists", "absent"):
        raise SystemExit("Unknown action: " + action)
    for attempt in range(5):
        root, parents, raw = snapshot()
        if len(sys.argv) == 4:
            with open(sys.argv[3], "wb") as evidence:
                evidence.write(raw)
        values = matching(root, parents, label, action if action in ("tap", "field") else "exists")
        # For a non-clickable text label, exists should not depend on its ancestors.
        exists = any(n.get("text") == label or n.get("content-desc") == label for n in root.iter("node"))
        if action == "absent" and not exists:
            print("PASS absent:", label)
            return
        if action == "exists" and exists:
            print("PASS visible:", label)
            return
        if action in ("tap", "field") and values:
            x, y = values[0]
            run("adb", "shell", "input", "tap", str(x), str(y))
            print("PASS tapped:", label, x, y)
            return
        time.sleep(1.5)
    raise SystemExit(f"UI condition failed: {action} {label!r}")

if __name__ == "__main__":
    main()
