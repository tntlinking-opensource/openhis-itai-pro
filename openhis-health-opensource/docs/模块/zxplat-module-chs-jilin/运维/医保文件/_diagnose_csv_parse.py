# -*- coding: utf-8 -*-
"""Diagnose CSV vs Java-style row count for mismatched packs."""
from __future__ import annotations

import csv
import re
from pathlib import Path

ROOT = Path(r"D:/0.product/02.Whale/whale-doc/whale-module-chs-jilin/目录更新通知/标准化文件")
STEMS = [
    "附件1：F服务目录29-33号文数据更新目录",
    "附件：F服务目录数据更新目录（2026.3.24）",
    "附件：F服务目录数据更新目录（2026.3.24） (1)",
]


def split_csv_line_java_like(line: str) -> list[str]:
    """Rough mirror of CanonicalPackParser.splitCsvLine for diagnostics."""
    result = []
    cur = []
    in_quotes = False
    i = 0
    while i < len(line):
        ch = line[i]
        if in_quotes:
            if ch == '"':
                if i + 1 < len(line) and line[i + 1] == '"':
                    cur.append('"')
                    i += 2
                    continue
                in_quotes = False
                i += 1
                continue
            cur.append(ch)
            i += 1
            continue
        if ch == '"':
            in_quotes = True
            i += 1
            continue
        if ch == ",":
            result.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(ch)
        i += 1
    result.append("".join(cur))
    return result


def count_java_style(path: Path) -> dict:
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    if text.startswith("\ufeff"):
        text = text[1:]
    # Java BufferedReader.readLine splits on \n / \r\n
    physical = text.splitlines()
    if not physical:
        return {"error": "empty"}
    headers = split_csv_line_java_like(physical[0])
    col = {h.strip().lower(): i for i, h in enumerate(headers)}
    action_idx = col.get("action")
    parsed = 0
    blank_action = 0
    blank_line = 0
    bad_lines = []
    open_quote_rows = []
    for phys_i, line in enumerate(physical[1:], start=2):
        if not line.strip():
            blank_line += 1
            continue
        # detect unclosed quotes on this physical line
        # (Java reads line-by-line; quoted newlines break fields)
        q = line.count('"')
        if q % 2 == 1:
            open_quote_rows.append(phys_i)
        cols = split_csv_line_java_like(line)
        action = None
        if action_idx is not None and action_idx < len(cols):
            action = cols[action_idx]
        if action is None or not str(action).strip():
            blank_action += 1
            continue
        parsed += 1
    # also python csv count
    py_n = 0
    py_blank = 0
    with path.open(encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            a = (row.get("action") or "").strip()
            if not a:
                py_blank += 1
                continue
            py_n += 1
    return {
        "physical_data_lines": len(physical) - 1,
        "java_style_parsed": parsed,
        "java_blank_action": blank_action,
        "java_blank_line": blank_line,
        "odd_quote_physical_rows": open_quote_rows[:20],
        "odd_quote_count": len(open_quote_rows),
        "python_csv_parsed": py_n,
        "python_blank_action": py_blank,
        "file_bytes": path.stat().st_size,
    }


def main() -> None:
    for stem in STEMS:
        p = ROOT / stem / "notice.lines.csv"
        print("====", stem)
        if not p.exists():
            print("  MISSING")
            continue
        r = count_java_style(p)
        for k, v in r.items():
            print(f"  {k}: {v}")


if __name__ == "__main__":
    main()
