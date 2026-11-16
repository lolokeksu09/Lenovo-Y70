#!/usr/bin/env python3
"""Select a visible Material3 bottom-navigation tab from UIAutomator XML.

Used only by the Android emulator smoke job. Fails closed if the expected
label cannot be found instead of faking a tap or an Activity start.
"""
import re
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 3:
    raise SystemExit("usage: select_bottom_tab.py TAB XML_FILE")

tab, xml_file = sys.argv[1:]
root = ET.parse(xml_file).getroot()
for node in root.iter("node"):
    text = node.attrib.get("text", "")
    description = node.attrib.get("content-desc", "")
    if text != tab and description != tab and not description.startswith(tab + ","):
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if match is None:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    raise SystemExit(0)

raise SystemExit("Navigation tab not found in UIAutomator tree: " + tab)
