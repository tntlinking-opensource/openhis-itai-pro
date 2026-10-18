# -*- coding: utf-8 -*-
"""仅 07 包：名称在 ext.医疗服务项目名称；键=project_code；缺键 upsert。"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "07.附件：F服务目录数据更新目录（2025.12.15）" / "notice.lines.csv"

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


def parse_ext(raw):
    raw = (raw or "").strip()
    return json.loads(raw) if raw else {}


def key_of(row):
    pc = (row.get("project_code") or "").strip()
    if pc:
        return ("P", pc)
    hc = (row.get("hilist_code") or "").strip()
    return ("H", hc)


def main():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {key_of(r): i for i, r in enumerate(rows)}
    stats = Counter()
    begin_dates = Counter()
    missing_price = []
    overlap_names = []

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            if action != "MODIFY":
                stats["other"] += 1
                continue
            ext = parse_ext(src.get("ext_json"))
            pc = (src.get("project_code") or "").strip()
            hc = (src.get("hilist_code") or "").strip()
            if not pc and not hc:
                stats["skip_no_key"] += 1
                continue
            name = flatten(ext.get("医疗服务项目名称") or src.get("hilist_name"))
            price = ext.get("医保目录定价上限金额")
            price_s = flatten(price) if price is not None else ""
            begin = (src.get("begin_date") or "").strip()
            begin_dates[begin or "(empty)"] += 1
            k = key_of({"project_code": pc, "hilist_code": hc})
            if k in index:
                rec = dict(rows[index[k]])
                rec["hilist_name"] = name or rec.get("hilist_name")
                rec["chrgitm_lv"] = src.get("chrgitm_lv") or rec.get("chrgitm_lv")
                if price_s != "":
                    rec["定价上限"] = price_s
                else:
                    missing_price.append(pc or hc)
                rec["计价单位"] = flatten(ext.get("计价单位")) or rec.get("计价单位")
                rec["限制使用标志"] = flatten(ext.get("限制使用标志")) or rec.get("限制使用标志")
                rec["限制使用范围"] = flatten(ext.get("限制使用范围")) or rec.get("限制使用范围")
                note = flatten(ext.get("诊疗项目说明") or ext.get("计价说明"))
                if note:
                    rec["诊疗项目说明"] = note
                outp = flatten(ext.get("医疗服务项目输出") or ext.get("诊疗项目内涵"))
                if outp:
                    rec["诊疗项目内涵"] = outp
                rec["begin_date"] = begin or rec.get("begin_date")
                rec["last_pack"] = "07"
                rec["last_action"] = "MODIFY"
                rec["last_notice_date"] = "2025-12-15"
                rows[index[k]] = rec
                stats["update"] += 1
                overlap_names.append("%s %s (was pack %s)" % (pc, name, rec.get("last_pack")))
            else:
                rec = {
                    "catalog_kind": "SERVICE_F",
                    "status": "ACTIVE",
                    "project_code": pc,
                    "hilist_code": hc,
                    "hilist_name": name,
                    "chrgitm_lv": src.get("chrgitm_lv") or "",
                    "list_type": src.get("list_type") or "201",
                    "begin_date": begin,
                    "end_date": "",
                    "计价单位": flatten(ext.get("计价单位")),
                    "定价上限": price_s,
                    "地方编码": flatten(ext.get("地方编码")),
                    "限制使用标志": flatten(ext.get("限制使用标志")),
                    "限制使用范围": flatten(ext.get("限制使用范围")),
                    "诊疗项目说明": flatten(ext.get("诊疗项目说明") or ext.get("计价说明")),
                    "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                    "诊疗项目内涵": flatten(ext.get("医疗服务项目输出") or ext.get("诊疗项目内涵")),
                    "last_pack": "07",
                    "last_action": "MODIFY",
                    "last_notice_date": "2025-12-15",
                }
                if price_s == "":
                    missing_price.append(pc or hc)
                index[k] = len(rows)
                rows.append(rec)
                stats["insert"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            out = {k: flatten(r.get(k, "")) for k in FIELDS}
            w.writerow(out)

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    crrt = next((r for r in rows if r.get("project_code") == "013110000080000"), None)
    scale = next((r for r in rows if r.get("project_code") == "011102010010000"), None)
    doula = next((r for r in rows if r.get("project_code") == "013112020100000"), None)
    report = {
        "stats": dict(stats),
        "begin_dates": dict(begin_dates),
        "missing_price": missing_price,
        "catalog": dict(cat),
        "total": len(rows),
        "crrt": crrt,
        "scale": {"name": scale.get("hilist_name") if scale else None, "price": scale.get("定价上限") if scale else None, "begin": scale.get("begin_date") if scale else None, "pack": scale.get("last_pack") if scale else None},
        "doula": doula,
    }
    (WORK / "pack07_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("stats", "begin_dates", "missing_price", "catalog", "total", "scale")}, ensure_ascii=False, indent=2))
    print("crrt_price", crrt.get("定价上限") if crrt else None, "crrt_pack", crrt.get("last_pack") if crrt else None)
    print("doula_name", doula.get("hilist_name") if doula else None, "doula_price", repr(doula.get("定价上限") if doula else None))


if __name__ == "__main__":
    main()
