# -*- coding: utf-8 -*-
"""全量校验 目录更新通知/标准化文件/ 下的标准落地包（对照方案 §6.0 / §6.0.3a）。

用法（在任意 cwd）：
  python audit_canonical_packs.py
  python audit_canonical_packs.py --json-out audit_report.json

退出码：0=无 ERROR；1=存在 ERROR（WARNING 不导致非 0）。
"""
from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STD = ROOT / "标准化文件"
ARCH = ROOT / "原始文件"
UNCONV = ARCH / "_无法转换"

REQUIRED_COLS = {
    "action",
    "hilist_code",
    "project_code",
    "hilist_name",
    "begin_date",
    "end_date",
    "change_fields",
    "chrgitm_lv",
    "list_type",
    "source_sheet",
    "source_row",
    "ext_json",
}
META_REQUIRED = {"doc_kind", "category", "source_filename", "wash_tool"}
DOC_KINDS = {"CATALOG_DELTA", "PAYMENT_MAP"}
CATEGORIES = {
    "DRUG",
    "SERVICE",
    "SERVICE_F",
    "DEVICE",
    "FORMULA",
    "HERBAL",
    "PREP",
    "OTHER",
}
CATALOG_ACTIONS = {"ADD", "STOP", "MODIFY", "PROVINCE_MODIFY"}
PAYMENT_ACTIONS = {"SET_PAY_CLASS"}
ALL_ACTIONS = CATALOG_ACTIONS | PAYMENT_ACTIONS
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
SRC_EXTS = {".xlsx", ".xls", ".et"}


def add_issue(issues: list, level: str, code: str, msg: str, count: int = 1) -> None:
    issues.append({"level": level, "code": code, "msg": msg, "count": count})


def norm_stem(s: str) -> str:
    """对齐尾部空格/下划线（如 F17 原件名 ...）_      ）。"""
    return (s or "").rstrip(" _\t")


def list_archived_stems() -> set[str]:
    stems: set[str] = set()
    if not ARCH.exists():
        return stems
    for p in ARCH.iterdir():
        if p.name.startswith(".") or p.name == "_无法转换":
            continue
        if p.is_file() and p.suffix.lower() in SRC_EXTS:
            stems.add(p.stem)
    return stems


def list_unconverted() -> list[str]:
    if not UNCONV.exists():
        return []
    out: list[str] = []
    for p in UNCONV.rglob("*"):
        if p.is_file() and p.name != "reason.txt":
            out.append(str(p.relative_to(ARCH)))
    return sorted(out)


def list_root_pending() -> list[str]:
    return sorted(
        p.name
        for p in ROOT.iterdir()
        if p.is_file() and p.suffix.lower() in SRC_EXTS
    )


def audit_pack(pack_dir: Path) -> dict:
    issues: list[dict] = []
    stem = pack_dir.name
    meta_path = pack_dir / "notice.meta.json"
    csv_path = pack_dir / "notice.lines.csv"

    extras = sorted(
        p.name
        for p in pack_dir.iterdir()
        if p.is_file() and p.name not in {"notice.meta.json", "notice.lines.csv"}
    )
    if extras:
        add_issue(issues, "WARNING", "EXTRA_FILES", f"标准包内多余文件: {extras}")

    if not meta_path.exists():
        add_issue(issues, "ERROR", "META_MISSING", "缺少 notice.meta.json")
        return _pack_result(stem, None, 0, {}, issues)
    if not csv_path.exists():
        add_issue(issues, "ERROR", "CSV_MISSING", "缺少 notice.lines.csv")
        return _pack_result(stem, None, 0, {}, issues)

    try:
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
    except Exception as e:
        add_issue(issues, "ERROR", "META_JSON", f"meta 无法解析: {e}")
        return _pack_result(stem, None, 0, {}, issues)

    if not isinstance(meta, dict):
        add_issue(issues, "ERROR", "META_TYPE", "meta 必须是 JSON 对象")
        return _pack_result(stem, None, 0, {}, issues)

    for k in META_REQUIRED:
        if k not in meta or meta[k] in (None, ""):
            add_issue(issues, "ERROR", "META_FIELD", f"meta 缺必填字段: {k}")

    doc_kind = meta.get("doc_kind")
    category = meta.get("category")
    if doc_kind not in DOC_KINDS:
        add_issue(issues, "ERROR", "DOC_KIND", f"非法 doc_kind={doc_kind!r}")
    if category not in CATEGORIES:
        add_issue(issues, "ERROR", "CATEGORY", f"非法 category={category!r}")
    if meta.get("wash_tool") != "cursor-ai":
        add_issue(
            issues,
            "WARNING",
            "WASH_TOOL",
            f"wash_tool={meta.get('wash_tool')!r}（约定多为 cursor-ai）",
        )
    nd = meta.get("notice_date")
    ed = meta.get("effective_date")
    if nd in (None, "") and ed in (None, ""):
        add_issue(
            issues,
            "ERROR",
            "META_NO_BUSINESS_DATE",
            "notice_date 与 effective_date 均为空（禁止应用）",
        )
    for dk in ("notice_date", "effective_date"):
        v = meta.get(dk)
        if v not in (None, "") and not (isinstance(v, str) and DATE_RE.match(v)):
            add_issue(issues, "ERROR", "META_DATE", f"{dk} 非 yyyy-MM-dd: {v!r}")

    raw = csv_path.read_bytes()
    if not raw.startswith(b"\xef\xbb\xbf"):
        add_issue(issues, "WARNING", "NO_BOM", "CSV 建议带 UTF-8 BOM")

    try:
        with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            headers = reader.fieldnames or []
            rows = list(reader)
    except Exception as e:
        add_issue(issues, "ERROR", "CSV_READ", f"CSV 无法读取: {e}")
        return _pack_result(stem, meta, 0, {}, issues)

    hdr_set = set(headers)
    missing = REQUIRED_COLS - hdr_set
    unknown = hdr_set - REQUIRED_COLS
    if missing:
        add_issue(issues, "ERROR", "HDR_MISSING", f"缺列: {sorted(missing)}")
    if unknown:
        add_issue(issues, "ERROR", "HDR_UNKNOWN", f"多余列: {sorted(unknown)}")

    acts: Counter[str] = Counter()
    bad_action = 0
    bad_lv = 0
    bad_ext = 0
    bad_date = 0
    hilist_01 = 0
    dirty_code = 0
    name_in_ext_only = 0
    pay_not_set = 0
    catalog_has_set = 0
    svc_add_bad = 0
    set_pay_bad = 0
    need_hilist_empty = 0
    stop_no_end = 0
    empty_rows = 0

    for i, row in enumerate(rows, start=2):
        action = (row.get("action") or "").strip()
        hilist = (row.get("hilist_code") or "").strip()
        project = (row.get("project_code") or "").strip()
        lv = (row.get("chrgitm_lv") or "").strip()
        begin = (row.get("begin_date") or "").strip()
        end = (row.get("end_date") or "").strip()
        ext = row.get("ext_json") or ""

        if not any((action, hilist, project, (row.get("hilist_name") or "").strip())):
            empty_rows += 1
            continue

        acts[action or "(empty)"] += 1
        if action not in ALL_ACTIONS:
            bad_action += 1
        elif doc_kind == "PAYMENT_MAP" and action not in PAYMENT_ACTIONS:
            pay_not_set += 1
        elif doc_kind == "CATALOG_DELTA" and action not in CATALOG_ACTIONS:
            catalog_has_set += 1

        if lv and lv not in {"1", "2", "3"}:
            bad_lv += 1

        try:
            parsed = json.loads(ext) if ext.strip() else {}
            if not isinstance(parsed, dict):
                bad_ext += 1
        except Exception:
            bad_ext += 1

        for dval, label in ((begin, "begin_date"), (end, "end_date")):
            if dval and not DATE_RE.match(dval):
                bad_date += 1
                break

        if hilist.startswith("01") or hilist.startswith("37"):
            hilist_01 += 1

        if "未赋码" in hilist or "未赋码" in project:
            dirty_code += 1
        elif any("\u4e00" <= ch <= "\u9fff" for ch in hilist + project):
            dirty_code += 1

        csv_name = (row.get("hilist_name") or "").strip()
        if not csv_name:
            try:
                parsed_ext = json.loads(ext) if ext.strip() else {}
            except Exception:
                parsed_ext = {}
            if isinstance(parsed_ext, dict) and (parsed_ext.get("医疗服务项目名称") or "").strip():
                name_in_ext_only += 1

        if action == "ADD" and category in {"SERVICE", "SERVICE_F"}:
            price_on_hilist = hilist.startswith("01") or hilist.startswith("37")
            if not project and not price_on_hilist:
                svc_add_bad += 1

        if action == "SET_PAY_CLASS":
            if not project or lv not in {"1", "2", "3"}:
                set_pay_bad += 1

        if action in {"STOP", "MODIFY", "PROVINCE_MODIFY"}:
            if category in {"SERVICE", "SERVICE_F"}:
                # 局发变更可能仅有项目代码 01*（无国家码）；与 ADD 一样只认 project_code
                if not hilist and not project:
                    need_hilist_empty += 1
            elif not hilist:
                need_hilist_empty += 1
        if action == "ADD" and category not in {"SERVICE", "SERVICE_F"} and not hilist:
            need_hilist_empty += 1

        if action == "STOP" and not end:
            stop_no_end += 1

    if empty_rows:
        add_issue(issues, "WARNING", "EMPTY_ROW", "空数据行", empty_rows)
    if bad_action:
        add_issue(issues, "ERROR", "BAD_ACTION", "非法 action", bad_action)
    if pay_not_set:
        add_issue(
            issues, "ERROR", "PAY_ACTION", "PAYMENT_MAP 行 action 非 SET_PAY_CLASS", pay_not_set
        )
    if catalog_has_set:
        add_issue(
            issues,
            "ERROR",
            "CATALOG_ACTION",
            "CATALOG_DELTA 出现 SET_PAY_CLASS 等非法动作",
            catalog_has_set,
        )
    if bad_lv:
        add_issue(issues, "ERROR", "BAD_LV", "chrgitm_lv 非 1/2/3/空", bad_lv)
    if bad_ext:
        add_issue(issues, "ERROR", "BAD_EXT", "ext_json 非法", bad_ext)
    if bad_date:
        add_issue(issues, "ERROR", "BAD_DATE", "行日期非 yyyy-MM-dd", bad_date)
    if hilist_01:
        add_issue(
            issues,
            "WARNING",
            "HILIST_01",
            "hilist_code 疑为物价码 01*/37*（解析会挪列，洗数应直接写 project_code）",
            hilist_01,
        )
    if dirty_code:
        add_issue(
            issues,
            "WARNING",
            "DIRTY_CODE",
            "编码含未赋码或汉字，扫描将跳行",
            dirty_code,
        )
    if name_in_ext_only:
        add_issue(
            issues,
            "WARNING",
            "NAME_IN_EXT",
            "名称只在 ext_json.医疗服务项目名称，应落到 hilist_name",
            name_in_ext_only,
        )
    if svc_add_bad:
        add_issue(
            issues,
            "ERROR",
            "SVC_ADD",
            "服务类 ADD 应 project_code 有值且 hilist_code 空",
            svc_add_bad,
        )
    if set_pay_bad:
        add_issue(
            issues,
            "ERROR",
            "SET_PAY",
            "SET_PAY_CLASS 缺 project_code 或 chrgitm_lv",
            set_pay_bad,
        )
    if need_hilist_empty:
        add_issue(
            issues,
            "ERROR",
            "NEED_HILIST",
            "停用/变更/非服务新增 缺 hilist_code（或非服务 ADD 缺码）",
            need_hilist_empty,
        )
    if stop_no_end:
        add_issue(issues, "WARNING", "STOP_NO_END", "STOP 无 end_date", stop_no_end)
    if len(rows) == 0:
        add_issue(issues, "ERROR", "NO_ROWS", "CSV 无数据行")

    return _pack_result(stem, meta, len(rows), dict(acts), issues)


def _pack_result(stem, meta, rows, actions, issues) -> dict:
    err = sum(1 for i in issues if i["level"] == "ERROR")
    warn = sum(1 for i in issues if i["level"] == "WARNING")
    return {
        "stem": stem,
        "doc_kind": (meta or {}).get("doc_kind"),
        "category": (meta or {}).get("category"),
        "rows": rows,
        "actions": actions,
        "error_count": err,
        "warn_count": warn,
        "status": "FAIL" if err else ("WARN" if warn else "OK"),
        "issues": issues,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="全量校验标准化落地包")
    ap.add_argument(
        "--json-out",
        type=Path,
        default=Path(__file__).resolve().parent / "audit_report.json",
        help="报告输出路径",
    )
    args = ap.parse_args()

    packs = sorted([p for p in STD.iterdir() if p.is_dir()]) if STD.exists() else []
    pack_results = [audit_pack(p) for p in packs]
    archived = list_archived_stems()
    pack_stems = {r["stem"] for r in pack_results}

    # 目录名可能 trim 尾空格/下划线 —— 归一化后对齐
    archived_norm = {norm_stem(s): s for s in archived}
    pack_norm = {norm_stem(s): s for s in pack_stems}

    missing_packs = []
    for norm, raw in sorted(archived_norm.items()):
        if norm not in pack_norm:
            missing_packs.append(raw)

    orphan_packs = []
    for norm, raw in sorted(pack_norm.items()):
        if norm not in archived_norm:
            orphan_packs.append(raw)

    coverage = {
        "archived_source_count": len(archived),
        "standard_pack_count": len(pack_stems),
        "missing_standard_pack": missing_packs,
        "orphan_standard_pack": orphan_packs,
        "unconverted": list_unconverted(),
        "root_pending": list_root_pending(),
    }

    ok = sum(1 for r in pack_results if r["status"] == "OK")
    warn = sum(1 for r in pack_results if r["status"] == "WARN")
    fail = sum(1 for r in pack_results if r["status"] == "FAIL")
    cov_errors = len(missing_packs)

    report = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "root": str(ROOT),
        "summary": {
            "packs_ok": ok,
            "packs_warn": warn,
            "packs_fail": fail,
            "coverage_missing_packs": cov_errors,
            "coverage_orphan_packs": len(orphan_packs),
            "root_pending": len(coverage["root_pending"]),
            "unconverted": len(coverage["unconverted"]),
        },
        "coverage": coverage,
        "packs": pack_results,
    }
    out_path: Path = args.json_out
    out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"ROOT={ROOT}")
    print(
        f"PACKS: OK={ok} WARN={warn} FAIL={fail} total={len(pack_results)}"
    )
    print(
        f"COVERAGE: archived={len(archived)} packs={len(pack_stems)} "
        f"missing_pack={len(missing_packs)} orphan_pack={len(orphan_packs)} "
        f"root_pending={len(coverage['root_pending'])} unconverted={len(coverage['unconverted'])}"
    )
    print(f"REPORT={out_path}")

    if missing_packs:
        print("\n[遗漏] 原始文件有原件但无标准包:")
        for s in missing_packs[:50]:
            print(f"  - {s}")
        if len(missing_packs) > 50:
            print(f"  ... +{len(missing_packs)-50}")

    if orphan_packs:
        print("\n[孤儿] 有标准包但原始文件未见对应原件:")
        for s in orphan_packs[:50]:
            print(f"  - {s}")

    if coverage["root_pending"]:
        print("\n[待洗] 根目录仍有附件:")
        for n in coverage["root_pending"]:
            print(f"  - {n}")

    if coverage["unconverted"]:
        print("\n[_无法转换] 残留:")
        for n in coverage["unconverted"][:30]:
            print(f"  - {n}")

    fails = [r for r in pack_results if r["status"] == "FAIL"]
    if fails:
        print("\n[ERROR 包]")
        for r in fails:
            codes = ", ".join(f"{i['code']}*{i['count']}" for i in r["issues"] if i["level"] == "ERROR")
            print(f"  FAIL {r['stem']}: {codes}")

    warns = [r for r in pack_results if r["status"] == "WARN"]
    if warns:
        print(f"\n[WARN 包] {len(warns)} 个（详见 JSON）")

    has_error = fail > 0 or cov_errors > 0
    return 1 if has_error else 0


if __name__ == "__main__":
    raise SystemExit(main())
