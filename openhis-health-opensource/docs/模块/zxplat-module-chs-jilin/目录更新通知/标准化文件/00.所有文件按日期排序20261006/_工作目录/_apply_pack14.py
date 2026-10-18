# -*- coding: utf-8 -*-
"""仅 14：普通服务 SERVICE。
ADD→SERVICE 01*；STOP 按 hilist 先 SERVICE 再 SERVICE_F；MODIFY 同。
"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "14.附件：服务目录数据更新目录（2026.5.19）" / "notice.lines.csv"

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
        "限制使用范围": flatten(ext.get("限制使用范围")),
        "诊疗项目说明": flatten(src.get("诊疗项目说明") or ext.get("诊疗项目说明") or ext.get("计价说明") or ext.get("使用说明")),
        "诊疗除外内容": flatten(ext.get("诊疗除外内容")),
        "诊疗项目内涵": flatten(ext.get("服务产出") or ext.get("诊疗项目内涵")),
        "last_pack": "14",
        "last_action": action,
        "last_notice_date": "2026-05-19",
    }


def apply_to(rows, index, rec, stats, tag):
    k = row_key(rec["catalog_kind"], rec["project_code"], rec["hilist_code"])
    if not k[2]:
        stats["skip_no_key"] += 1
        return False
    if k in index:
        old = rows[index[k]]
        if rec["last_action"] != "STOP" and old.get("status") == "STOPPED":
            rec["status"] = "STOPPED"
            rec["end_date"] = old.get("end_date") or rec["end_date"]
        if not rec["定价上限"]:
            rec["定价上限"] = old.get("定价上限") or ""
        rows[index[k]] = rec
        stats["update_" + tag] += 1
        return True
    index[k] = len(rows)
    rows.append(rec)
    stats["insert_" + tag] += 1
    return True


def main():
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    index = {
        row_key(r.get("catalog_kind"), r.get("project_code"), r.get("hilist_code")): i
        for i, r in enumerate(rows)
    }
    stats = Counter()

    with SRC.open("r", encoding="utf-8-sig", newline="") as f:
        for src in csv.DictReader(f):
            action = (src.get("action") or "").strip()
            ext = parse_ext(src.get("ext_json"))
            hc = (src.get("hilist_code") or "").strip()
            pc = (src.get("project_code") or "").strip()
            if action == "ADD":
                stats["add"] += 1
                apply_to(rows, index, build_rec(src, ext, action, "SERVICE"), stats, "add")
            elif action == "STOP":
                stats["stop"] += 1
                hit = False
                for kind in ("SERVICE", "SERVICE_F"):
                    k = row_key(kind, "", hc)
                    if k in index:
                        rec = build_rec(src, ext, action, kind)
                        rec["catalog_kind"] = kind
                        apply_to(rows, index, rec, stats, "stop_" + kind)
                        hit = True
                if not hit:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE"), stats, "stop_new")
            elif action == "MODIFY":
                stats["modify"] += 1
                k_s = row_key("SERVICE", pc, hc)
                k_f = row_key("SERVICE_F", pc, hc)
                if k_s in index:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE"), stats, "mod_S")
                elif k_f in index:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE_F"), stats, "mod_F")
                else:
                    apply_to(rows, index, build_rec(src, ext, action, "SERVICE"), stats, "mod_new")
            else:
                stats["other"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    eeg = next((r for r in rows if r.get("project_code") == "012401000010000"), None)
    gastro = next((r for r in rows if (r.get("hilist_code") or "").startswith("003109020050000") and r.get("catalog_kind") == "SERVICE"), None)
    report = {"stats": dict(stats), "catalog": dict(cat), "total": len(rows), "eeg": eeg, "gastro_svc": gastro}
    (WORK / "pack14_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("stats", "catalog", "total")}, ensure_ascii=False, indent=2))
    if eeg:
        print("EEG", eeg.get("catalog_kind"), eeg.get("定价上限"), eeg.get("chrgitm_lv"))
    if gastro:
        print("胃镜SERVICE", gastro.get("last_pack"), gastro.get("begin_date"))


if __name__ == "__main__":
    main()
