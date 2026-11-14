#!/usr/bin/env python3
"""Real Android-emulator UI smoke test for the *signed* Legion Control 1.8.0 Beta 10 APK.

No mocks, no privileged permission writes, no emulator-specific app code.
On failure collect screenshots, UI hierarchy and logcat for diagnosis.
"""
import os, re, sys, time, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(os.environ.get("SMOKE_OUTPUT", "/tmp/legion-emulator-report"))
ROOT.mkdir(parents=True, exist_ok=True)
APK = os.environ.get("LEGION_APK", "/tmp/legion-apk/Legion-Control-v1.8.0-beta10-release.apk")
PACKAGE = "dev.legion.control"
ACTIVITY = "dev.legion.control/.MainActivity"
PASSED = []

def command(*args, timeout=30, check=True):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError("%s returned %d: %s" %
                           (" ".join(args), result.returncode, result.stdout[-1600:]))
    return result.stdout.strip()

def adb(*args, timeout=30, check=True):
    return command("adb", *args, timeout=timeout, check=check)

def take_screenshot(label):
    path = ROOT / (label + ".png")
    with path.open("wb") as out:
        result = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                                stdout=out, stderr=subprocess.PIPE, timeout=25)
    if result.returncode:
        print("SCREENSHOT FAILED:", label, result.stderr.decode(errors="replace"))

def ui(label="current"):
    command("adb", "shell", "uiautomator", "dump", "/sdcard/legion_ui.xml", timeout=45)
    xml = adb("shell", "cat", "/sdcard/legion_ui.xml", timeout=15)
    xml = xml[xml.index("<hierarchy"):] if "<hierarchy" in xml else xml
    (ROOT / (label + ".xml")).write_text(xml, encoding="utf-8")
    return ET.fromstring(xml)

def nodes(root):
    return list(root.iter("node"))

def find(root, needle, exact=False):
    for n in nodes(root):
        text = n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")
        if (text.strip() == needle if exact else needle.lower() in text.lower()):
            return n
    return None

def tap_node(n):
    bounds = n.attrib.get("bounds", "")
    coords = list(map(int, re.findall(r"\d+", bounds)))
    if len(coords) != 4: raise RuntimeError("Node missing bounds: "+str(n.attrib))
    x, y = (coords[0]+coords[2])//2, (coords[1]+coords[3])//2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.5)

def visible_text(root):
    return " | ".join(n.attrib.get("text","") for n in nodes(root) if n.attrib.get("text"))

def swipe_up():
    adb("shell", "input", "swipe", "530", "1720", "530", "560", "420")
    time.sleep(1.2)

def tap_when_visible(label, exact=False, max_scroll=6):
    for attempt in range(max_scroll+1):
        root=ui("search_"+str(attempt))
        n=find(root,label,exact=exact)
        if n is not None:
            print("TAPPING:", label, n.attrib.get("bounds",""))
            tap_node(n)
            return
        if attempt<max_scroll:swipe_up()
    raise AssertionError("UI element not found: %s\nVisible: %s" %
                         (label, visible_text(root)[-1600:]))

def wait_text(label, seconds=22, exact=False):
    deadline=time.monotonic()+seconds
    last=None
    while time.monotonic()<deadline:
        last=ui("poll")
        if find(last,label,exact=exact):
            return last
        time.sleep(1.2)
    raise AssertionError("Text never appeared: %r; visible: %s" %
                         (label, visible_text(last)[-1700:] if last is not None else "none"))

def collect():
    take_screenshot("final")
    try:ui("final")
    except Exception as e:print("UI DUMP FAILED:", e)
    (ROOT/"logcat_legion.txt").write_text(
        adb("logcat","-d","-v","time","-s","LegionAccessCenter:V","AndroidRuntime:E","ActivityTaskManager:I",
            timeout=40,check=False),
        encoding="utf-8")
    (ROOT/"activities.txt").write_text(
        adb("shell","dumpsys","activity","activities",timeout=40,check=False),
        encoding="utf-8")

def pass_check(s):
    PASSED.append(s);print("PASS:",s,flush=True)

try:
    print("Emulator:", adb("shell","getprop","ro.build.version.release"),
          adb("shell","getprop","ro.product.model"),flush=True)
    print("Installing EXACT signed Beta 10:", APK, flush=True)
    print(adb("install","-r",APK,timeout=120),flush=True)
    package_info=adb("shell","dumpsys","package",PACKAGE,timeout=25)
    if "versionCode=19" not in package_info:
        raise AssertionError("Expected signed Beta 10 versionCode=19")
    pass_check("Signed versionCode 19 installed via adb")

    print(adb("shell","am","start","-W","-n",ACTIVITY,timeout=40),flush=True)
    wait_text("Система под контролем.",seconds=30)
    take_screenshot("main")
    pass_check("Main Activity actually launched and rendered")

    tap_when_visible("Система",exact=True,max_scroll=0)
    wait_text("Системные инструменты")
    take_screenshot("system")
    pass_check("System tab responds to click")

    tap_when_visible("Открыть центр доступа",max_scroll=5)
    wait_text("Доступ приложений",seconds=25)
    wait_text("Службы Android",seconds=30)
    take_screenshot("access_center")
    pass_check("Access Center Activity opens and enumerates services without Shizuku")

    tap_when_visible("Проверить снова",max_scroll=2)
    wait_text("Проверка №1:",seconds=30)
    take_screenshot("refresh_completed")
    pass_check("Refresh click actually produces a completed result")

    tap_when_visible("Самопроверка кнопки",max_scroll=8)
    wait_text("Нажатий обработано: 1",seconds=12)
    take_screenshot("selftest")
    pass_check("Self-test click is dispatched and reflected in UI")

    tap_when_visible("Копировать отчёт",max_scroll=4)
    time.sleep(1.2)
    current=adb("shell","dumpsys","activity","activities",timeout=35)
    if PACKAGE not in current:raise AssertionError("Main app vanished after copy")
    pass_check("Report copy action does not crash the app")

    for label in ["main","system","access_center","refresh_completed","selftest"]:
        if not (ROOT/(label+".png")).is_file():raise AssertionError("Missing screenshot: "+label)
    pass_check("Real screenshots captured")
finally:
    collect()
    (ROOT/"results.txt").write_text("\n".join("PASS "+x for x in PASSED)+"\n",encoding="utf-8")
    print("Completed checks:",len(PASSED),flush=True)
