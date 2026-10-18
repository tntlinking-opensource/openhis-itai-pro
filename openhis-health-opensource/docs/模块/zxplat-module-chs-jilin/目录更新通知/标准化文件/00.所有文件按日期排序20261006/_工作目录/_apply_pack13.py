# -*- coding: utf-8 -*-
"""仅 13：SERVICE_F。新增 01* 名称在 CSV；停用/变更用 hilist 旧码。"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "13.附件：F服务目录数据更新目录（2026.4.30）" / "notice.lines.csv"

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


def row_key(kind, pc, hc):
    pc = (pc or "").strip()
    hc = (hc or "").strip()
    if pc:
        return (kind, "P", pc)
    return (kind, "H", hc)


def main():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {
        row_key(r.get("catalog_kind"), r.get("project_code"), r.get("hilist_code")): i
        for i, r in enumerate(rows)
    }
    stats = Counter()
    stop_end = Counter()

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            ext = parse_ext(src.get("ext_json"))
            pc = (src.get("project_code") or "").strip()
            hc = (src.get("hilist_code") or "").strip()
            k = row_key("SERVICE_F", pc, hc)
            if not k[2]:
                stats["skip_no_key"] += 1
                continue
            name = flatten(src.get("hilist_name") or ext.get("医疗服务项目名称"))
            price = ext.get("医保目录定价上限金额")
            price_s = flatten(price) if price is not None else ""
            rec_new = {
                "catalog_kind": "SERVICE_F",
                "status": "STOPPED" if action == "STOP" else "ACTIVE",
                "project_code": pc,
                "hilist_code": hc,
                "hilist_name": name,
                "chrgitm_lv": src.get("chrgitm_lv") or "",
                "list_type": src.get("list_type") or "201",
                "begin_date": src.get("begin_date") or "",
                "end_date": src.get("end_date") or "",
                "计价单位": flatten(ext.get("计价单位")),
                "定价上限": price_s,
                "地方编码": flatten(ext.get("地方编码")),
                "限制使用标志": flatten(ext.get("限制使用标志")),
                "限制使用范围": flatten(ext.get("限制使用范围")),
                "诊疗项目说明": flatten(ext.get("诊疗项目说明") or ext.get("计价说明") or ext.get("使用说明")),
                "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                "诊疗项目内涵": flatten(ext.get("服务产出") or ext.get("诊疗项目内涵") or ext.get("医疗服务项目输出")),
                "last_pack": "13",
                "last_action": action,
                "last_notice_date": "2026-04-30",
            }
            if action == "ADD":
                stats["add"] += 1
            elif action == "STOP":
                stats["stop"] += 1
                stop_end[rec_new["end_date"] or "(empty)"] += 1
            elif action == "MODIFY":
                stats["modify"] += 1
            else:
                stats["other"] += 1
                continue
            if k in index:
                old = rows[index[k]]
                if action == "MODIFY" and old.get("status") == "STOPPED":
                    rec_new["status"] = "STOPPED"
                    rec_new["end_date"] = old.get("end_date") or rec_new["end_date"]
                    stats["modify_keep_stopped"] += 1
                if action == "STOP":
                    rec_new["status"] = "STOPPED"
                if not rec_new["定价上限"]:
                    rec_new["定价上限"] = old.get("定价上限") or ""
                rows[index[k]] = rec_new
                stats["update"] += 1
            else:
                index[k] = len(rows)
                rows.append(rec_new)
                stats["insert"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    us = next((r for r in rows if r.get("project_code") == "012302010010000"), None)
    report = {
        "stats": dict(stats),
        "stop_end": dict(stop_end),
        "catalog": dict(cat),
        "total": len(rows),
        "a_us": us,
    }
    (WORK / "pack13_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in report if k != "a_us"}, ensure_ascii=False, indent=2))
    if us:
        print("A超", us.get("hilist_name"), us.get("chrgitm_lv"), us.get("定价上限"), us.get("status"))


if __name__ == "__main__":
    main()
