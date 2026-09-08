"""Print tappable/labelled nodes from a uiautomator dump, with tap centres.

Exists because driving a Compose UI from PowerShell means regexing an XML dump, and doing that
inline in the shell was costing more attempts than a five-line script.

    adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
    python tools/ui-dump.py ui.xml [substring]
"""

import re
import sys
import xml.etree.ElementTree as ET

# The labels are Devanagari; the Windows console default is cp1252 and would raise.
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

path = sys.argv[1] if len(sys.argv) > 1 else "ui.xml"
needle = sys.argv[2].lower() if len(sys.argv) > 2 else None

BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")

for node in ET.parse(path).iter("node"):
    text = node.get("text") or ""
    desc = node.get("content-desc") or ""
    label = text or desc
    clickable = node.get("clickable") == "true"
    if not label and not clickable:
        continue
    if needle and needle not in label.lower():
        continue
    m = BOUNDS.match(node.get("bounds", ""))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    tap = f"{(x1 + x2) // 2},{(y1 + y2) // 2}"
    flag = "TAP" if clickable else "   "
    print(f"{flag} {tap:>12}  {x2 - x1:>4}x{y2 - y1:<4}  {label}")
