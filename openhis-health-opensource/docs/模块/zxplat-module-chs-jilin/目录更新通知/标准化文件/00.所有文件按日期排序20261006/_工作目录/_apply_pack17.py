# -*- coding: utf-8 -*-
"""仅 17：PAYMENT_MAP，与 12 同码同甲乙丙。只改已有 01* 的 chrgitm_lv，不插影子行。国家未赋码跳过。"""
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "17.医疗服务价格项目规范新医保码(2025年25号文件)" / "notice.lines.csv"

FIELDS = [
    "catalog_kind", "status", "project_code", "hilist_code", "hilist_name",
    "chrgitm_lv", "list_type", "begin_date", "end_date", "计价单位", "定价上限",
    "地方编码", "限制使用标志", "限制使用范围", "诊疗项目说明", "诊疗除外内容",
    "诊疗项目内涵", "last_pack", "last_action", "last_notice_date",
]


def flatten(v):
    if v is None:
        return ""
    return str(v).replace("\r\n", " ").replace("\n", " ").replace("\r", " ")


def main():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    by_pc = defaultdict(list)
    for i, r in enumerate(rows):
        pc = (r.get("project_code") or "").strip()
        if pc:
            by_pc[pc].append(i)

    stats = Counter()
    lv_change = Counter()
    miss = []

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            if (src.get("action") or "").strip() != "SET_PAY_CLASS":
                stats["other"] += 1
                continue
            pc = (src.get("project_code") or "").strip()
            lv = (src.get("chrgitm_lv") or "").strip()
            if (not pc) or ("未赋码" in pc) or pc == "国家未赋码":
                stats["skip_dirty"] += 1
                continue
            if not lv:
                stats["skip_bad"] += 1
                continue
            idxs = by_pc.get(pc) or []
            if not idxs:
                stats["miss"] += 1
                miss.append(pc)
                continue
            for i in idxs:
                rec = dict(rows[i])
                old = rec.get("chrgitm_lv") or ""
                rec["chrgitm_lv"] = lv
                rec["last_pack"] = "17"
                rec["last_action"] = "SET_PAY_CLASS"
                rec["last_notice_date"] = ""
                if old != lv:
                    lv_change["%s->%s" % (old or "空", lv)] += 1
                    stats["lv_changed"] += 1
                else:
                    stats["lv_same"] += 1
                rows[i] = rec
            stats["hit_codes"] += 1
            stats["hit_rows"] += len(idxs)

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    a = next((r for r in rows if r.get("project_code") == "012302010010000"), None)
    report = {
        "stats": dict(stats),
        "lv_change": dict(lv_change),
        "miss_n": len(miss),
        "miss_sample": miss[:20],
        "catalog": dict(cat),
        "total": len(rows),
        "a_us": a,
    }
    (WORK / "pack17_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in report if k != "a_us"}, ensure_ascii=False, indent=2))
    if a:
        print("A超", a.get("chrgitm_lv"), a.get("last_pack"), a.get("status"))


if __name__ == "__main__":
    main()
