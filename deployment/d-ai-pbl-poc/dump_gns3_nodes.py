#!/usr/bin/env python3
"""Dump GNS3 project node -> console port mapping."""
import json
import os
import sys
import glob

PROJ_DIR = "/home/ssh1032007/GNS3/projects/D-AI-PBL-PoC-Network"

paths = glob.glob(os.path.join(PROJ_DIR, "*.gns3"))
if not paths:
    print("NO_PROJECT_FILE")
    sys.exit(1)

path = paths[0]
data = json.load(open(path))

nodes = data.get("topology", {}).get("nodes", [])
print(f"project={os.path.basename(path)} nodes={len(nodes)}")
print()

rows = []
for node in nodes:
    name = node.get("name", "?")
    console = node.get("console")
    ctype = node.get("console_type", "")
    ntype = node.get("node_type", "")
    compute = node.get("compute_id", "")
    rows.append((console or 0, name, console, ctype, ntype, compute))

for console, name, c, ctype, ntype, compute in sorted(rows):
    print(f"{c if c else '-':>6}  {name:28s} type={ntype:12s} console_type={ctype}")

print()
print("--- port bindings ---")
for port in data.get("topology", {}).get("links", []):
    pass
