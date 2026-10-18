# -*- coding: utf-8 -*-
"""Flatten embedded newlines inside CSV quoted fields for the 3 mismatched packs.

Keeps logical row count；rewrite in place with UTF-8 BOM；hash will change → re-import as NEW after soft-delete.
"""
from __future__ import annotations

import csv
from pathlib import Path

ROOT = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/目录更新通知/标准化文件")
STEMS = [
    "附件1：F服务目录29-33号文数据更新目录",
    "附件：F服务目录数据更新目录（2026.3.24）",
    "附件：F服务目录数据更新目录（2026.3.24） (1)",
]


def flatten(value: str | None) -> str:
    if value is None:
        return ""
    # 字段内换行改为空格，避免按行 CSV 解析器裂行
    return value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")


def rewrite(stem: str) -> None:
    path = ROOT / stem / "notice.lines.csv"
    rows = []
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        assert fieldnames
        for row in reader:
            rows.append({k: flatten(row.get(k)) for k in fieldnames})
    backup = path.with_suffix(".csv.bak_multiline")
    if not backup.exists():
        backup.write_bytes(path.read_bytes())
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        writer.writerows(rows)
    print(f"rewrote {stem}: rows={len(rows)} backup={backup.name}")


def main() -> None:
    for stem in STEMS:
        rewrite(stem)


if __name__ == "__main__":
    main()
