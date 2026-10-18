# -*- coding: utf-8 -*-
"""仅 16：F 29–33 号文。notice_date 空、止期 2025-03，后叠。
ADD 01* 已在 07/09 → 不回写以免盖掉后续限价。
STOP 旧 hilist：在用则停；已停不动；库中没有则补插 STOPPED。
MODIFY 已停不复活；在用只合并限制字段。
"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
SRC = ROOT / "16.附件1：F服务目录29-33号文数据更新目录" / "notice.lines.csv"

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
        "last_pack": "16",
        "last_action": action,
        "last_notice_date": "",
    }


def apply_insert(rows, index, rec, stats, tag):
    k = row_key(rec["catalog_kind"], rec["project_code"], rec["hilist_code"])
    if not k[2]:
        stats["skip_no_key"] += 1
        return
    if k in index:
        stats["skip_exists_" + tag] += 1
        return
    index[k] = len(rows)
    rows.append(rec)
    stats["insert_" + tag] += 1


def lookup(index, kind, pc, hc):
    for k in (row_key(kind, pc, hc), row_key(kind, "", hc), row_key(kind, pc, "")):
        if k[2] and k in index:
            return k
    return None


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
            pc = (src.get("project_code") or "").strip()
            hc = (src.get("hilist_code") or "").strip()
            if action == "ADD":
                stats["add"] += 1
                k = lookup(index, "SERVICE_F", pc, hc)
                if k:
                    stats["add_skip_exists"] += 1
                    continue
                apply_insert(rows, index, build_rec(src, ext, action, "SERVICE_F"), stats, "add")
            elif action == "STOP":
                stats["stop"] += 1
                hit = False
                for kind in ("SERVICE_F", "SERVICE"):
                    k = lookup(index, kind, pc, hc)
                    if not k:
                        continue
                    hit = True
                    old = rows[index[k]]
                    if old.get("status") == "STOPPED":
                        stats["stop_already_" + kind] += 1
                        continue
                    rec = build_rec(src, ext, "STOP", kind)
                    rec["project_code"] = old.get("project_code") or rec["project_code"]
                    rec["hilist_code"] = old.get("hilist_code") or rec["hilist_code"]
                    if not rec["定价上限"]:
                        rec["定价上限"] = old.get("定价上限") or ""
                    rows[index[k]] = rec
                    stats["stop_active_" + kind] += 1
                if not hit:
                    apply_insert(rows, index, build_rec(src, ext, "STOP", "SERVICE_F"), stats, "stop_hist")
            elif action == "MODIFY":
                stats["modify"] += 1
                k = lookup(index, "SERVICE_F", pc, hc) or lookup(index, "SERVICE", pc, hc)
                if not k:
                    apply_insert(rows, index, build_rec(src, ext, action, "SERVICE_F"), stats, "mod_new")
                    continue
                old = rows[index[k]]
                if old.get("status") == "STOPPED":
                    stats["mod_skip_stopped"] += 1
                    continue
                rec = dict(old)
                rec["限制使用标志"] = flatten(ext.get("限制使用标志")) or rec.get("限制使用标志") or ""
                rec["限制使用范围"] = flatten(ext.get("限制使用范围")) or rec.get("限制使用范围") or ""
                note = flatten(ext.get("诊疗项目说明"))
                if note:
                    rec["诊疗项目说明"] = note
                rec["last_pack"] = "16"
                rec["last_action"] = "MODIFY"
                rec["last_notice_date"] = ""
                rows[index[k]] = rec
                stats["mod_active"] += 1
            else:
                stats["other"] += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: flatten(r.get(k, "")) for k in FIELDS})

    cat = Counter("%s:%s" % (r.get("catalog_kind"), r.get("status")) for r in rows)
    probe = next((r for r in rows if (r.get("hilist_code") or "") == "003314000060000-331400006"), None)
    report = {"stats": dict(stats), "catalog": dict(cat), "total": len(rows), "probe_stop": probe}
    (WORK / "pack16_apply_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("stats", "catalog", "total")}, ensure_ascii=False, indent=2))
    if probe:
        print("绒毛膜羊膜分离术", probe.get("status"), probe.get("end_date"), probe.get("last_pack"))


if __name__ == "__main__":
    main()
