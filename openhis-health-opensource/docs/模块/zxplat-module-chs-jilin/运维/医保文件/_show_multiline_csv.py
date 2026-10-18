# -*- coding: utf-8 -*-
"""Show the multiline quoted field examples that break Java line CSV parser."""
from pathlib import Path

STEMS = [
    "附件1：F服务目录29-33号文数据更新目录",
    "附件：F服务目录数据更新目录（2026.3.24）",
]
ROOT = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/目录更新通知/标准化文件")

for stem in STEMS:
    path = ROOT / stem / "notice.lines.csv"
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    print("====", stem, "total physical", len(lines) - 1)
    for i, line in enumerate(lines[1:], start=2):
        if line.count('"') % 2 == 1:
            print(f"-- phys {i} odd-quotes len={len(line)}")
            print(line[:200].replace("\t", "\\t"))
            if i + 1 <= len(lines):
                print(f"   next[{i+1}]:", lines[i][:200])
            print()
