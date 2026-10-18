# -*- coding: utf-8 -*-
"""1C 返工：修复 hilist_code 误填 01*；覆盖命名；无法转换标注。"""
from __future__ import annotations

import csv
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STD = ROOT / "标准化文件"
ARCH = ROOT / "原始文件"
UNCONV = ARCH / "_无法转换"

REQUIRED = [
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
]

FAIL_STEMS = [
    "医疗服务数据更新目录(Z2024.7.1)",
    "附件：F服务目录15号文数据更新目录（2025.11.17）",
    "附件：F服务目录数据更新目录（2025.12.15）",
    "附件：F服务目录数据更新目录（2026.3.24）",
    "附件：F服务目录数据更新目录（2026.3.24） (1)",
]


def fix_hilist01(stem: str) -> dict:
    pack = STD / stem
    csv_path = pack / "notice.lines.csv"
    meta_path = pack / "notice.meta.json"
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    fixed = 0
    for row in rows:
        hilist = (row.get("hilist_code") or "").strip()
        project = (row.get("project_code") or "").strip()
        if not hilist.startswith("01"):
            continue
        if not project:
            row["project_code"] = hilist
        elif project != hilist:
            # 保留已有 project；原误填 hilist 进 ext
            try:
                ext = json.loads(row.get("ext_json") or "{}")
            except Exception:
                ext = {}
            if not isinstance(ext, dict):
                ext = {}
            ext["washed_from_hilist_code"] = hilist
            row["ext_json"] = json.dumps(ext, ensure_ascii=False, separators=(",", ":"))
        row["hilist_code"] = ""
        fixed += 1

    with csv_path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=REQUIRED, extrasaction="ignore")
        w.writeheader()
        for row in rows:
            out = {k: (row.get(k) or "") for k in REQUIRED}
            if not out["ext_json"]:
                out["ext_json"] = "{}"
            w.writerow(out)

    note = meta.get("wash_note") or ""
    addon = "1C返工：服务类行误填 hilist 的01*已迁入 project_code，hilist_code 置空。"
    if addon not in note:
        meta["wash_note"] = (note + "；" + addon).strip("；")
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"stem": stem, "fixed_rows": fixed, "total": len(rows)}


def restore_device_dup_archive() -> str | None:
    """耗材 2026.5.9 (1) 标准包在、原件丢失（与无(1)内容相同）→ 从无(1)复原归档名。"""
    src = ARCH / "附件：耗材数据更新目录（2026.5.9）.xlsx"
    dst = ARCH / "附件：耗材数据更新目录（2026.5.9） (1).xlsx"
    if dst.exists():
        return None
    if not src.exists():
        return "missing source for device dup"
    shutil.copy2(src, dst)
    return f"copied {src.name} -> {dst.name}"


def annotate_unconverted() -> list[str]:
    notes = []
    mapping = [
        (
            UNCONV / "耗材数据更新（H2024.7.1）" / "reason.txt",
            "伪 xlsx（BadZipFile）无法解析。已由另存 `耗材数据更新（H2024.7.1）.xls` 洗成标准包：标准化文件/耗材数据更新（H2024.7.1）/；原件归档于 原始文件/耗材数据更新（H2024.7.1）.xls。本目录保留失败原件备查。\n",
        ),
        (
            UNCONV / "诊疗目录新增(2024.7.8).et" / "reason.txt",
            ".et 无法解析。已由另存 `诊疗目录新增(2024.7.8).xls` 洗成标准包：标准化文件/诊疗目录新增(2024.7.8)/；原件归档于 原始文件/诊疗目录新增(2024.7.8).xls。本目录保留失败原件备查。\n",
        ),
    ]
    for path, text in mapping:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        notes.append(str(path.relative_to(ROOT)))
    return notes


def main() -> None:
    results = [fix_hilist01(s) for s in FAIL_STEMS]
    dup = restore_device_dup_archive()
    notes = annotate_unconverted()
    out = {
        "hilist01_fixes": results,
        "device_dup_archive": dup,
        "unconverted_notes": notes,
    }
    Path(__file__).with_name("fix_1c_remediation_summary.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
