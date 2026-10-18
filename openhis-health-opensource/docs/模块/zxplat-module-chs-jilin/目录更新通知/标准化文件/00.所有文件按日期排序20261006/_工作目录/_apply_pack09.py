# -*- coding: utf-8 -*-
"""仅 09 包：同一文件两套键。
- 多数：hilist_code 旧码，名称在 CSV
- 少数：project_code 01*，名称也在 CSV（与 05/06/07 名称在 ext 不同）
已 STOPPED 的键只更新字段、不复活。
"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "09.附件：F服务目录数据更新目录（2026.3.24）" / "notice.lines.csv"

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


def key_of(pc, hc):
    pc = (pc or "").strip()
    hc = (hc or "").strip()
    if pc:
        return ("P", pc)
    return ("H", hc)


def main():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {key_of(r.get("project_code"), r.get("hilist_code")): i for i, r in enumerate(rows)}
    stats = Counter()
    key_kind = Counter()
    stopped_hits = []

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            if action != "MODIFY":
                stats["other"] += 1
                continue
            ext = parse_ext(src.get("ext_json"))
            pc = (src.get("project_code") or "").strip()
            hc = (src.get("hilist_code") or "").strip()
            k = key_of(pc, hc)
            if not k[1]:
                stats["skip_no_key"] += 1
                continue
            if pc:
                key_kind["project_01"] += 1
            elif hc.startswith("L"):
                key_kind["hilist_L"] += 1
            else:
                key_kind["hilist_old"] += 1
            name = flatten(src.get("hilist_name") or ext.get("医疗服务项目名称"))
            price = ext.get("医保目录定价上限金额")
            price_s = flatten(price) if price is not None else ""
            begin = (src.get("begin_date") or "").strip()
            patch = {
                "hilist_name": name,
                "chrgitm_lv": src.get("chrgitm_lv") or "",
                "list_type": src.get("list_type") or "201",
                "begin_date": begin,
                "计价单位": flatten(ext.get("计价单位")),
                "限制使用标志": flatten(ext.get("限制使用标志")),
                "限制使用范围": flatten(ext.get("限制使用范围")),
                "诊疗项目说明": flatten(ext.get("诊疗项目说明") or ext.get("计价说明")),
                "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                "诊疗项目内涵": flatten(ext.get("诊疗项目内涵") or ext.get("医疗服务项目输出")),
                "last_pack": "09",
                "last_action": "MODIFY",
                "last_notice_date": "2026-03-24",
            }
            if price_s != "":
                patch["定价上限"] = price_s
            if k in index:
                rec = dict(rows[index[k]])
                old_status = rec.get("status")
                rec.update(patch)
                rec["project_code"] = pc or rec.get("project_code") or ""
                rec["hilist_code"] = hc or rec.get("hilist_code") or ""
                if old_status == "STOPPED":
                    rec["status"] = "STOPPED"
                    rec["end_date"] = rec.get("end_date") or ""
                    stats["update_keep_stopped"] += 1
                    stopped_hits.append(k[1])
                else:
                    rec["status"] = rec.get("status") or "ACTIVE"
                    stats["update"] += 1
                rows[index[k]] = rec
            else:
                rec = {
                    "catalog_kind": "SERVICE_F",
                    "status": "ACTIVE",
                    "project_code": pc,
                    "hilist_code": hc,
                    "end_date": "",
                    "地方编码": flatten(ext.get("地方编码")),
                    "定价上限": price_s,
                }
                rec.update(patch)
                index[k] = len(rows)
                rows.append(rec)
                stats["insert"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    scale = next((r for r in rows if r.get("project_code") == "011102010010000"), None)
    tb = next((r for r in rows if (r.get("hilist_code") or "").startswith("002505020100000")), None)
    report = {
        "stats": dict(stats),
        "key_kind": dict(key_kind),
        "stopped_hit_n": len(stopped_hits),
        "stopped_hit_sample": stopped_hits[:15],
        "catalog": dict(cat),
        "total": len(rows),
        "scale": scale,
        "tb": tb,
    }
    (WORK / "pack09_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    slim = {k: report[k] for k in ("stats", "key_kind", "stopped_hit_n", "stopped_hit_sample", "catalog", "total")}
    print(json.dumps(slim, ensure_ascii=False, indent=2))
    if scale:
        print("scale", scale.get("hilist_name"), scale.get("定价上限"), scale.get("last_pack"))
    if tb:
        print("tb", tb.get("hilist_name"), tb.get("定价上限"), tb.get("last_pack"))


if __name__ == "__main__":
    main()
