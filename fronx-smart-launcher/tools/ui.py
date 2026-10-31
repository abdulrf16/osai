#!/usr/bin/env python3
"""Tiny uiautomator helper: dump the UI tree, find a node by text, tap it. Used by emulator-test.sh."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

def adb(*a, check=True):
    return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=120)

def dump():
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        r = adb("shell", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in r.stdout:
            return ET.fromstring(r.stdout[r.stdout.index("<hierarchy"):])
        time.sleep(1)
    raise SystemExit("uiautomator dump failed")

def nodes(root):
    for n in root.iter("node"):
        txt = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            yield txt, ((x1 + x2) // 2, (y1 + y2) // 2), (x1, y1, x2, y2)

def find(root, text, exact=True):
    out = []
    for t, c, b in nodes(root):
        parts = t.split("|")
        if any((p == text) if exact else (text.lower() in p.lower()) for p in parts if p):
            out.append((c, b))
    return out

if __name__ == "__main__":
    cmd, text = sys.argv[1], sys.argv[2]
    pick = sys.argv[3] if len(sys.argv) > 3 else "lowest"   # lowest|highest|first
    exact = (sys.argv[4] != "contains") if len(sys.argv) > 4 else True
    root = dump()
    hits = find(root, text, exact)
    if cmd == "textof":     # print the text of the node whose content-desc equals the argument
        for n in root.iter("node"):
            if n.get("content-desc") == text:
                print(n.get("text") or ""); sys.exit(0)
        sys.exit(1)
    if cmd == "hasabove":   # node with this text whose centre is above y (excludes the dock)
        ymax = int(sys.argv[3])
        sys.exit(0 if any(c[1] < ymax for c, b in hits) else 1)
    if cmd == "has":
        sys.exit(0 if hits else 1)
    if cmd == "tap":
        if not hits:
            print("NOT FOUND:", text); sys.exit(1)
        hits.sort(key=lambda h: h[0][1])
        c = hits[-1][0] if pick == "lowest" else hits[0][0]
        adb("shell", "input", "tap", str(c[0]), str(c[1]))
        sys.exit(0)
    if cmd == "hold":
        if not hits:
            print("NOT FOUND:", text); sys.exit(1)
        hits.sort(key=lambda h: h[0][1])
        c = hits[0][0]
        adb("shell", "input", "swipe", str(c[0]), str(c[1]), str(c[0]), str(c[1]), "900")
        sys.exit(0)
