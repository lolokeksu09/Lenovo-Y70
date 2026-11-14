#!/usr/bin/env python3
"""Optional real Shizuku shell Binder integration on an isolated emulator.
Never enables Accessibility or modifies Restricted Settings.
"""
import os,re,time,subprocess,pathlib,xml.etree.ElementTree as ET
out=pathlib.Path(os.environ.get("SMOKE_OUTPUT","/tmp/legion-emulator-report"))
out.mkdir(parents=True,exist_ok=True)

def run(*args,check=True,timeout=45):
    r=subprocess.run(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
    if check and r.returncode:raise RuntimeError(repr(args)+"\n"+r.stdout[-3000:])
    return r.stdout.strip()

def adb(*args,check=True,timeout=45):return run("adb",*args,check=check,timeout=timeout)

def dump():
    adb("shell","uiautomator","dump","/sdcard/legion_probe_ui.xml")
    raw=adb("shell","cat","/sdcard/legion_probe_ui.xml")
    return ET.fromstring(raw[raw.index("<hierarchy"):])

def texts(root):
    return " | ".join(n.get("text","") for n in root.iter("node") if n.get("text"))

def tap(label,max_scroll=6):
    for attempt in range(max_scroll+1):
        root=dump()
        for node in root.iter("node"):
            if (node.get("text","")==label if label=="Система" else label in node.get("text","")):
                values=[int(x) for x in re.findall(r"\d+",node.get("bounds",""))]
                if len(values)!=4:raise AssertionError("Missing view bounds")
                adb("shell","input","tap",str((values[0]+values[2])//2),str((values[1]+values[3])//2))
                time.sleep(2);return
        if attempt<max_scroll:
            adb("shell","input","swipe","530","1700","530","560","400")
            time.sleep(1)
    raise AssertionError("No "+label+" in "+texts(root))

try:
    print("Shizuku manager install:",adb("install","-r",os.environ["SHIZUKU_APK"],timeout=100),flush=True)
    print("Grant Shizuku API on isolated emulator:",adb("shell","pm","grant",
        "dev.legion.control","moe.shizuku.manager.permission.API_V23",check=False),flush=True)
    print("Start manager UI:",adb("shell","monkey","-p","moe.shizuku.privileged.api","1",check=False),flush=True)
    time.sleep(4)
    script='p="$(pm path moe.shizuku.privileged.api | head -n 1 | sed s/^package://)"; lib="$(dirname "$p")/lib"; ls -l "$lib"/*/libshizuku.so 2>&1; for exe in "$lib"/*/libshizuku.so; do if [ -f "$exe" ]; then "$exe" > /data/local/tmp/shizuku-emulator.log 2>&1 & echo START_PID=$!; exit 0; fi; done; echo NO_LAUNCHER; exit 2'
    print("Start native shell daemon:",adb("shell","sh","-c",script,check=False),flush=True)
    time.sleep(9)
    print("Shizuku daemon log:",adb("shell","cat","/data/local/tmp/shizuku-emulator.log",check=False)[-2000:],flush=True)
    adb("shell","am","force-stop","dev.legion.control")
    print("Restart Legion:",adb("shell","am","start","-W","-n","dev.legion.control/.MainActivity"),flush=True)
    time.sleep(4)
    tap("Система",0)
    tap("Открыть центр доступа")
    final=""
    for i in range(6):
        root=dump()
        final=texts(root)
        if "Shizuku: автоматически проверено" in final:break
        if "Проверить снова" in final:tap("Проверить снова",1)
        time.sleep(2)
    (out/"shizuku_auto_probe_ui.txt").write_text(final,encoding="utf-8")
    with (out/"shizuku_auto_probe.png").open("wb") as fp:
        subprocess.run(["adb","exec-out","screencap","-p"],stdout=fp,check=True,timeout=20)
    print("Auto-probe status:",final[:3200],flush=True)
    if "Shizuku: автоматически проверено" not in final:
        raise AssertionError("Shizuku auto eligibility not confirmed on AOSP emulator")
    print("PASS: Shizuku auto eligibility returned actual system verdicts",flush=True)
finally:
    (out/"shizuku_logcat.txt").write_text(adb("logcat","-d","-v","time","-s",
        "Shizuku:V","ShizukuService:V","AndroidRuntime:E","LegionAccessCenter:V",
        check=False),encoding="utf-8")
