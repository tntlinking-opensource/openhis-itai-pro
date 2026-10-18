# -*- coding: utf-8 -*-
import csv
import json
from collections import Counter
from pathlib import Path

base = Path(r"D:\0.product\02.Whale\whale-doc\whale-module-chs-jilin\目录更新通知")
stems = ["耗材数据更新（H2024.7.1）", "诊疗目录新增(2024.7.8)"]
expected_hdr = [
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
]
report = []
for stem in stems:
    d = base / "标准化文件" / stem
    meta = json.loads((d / "notice.meta.json").read_text(encoding="utf-8"))
    with open(d / "notice.lines.csv", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        hdr = reader.fieldnames
        rows = list(reader)
    issues = []
    if list(hdr) != expected_hdr:
        issues.append(f"header mismatch: {hdr}")
    raw = (d / "notice.lines.csv").read_bytes()[:3]
    if raw != b"\xef\xbb\xbf":
        issues.append(f"no BOM: {raw!r}")
    acts = Counter(x["action"] for x in rows)
    lv_bad = [x for x in rows if x["chrgitm_lv"] and x["chrgitm_lv"] not in ("1", "2", "3")]
    if lv_bad:
        issues.append(f"bad lv count {len(lv_bad)} eg={lv_bad[0]['chrgitm_lv']}")
    bad_json = 0
    for x in rows:
        try:
            json.loads(x["ext_json"] or "{}")
        except Exception:
            bad_json += 1
    if bad_json:
        issues.append(f"bad ext_json {bad_json}")
    if meta["category"] == "DEVICE":
        empty_hilist = [x for x in rows if not x["hilist_code"]]
        if empty_hilist:
            issues.append(f"DEVICE empty hilist {len(empty_hilist)}")
    if meta["category"] == "SERVICE":
        add_bad = [
            x
            for x in rows
            if x["action"] == "ADD"
            and (x["hilist_code"] or not x["project_code"] or not str(x["project_code"]).startswith("01"))
        ]
        if add_bad:
            issues.append(f"SERVICE ADD bad {len(add_bad)}")
    no_begin = sum(1 for x in rows if not x["begin_date"])
    if no_begin:
        issues.append(f"missing begin_date {no_begin}")
    moved = list((base / "原始文件").glob(stem + ".*"))
    root_still = [p.name for p in base.glob(stem + ".*") if p.is_file()]
    report.append(
        {
            "stem": stem,
            "doc_kind": meta["doc_kind"],
            "category": meta["category"],
            "notice_date": meta["notice_date"],
            "wash_tool": meta.get("wash_tool"),
            "actions": dict(acts),
            "rows": len(rows),
            "issues": issues,
            "samples": rows[:2],
            "moved": [m.name for m in moved],
            "still_in_root": root_still,
        }
    )

out = base / "_wash_2xls_verify.json"
out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(out)
for r in report:
    print("====", r["stem"])
    print("  OK" if not r["issues"] else "  ISSUES", r["issues"])
    print(" ", r["doc_kind"], r["category"], r["actions"], "moved=", r["moved"], "root=", r["still_in_root"])
