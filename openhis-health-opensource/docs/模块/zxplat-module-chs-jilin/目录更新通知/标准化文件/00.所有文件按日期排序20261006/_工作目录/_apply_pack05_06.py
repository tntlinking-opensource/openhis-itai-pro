# -*- coding: utf-8 -*-
"""仅用于 05、06 两包：字段映射已按人工核对写死，不是通用回放引擎。"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"

FIELDS = [
    "catalog_kind", "status", "project_code", "hilist_code", "hilist_name",
    "chrgitm_lv", "list_type", "begin_date", "end_date", "计价单位", "定价上限",
    "地方编码", "限制使用标志", "限制使用范围", "诊疗项目说明", "诊疗除外内容",
    "诊疗项目内涵", "last_pack", "last_action", "last_notice_date",
]


def key_of(row):
    pc = (row.get("project_code") or "").strip()
    if pc:
        return ("P", pc)
    hc = (row.get("hilist_code") or "").strip()
    return ("H", hc)


def load_catalog():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {key_of(r): i for i, r in enumerate(rows)}
    return rows, index


def dump_catalog(rows):
    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            out = {k: r.get(k, "") for k in FIELDS}
            for k, v in list(out.items()):
                if isinstance(v, str):
                    out[k] = v.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
            w.writerow(out)


def parse_ext(raw):
    raw = (raw or "").strip()
    if not raw:
        return {}
    return json.loads(raw)


def flatten(v):
    if v is None:
        return ""
    return str(v).replace("\r\n", " ").replace("\n", " ").replace("\r", " ")


def upsert(rows, index, rec, stats, pack):
    k = key_of(rec)
    if not k[1]:
        stats["skip_no_key"] += 1
        return
    if k in index:
        old = rows[index[k]]
        rec["catalog_kind"] = old.get("catalog_kind") or rec["catalog_kind"]
        rows[index[k]] = rec
        stats["update"] += 1
    else:
        index[k] = len(rows)
        rows.append(rec)
        stats["insert"] += 1


def apply_05(rows, index):
    path = ROOT / "05.附件：1.F服务目录15号文本数据更新目录（2025.11.01）" / "notice.lines.csv"
    stats = Counter()
    add_codes = []
    stop_codes = []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            ext = parse_ext(src.get("ext_json"))
            if action == "ADD":
                # 05 新增：CSV hilist_name 空，名称在 ext.医疗服务项目名称
                name = flatten(ext.get("医疗服务项目名称") or src.get("hilist_name"))
                rec = {
                    "catalog_kind": "SERVICE_F",
                    "status": "ACTIVE",
                    "project_code": (src.get("project_code") or "").strip(),
                    "hilist_code": "",
                    "hilist_name": name,
                    "chrgitm_lv": src.get("chrgitm_lv") or "",
                    "list_type": src.get("list_type") or "201",
                    "begin_date": src.get("begin_date") or "2025-11-01",
                    "end_date": "",
                    "计价单位": flatten(ext.get("计价单位")),
                    "定价上限": flatten(ext.get("医保目录定价上限金额")),
                    "地方编码": flatten(ext.get("地方编码")),
                    "限制使用标志": flatten(ext.get("限制使用标志")),
                    "限制使用范围": flatten(ext.get("限制使用范围")),
                    "诊疗项目说明": flatten(ext.get("诊疗项目说明") or ext.get("计价说明")),
                    "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                    "诊疗项目内涵": flatten(ext.get("医疗服务项目输出") or ext.get("诊疗项目内涵")),
                    "last_pack": "05",
                    "last_action": "ADD",
                    "last_notice_date": "2025-11-01",
                }
                add_codes.append(rec["project_code"])
                upsert(rows, index, rec, stats, "05")
            elif action == "STOP":
                # 05 停用：名称在 CSV hilist_name；键是 002/001/003*-地方码
                rec = {
                    "catalog_kind": "SERVICE_F",
                    "status": "STOPPED",
                    "project_code": (src.get("project_code") or "").strip(),
                    "hilist_code": (src.get("hilist_code") or "").strip(),
                    "hilist_name": flatten(src.get("hilist_name") or ext.get("医疗服务项目名称")),
                    "chrgitm_lv": src.get("chrgitm_lv") or "",
                    "list_type": src.get("list_type") or "201",
                    "begin_date": src.get("begin_date") or "",
                    "end_date": src.get("end_date") or "2025-10-31",
                    "计价单位": flatten(ext.get("计价单位")),
                    "定价上限": flatten(ext.get("医保目录定价上限金额")),
                    "地方编码": flatten(ext.get("地方编码")),
                    "限制使用标志": flatten(ext.get("限制使用标志")),
                    "限制使用范围": flatten(ext.get("限制使用范围")),
                    "诊疗项目说明": flatten(ext.get("诊疗项目说明")),
                    "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                    "诊疗项目内涵": flatten(ext.get("诊疗项目内涵") or ext.get("医疗服务项目输出")),
                    "last_pack": "05",
                    "last_action": "STOP",
                    "last_notice_date": "2025-11-01",
                }
                stop_codes.append(rec["hilist_code"])
                upsert(rows, index, rec, stats, "05")
            else:
                stats["other_action"] += 1
    stats["add_rows"] = len(add_codes)
    stats["stop_rows"] = len(stop_codes)
    stats["add_dup"] = len(add_codes) - len(set(add_codes))
    stats["stop_dup"] = len(stop_codes) - len(set(c for c in stop_codes if c))
    stats["add_empty_name"] = 0  # filled later
    return stats, set(add_codes)


def apply_06(rows, index, add05_codes):
    path = ROOT / "06.附件：F服务目录15号文数据更新目录（2025.11.17）" / "notice.lines.csv"
    stats = Counter()
    codes = []
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            if action != "MODIFY":
                stats["other_action"] += 1
                continue
            ext = parse_ext(src.get("ext_json"))
            pc = (src.get("project_code") or "").strip()
            codes.append(pc)
            k = ("P", pc)
            name = flatten(ext.get("医疗服务项目名称") or src.get("hilist_name"))
            if k in index:
                rec = dict(rows[index[k]])
                rec["hilist_name"] = name or rec.get("hilist_name")
                rec["chrgitm_lv"] = src.get("chrgitm_lv") or rec.get("chrgitm_lv")
                rec["定价上限"] = flatten(ext.get("医保目录定价上限金额"))
                rec["计价单位"] = flatten(ext.get("计价单位")) or rec.get("计价单位")
                rec["限制使用标志"] = flatten(ext.get("限制使用标志")) or rec.get("限制使用标志")
                rec["限制使用范围"] = flatten(ext.get("限制使用范围")) or rec.get("限制使用范围")
                note = flatten(ext.get("诊疗项目说明") or ext.get("计价说明"))
                if note:
                    rec["诊疗项目说明"] = note
                outp = flatten(ext.get("医疗服务项目输出"))
                if outp:
                    rec["诊疗项目内涵"] = outp
                rec["last_pack"] = "06"
                rec["last_action"] = "MODIFY"
                rec["last_notice_date"] = "2025-11-17"
                rows[index[k]] = rec
                stats["update"] += 1
            else:
                rec = {
                    "catalog_kind": "SERVICE_F",
                    "status": "ACTIVE",
                    "project_code": pc,
                    "hilist_code": "",
                    "hilist_name": name,
                    "chrgitm_lv": src.get("chrgitm_lv") or "",
                    "list_type": src.get("list_type") or "201",
                    "begin_date": src.get("begin_date") or "2025-11-01",
                    "end_date": "",
                    "计价单位": flatten(ext.get("计价单位")),
                    "定价上限": flatten(ext.get("医保目录定价上限金额")),
                    "地方编码": flatten(ext.get("地方编码")),
                    "限制使用标志": flatten(ext.get("限制使用标志")),
                    "限制使用范围": flatten(ext.get("限制使用范围")),
                    "诊疗项目说明": flatten(ext.get("诊疗项目说明") or ext.get("计价说明")),
                    "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                    "诊疗项目内涵": flatten(ext.get("医疗服务项目输出")),
                    "last_pack": "06",
                    "last_action": "MODIFY",
                    "last_notice_date": "2025-11-17",
                }
                upsert(rows, index, rec, stats, "06")
                stats["upsert_missing_from_05"] += 1
    s06 = set(c for c in codes if c)
    stats["rows"] = len(codes)
    stats["in_05"] = len(s06 & add05_codes)
    stats["only_06"] = len(s06 - add05_codes)
    stats["only_05"] = len(add05_codes - s06)
    stats["only_06_list"] = sorted(s06 - add05_codes)
    stats["only_05_list"] = sorted(add05_codes - s06)
    return stats


def summarize(rows):
    c = Counter()
    empty_name = 0
    for r in rows:
        c[f"{r.get('catalog_kind')}:{r.get('status')}"] += 1
        if not (r.get("hilist_name") or "").strip():
            empty_name += 1
            c["empty_name"] += 1
    return c, empty_name


def main():
    rows, index = load_catalog()
    before = len(rows)
    s05, add05 = apply_05(rows, index)
    dump_catalog(rows)
    mid = len(rows)
    s06 = apply_06(rows, index, add05)
    dump_catalog(rows)
    c, empty_name = summarize(rows)
    report = WORK / "pack05_06_apply_report.json"
    out = {
        "before": before,
        "after_05": mid,
        "after_06": len(rows),
        "stats_05": dict(s05),
        "stats_06": {k: v for k, v in s06.items() if k not in ("only_06_list", "only_05_list")},
        "only_06_codes": s06["only_06_list"],
        "only_05_codes": s06["only_05_list"],
        "catalog": dict(c),
        "empty_name": empty_name,
        "sample_xray": next((r for r in rows if r.get("project_code") == "012301010010000"), None),
        "sample_stop": next((r for r in rows if r.get("hilist_code") == "002101020160000-210102016"), None),
    }
    report.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: out[k] for k in out if k not in ("sample_xray", "sample_stop", "only_05_codes")}, ensure_ascii=False, indent=2))
    print("only_05_count", len(s06["only_05_list"]))
    print("xray_price", out["sample_xray"].get("定价上限") if out["sample_xray"] else None)
    print("xray_name", out["sample_xray"].get("hilist_name") if out["sample_xray"] else None)


if __name__ == "__main__":
    main()
