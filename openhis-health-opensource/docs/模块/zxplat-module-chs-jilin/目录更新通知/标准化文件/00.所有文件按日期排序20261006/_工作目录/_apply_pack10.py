# -*- coding: utf-8 -*-
"""仅 10：普通服务 SERVICE。键=(catalog_kind, project|hilist)。
与 09 的 F 旧码同码时另开 SERVICE 行，不改 F。
11 与 10 正文相同，本脚本不叠 11。
"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "10.附件：服务目录数据更新目录（2026.4.7）" / "notice.lines.csv"
SRC11 = ROOT / "11.附件：服务目录数据更新目录（2026.4.7） (1)" / "notice.lines.csv"

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
    same = SRC.read_bytes() == SRC11.read_bytes()
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {
        row_key(r.get("catalog_kind"), r.get("project_code"), r.get("hilist_code")): i
        for i, r in enumerate(rows)
    }
    stats = Counter()
    f_same_code = []

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            ext = parse_ext(src.get("ext_json"))
            pc = (src.get("project_code") or "").strip()
            hc = (src.get("hilist_code") or "").strip()
            fk = ("SERVICE_F", "H" if hc else "P", hc or pc)
            if fk in {
                row_key(r.get("catalog_kind"), r.get("project_code"), r.get("hilist_code"))
                for r in rows
            }:
                f_same_code.append(hc)
            k = row_key("SERVICE", pc, hc)
            rec = {
                "catalog_kind": "SERVICE",
                "status": "ACTIVE",
                "project_code": pc,
                "hilist_code": hc,
                "hilist_name": flatten(src.get("hilist_name")),
                "chrgitm_lv": src.get("chrgitm_lv") or "",
                "list_type": src.get("list_type") or "201",
                "begin_date": src.get("begin_date") or "",
                "end_date": "",
                "计价单位": flatten(ext.get("计价单位")),
                "定价上限": flatten(ext.get("医保目录定价上限金额")),
                "地方编码": flatten(ext.get("地方编码")),
                "限制使用标志": flatten(ext.get("限制使用标志")),
                "限制使用范围": flatten(ext.get("限制使用范围")),
                "诊疗项目说明": flatten(ext.get("诊疗项目说明")),
                "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
                "诊疗项目内涵": flatten(ext.get("诊疗项目内涵")),
                "last_pack": "10",
                "last_action": "MODIFY",
                "last_notice_date": "2026-04-07",
            }
            if k in index:
                old = rows[index[k]]
                rec["status"] = old.get("status") or "ACTIVE"
                rec["end_date"] = old.get("end_date") or ""
                if not rec["定价上限"]:
                    rec["定价上限"] = old.get("定价上限") or ""
                rows[index[k]] = rec
                stats["update"] += 1
            else:
                index[k] = len(rows)
                rows.append(rec)
                stats["insert"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    report = {
        "pack10_11_bytes_identical": same,
        "stats": dict(stats),
        "also_in_SERVICE_F": len(set(f_same_code)),
        "also_in_SERVICE_F_codes": sorted(set(f_same_code)),
        "catalog": dict(cat),
        "total": len(rows),
    }
    (WORK / "pack10_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
