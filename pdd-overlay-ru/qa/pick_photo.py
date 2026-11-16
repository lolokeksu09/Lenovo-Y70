#!/usr/bin/env python3
"""Pick a single seeded PNG thumbnail in Android's system media chooser.
Fail closed and preserve XML if the chooser's structure cannot be verified.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("usage: pick_photo.py OUTPUT_XML")

def adb(*args, timeout=50):
    return subprocess.check_output(("adb",)+args, stderr=subprocess.DEVNULL, timeout=timeout)

adb("shell","uiautomator","dump","/sdcard/pdd-photo-picker.xml")
raw=adb("exec-out","cat","/sdcard/pdd-photo-picker.xml")
with open(sys.argv[1],"wb") as f:
    f.write(raw)

root=ET.fromstring(raw)
nodes=list(root.iter("node"))
packages={x.get("package","") for x in nodes}
if packages=={"app.pinduoduo.overlayru"}:
    raise SystemExit("Photo picker was not launched (only our app is visible)")
candidates=[]
for node in nodes:
    if node.get("clickable")!="true":
        continue
    bounds=node.get("bounds","")
    m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",bounds)
    if not m:
        continue
    x1,y1,x2,y2=map(int,m.groups())
    w,h=x2-x1,y2-y1
    if min(w,h)<120 or max(w,h)>950 or y1<150 or y2>2200:
        continue
    rid=node.get("resource-id","").lower()
    desc=node.get("content-desc","").lower()
    descendants=list(node.iter("node"))
    image=any(x.get("class","").endswith("ImageView") for x in descendants)
    media=any(k in rid for k in ("thumbnail","item","media","photo")) or any(k in desc for k in ("photo","image","png"))
    square=abs(w-h)<0.45*max(w,h)
    if not (square or image or media):
        continue
    score=(6 if image else 0)+(4 if media else 0)+(2 if square else 0)
    candidates.append((score,y1,x1,(x1+x2)//2,(y1+y2)//2,rid,desc))
if not candidates:
    print("Visible media chooser nodes:",[(n.get("text"),n.get("resource-id"),n.get("content-desc")) for n in nodes if n.get("clickable")=="true"][:30])
    raise SystemExit("No clickable image thumbnail found")
candidates.sort(key=lambda x:(-x[0],x[1],x[2]))
best=candidates[0]
print("Picker thumbnail candidate:",best[0],best[3],best[4],best[5][:100],best[6][:100])
adb("shell","input","tap",str(best[3]),str(best[4]))
