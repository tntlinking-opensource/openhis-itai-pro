# -*- coding: utf-8 -*-
import csv
import json
from collections import Counter
from pathlib import Path

pack = Path(
    r"D:\0.product\02.Whale\whale-doc\whale-module-chs-jilin\目录更新通知\标准化文件\F服务目录5类工程师版（2026.7.28）"
)
meta = json.loads((pack / "notice.meta.json").read_text(encoding="utf-8"))
print("META", json.dumps(meta, ensure_ascii=False, indent=2))

required = {
    "action",
    "hilist_code",
    "project_code",
    "hilist_name",
    "begin_date",
    "end_date",
    "change_fields",
    "chrgitm_lv",
    "list_type",
    "source_sheet",
    "source_row",
    "ext_json",
}
errs = []
warns = []
actions = Counter()
with (pack / "notice.lines.csv").open(encoding="utf-8-sig", newline="") as f:
    r = csv.DictReader(f)
    cols = set(r.fieldnames or [])
    missing = required - cols
    if missing:
        errs.append(f"missing cols {missing}")
    for i, row in enumerate(r, start=2):
        a = row["action"]
        actions[a] += 1
        if a not in {"ADD", "STOP", "MODIFY", "PROVINCE_MODIFY"}:
            errs.append(f"L{i} bad action {a}")
        try:
            ej = json.loads(row["ext_json"] or "{}")
            if not isinstance(ej, dict):
                errs.append(f"L{i} ext_json not object")
        except Exception as e:
            errs.append(f"L{i} ext_json parse: {e}")
        lv = row["chrgitm_lv"]
        if lv and lv not in {"1", "2", "3"}:
            errs.append(f"L{i} bad chrgitm_lv {lv!r}")
        if a == "ADD":
            if not row["project_code"]:
                errs.append(f"L{i} ADD missing project_code")
            if row["hilist_code"]:
                errs.append(f"L{i} ADD hilist_code should be empty: {row['hilist_code']}")
            if row["project_code"] and not row["project_code"].startswith("01"):
                warns.append(f"L{i} ADD project_code not 01*: {row['project_code']}")
        else:
            if not row["hilist_code"]:
                errs.append(f"L{i} {a} missing hilist_code")
            if (
                row["hilist_code"].startswith("01")
                and len(row["hilist_code"]) >= 14
                and "-" not in row["hilist_code"]
            ):
                errs.append(f"L{i} hilist looks like project_code 01*")
            if a == "STOP" and not row["end_date"]:
                warns.append(f"L{i} STOP no end_date")

print("actions", dict(actions))
print("ERROR count", len(errs))
print("WARN count", len(warns))
for e in errs[:20]:
    print("E", e)
for w in warns[:10]:
    print("W", w)

with (pack / "notice.lines.csv").open(encoding="utf-8-sig", newline="") as f:
    r = csv.DictReader(f)
    seen = set()
    for row in r:
        if row["action"] in seen:
            continue
        seen.add(row["action"])
        s = {
            k: row[k]
            for k in [
                "action",
                "hilist_code",
                "project_code",
                "hilist_name",
                "begin_date",
                "end_date",
                "change_fields",
                "chrgitm_lv",
                "list_type",
                "source_sheet",
                "source_row",
            ]
        }
        ej = row["ext_json"]
        s["ext_json"] = ej[:180] + ("..." if len(ej) > 180 else "")
        print("SAMPLE", json.dumps(s, ensure_ascii=False))
        if len(seen) == 3:
            break
