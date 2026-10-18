# -*- coding: utf-8 -*-
"""仅 15：F 工程师版。ADD→SERVICE_F；STOP 按 hilist 或 01* project 停 F 并顺带 SERVICE。"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "15.F服务目录5类工程师版（2026.7.28）" / "notice.lines.csv"

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


def build_rec(src, ext, action, kind):
    pc = (src.get("project_code") or "").strip()
    hc = (src.get("hilist_code") or "").strip()
    price = ext.get("医保目录定价上限金额")
    return {
        "catalog_kind": kind,
        "status": "STOPPED" if action == "STOP" else "ACTIVE",
        "project_code": pc,
        "hilist_code": hc,
        "hilist_name": flatten(src.get("hilist_name")),
        "chrgitm_lv": src.get("chrgitm_lv") or "",
        "list_type": src.get("list_type") or "201",
        "begin_date": src.get("begin_date") or "",
        "end_date": src.get("end_date") or "",
        "计价单位": flatten(ext.get("计价单位")),
        "定价上限": flatten(price) if price is not None else "",
        "地方编码": flatten(ext.get("地方编码")),
        "限制使用标志": flatten(ext.get("限制使用标志")),
        "限制使用范围": flatten(ext.get("限制使用范围") or ext.get("项目说明")),
        "诊疗项目说明": flatten(ext.get("诊疗项目说明") or ext.get("计价说明") or ext.get("使用说明") or ext.get("项目说明")),
        "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
        "诊疗项目内涵": flatten(ext.get("服务产出") or ext.get("诊疗项目内涵") or ext.get("医疗服务项目输出")),
        "last_pack": "15",
        "last_action": action,
        "last_notice_date": "2026-07-28",
    }


def apply_to(rows, index, rec, stats, tag):
    k = row_key(rec["catalog_kind"], rec["project_code"], rec["hilist_code"])
    if not k[2]:
        stats["skip_no_key"] += 1
        return
    if k in index:
        old = rows[index[k]]
        if rec["last_action"] != "STOP" and old.get("status") == "STOPPED":
            rec["status"] = "STOPPED"
            rec["end_date"] = old.get("end_date") or rec["end_date"]
        if not rec["定价上限"]:
            rec["定价上限"] = old.get("定价上限") or ""
        rows[index[k]] = rec
        stats["update_" + tag] += 1
    else:
        index[k] = len(rows)
        rows.append(rec)
        stats["insert_" + tag] += 1


def stop_all(rows, index, src, ext, stats):
    pc = (src.get("project_code") or "").strip()
    hc = (src.get("hilist_code") or "").strip()
    hit = False
    for kind in ("SERVICE_F", "SERVICE"):
        for k in (row_key(kind, pc, hc), row_key(kind, "", hc), row_key(kind, pc, "")):
            if k[2] and k in index:
                rec = build_rec(src, ext, "STOP", kind)
                rec["catalog_kind"] = kind
                rec["project_code"] = rows[index[k]].get("project_code") or rec["project_code"]
                rec["hilist_code"] = rows[index[k]].get("hilist_code") or rec["hilist_code"]
                apply_to(rows, index, rec, stats, "stop_" + kind)
                hit = True
    if not hit:
        apply_to(rows, index, build_rec(src, ext, "STOP", "SERVICE_F"), stats, "stop_new")


def main():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {
        row_key(r.get("catalog_kind"), r.get("project_code"), r.get("hilist_code")): i
        for i, r in enumerate(rows)
    }
    stats = Counter()
    add_t = 0

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            ext = parse_ext(src.get("ext_json"))
            pc = (src.get("project_code") or "").strip()
            hc = (src.get("hilist_code") or "").strip()
            if action == "ADD":
                stats["add"] += 1
                if pc.endswith("T"):
                    add_t += 1
                apply_to(rows, index, build_rec(src, ext, action, "SERVICE_F"), stats, "add")
            elif action == "STOP":
                stats["stop"] += 1
                stop_all(rows, index, src, ext, stats)
            elif action == "MODIFY":
                stats["modify"] += 1
                k_f = row_key("SERVICE_F", pc, hc)
                k_s = row_key("SERVICE", pc, hc)
                if k_f in index:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE_F"), stats, "mod_F")
                elif k_s in index:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE"), stats, "mod_S")
                else:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE_F"), stats, "mod_new")
            else:
                stats["other"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    ear = next((r for r in rows if r.get("project_code") == "012404000010000"), None)
    stats["add_T_suffix"] = add_t
    report = {"stats": dict(stats), "catalog": dict(cat), "total": len(rows), "ear": ear}
    (WORK / "pack15_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("stats", "catalog", "total")}, ensure_ascii=False, indent=2))
    if ear:
        print("耳内镜", ear.get("定价上限"), ear.get("chrgitm_lv"), ear.get("status"))


if __name__ == "__main__":
    main()
