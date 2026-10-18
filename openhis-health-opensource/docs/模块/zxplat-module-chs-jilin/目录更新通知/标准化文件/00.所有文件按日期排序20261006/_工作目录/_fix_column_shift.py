# -*- coding: utf-8 -*-
"""把工作目录里串列的说明/除外/内涵/限制范围按各包 notice.lines.csv 重写。"""
import csv
import json
import re
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
WORK = Path(__file__).resolve().parent
CATALOG = WORK / "service_catalog_working.csv"
PAYMENT = {12, 17, 18}

FIELDS = [
    "catalog_kind", "status", "project_code", "hilist_code", "hilist_name",
    "chrgitm_lv", "list_type", "begin_date", "end_date", "计价单位", "定价上限",
    "地方编码", "限制使用标志", "限制使用范围", "诊疗项目说明", "诊疗除外内容",
    "诊疗项目内涵", "last_pack", "last_action", "last_notice_date",
]
NOTE_KEYS = ("诊疗项目说明", "计价说明", "使用说明")
INN_KEYS = ("诊疗项目内涵", "服务产出", "医疗服务项目输出", "价格构成")


def flat(v):
    if v is None:
        return ""
    return str(v).replace("\r\n", " ").replace("\n", " ").replace("\r", " ").strip()


def load_sources():
    by_pc = {}
    by_hc = {}
    exact = {}
    for d in BASE.iterdir():
        m = re.match(r"^(\d+)\.", d.name)
        if not m:
            continue
        num = int(m.group(1))
        if num in PAYMENT:
            continue
        path = d / "notice.lines.csv"
        if not path.exists():
            continue
        meta_path = d / "notice.meta.json"
        notice = ""
        if meta_path.exists():
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            notice = (meta.get("notice_date") or meta.get("effective_date") or "")[:10]
        with path.open("r", encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                try:
                    ext = json.loads(row.get("ext_json") or "{}")
                except json.JSONDecodeError:
                    continue
                pc = (row.get("project_code") or "").strip()
                hc = (row.get("hilist_code") or "").strip()
                action = (row.get("action") or "").strip()
                item = (num, action, notice, ext)
                exact[(num, pc, hc)] = item
                if pc:
                    by_pc.setdefault(pc, []).append(item)
                if hc:
                    by_hc.setdefault(hc, []).append(item)
    return exact, by_pc, by_hc


def pick(exact, by_pc, by_hc, pc, hc, pack):
    if pack is not None and (pack, pc, hc) in exact:
        return exact[(pack, pc, hc)]
    pool = by_pc.get(pc, []) if pc else by_hc.get(hc, [])
    if pack is not None:
        pool = [it for it in pool if it[0] <= pack]
    if not pool:
        pool = by_pc.get(pc, []) if pc else by_hc.get(hc, [])
    if not pool:
        return None
    return max(pool, key=lambda it: it[0])


def canon(ext):
    scope = flat(ext.get("限制使用范围")) if "限制使用范围" in ext else None
    if any(k in ext for k in NOTE_KEYS):
        note = flat(ext.get("诊疗项目说明") or ext.get("计价说明") or ext.get("使用说明"))
    else:
        note = None
    excl = flat(ext.get("诊疗除外内容")) if "诊疗除外内容" in ext else None
    if any(k in ext for k in INN_KEYS):
        outp = flat(ext.get("服务产出") or ext.get("医疗服务项目输出"))
        comp = flat(ext.get("价格构成"))
        inn0 = flat(ext.get("诊疗项目内涵"))
        if outp or comp:
            parts = []
            if outp:
                parts.append(outp)
            if comp and comp not in outp:
                parts.append(comp)
            inn = " ".join(parts)
        else:
            inn = inn0
    else:
        inn = None
    if scope and scope.startswith("按实际每本项目"):
        scope = scope[len("按实际每"):]
    return scope, note, excl, inn


def main():
    exact, by_pc, by_hc = load_sources()
    with CATALOG.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    changed = 0
    tail_fixed = 0
    for rec in rows:
        pack_s = (rec.get("last_pack") or "").strip()
        pack = int(pack_s) if pack_s.isdigit() else None
        pc = (rec.get("project_code") or "").strip()
        hc = (rec.get("hilist_code") or "").strip()
        item = pick(exact, by_pc, by_hc, pc, hc, pack)
        if item is None:
            continue
        src_pack, action, notice, ext = item
        scope, note, excl, inn = canon(ext)
        touched = False
        for name, val in (
            ("限制使用范围", scope),
            ("诊疗项目说明", note),
            ("诊疗除外内容", excl),
            ("诊疗项目内涵", inn),
        ):
            if val is None:
                continue
            if flat(rec.get(name)) != val:
                rec[name] = val
                touched = True
        # 左移残留：源里没有「限制使用范围」，工作目录里的范围其实是说明
        if scope is None and note and flat(rec.get("限制使用范围")) == note:
            rec["限制使用范围"] = ""
            touched = True
        # 价格构成曾被写进说明；内涵已收回后，说明里的同一句删掉
        comp = flat(ext.get("价格构成"))
        if note is None and comp and flat(rec.get("诊疗项目说明")) == comp and flat(rec.get("诊疗项目内涵")) == comp:
            rec["诊疗项目说明"] = ""
            touched = True
        if pack is None:
            rec["last_pack"] = f"{src_pack:02d}"
            rec["last_action"] = action or rec.get("last_action") or ""
            if notice:
                rec["last_notice_date"] = notice
            tail_fixed += 1
            touched = True
        if touched:
            changed += 1

    with CATALOG.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        w.writeheader()
        for rec in rows:
            w.writerow({k: flat(rec.get(k, "")) for k in FIELDS})
    print("changed", changed, "tail_fixed", tail_fixed, "rows", len(rows))


if __name__ == "__main__":
    main()
